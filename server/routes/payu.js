const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { getFirestore } = require('../utils/firebase');
const authMiddleware = require('../middleware/authMiddleware');
const { requireAdminRole } = require('../middleware/adminAuthMiddleware');
const { FieldValue, Timestamp } = require('firebase-admin/firestore');

// Environment Credentials
const PAYU_KEY = process.env.PAYU_KEY;
const PAYU_SALT = process.env.PAYU_SALT;
const PAYU_MODE = (process.env.PAYU_MODE || 'test').toLowerCase();
const PAYU_ACTION_URL = PAYU_MODE === 'live' 
  ? 'https://secure.payu.in/_payment' 
  : 'https://test.payu.in/_payment';

const BASE_URL = process.env.PAYU_BASE_URL || 'https://our-bloom.onrender.com';
const CLIENT_URL = process.env.CLIENT_URL || 'https://our-bloom-gamma.vercel.app';
const ENABLE_PRODUCTION_PAYOUTS = process.env.ENABLE_PRODUCTION_PAYOUTS === 'true';

if (!PAYU_KEY || !PAYU_SALT) {
  console.warn('⚠️ PAYU_KEY or PAYU_SALT is not configured in environment. Payment operations will fail.');
}

// Configurable Financial Product Limits
const CONFIG = {
  MIN_DEPOSIT_PAISE: 100n, // ₹1.00 minimum deposit
  MAX_DEPOSIT_PAISE: 50000000n, // ₹500,000.00 configured product ceiling (5 Lakh INR)
  MAX_SAFE_PAISE: BigInt(Number.MAX_SAFE_INTEGER),
  PLATFORM_FEE_BPS: 200n, // 2.00%
  INTENT_EXPIRATION_MS: 30 * 60 * 1000 // 30 minutes
};

/**
 * Validates and sanitizes a financial paise integer before write
 */
function assertSafePaise(value) {
  if (typeof value === 'bigint') {
    if (value < 0n || value > CONFIG.MAX_SAFE_PAISE) {
      throw new Error(`Invalid paise value: ${value}`);
    }
    return Number(value);
  }
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Invalid paise value: ${value}`);
  }
  return value;
}

/**
 * Validates existing Firestore balance fields prior to arithmetic
 */
function readSafePaise(value, fieldName = 'paise') {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Corrupt or invalid wallet field: ${fieldName} (${value})`);
  }
  if (value > Number(CONFIG.MAX_SAFE_PAISE)) {
    throw new Error(`Wallet balance ceiling exceeded in field: ${fieldName}`);
  }
  return value;
}

/**
 * Parses INR decimal string into integer paise via BigInt
 * Zero floating-point conversions
 */
function parseRupeesToPaise(value) {
  const amount = String(value || '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(amount)) {
    throw new Error('Invalid INR amount format');
  }
  const [rupees, paise = ''] = amount.split('.');
  const grossPaise = BigInt(rupees) * 100n + BigInt((paise + '00').slice(0, 2));

  if (grossPaise < CONFIG.MIN_DEPOSIT_PAISE) {
    throw new Error('Deposit amount must be at least ₹1.00');
  }
  if (grossPaise > CONFIG.MAX_DEPOSIT_PAISE || grossPaise > CONFIG.MAX_SAFE_PAISE) {
    throw new Error('Deposit amount exceeds configured limit of ₹500,000.00');
  }
  return grossPaise;
}

/**
 * Authoritative Fee-on-Top calculation in pure integer paise
 * Invariant: payableAmountPaise === vaultAmountPaise + platformFeePaise
 */
function calculateDepositAmounts(vaultAmountPaise) {
  const vaultBigInt = typeof vaultAmountPaise === 'bigint' ? vaultAmountPaise : BigInt(vaultAmountPaise);
  assertSafePaise(Number(vaultBigInt));

  if (
    vaultBigInt < CONFIG.MIN_DEPOSIT_PAISE ||
    vaultBigInt > CONFIG.MAX_DEPOSIT_PAISE
  ) {
    throw new Error('Deposit amount out of bounds');
  }

  // Exact round-half-up integer division: (vault * 200 + 5000) / 10000
  const platformFeePaise = (vaultBigInt * CONFIG.PLATFORM_FEE_BPS + 5000n) / 10000n;
  const payableAmountPaise = vaultBigInt + platformFeePaise;

  assertSafePaise(Number(platformFeePaise));
  assertSafePaise(Number(payableAmountPaise));

  return {
    vaultAmountPaise: Number(vaultBigInt),
    platformFeePaise: Number(platformFeePaise),
    payableAmountPaise: Number(payableAmountPaise),
    platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
    pricingModel: 'FEE_ON_TOP'
  };
}

/**
 * Historical 2% fee breakdown computation in integer paise (Fee-Deducted Model)
 * Preserved for immutable historical audits
 * Invariant: grossAmountPaise === platformFeePaise + netVaultCreditPaise
 */
function calculateDeposit(grossAmountPaiseBigInt) {
  if (
    typeof grossAmountPaiseBigInt !== 'bigint' ||
    grossAmountPaiseBigInt < CONFIG.MIN_DEPOSIT_PAISE ||
    grossAmountPaiseBigInt > CONFIG.MAX_DEPOSIT_PAISE
  ) {
    throw new Error('Invalid or out-of-bounds deposit amount');
  }

  // Exact round-half-up integer division: (gross * 200 + 5000) / 10000
  const platformFeePaiseBigInt = (grossAmountPaiseBigInt * CONFIG.PLATFORM_FEE_BPS + 5000n) / 10000n;
  const netVaultCreditPaiseBigInt = grossAmountPaiseBigInt - platformFeePaiseBigInt;

  return {
    grossAmountPaise: assertSafePaise(Number(grossAmountPaiseBigInt)),
    platformFeePaise: assertSafePaise(Number(platformFeePaiseBigInt)),
    netVaultCreditPaise: assertSafePaise(Number(netVaultCreditPaiseBigInt)),
    platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
    pricingModel: 'FEE_DEDUCTED'
  };
}

/**
 * Generates PayU payment hash
 * Formula: sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT)
 */
function generatePayUHash(params) {
  if (!PAYU_KEY || !PAYU_SALT) {
    throw new Error('Payment gateway credentials not configured');
  }
  const { txnid, amount, productinfo, firstname, email, udf1 = '', udf2 = '', udf3 = '', udf4 = '', udf5 = '' } = params;
  const formattedAmount = parseFloat(amount).toFixed(2);
  const hashString = `${PAYU_KEY}|${txnid}|${formattedAmount}|${productinfo}|${firstname}|${email}|${udf1}|${udf2}|${udf3}|${udf4}|${udf5}||||||${PAYU_SALT}`;
  return crypto.createHash('sha512').update(hashString).digest('hex');
}

/**
 * Verifies PayU reverse response hash
 * Formula: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
 * Or with additionalCharges: sha512(additionalCharges|SALT|status...)
 */
function verifyPayUResponseHash(body) {
  if (!PAYU_KEY || !PAYU_SALT) return false;

  const {
    status,
    txnid,
    amount,
    productinfo,
    firstname,
    email,
    udf1 = '',
    udf2 = '',
    udf3 = '',
    udf4 = '',
    udf5 = '',
    additionalCharges,
    hash: receivedHash
  } = body;

  if (!receivedHash) return false;

  let hashString = '';
  if (additionalCharges) {
    hashString = `${additionalCharges}|${PAYU_SALT}|${status}||||||${udf5}|${udf4}|${udf3}|${udf2}|${udf1}|${email}|${firstname}|${productinfo}|${amount}|${txnid}|${PAYU_KEY}`;
  } else {
    hashString = `${PAYU_SALT}|${status}||||||${udf5}|${udf4}|${udf3}|${udf2}|${udf1}|${email}|${firstname}|${productinfo}|${amount}|${txnid}|${PAYU_KEY}`;
  }

  const computedHash = crypto.createHash('sha512').update(hashString).digest('hex');
  return computedHash.toLowerCase() === String(receivedHash).toLowerCase();
}

/**
 * GET /api/payu/checkout
 * Hosted checkout initiator with safe parsing
 */
router.get('/checkout', (req, res) => {
  try {
    if (!PAYU_KEY || !PAYU_SALT) {
      return res.status(503).send('Payment gateway configuration is missing.');
    }

    const { 
      amount, 
      coupleId, 
      userId = '', 
      userName = 'Partner', 
      userEmail = '', 
      userPhone = '',
      note = 'Couple Vault Deposit', 
      goalId = '', 
      goalTitle = '' 
    } = req.query;

    const vaultPaiseBigInt = parseRupeesToPaise(amount);
    const depositBreakdown = calculateDepositAmounts(vaultPaiseBigInt);

    if (!coupleId) {
      return res.status(400).send('Missing coupleId for deposit.');
    }

    const txnid = `OB_DEP_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
    const productinfo = (goalTitle ? `Vault ${goalTitle}` : 'Our Bloom Vault').replace(/[^a-zA-Z0-9 ]/g, '').trim().substring(0, 50) || 'Our Bloom Vault';
    const cleanFirstName = String(userName || 'Partner').replace(/[^a-zA-Z0-9]/g, '').trim() || 'Partner';
    const cleanEmail = (String(userEmail).trim() && userEmail.includes('@')) ? userEmail.trim() : 'support@ourbloom.app';
    const digitsOnly = String(userPhone || '').replace(/\D/g, '');
    const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : '9999999999';
    const cleanNote = String(note || '').replace(/[|\n\r]/g, ' ').trim().substring(0, 100);

    const formattedAmount = (depositBreakdown.payableAmountPaise / 100).toFixed(2);

    const params = {
      key: PAYU_KEY,
      txnid,
      amount: formattedAmount,
      productinfo,
      firstname: cleanFirstName,
      email: cleanEmail,
      phone: cleanPhone,
      surl: `${BASE_URL}/api/payu/success`,
      furl: `${BASE_URL}/api/payu/failure`,
      udf1: coupleId,
      udf2: userId || '',
      udf3: cleanFirstName,
      udf4: goalId || '',
      udf5: cleanNote
    };

    const hash = generatePayUHash(params);
    params.hash = hash;

    res.send(`
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Securing Payment...</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #FFF8F9; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; text-align: center; color: #2D2D2D; }
          .card { background: #FFFFFF; padding: 36px 24px; border-radius: 28px; box-shadow: 0 12px 40px rgba(232, 93, 117, 0.15); max-width: 400px; width: 100%; border: 1px solid #FCE4EC; }
          .spinner { width: 50px; height: 50px; border: 4px solid #FCE4EC; border-top: 4px solid #E85D75; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 20px; }
          @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
          h2 { font-size: 20px; margin: 0 0 8px; color: #E85D75; }
          p { font-size: 14px; color: #666; margin: 0 0 20px; }
          .breakdown { background: #FFF0F3; border-radius: 16px; padding: 14px; margin-bottom: 24px; text-align: left; font-size: 13px; }
          .row { display: flex; justify-content: space-between; margin-bottom: 6px; }
          .row:last-child { margin-bottom: 0; border-top: 1px dashed #F8BBD0; padding-top: 6px; font-weight: bold; }
          .btn-continue { background: #E85D75; color: #FFFFFF; border: none; padding: 14px 28px; border-radius: 25px; font-size: 15px; font-weight: bold; cursor: pointer; }
        </style>
      </head>
      <body onload="document.forms['payuForm'].submit()">
        <div class="card">
          <div class="spinner"></div>
          <h2>Securing Your Payment...</h2>
          <p>Connecting to Secure Gateway for Couple's Savings Vault 🌸</p>
          <div class="breakdown">
            <div class="row"><span>Amount to add to Vault:</span><span>₹${(depositBreakdown.vaultAmountPaise / 100).toFixed(2)}</span></div>
            <div class="row"><span>Platform Service Fee (2%):</span><span>+ ₹${(depositBreakdown.platformFeePaise / 100).toFixed(2)}</span></div>
            <div class="row"><span>Total Payable:</span><span>₹${(depositBreakdown.payableAmountPaise / 100).toFixed(2)}</span></div>
          </div>
          <form name="payuForm" method="POST" action="${PAYU_ACTION_URL}">
            ${Object.entries(params).map(([k, v]) => `<input type="hidden" name="${k}" value="${String(v).replace(/"/g, '&quot;')}" />`).join('\n            ')}
            <noscript>
              <button type="submit" class="btn-continue">Tap to Proceed to Payment</button>
            </noscript>
          </form>
        </div>
      </body>
      </html>
    `);
  } catch (err) {
    console.error('✿ Error in /api/payu/checkout:', err);
    res.status(400).send('Error initiating payment: ' + err.message);
  }
});

/**
 * POST /api/payu/create-payment
 * Authenticated intent creation with SHA-256 request fingerprinting and 409 Conflict validation
 */
router.post('/create-payment', authMiddleware, async (req, res) => {
  try {
    if (!PAYU_KEY || !PAYU_SALT) {
      return res.status(503).json({ error: 'Payment gateway configuration missing' });
    }

    const { 
      amount, 
      coupleId, 
      userName = 'Partner', 
      userEmail = 'support@ourbloom.app', 
      userPhone = '9999999999', 
      note = 'Couple Vault Deposit', 
      goalId = '', 
      goalTitle = '' 
    } = req.body;

    if (!coupleId) {
      return res.status(400).json({ error: 'coupleId is required' });
    }

    // Verify authenticated user belongs to couple
    if (req.user.coupleId && req.user.coupleId.toString() !== coupleId.toString()) {
      return res.status(403).json({ error: 'You do not belong to this couple' });
    }

    const vaultAmountPaiseBigInt = parseRupeesToPaise(amount);
    const depositBreakdown = calculateDepositAmounts(vaultAmountPaiseBigInt);

    // Idempotency-Key handling & validation
    const rawIdempotencyKey = req.headers['idempotency-key'] || req.body.idempotencyKey;
    let idempotencyKey = rawIdempotencyKey ? String(rawIdempotencyKey).trim() : null;

    if (idempotencyKey) {
      if (!/^[a-zA-Z0-9_-]{1,128}$/.test(idempotencyKey)) {
        return res.status(400).json({ error: 'Invalid Idempotency-Key format. Must be alphanumeric with dashes/underscores (1-128 chars).' });
      }
    } else {
      idempotencyKey = `REQ_${Date.now()}_${crypto.randomBytes(8).toString('hex')}`;
    }

    // Hashed safe document ID for idempotency records
    const userIdentifier = req.user.firebaseUid || req.user.userId.toString();
    const requestId = crypto
      .createHash('sha256')
      .update(`${userIdentifier}:${idempotencyKey}`)
      .digest('hex');

    // Request fingerprint for collision detection with CONFIG.PLATFORM_FEE_BPS
    const requestHash = crypto
      .createHash('sha256')
      .update(`${coupleId}|${depositBreakdown.vaultAmountPaise}|INR|${CONFIG.PLATFORM_FEE_BPS}`)
      .digest('hex');

    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ error: 'Firestore database unavailable' });
    }

    const reqDocRef = db.collection('payment_intent_requests').doc(requestId);
    let txnid = null;
    let params = null;
    let hash = null;
    let conflict = false;

    await db.runTransaction(async (transaction) => {
      const existingReq = await transaction.get(reqDocRef);

      if (existingReq.exists) {
        const data = existingReq.data();
        if (
          data.requestHash !== requestHash ||
          data.coupleId !== coupleId ||
          (data.vaultAmountPaise !== depositBreakdown.vaultAmountPaise && data.expectedAmountPaise !== depositBreakdown.payableAmountPaise) ||
          data.currency !== 'INR'
        ) {
          conflict = true;
          return;
        }

        // Return existing intent
        txnid = data.txnid;
        const intentDoc = await transaction.get(db.collection('payment_intents').doc(txnid));
        if (intentDoc.exists) {
          const iData = intentDoc.data();
          const cleanFirstName = String(userName || 'Partner').replace(/[^a-zA-Z0-9]/g, '').trim() || 'Partner';
          const cleanEmail = (String(userEmail).trim() && userEmail.includes('@')) ? userEmail.trim() : 'support@ourbloom.app';
          const digitsOnly = String(userPhone || '').replace(/\D/g, '');
          const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : '9999999999';
          const cleanNote = String(note || '').replace(/[|\n\r]/g, ' ').trim().substring(0, 100);

          const payablePaise = iData.payableAmountPaise || iData.expectedAmountPaise;
          params = {
            key: PAYU_KEY,
            txnid,
            amount: (payablePaise / 100).toFixed(2),
            productinfo: iData.productinfo || 'Our Bloom Vault',
            firstname: cleanFirstName,
            email: cleanEmail,
            phone: cleanPhone,
            surl: `${BASE_URL}/api/payu/success`,
            furl: `${BASE_URL}/api/payu/failure`,
            udf1: coupleId,
            udf2: req.user.userId.toString(),
            udf3: cleanFirstName,
            udf4: goalId || '',
            udf5: cleanNote
          };
          hash = generatePayUHash(params);
          params.hash = hash;
          return;
        }
      }

      // Generate new intent
      txnid = `OB_DEP_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
      const productinfo = (goalTitle ? `Vault ${goalTitle}` : 'Our Bloom Vault').replace(/[^a-zA-Z0-9 ]/g, '').trim().substring(0, 50) || 'Our Bloom Vault';
      const cleanFirstName = String(userName || 'Partner').replace(/[^a-zA-Z0-9]/g, '').trim() || 'Partner';
      const cleanEmail = (String(userEmail).trim() && userEmail.includes('@')) ? userEmail.trim() : 'support@ourbloom.app';
      const digitsOnly = String(userPhone || '').replace(/\D/g, '');
      const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : '9999999999';
      const cleanNote = String(note || '').replace(/[|\n\r]/g, ' ').trim().substring(0, 100);

      const now = Timestamp.now();
      const expiresAt = Timestamp.fromMillis(now.toMillis() + CONFIG.INTENT_EXPIRATION_MS);

      const intentRef = db.collection('payment_intents').doc(txnid);
      transaction.set(intentRef, {
        txnid,
        coupleId,
        userId: req.user.userId.toString(),
        vaultAmountPaise: depositBreakdown.vaultAmountPaise,
        platformFeePaise: depositBreakdown.platformFeePaise,
        payableAmountPaise: depositBreakdown.payableAmountPaise,
        expectedAmountPaise: depositBreakdown.payableAmountPaise,
        platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
        currency: 'INR',
        pricingModel: 'FEE_ON_TOP',
        productinfo,
        firstname: cleanFirstName,
        email: cleanEmail,
        status: 'INITIATED',
        createdAt: now,
        expiresAt
      });

      transaction.set(reqDocRef, {
        requestId,
        idempotencyKey,
        userId: userIdentifier,
        coupleId,
        vaultAmountPaise: depositBreakdown.vaultAmountPaise,
        platformFeePaise: depositBreakdown.platformFeePaise,
        payableAmountPaise: depositBreakdown.payableAmountPaise,
        expectedAmountPaise: depositBreakdown.payableAmountPaise,
        platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
        currency: 'INR',
        requestHash,
        txnid,
        createdAt: now
      });

      params = {
        key: PAYU_KEY,
        txnid,
        amount: (depositBreakdown.payableAmountPaise / 100).toFixed(2),
        productinfo,
        firstname: cleanFirstName,
        email: cleanEmail,
        phone: cleanPhone,
        surl: `${BASE_URL}/api/payu/success`,
        furl: `${BASE_URL}/api/payu/failure`,
        udf1: coupleId,
        udf2: req.user.userId.toString(),
        udf3: cleanFirstName,
        udf4: goalId || '',
        udf5: cleanNote
      };
      hash = generatePayUHash(params);
      params.hash = hash;
    });

    if (conflict) {
      return res.status(409).json({
        error: 'IDEMPOTENCY_KEY_PARAM_MISMATCH',
        message: 'The Idempotency-Key was already used with different payment parameters.'
      });
    }

    res.json({
      success: true,
      actionUrl: PAYU_ACTION_URL,
      params,
      mode: PAYU_MODE,
      financials: depositBreakdown
    });
  } catch (err) {
    console.error('✿ Error in /api/payu/create-payment:', err);
    res.status(400).json({ error: err.message || 'Failed to create payment session' });
  }
});

/**
 * GET /api/payu/payment-status/:txnid
 * Sanitized client payment intent polling endpoint (strictly strips sensitive gateway keys/hashes)
 */
router.get('/payment-status/:txnid', authMiddleware, async (req, res) => {
  try {
    const { txnid } = req.params;
    if (!txnid) {
      return res.status(400).json({ error: 'Missing transaction ID' });
    }

    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ error: 'Database unavailable' });
    }

    const intentDoc = await db.collection('payment_intents').doc(txnid).get();
    if (!intentDoc.exists) {
      return res.status(404).json({ error: 'Payment intent not found' });
    }

    const data = intentDoc.data();

    // Verify user belongs to couple
    if (req.user.coupleId && req.user.coupleId.toString() !== data.coupleId.toString()) {
      return res.status(403).json({ error: 'Access denied to this payment intent' });
    }

    // Return sanitized DTO only (never exposes secrets, salt, raw hashes, customer PII, or internal reconciliation notes)
    const vaultAmountPaise = data.vaultAmountPaise ?? data.netVaultCreditPaise ?? null;
    const platformFeePaise = data.platformFeePaise ?? null;
    const payableAmountPaise = data.payableAmountPaise ?? data.expectedAmountPaise ?? null;
    const pricingModel = data.pricingModel || (data.netVaultCreditPaise ? 'FEE_DEDUCTED' : 'FEE_ON_TOP');

    res.json({
      txnid: data.txnid,
      coupleId: data.coupleId,
      status: data.status, // INITIATED | PROCESSING | COMPLETED | FAILED | EXPIRED | REFUNDED
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      pricingModel,
      netVaultCreditPaise: vaultAmountPaise, // backward-compat field
      updatedAt: data.updatedAt || data.completedAt || data.createdAt
    });
  } catch (err) {
    console.error('✿ Error in /api/payu/payment-status:', err);
    res.status(500).json({ error: 'Failed to retrieve payment status' });
  }
});

/**
 * POST /api/payu/success
 * Webhook handler with reverse hash validation, atomic deduplication, and integer-paise wallet crediting
 */
router.post('/success', async (req, res) => {
  try {
    const {
      key,
      status,
      txnid,
      amount: amountStr,
      mihpayid,
      bank_ref_num,
      udf1: coupleId,
      udf2: userId
    } = req.body;

    // 1. Validate Merchant Key
    if (!PAYU_KEY || key !== PAYU_KEY) {
      console.error('⚠️ PayU callback merchant key mismatch or missing!');
      return res.status(401).json({ error: 'INVALID_MERCHANT_KEY' });
    }

    // 2. Validate SHA-512 Reverse Hash
    const isVerified = verifyPayUResponseHash(req.body);
    if (!isVerified) {
      console.error('⚠️ PayU response hash verification failed!');
      return res.status(401).json({ error: 'INVALID_SIGNATURE' });
    }

    // 3. Provider Status Check
    if (status !== 'success') {
      console.warn('⚠️ PayU callback status is not success:', status);
      return res.status(400).json({ error: 'PAYMENT_NOT_SUCCESSFUL' });
    }

    // 4. Currency Check
    if (req.body.currency && req.body.currency !== 'INR') {
      return res.status(400).json({ error: 'INVALID_CURRENCY' });
    }

    const callbackAmountPaise = parseRupeesToPaise(amountStr);
    const db = getFirestore();

    if (!db || !txnid) {
      return res.status(500).json({ error: 'INTERNAL_ERROR' });
    }

    // 5. Atomic Multi-Document Firestore Transaction
    let outcome = 'SUCCESS';
    let creditedPaise = 0;
    let netPaise = 0;
    let feePaise = 0;

    await db.runTransaction(async (transaction) => {
      // A. Check gateway event deduplication
      const eventRef = db.collection('gateway_events').doc(txnid);
      const eventDoc = await transaction.get(eventRef);
      if (eventDoc.exists && eventDoc.data().status === 'PROCESSED') {
        outcome = 'ALREADY_PROCESSED';
        return;
      }

      // B. Check immutable ledger existence
      const ledgerRef = db.collection('savings_transactions').doc(`LEDGER_${txnid}`);
      const ledgerDoc = await transaction.get(ledgerRef);
      if (ledgerDoc.exists) {
        outcome = 'LEDGER_EXISTS';
        return;
      }

      // C. Read and validate payment intent
      const intentRef = db.collection('payment_intents').doc(txnid);
      const intentDoc = await transaction.get(intentRef);
      if (!intentDoc.exists) {
        outcome = 'INTENT_NOT_FOUND';
        return;
      }

      const intent = intentDoc.data();
      const now = Timestamp.now();

      // D. Check intent expiration
      if (
        intent.status !== 'INITIATED' ||
        !intent.expiresAt ||
        intent.expiresAt.toMillis() <= now.toMillis()
      ) {
        if (intent.status === 'INITIATED') {
          transaction.update(intentRef, {
            status: 'EXPIRED',
            updatedAt: FieldValue.serverTimestamp()
          });
        }
        outcome = 'INTENT_EXPIRED';
        return;
      }

      // E. Exact Amount Invariant Check
      const expectedPayablePaise = BigInt(intent.payableAmountPaise || intent.expectedAmountPaise);
      if (callbackAmountPaise !== expectedPayablePaise) {
        outcome = 'PARAM_MISMATCH';
        return;
      }

      // F. Calculate Authoritative Fee Breakdown based on Pricing Model
      let vaultAmountPaise = 0;
      let platformFeePaise = 0;
      let payableAmountPaise = 0;
      let pricingModel = intent.pricingModel || (intent.vaultAmountPaise ? 'FEE_ON_TOP' : 'FEE_DEDUCTED');

      if (pricingModel === 'FEE_ON_TOP' || intent.vaultAmountPaise) {
        vaultAmountPaise = intent.vaultAmountPaise;
        platformFeePaise = intent.platformFeePaise;
        payableAmountPaise = intent.payableAmountPaise || (vaultAmountPaise + platformFeePaise);
      } else {
        // Historical FEE_DEDUCTED fallback
        const breakdown = calculateDeposit(callbackAmountPaise);
        vaultAmountPaise = breakdown.netVaultCreditPaise;
        platformFeePaise = breakdown.platformFeePaise;
        payableAmountPaise = breakdown.grossAmountPaise;
      }

      // Invariant: payableAmountPaise === vaultAmountPaise + platformFeePaise
      if (payableAmountPaise !== vaultAmountPaise + platformFeePaise) {
        throw new Error('FINANCIAL_INVARIANT_VIOLATION: payableAmountPaise !== vaultAmountPaise + platformFeePaise');
      }

      // Destination couple derived strictly from intent
      const targetCoupleId = intent.coupleId;

      // G. Read and Validate Wallet Balances
      const walletRef = db.collection('savings_wallets').doc(targetCoupleId);
      const walletDoc = await transaction.get(walletRef);
      const wData = walletDoc.exists ? walletDoc.data() : {};

      const currentTotalPaise = readSafePaise(wData.totalBalancePaise ?? Math.round((Number(wData.totalBalance) || 0) * 100), 'totalBalancePaise');
      const currentAvailablePaise = readSafePaise(wData.availableBalancePaise ?? currentTotalPaise, 'availableBalancePaise');
      const currentReservedPaise = readSafePaise(wData.reservedBalancePaise || 0, 'reservedBalancePaise');
      const currentPlatformFeesPaise = readSafePaise(wData.totalPlatformFeesPaise || 0, 'totalPlatformFeesPaise');

      // Credit Vault atomically with intent.vaultAmountPaise ONLY
      const nextTotalPaise = currentTotalPaise + vaultAmountPaise;
      const nextAvailablePaise = currentAvailablePaise + vaultAmountPaise;
      const nextPlatformFeesPaise = currentPlatformFeesPaise + platformFeePaise;

      // Assert write-boundary & transaction invariants
      assertSafePaise(nextTotalPaise);
      assertSafePaise(nextAvailablePaise);
      assertSafePaise(nextPlatformFeesPaise);

      if (nextTotalPaise < nextAvailablePaise || nextTotalPaise < 0 || nextAvailablePaise < 0 || nextPlatformFeesPaise < 0) {
        throw new Error('TRANSACTION_INVARIANT_VIOLATION: Balances cannot be negative or total < available');
      }

      // H. Update Wallet
      transaction.set(walletRef, {
        coupleId: targetCoupleId,
        totalBalancePaise: nextTotalPaise,
        availableBalancePaise: nextAvailablePaise,
        reservedBalancePaise: currentReservedPaise,
        totalPlatformFeesPaise: nextPlatformFeesPaise,
        totalBalance: nextTotalPaise / 100, // @Deprecated display compatibility only
        currency: 'INR',
        version: (wData.version || 0) + 1,
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      // I. Write Immutable Audit Ledger Entry
      transaction.set(ledgerRef, {
        ledgerId: `LEDGER_${txnid}`,
        id: `LEDGER_${txnid}`,
        requestId: txnid,
        coupleId: targetCoupleId,
        userId: intent.userId || 'payu_gateway',
        actorId: intent.userId || 'payu_gateway',
        actorRole: 'gateway_webhook',
        type: 'DEPOSIT',
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
        pricingModel,
        currency: 'INR',
        providerTxnId: mihpayid || null,
        providerBankReference: bank_ref_num || null,
        providerRefundId: null,
        providerReference: mihpayid || null,
        status: 'COMPLETED',
        // Backward compatibility fields
        grossAmountPaise: payableAmountPaise,
        netVaultCreditPaise: vaultAmountPaise,
        gatewayFeePaise: null,
        gatewaySettlementAmountPaise: null,
        settlementStatus: 'PENDING',
        amountPaise: vaultAmountPaise,
        amount: vaultAmountPaise / 100,
        grossAmount: payableAmountPaise / 100,
        platformFee: platformFeePaise / 100,
        netVaultCredit: vaultAmountPaise / 100,
        previousBusinessStatus: 'INITIATED',
        newBusinessStatus: 'COMPLETED',
        previousGatewayStatus: 'PROCESSING',
        newGatewayStatus: 'COMPLETED',
        txnid: txnid,
        platformTransactionId: txnid,
        serverTimestamp: FieldValue.serverTimestamp(),
        idempotencyKey: txnid
      });

      // J. Write Platform Fee Record
      const feeRef = db.collection('platform_fees').doc(`FEE_${txnid}`);
      transaction.set(feeRef, {
        feeId: `FEE_${txnid}`,
        txnid,
        coupleId: targetCoupleId,
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        pricingModel,
        grossAmountPaise: payableAmountPaise,
        platformFeeBps: Number(CONFIG.PLATFORM_FEE_BPS),
        providerReference: mihpayid || null,
        timestamp: FieldValue.serverTimestamp()
      });

      // K. Transition Payment Intent to COMPLETED
      transaction.update(intentRef, {
        status: 'COMPLETED',
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        netVaultCreditPaise: vaultAmountPaise,
        completedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });

      // L. Mark Gateway Event PROCESSED
      transaction.set(eventRef, {
        eventId: `EVENT_${txnid}`,
        providerTxnId: mihpayid || null,
        txnid,
        coupleId: targetCoupleId,
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        grossAmountPaise: payableAmountPaise,
        netVaultCreditPaise: vaultAmountPaise,
        status: 'PROCESSED',
        processedAt: FieldValue.serverTimestamp()
      });
    });

    // Deterministic HTTP response handling
    if (outcome === 'ALREADY_PROCESSED') {
      return res.status(200).json({ status: 'ALREADY_PROCESSED' });
    }
    if (outcome === 'LEDGER_EXISTS') {
      return res.status(200).json({ status: 'LEDGER_EXISTS' });
    }
    if (outcome === 'INTENT_EXPIRED') {
      return res.status(200).json({ status: 'INTENT_EXPIRED' });
    }
    if (outcome === 'INTENT_NOT_FOUND') {
      return res.status(400).json({ error: 'INTENT_NOT_FOUND' });
    }
    if (outcome === 'PARAM_MISMATCH' || outcome === 'AMOUNT_MISMATCH') {
      return res.status(400).json({ error: 'PARAM_MISMATCH' });
    }

    // Render receipt if requested by browser redirect
    if (req.headers.accept && req.headers.accept.includes('text/html')) {
      return res.send(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Payment Successful! 🌸 Our Bloom</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #FFF8F9; text-align: center; padding: 40px 16px; margin: 0; color: #2D2D2D; }
            .card { max-width: 480px; margin: 0 auto; background: #FFFFFF; border-radius: 28px; padding: 40px 24px; box-shadow: 0 10px 40px rgba(232, 93, 117, 0.12); border: 1px solid #F3D9E0; }
            .icon { font-size: 54px; margin-bottom: 16px; }
            h1 { color: #2E7D32; font-size: 26px; margin: 0 0 8px 0; }
            p { color: #666; margin: 8px 0 24px 0; font-size: 15px; }
            .details { background: #F9FBF9; border: 1px solid #C8E6C9; border-radius: 16px; padding: 18px; text-align: left; margin-bottom: 24px; font-size: 14px; }
            .row { display: flex; justify-content: space-between; margin-bottom: 8px; }
            .row:last-child { margin-bottom: 0; border-top: 1px dashed #C8E6C9; padding-top: 8px; font-weight: bold; }
            .label { color: #666; }
            .val { font-weight: bold; color: #2D2D2D; }
            .btn { display: inline-block; background: #E85D75; color: #FFFFFF; text-decoration: none; padding: 14px 32px; border-radius: 30px; font-weight: bold; font-size: 16px; box-shadow: 0 4px 14px rgba(232, 93, 117, 0.3); }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="icon">🌸✨</div>
            <h1>Payment Successful!</h1>
            <p>Your deposit has been verified and added to your Couple's Savings Vault.</p>
            <div class="details">
              <div class="row"><span class="label">Amount added to Vault:</span><span class="val" style="color:#2E7D32;">+ ₹${(creditedPaise / 100).toFixed(2)}</span></div>
              <div class="row"><span class="label">Platform Service Fee (2%):</span><span class="val">+ ₹${(feePaise / 100).toFixed(2)}</span></div>
              <div class="row"><span class="label">Total Paid:</span><span class="val">₹${(netPaise / 100).toFixed(2)}</span></div>
            </div>
            <a href="intent://vault#Intent;scheme=ourbloom;package=com.ourbloom.app;end" class="btn">Return to Our Bloom 💖</a>
          </div>
        </body>
        </html>
      `);
    }

    return res.status(200).json({
      status: 'COMPLETED',
      txnid,
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      pricingModel,
      grossAmountPaise: payableAmountPaise,
      netVaultCreditPaise: vaultAmountPaise
    });
  } catch (err) {
    console.error('✿ Error in PayU success webhook:', err);
    // Return HTTP 500 so PayU safely retries transient failures
    res.status(500).json({ error: 'INTERNAL_ERROR', message: err.message });
  }
});

/**
 * POST /api/payu/failure
 * Webhook handler for aborted or declined payments
 */
router.post('/failure', async (req, res) => {
  const { txnid, error_Message, unmappedstatus } = req.body;
  const errorMsg = error_Message || unmappedstatus || 'Transaction was not completed.';

  try {
    const db = getFirestore();
    if (db && txnid) {
      await db.collection('gateway_events').doc(txnid).set({
        eventId: `EVENT_${txnid}`,
        providerTxnId: txnid,
        status: 'FAILED',
        errorMsg,
        receivedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      await db.collection('payment_intents').doc(txnid).update({
        status: 'FAILED',
        errorMsg,
        updatedAt: FieldValue.serverTimestamp()
      }).catch(() => {});
    }
  } catch (_) {}

  if (req.headers.accept && req.headers.accept.includes('text/html')) {
    return res.send(`
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Payment Incomplete | Our Bloom</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #FFF8F9; text-align: center; padding: 40px 16px; margin: 0; color: #2D2D2D; }
          .card { max-width: 480px; margin: 0 auto; background: #FFFFFF; border-radius: 28px; padding: 40px 24px; box-shadow: 0 10px 40px rgba(0,0,0,0.06); border: 1px solid #F3D9E0; }
          .icon { font-size: 54px; margin-bottom: 16px; }
          h1 { color: #C62828; font-size: 24px; margin: 0 0 8px 0; }
          p { color: #666; margin: 8px 0 24px 0; font-size: 15px; }
          .btn { display: inline-block; background: #E85D75; color: #FFFFFF; text-decoration: none; padding: 14px 32px; border-radius: 30px; font-weight: bold; font-size: 16px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">🌸💔</div>
          <h1>Payment Incomplete</h1>
          <p>${errorMsg}</p>
          <p style="font-size:13px; color:#888;">No funds were deducted. You can try again anytime.</p>
          <a href="intent://vault#Intent;scheme=ourbloom;package=com.ourbloom.app;end" class="btn">Return to Our Bloom</a>
        </div>
      </body>
      </html>
    `);
  }

  res.status(200).json({ status: 'FAILED', message: errorMsg });
});

/**
 * Dispatches refund request to PayU gateway
 */
async function executePayURefund({ txnid, providerTxnId, amountRupees, refundId }) {
  if (PAYU_MODE === 'test' || !PAYU_KEY || !PAYU_SALT || PAYU_KEY.includes('TEST')) {
    return {
      success: true,
      status: 'ACCEPTED',
      providerRefundId: `PAYU_RFND_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`
    };
  }

  try {
    const command = 'cancel_refund_transaction';
    const var1 = providerTxnId || txnid;
    const var2 = refundId;
    const var3 = amountRupees;
    const hashString = `${PAYU_KEY}|${command}|${var1}|${PAYU_SALT}`;
    const hash = crypto.createHash('sha512').update(hashString).digest('hex');

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);

    const postUrl = PAYU_MODE === 'live' 
      ? 'https://info.payu.in/merchant/postservice.php?form=2'
      : 'https://test.payu.in/merchant/postservice.php?form=2';

    const params = new URLSearchParams();
    params.append('key', PAYU_KEY);
    params.append('command', command);
    params.append('hash', hash);
    params.append('var1', var1);
    params.append('var2', var2);
    params.append('var3', var3);

    const response = await fetch(postUrl, {
      method: 'POST',
      body: params,
      signal: controller.signal
    });
    clearTimeout(timeout);

    const data = await response.json().catch(() => ({}));
    if (data.status === 1 || data.status === '1' || data.msg === 'Refund Initiated') {
      return {
        success: true,
        status: 'ACCEPTED',
        providerRefundId: data.result || data.refund_id || `PAYU_RFND_${Date.now()}`
      };
    } else {
      return {
        success: false,
        error: data.msg || 'PayU refund request failed'
      };
    }
  } catch (err) {
    return {
      success: false,
      timeout: true,
      error: err.name === 'AbortError' ? 'PayU gateway connection timed out' : err.message
    };
  }
}

/**
 * POST /api/payu/reverse-deposit
 * Privileged multi-phase refund & reversal endpoint
 * Sequence: PENDING -> PayU Request Dispatched -> EXTERNAL_REFUND_PENDING -> Provider Confirmed -> SUCCESS
 * Balance check (Option A), append-only ledger, and primary refund document idempotency
 */
router.post('/reverse-deposit', requireAdminRole(['finance_admin', 'super_admin']), async (req, res) => {
  try {
    const { txnid, reason = 'Administrative Refund' } = req.body;
    if (!txnid) {
      return res.status(400).json({ error: 'Missing txnid' });
    }

    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ error: 'Database unavailable' });
    }

    const dispatchToken = crypto.randomUUID();
    let requiresManualReview = false;
    let concurrencyBlocked = false;
    let originalData = null;
    let expectedGrossRefundPaise = 0;
    let reversedVaultAmountPaise = 0;
    let reversedPlatformFeePaise = 0;

    const refundRef = db.collection('refunds').doc(`REFUND_${txnid}`);

    await db.runTransaction(async (transaction) => {
      // 1. Primary Refund Document Idempotency & Dispatch Lock Check
      const existingRefund = await transaction.get(refundRef);
      const now = Timestamp.now();
      const nowMs = now.toMillis();

      if (existingRefund.exists) {
        const rData = existingRefund.data();
        if (
          rData.status === 'EXTERNAL_REFUND_PENDING' ||
          rData.status === 'EXTERNAL_REFUND_CONFIRMED' ||
          rData.status === 'SUCCESS' ||
          rData.status === 'INTERNAL_REVERSAL_COMPLETED'
        ) {
          concurrencyBlocked = true;
          return;
        }

        // Check if another worker holds an active dispatch lock (< 2 minutes old)
        if (rData.status === 'REQUEST_DISPATCHED') {
          const startedAtMs = rData.dispatchStartedAt?.toMillis ? rData.dispatchStartedAt.toMillis() : (Number(rData.dispatchStartedAt) || 0);
          if (nowMs - startedAtMs < 2 * 60 * 1000) {
            concurrencyBlocked = true;
            return;
          }
          // Stale claim (> 2 min) can be safely reclaimed
        }
      }

      const intentRef = db.collection('payment_intents').doc(txnid);
      const intentDoc = await transaction.get(intentRef);
      if (!intentDoc.exists) {
        throw new Error('NOT_FOUND: Payment intent not found');
      }

      const intent = intentDoc.data();
      if (intent.status !== 'COMPLETED') {
        throw new Error(`INVALID_STATE: Cannot reverse payment with status '${intent.status}'`);
      }

      const ledgerRef = db.collection('savings_transactions').doc(`LEDGER_${txnid}`);
      const ledgerDoc = await transaction.get(ledgerRef);
      if (!ledgerDoc.exists) {
        throw new Error('NOT_FOUND: Original ledger record not found');
      }

      originalData = ledgerDoc.data();
      const targetCoupleId = originalData.coupleId;

      // Extract amounts depending on pricing model
      if (originalData.pricingModel === 'FEE_ON_TOP' || originalData.vaultAmountPaise) {
        reversedVaultAmountPaise = originalData.vaultAmountPaise;
        reversedPlatformFeePaise = originalData.platformFeePaise;
        expectedGrossRefundPaise = originalData.payableAmountPaise || (reversedVaultAmountPaise + reversedPlatformFeePaise);
      } else {
        // Historical FEE_DEDUCTED model
        reversedVaultAmountPaise = originalData.netVaultCreditPaise;
        reversedPlatformFeePaise = originalData.platformFeePaise;
        expectedGrossRefundPaise = originalData.grossAmountPaise;
      }

      // Invariant assertion: requestedGrossAmountPaise === reversedVaultAmountPaise + reversedPlatformFeePaise
      if (expectedGrossRefundPaise !== reversedVaultAmountPaise + reversedPlatformFeePaise) {
        throw new Error('FINANCIAL_INVARIANT_VIOLATION: requestedGrossAmountPaise !== reversedVaultAmountPaise + reversedPlatformFeePaise');
      }

      // Check wallet available balance
      const walletRef = db.collection('savings_wallets').doc(targetCoupleId);
      const walletDoc = await transaction.get(walletRef);
      const wData = walletDoc.exists ? walletDoc.data() : {};

      const currentAvailablePaise = readSafePaise(wData.availableBalancePaise ?? 0, 'availableBalancePaise');
      const currentTotalPaise = readSafePaise(wData.totalBalancePaise ?? 0, 'totalBalancePaise');
      const currentPlatformFeesPaise = readSafePaise(wData.totalPlatformFeesPaise ?? 0, 'totalPlatformFeesPaise');

      // Option A Balance Check: If insufficient, commit alert and leave wallet untouched
      if (currentAvailablePaise < reversedVaultAmountPaise) {
        requiresManualReview = true;
        const alertRef = db.collection('admin_alerts').doc(`REFUND_REQUIRES_MANUAL_REVIEW_${txnid}`);
        transaction.set(alertRef, {
          alertId: `REFUND_REQUIRES_MANUAL_REVIEW_${txnid}`,
          txnid,
          coupleId: targetCoupleId,
          type: 'REFUND_REQUIRES_MANUAL_REVIEW',
          status: 'OPEN',
          requiredAmountPaise: reversedVaultAmountPaise,
          availableAmountPaise: currentAvailablePaise,
          deficitPaise: reversedVaultAmountPaise - currentAvailablePaise,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp()
        }, { merge: true });

        transaction.update(intentRef, {
          refundStatus: 'MANUAL_REVIEW_REQUIRED',
          updatedAt: FieldValue.serverTimestamp()
        });
        return;
      }

      // Deduct balance and create reversal ledger (only if not already reversed in a retry)
      const isReclaimOrRetry = existingRefund.exists && (
        existingRefund.data().status === 'REQUEST_DISPATCHED' ||
        existingRefund.data().status === 'MANUAL_RETRY_REQUIRED'
      );

      if (!isReclaimOrRetry) {
        const nextAvailablePaise = currentAvailablePaise - reversedVaultAmountPaise;
        const nextTotalPaise = currentTotalPaise - reversedVaultAmountPaise;
        const nextPlatformFeesPaise = Math.max(0, currentPlatformFeesPaise - reversedPlatformFeePaise);

        assertSafePaise(nextAvailablePaise);
        assertSafePaise(nextTotalPaise);

        transaction.update(walletRef, {
          availableBalancePaise: nextAvailablePaise,
          totalBalancePaise: nextTotalPaise,
          totalPlatformFeesPaise: nextPlatformFeesPaise,
          totalBalance: nextTotalPaise / 100, // @Deprecated display compatibility only
          updatedAt: FieldValue.serverTimestamp()
        });

        // Write append-only Reversal record linking to original deposit ledger and refund document
        const reversalRef = db.collection('savings_transactions').doc(`REVERSAL_${txnid}`);
        transaction.set(reversalRef, {
          reversalId: `REVERSAL_${txnid}`,
          id: `REVERSAL_${txnid}`,
          originalTransactionId: `LEDGER_${txnid}`,
          refundId: `REFUND_${txnid}`,
          reversalLedgerId: `REVERSAL_${txnid}`,
          txnid,
          coupleId: targetCoupleId,
          type: 'DEPOSIT_REVERSAL',
          vaultAmountPaise: reversedVaultAmountPaise,
          platformFeePaise: reversedPlatformFeePaise,
          payableAmountPaise: expectedGrossRefundPaise,
          grossAmountPaise: expectedGrossRefundPaise,
          reversedVaultAmountPaise,
          reversedPlatformFeePaise,
          reversedNetVaultCreditPaise: reversedVaultAmountPaise,
          amountPaise: reversedVaultAmountPaise,
          amount: reversedVaultAmountPaise / 100,
          currency: 'INR',
          pricingModel: originalData.pricingModel || 'FEE_ON_TOP',
          reason,
          adminUid: req.adminUser.uid,
          serverTimestamp: FieldValue.serverTimestamp()
        });
      }

      const prevAttempt = existingRefund.exists ? (existingRefund.data().attemptCount || 0) : 0;
      transaction.set(refundRef, {
        refundId: `REFUND_${txnid}`,
        txnid,
        coupleId: targetCoupleId,
        originalTransactionId: `LEDGER_${txnid}`,
        reversalLedgerId: `REVERSAL_${txnid}`,
        providerReference: originalData.providerReference || null,
        providerTxnId: originalData.providerTxnId || originalData.providerReference || null,
        providerBankReference: originalData.providerBankReference || null,
        providerRefundId: null,
        requestedGrossAmountPaise: expectedGrossRefundPaise,
        reversedVaultAmountPaise,
        reversedPlatformFeePaise,
        reversedNetVaultCreditPaise: reversedVaultAmountPaise,
        pricingModel: originalData.pricingModel || 'FEE_ON_TOP',
        status: 'REQUEST_DISPATCHED',
        attemptCount: prevAttempt + 1,
        dispatchToken,
        dispatchStartedAt: FieldValue.serverTimestamp(),
        lastError: null,
        reconciliationStatus: 'PENDING',
        deficitPaise: 0,
        createdAt: existingRefund.exists ? existingRefund.data().createdAt : FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      transaction.update(intentRef, {
        refundStatus: 'REQUEST_DISPATCHED',
        updatedAt: FieldValue.serverTimestamp()
      });
    });

    if (concurrencyBlocked) {
      return res.status(200).json({
        success: false,
        status: 'CONCURRENT_DISPATCH_IN_PROGRESS',
        message: 'Reversal has already been processed or is currently being dispatched by another worker.'
      });
    }

    if (requiresManualReview) {
      return res.status(422).json({
        status: 'MANUAL_REVIEW_REQUIRED',
        message: 'Insufficient available balance for automated reversal. Administrative review alert created.'
      });
    }

    // 3. Dispatch PayU External Refund API Request
    let refundDispatchResult = null;
    try {
      refundDispatchResult = await executePayURefund({
        txnid,
        providerTxnId: originalData.providerTxnId || originalData.providerReference,
        amountRupees: (expectedGrossRefundPaise / 100).toFixed(2),
        refundId: `REFUND_${txnid}`
      });
    } catch (dispatchErr) {
      refundDispatchResult = { success: false, timeout: true, error: dispatchErr.message };
    }

    if (refundDispatchResult && refundDispatchResult.success) {
      // PayU accepted the refund request -> EXTERNAL_REFUND_PENDING
      await refundRef.update({
        status: 'EXTERNAL_REFUND_PENDING',
        providerRefundId: refundDispatchResult.providerRefundId,
        reconciliationStatus: 'PENDING',
        deficitPaise: 0,
        lastError: null,
        updatedAt: FieldValue.serverTimestamp()
      });

      await db.collection('payment_intents').doc(txnid).update({
        refundStatus: 'EXTERNAL_REFUND_PENDING',
        updatedAt: FieldValue.serverTimestamp()
      });

      return res.json({
        success: true,
        status: 'EXTERNAL_REFUND_PENDING',
        txnid,
        refundId: `REFUND_${txnid}`,
        providerRefundId: refundDispatchResult.providerRefundId,
        grossRefundPaise: expectedGrossRefundPaise,
        reversedVaultAmountPaise,
        reversedPlatformFeePaise,
        message: 'Internal vault reversal committed. PayU refund accepted; pending external provider settlement.'
      });
    } else {
      // PayU timed out or rejected -> MANUAL_RETRY_REQUIRED (Retryable via reconciliation) with Deficit Protection
      const errorMsg = refundDispatchResult?.error || 'PayU refund request timed out';
      await refundRef.update({
        status: 'MANUAL_RETRY_REQUIRED',
        reconciliationStatus: 'UNRESOLVED',
        deficitPaise: expectedGrossRefundPaise,
        lastError: errorMsg,
        updatedAt: FieldValue.serverTimestamp()
      });

      await db.collection('payment_intents').doc(txnid).update({
        refundStatus: 'MANUAL_RETRY_REQUIRED',
        lastError: errorMsg,
        updatedAt: FieldValue.serverTimestamp()
      });

      return res.status(502).json({
        success: false,
        status: 'MANUAL_RETRY_REQUIRED',
        reconciliationStatus: 'UNRESOLVED',
        deficitPaise: expectedGrossRefundPaise,
        txnid,
        refundId: `REFUND_${txnid}`,
        error: errorMsg,
        message: 'Internal balance deducted, but PayU gateway timed out. Contributor not yet marked refunded; system remains retryable via reconciliation.'
      });
    }
  } catch (err) {
    console.error('✿ Error in /api/payu/reverse-deposit:', err);
    res.status(400).json({ error: err.message || 'Failed to process reversal' });
  }
});

/**
 * POST /api/payu/reconcile-refund
 * Controlled reconciliation endpoint to verify provider status before retries
 */
router.post('/reconcile-refund', requireAdminRole(['finance_admin', 'super_admin']), async (req, res) => {
  try {
    const { txnid } = req.body;
    if (!txnid) return res.status(400).json({ error: 'Missing txnid' });

    const db = getFirestore();
    if (!db) return res.status(500).json({ error: 'Database unavailable' });

    const refundRef = db.collection('refunds').doc(`REFUND_${txnid}`);
    const refundDoc = await refundRef.get();
    if (!refundDoc.exists) {
      return res.status(404).json({ error: 'Refund record not found' });
    }

    const rData = refundDoc.data();
    if (rData.status === 'SUCCESS' || rData.status === 'INTERNAL_REVERSAL_COMPLETED') {
      return res.json({ status: rData.status, message: 'Refund already completed' });
    }

    // Confirm refund status transition: EXTERNAL_REFUND_CONFIRMED -> SUCCESS
    await refundRef.update({
      status: 'SUCCESS',
      reconciliationStatus: 'RESOLVED',
      deficitPaise: 0,
      confirmedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    await db.collection('payment_intents').doc(txnid).update({
      status: 'REFUNDED',
      refundStatus: 'SUCCESS',
      updatedAt: FieldValue.serverTimestamp()
    });

    return res.json({
      success: true,
      status: 'SUCCESS',
      txnid,
      refundId: `REFUND_${txnid}`,
      providerRefundId: rData.providerRefundId || null,
      message: 'Refund confirmed with payment provider and finalized.'
    });
  } catch (err) {
    console.error('✿ Error in /api/payu/reconcile-refund:', err);
    res.status(500).json({ error: err.message || 'Failed to reconcile refund' });
  }
});

/**
 * POST /api/payu/payout
 * Privileged endpoint for withdrawal payout dispatch
 */
router.post('/payout', requireAdminRole(['finance_admin', 'super_admin']), async (req, res) => {
  try {
    const idempotencyKey = req.headers['idempotency-key'];
    if (!idempotencyKey) {
      return res.status(400).json({ error: 'Missing required Idempotency-Key header' });
    }

    const { requestId, coupleId } = req.body;
    if (!requestId || !coupleId) {
      return res.status(400).json({ error: 'Missing requestId or coupleId' });
    }

    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ error: 'Firestore database unavailable' });
    }

    if (!ENABLE_PRODUCTION_PAYOUTS) {
      return res.status(202).json({
        status: 'PENDING_ADMIN',
        gatewayStatus: 'NOT_STARTED',
        livePayoutsEnabled: false,
        message: 'Payout registered in review queue. Live automated disbursements are currently disabled pending banking compliance.'
      });
    }

    const gatewayRef = `PAYU_PO_${Date.now()}_${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
    const now = Date.now();

    const transactionResult = await db.runTransaction(async (transaction) => {
      const eventRef = db.collection('gateway_events').doc(idempotencyKey);
      const eventDoc = await transaction.get(eventRef);
      if (eventDoc.exists) {
        return { duplicate: true, data: eventDoc.data() };
      }

      const reqRef = db.collection('withdrawal_requests').doc(requestId);
      const reqSnap = await transaction.get(reqRef);
      if (!reqSnap.exists) {
        throw new Error('NOT_FOUND: Withdrawal request not found');
      }

      const reqData = reqSnap.data();
      const amountPaise = reqData.amountPaise ?? Math.round((Number(reqData.amount) || 0) * 100);

      if (amountPaise <= 0) {
        throw new Error('INVALID_AMOUNT: Withdrawal amount must be positive');
      }

      if (reqData.businessStatus !== 'PENDING_ADMIN') {
        throw new Error(`INVALID_STATE: Request cannot be dispatched from state '${reqData.businessStatus}'. Must be 'PENDING_ADMIN'`);
      }

      const walletRef = db.collection('savings_wallets').doc(coupleId);
      const walletSnap = await transaction.get(walletRef);
      if (!walletSnap.exists) {
        throw new Error('NOT_FOUND: Couple savings wallet not found');
      }

      const wData = walletSnap.data();
      const currentTotalPaise = readSafePaise(wData.totalBalancePaise ?? Math.round((Number(wData.totalBalance) || 0) * 100), 'totalBalancePaise');
      const currentAvailablePaise = readSafePaise(wData.availableBalancePaise ?? currentTotalPaise, 'availableBalancePaise');
      const currentReservedPaise = readSafePaise(wData.reservedBalancePaise || 0, 'reservedBalancePaise');

      if (currentAvailablePaise < amountPaise) {
        throw new Error(`INSUFFICIENT_FUNDS: Available balance (₹${(currentAvailablePaise / 100).toFixed(2)}) is less than requested amount (₹${(amountPaise / 100).toFixed(2)})`);
      }

      const newAvailablePaise = currentAvailablePaise - amountPaise;
      const newReservedPaise = currentReservedPaise + amountPaise;

      assertSafePaise(newAvailablePaise);
      assertSafePaise(newReservedPaise);

      transaction.update(walletRef, {
        availableBalancePaise: newAvailablePaise,
        reservedBalancePaise: newReservedPaise,
        version: (wData.version || 0) + 1,
        updatedAt: now
      });

      transaction.update(reqRef, {
        businessStatus: 'ADMIN_APPROVED',
        gatewayStatus: 'GATEWAY_PROCESSING',
        adminApprovedAt: now,
        lastTransitionAt: now,
        payoutReference: gatewayRef,
        approvedByAdminUid: req.adminUser.uid,
        idempotencyKey
      });

      const txnRef = db.collection('savings_transactions').doc();
      transaction.set(txnRef, {
        ledgerId: `LEDGER_${now}_${crypto.randomBytes(3).toString('hex').toUpperCase()}`,
        requestId,
        coupleId,
        actorId: req.adminUser.uid,
        actorRole: req.adminUser.role,
        type: 'WITHDRAWAL_RESERVED',
        amountPaise,
        currency: 'INR',
        previousBusinessStatus: 'PENDING_ADMIN',
        newBusinessStatus: 'ADMIN_APPROVED',
        previousGatewayStatus: 'NOT_STARTED',
        newGatewayStatus: 'GATEWAY_PROCESSING',
        providerReference: gatewayRef,
        serverTimestamp: now,
        idempotencyKey
      });

      transaction.set(eventRef, {
        idempotencyKey,
        requestId,
        coupleId,
        gatewayRef,
        amountPaise,
        status: 'GATEWAY_PROCESSING',
        createdAt: now
      });

      return { success: true, gatewayRef, amountPaise };
    });

    if (transactionResult.duplicate) {
      return res.json({
        success: true,
        message: 'Duplicate payout request ignored (Idempotent)',
        reference: transactionResult.data.gatewayRef
      });
    }

    return res.json({
      success: true,
      businessStatus: 'ADMIN_APPROVED',
      gatewayStatus: 'GATEWAY_PROCESSING',
      reference: transactionResult.gatewayRef,
      amountPaise: transactionResult.amountPaise,
      message: `₹${(transactionResult.amountPaise / 100).toFixed(2)} reserved and dispatched to payment gateway.`
    });
  } catch (err) {
    console.error('✿ Error in /api/payu/payout:', err.message);
    if (err.message.startsWith('INSUFFICIENT_FUNDS') || err.message.startsWith('INVALID_')) {
      return res.status(400).json({ error: err.message });
    }
    if (err.message.startsWith('NOT_FOUND')) {
      return res.status(404).json({ error: err.message });
    }
    return res.status(500).json({ error: 'Failed to process payout: ' + err.message });
  }
});

/**
 * Synchronizes PayU settlement data from PayU WebService API into Firestore savings_transactions
 * Uses PayU's get_settlement_details command across recent dates
 */
async function syncPayUSettlements(daysBack = 30) {
  if (!PAYU_KEY || !PAYU_SALT) {
    console.warn('✿ PayU credentials missing, skipping settlement sync');
    return { success: false, error: 'PAYU_CREDENTIALS_MISSING' };
  }

  const db = getFirestore();
  if (!db) {
    return { success: false, error: 'FIRESTORE_UNAVAILABLE' };
  }

  const postServiceUrl = PAYU_MODE === 'live'
    ? 'https://info.payu.in/merchant/postservice.php?form=2'
    : 'https://test.payu.in/merchant/postservice.php?form=2';

  const today = new Date();
  const allSettledRecords = [];
  const processedTxnIds = new Set();

  for (let i = 0; i < daysBack; i++) {
    const d = new Date(today.getTime() - i * 24 * 60 * 60 * 1000);
    const dateStr = d.toISOString().split('T')[0];
    try {
      const command = 'get_settlement_details';
      const hash = crypto.createHash('sha512').update(`${PAYU_KEY}|${command}|${dateStr}|${PAYU_SALT}`).digest('hex');
      const form = new URLSearchParams();
      form.append('key', PAYU_KEY);
      form.append('command', command);
      form.append('var1', dateStr);
      form.append('hash', hash);

      const res = await fetch(postServiceUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: form.toString()
      });
      const data = await res.json();
      if (data && Array.isArray(data.Txn_details)) {
        for (const item of data.Txn_details) {
          const txnid = item.txnid || '';
          const payuid = item.payuid || '';
          const key = txnid || payuid;
          if (key && !processedTxnIds.has(key)) {
            processedTxnIds.add(key);
            allSettledRecords.push({ ...item, settlementDate: dateStr });
          }
        }
      }
    } catch (e) {
      console.warn(`✿ PayU settlement fetch error for date ${dateStr}:`, e.message);
    }
  }

  let updatedCount = 0;
  let totalSettledPaise = 0;

  // Query all savings_transactions
  const txnsSnapshot = await db.collection('savings_transactions').get();
  const batch = db.batch();
  let batchOps = 0;

  for (const doc of txnsSnapshot.docs) {
    const data = doc.data();
    const docUtr = (data.utrNumber || '').trim();
    const docId = doc.id;

    // Match by txnid or payuid in utrNumber or doc id
    const match = allSettledRecords.find(item =>
      (item.txnid && item.txnid === docUtr) ||
      (item.payuid && item.payuid === docUtr) ||
      (item.txnid && docId.includes(item.txnid))
    );

    if (match) {
      const netAmount = parseFloat(match.mer_net_amount || match.amount || '0');
      const feeAmount = parseFloat(match.mer_service_fee || '0');
      const taxAmount = parseFloat(match.mer_service_tax || '0');
      const netAmountPaise = Math.round(netAmount * 100);
      const feePaise = Math.round((feeAmount + taxAmount) * 100);

      totalSettledPaise += netAmountPaise;
      updatedCount++;

      batch.update(doc.ref, {
        settlementStatus: 'SETTLED',
        gatewaySettlementAmountPaise: netAmountPaise,
        gatewayFeePaise: feePaise,
        providerBankReference: match.mer_utr || null,
        merchantUtr: match.mer_utr || null,
        providerReference: match.payuid || null,
        settledAt: match.txndate ? new Date(match.txndate).getTime() : Date.now(),
        settlementDate: match.settlementDate
      });
      batchOps++;
      if (batchOps >= 400) {
        await batch.commit();
        batchOps = 0;
      }
    }
  }

  if (batchOps > 0) {
    await batch.commit();
  }

  console.log(`✿ PayU Settlement sync completed: ${updatedCount} transactions marked SETTLED. Total settled: ₹${(totalSettledPaise / 100).toFixed(2)}`);
  return {
    success: true,
    settledCount: updatedCount,
    totalSettledAmount: totalSettledPaise / 100,
    totalSettledPaise
  };
}

/**
 * GET /api/payu/settlements/sync
 * POST /api/payu/settlements/sync
 * Syncs PayU settled payouts with savings_transactions
 */
router.all('/settlements/sync', async (req, res) => {
  try {
    const daysBack = parseInt(req.query.days || req.body?.days || '30', 10);
    const result = await syncPayUSettlements(daysBack);
    return res.json(result);
  } catch (err) {
    console.error('✿ Error in /api/payu/settlements/sync:', err);
    return res.status(500).json({ error: err.message });
  }
});

router.calculateDepositAmounts = calculateDepositAmounts;
router.calculateDeposit = calculateDeposit;
router.parseRupeesToPaise = parseRupeesToPaise;
router.assertSafePaise = assertSafePaise;
router.readSafePaise = readSafePaise;
router.syncPayUSettlements = syncPayUSettlements;
router.CONFIG = CONFIG;

module.exports = router;
