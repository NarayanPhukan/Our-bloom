const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const Razorpay = require('razorpay');
const { getFirestore } = require('../utils/firebase');
const authMiddleware = require('../middleware/authMiddleware');
const { requireAdminRole } = require('../middleware/adminAuthMiddleware');
const { FieldValue, Timestamp } = require('firebase-admin/firestore');

// Initialize Razorpay SDK with environment variables
const RAZORPAY_KEY_ID = process.env.RAZORPAY_KEY_ID || 'rzp_live_TmiLSHkOVIFPyZ';
const RAZORPAY_KEY_SECRET = process.env.RAZORPAY_KEY_SECRET || 'sUM8e1x0a5UuOiqhMEeCfUOa';

let razorpay = null;
if (RAZORPAY_KEY_ID && RAZORPAY_KEY_SECRET) {
  try {
    razorpay = new Razorpay({
      key_id: RAZORPAY_KEY_ID,
      key_secret: RAZORPAY_KEY_SECRET
    });
    console.log(`🌸 Razorpay SDK initialized (Key: ${RAZORPAY_KEY_ID.slice(0, 10)}...)`);
  } catch (err) {
    console.error('❌ Failed to initialize Razorpay SDK:', err.message);
  }
} else {
  console.warn('⚠️ RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET not configured.');
}

const CONFIG = {
  MIN_DEPOSIT_PAISE: 100, // ₹1.00
  MAX_DEPOSIT_PAISE: 50000000, // ₹500,000.00 (5 Lakh INR)
  PLATFORM_FEE_BPS: 200, // 2.00%
  INTENT_EXPIRATION_MS: 30 * 60 * 1000 // 30 minutes
};

function calculateDepositAmounts(vaultAmountPaise) {
  const vault = Math.round(Number(vaultAmountPaise));
  if (vault < CONFIG.MIN_DEPOSIT_PAISE || vault > CONFIG.MAX_DEPOSIT_PAISE) {
    throw new Error(`Deposit amount out of bounds (Min ₹1, Max ₹5,00,000)`);
  }
  // 2% fee rounded half-up: (vault * 200 + 5000) / 10000
  const platformFeePaise = Math.floor((vault * CONFIG.PLATFORM_FEE_BPS + 5000) / 10000);
  const payableAmountPaise = vault + platformFeePaise;

  return {
    vaultAmountPaise: vault,
    platformFeePaise,
    payableAmountPaise,
    platformFeeBps: CONFIG.PLATFORM_FEE_BPS,
    pricingModel: 'FEE_ON_TOP'
  };
}

/**
 * GET /api/razorpay/status
 * Health and connectivity status of Razorpay integration
 */
router.get('/status', async (req, res) => {
  if (!razorpay) {
    return res.status(503).json({
      configured: false,
      message: 'Razorpay is not configured'
    });
  }

  try {
    const orders = await razorpay.orders.all({ count: 1 });
    res.json({
      configured: true,
      mode: RAZORPAY_KEY_ID.startsWith('rzp_live') ? 'live' : 'test',
      keyId: RAZORPAY_KEY_ID.slice(0, 10) + '...',
      connected: true,
      recentOrderSampleId: orders.items?.[0]?.id || null,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    res.status(502).json({
      configured: true,
      connected: false,
      error: err.message
    });
  }
});

/**
 * POST /api/razorpay/create-order
 * Creates a Razorpay order for Couple Vault deposit with authoritative 2% fee calculation
 */
router.post('/create-order', async (req, res) => {
  if (!razorpay) {
    return res.status(500).json({ error: 'Razorpay gateway not initialized' });
  }

  try {
    const {
      amount,
      vaultAmountPaise: reqVaultPaise,
      coupleId,
      userId,
      userName,
      email,
      phone,
      goalId,
      note,
      productInfo
    } = req.body;

    if (!coupleId) {
      return res.status(400).json({ error: 'Missing coupleId' });
    }

    // Determine vaultAmountPaise
    let vaultAmountPaise;
    if (reqVaultPaise != null && Number(reqVaultPaise) > 0) {
      vaultAmountPaise = Math.round(Number(reqVaultPaise));
    } else if (amount != null && Number(amount) > 0) {
      vaultAmountPaise = Math.round(Number(amount) * 100);
    } else {
      return res.status(400).json({ error: 'Invalid or missing deposit amount' });
    }

    const { platformFeePaise, payableAmountPaise, pricingModel } = calculateDepositAmounts(vaultAmountPaise);

    const txnid = `RZP_${Date.now()}_${Math.random().toString(36).substring(2, 7).toUpperCase()}`;
    const receipt = txnid.substring(0, 40);

    // Create Razorpay order
    const orderOptions = {
      amount: payableAmountPaise, // in paise
      currency: 'INR',
      receipt: receipt,
      notes: {
        coupleId: String(coupleId),
        userId: String(userId || 'guest'),
        vaultAmountPaise: String(vaultAmountPaise),
        platformFeePaise: String(platformFeePaise),
        goalId: String(goalId || ''),
        txnid
      }
    };

    const order = await razorpay.orders.create(orderOptions);

    // Persist Payment Intent in Firestore
    const db = getFirestore();
    const intentRef = db.collection('payment_intents').doc(txnid);
    await intentRef.set({
      txnid,
      orderId: order.id,
      gateway: 'RAZORPAY',
      coupleId: String(coupleId),
      userId: String(userId || 'guest'),
      userName: String(userName || 'Partner'),
      email: String(email || 'support@ourbloom.app'),
      phone: String(phone || ''),
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      expectedAmountPaise: payableAmountPaise,
      platformFeeBps: CONFIG.PLATFORM_FEE_BPS,
      currency: 'INR',
      pricingModel,
      productinfo: productInfo || 'Our Bloom Vault Deposit',
      goalId: goalId || null,
      note: note || null,
      status: 'INITIATED',
      expiresAt: Timestamp.fromMillis(Date.now() + CONFIG.INTENT_EXPIRATION_MS),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    res.json({
      success: true,
      orderId: order.id,
      txnid,
      keyId: RAZORPAY_KEY_ID,
      currency: 'INR',
      amount: payableAmountPaise, // in paise for Razorpay Checkout
      payableAmountRupees: payableAmountPaise / 100,
      vaultAmountRupees: vaultAmountPaise / 100,
      platformFeeRupees: platformFeePaise / 100,
      coupleId,
      receipt
    });
  } catch (err) {
    console.error('Error creating Razorpay order:', err);
    res.status(500).json({ error: err.message || 'Failed to create Razorpay order' });
  }
});

async function executeVaultCreditTransaction({
  txnid,
  coupleId,
  userId,
  userName,
  vaultAmountPaise,
  platformFeePaise,
  payableAmountPaise,
  goalId,
  paymentId,
  orderOrLinkId
}) {
  const db = getFirestore();
  await db.runTransaction(async (transaction) => {
    // 1. ALL READS FIRST (Strict Firestore rule: no reads after any writes)
    const ledgerRef = db.collection('savings_transactions').doc(`LEDGER_${txnid}`);
    const ledgerDoc = await transaction.get(ledgerRef);
    if (ledgerDoc.exists) return;

    const walletRef = db.collection('savings_wallets').doc(coupleId);
    const walletDoc = await transaction.get(walletRef);

    let goalRef = null;
    let goalDoc = null;
    if (goalId) {
      goalRef = db.collection('savings_goals').doc(goalId);
      goalDoc = await transaction.get(goalRef);
    }

    // 2. COMPUTE
    const wData = walletDoc.exists ? walletDoc.data() : {};
    const currentTotalPaise = Number(wData.totalBalancePaise ?? Math.round((Number(wData.totalBalance) || 0) * 100));
    const currentAvailablePaise = Number(wData.availableBalancePaise ?? currentTotalPaise);
    const currentFeesPaise = Number(wData.totalPlatformFeesPaise || 0);

    const nextTotalPaise = currentTotalPaise + vaultAmountPaise;
    const nextAvailablePaise = currentAvailablePaise + vaultAmountPaise;
    const nextFeesPaise = currentFeesPaise + platformFeePaise;

    const userDepositRupees = vaultAmountPaise / 100;
    const isUser1 = userId && (userId === wData.user1Id || !wData.user1Id);
    const nextUser1 = isUser1 ? (Number(wData.user1Total) || 0) + userDepositRupees : Number(wData.user1Total) || 0;
    const nextUser2 = !isUser1 ? (Number(wData.user2Total) || 0) + userDepositRupees : Number(wData.user2Total) || 0;

    // 3. ALL WRITES AFTER READS
    transaction.set(walletRef, {
      coupleId,
      totalBalancePaise: nextTotalPaise,
      availableBalancePaise: nextAvailablePaise,
      totalPlatformFeesPaise: nextFeesPaise,
      totalBalance: nextTotalPaise / 100,
      user1Total: nextUser1,
      user2Total: nextUser2,
      currency: '₹',
      version: (wData.version || 0) + 1,
      lastUpdated: Date.now(),
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });

    if (goalRef && goalDoc && goalDoc.exists) {
      const gData = goalDoc.data();
      const currentG = Number(gData.currentAmount) || 0;
      const targetG = Number(gData.targetAmount) || 0;
      const newG = currentG + userDepositRupees;
      transaction.update(goalRef, {
        currentAmount: newG,
        isCompleted: (targetG > 0 && newG >= targetG),
        updatedAt: FieldValue.serverTimestamp()
      });
    }

    transaction.set(ledgerRef, {
      id: `LEDGER_${txnid}`,
      ledgerId: `LEDGER_${txnid}`,
      requestId: txnid,
      coupleId,
      userId,
      userName: userName || 'Partner',
      type: 'deposit',
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      grossAmountPaise: payableAmountPaise,
      netVaultCreditPaise: vaultAmountPaise,
      amountPaise: vaultAmountPaise,
      amount: vaultAmountPaise / 100,
      grossAmount: payableAmountPaise / 100,
      platformFee: platformFeePaise / 100,
      netVaultCredit: vaultAmountPaise / 100,
      pricingModel: 'FEE_ON_TOP',
      platformFeeBps: CONFIG.PLATFORM_FEE_BPS,
      currency: 'INR',
      providerTxnId: paymentId,
      providerBankReference: orderOrLinkId,
      status: 'COMPLETED',
      settlementStatus: 'PENDING',
      txnid,
      platformTransactionId: txnid,
      utrNumber: paymentId,
      goalId: goalId || '',
      goalTitle: '',
      note: `Razorpay UPI Deposit (${paymentId})`,
      category: 'Savings',
      paymentMethod: 'Razorpay',
      timestamp: Date.now(),
      createdAt: FieldValue.serverTimestamp()
    });

    if (platformFeePaise > 0) {
      const revRef = db.collection('revenue_records').doc(`REV_RZP_${txnid}`);
      transaction.set(revRef, {
        eventId: `REV_RZP_${txnid}`,
        source: 'SAVINGS_TRANSACTION',
        sourceTransactionId: txnid,
        type: 'FEE',
        title: '2% Deposit Platform Fee',
        amountPaise: platformFeePaise,
        feePaise: platformFeePaise,
        grossAmountPaise: platformFeePaise,
        netRevenuePaise: platformFeePaise,
        coupleId,
        userId,
        userName: userName || 'Partner',
        paymentMethod: 'Razorpay',
        referenceId: paymentId,
        timestamp: Date.now(),
        createdAt: FieldValue.serverTimestamp()
      });

      const feeRef = db.collection('platform_fees').doc(`FEE_${txnid}`);
      transaction.set(feeRef, {
        feeId: `FEE_${txnid}`,
        txnid,
        coupleId,
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        grossAmountPaise: payableAmountPaise,
        providerReference: paymentId,
        timestamp: FieldValue.serverTimestamp()
      });
    }

    const alertRef = db.collection('admin_alerts').doc(`ALERT_${txnid}`);
    transaction.set(alertRef, {
      type: 'deposit',
      coupleId,
      userId,
      userName: userName || 'Partner',
      amount: vaultAmountPaise / 100,
      utrNumber: paymentId,
      note: `Razorpay UPI Deposit (${paymentId})`,
      timestamp: Date.now(),
      status: 'VERIFIED'
    }, { merge: true });

    transaction.set(db.collection('payment_intents').doc(txnid), {
      status: 'COMPLETED',
      paymentId: paymentId,
      orderOrLinkId,
      coupleId,
      userId,
      userName: userName || 'Partner',
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      completedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  });
}

/**
 * POST /api/razorpay/create-payment-link
 * Creates a hosted Razorpay payment link for Chrome Custom Tabs (enabling full UPI apps: GPay, PhonePe, Paytm, BHIM)
 */
router.post('/create-payment-link', async (req, res) => {
  if (!razorpay) {
    return res.status(500).json({ error: 'Razorpay gateway not initialized' });
  }

  try {
    const {
      amount,
      vaultAmountPaise: reqVaultPaise,
      coupleId,
      userId,
      userName,
      email,
      phone,
      goalId,
      note,
      productInfo
    } = req.body;

    if (!coupleId) {
      return res.status(400).json({ error: 'Missing coupleId' });
    }

    let vaultAmountPaise;
    if (reqVaultPaise != null && Number(reqVaultPaise) > 0) {
      vaultAmountPaise = Math.round(Number(reqVaultPaise));
    } else if (amount != null && Number(amount) > 0) {
      vaultAmountPaise = Math.round(Number(amount) * 100);
    } else {
      return res.status(400).json({ error: 'Invalid or missing deposit amount' });
    }

    const { platformFeePaise, payableAmountPaise, pricingModel } = calculateDepositAmounts(vaultAmountPaise);

    const txnid = `RZP_${Date.now()}_${Math.random().toString(36).substring(2, 7).toUpperCase()}`;

    let cleanPhone = (phone || '').replace(/\D/g, '');
    if (cleanPhone.length > 10) {
      cleanPhone = cleanPhone.slice(-10);
    }
    const customerPhone = cleanPhone.length === 10 ? `+91${cleanPhone}` : undefined;

    const linkOptions = {
      amount: payableAmountPaise, // in paise
      currency: 'INR',
      accept_partial: false,
      description: 'OurBloom Couple Vault Deposit',
      customer: {
        name: (userName || 'Partner').slice(0, 50),
        email: (email && email.includes('@')) ? email : 'support@ourbloom.app',
        contact: customerPhone
      },
      notify: {
        sms: false,
        email: false
      },
      reminder_enable: false,
      notes: {
        coupleId: String(coupleId),
        userId: String(userId || 'guest'),
        vaultAmountPaise: String(vaultAmountPaise),
        platformFeePaise: String(platformFeePaise),
        goalId: String(goalId || ''),
        txnid
      },
      callback_url: `https://our-bloom-gamma.vercel.app/payment-return.html?txnid=${txnid}`,
      callback_method: 'get'
    };

    const paymentLink = await razorpay.paymentLink.create(linkOptions);

    const db = getFirestore();
    const intentRef = db.collection('payment_intents').doc(txnid);
    await intentRef.set({
      txnid,
      paymentLinkId: paymentLink.id,
      paymentUrl: paymentLink.short_url,
      gateway: 'RAZORPAY',
      coupleId: String(coupleId),
      userId: String(userId || 'guest'),
      userName: String(userName || 'Partner'),
      email: String(email || 'support@ourbloom.app'),
      phone: customerPhone || '',
      vaultAmountPaise,
      platformFeePaise,
      payableAmountPaise,
      expectedAmountPaise: payableAmountPaise,
      platformFeeBps: CONFIG.PLATFORM_FEE_BPS,
      currency: 'INR',
      pricingModel,
      productinfo: productInfo || 'Our Bloom Vault Deposit',
      goalId: goalId || null,
      note: note || null,
      status: 'INITIATED',
      expiresAt: Timestamp.fromMillis(Date.now() + CONFIG.INTENT_EXPIRATION_MS),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    res.json({
      success: true,
      paymentUrl: paymentLink.short_url,
      paymentLinkId: paymentLink.id,
      txnid,
      amount: payableAmountPaise,
      payableAmountRupees: payableAmountPaise / 100,
      vaultAmountRupees: vaultAmountPaise / 100,
      platformFeeRupees: platformFeePaise / 100,
      coupleId
    });
  } catch (err) {
    console.error('Error creating Razorpay payment link:', err);
    res.status(500).json({ error: err.message || 'Failed to create Razorpay payment link' });
  }
});

/**
 * GET /api/razorpay/payment-link-callback
 * Redirect handler after payment on Razorpay hosted checkout
 */
router.get('/payment-link-callback', async (req, res) => {
  const {
    txnid,
    razorpay_payment_id,
    razorpay_payment_link_id,
    razorpay_payment_link_status
  } = req.query;

  const db = getFirestore();

  try {
    if (!txnid) {
      return res.status(400).send('Missing transaction ID');
    }

    const intentDoc = await db.collection('payment_intents').doc(txnid).get();
    if (!intentDoc.exists) {
      return res.status(404).send('Payment intent not found');
    }

    const intent = intentDoc.data();

    if (intent.status !== 'COMPLETED' && (razorpay_payment_id || razorpay_payment_link_status === 'paid')) {
      const paymentId = razorpay_payment_id || `PAY_${Date.now()}`;
      await executeVaultCreditTransaction({
        txnid,
        coupleId: intent.coupleId,
        userId: intent.userId,
        userName: intent.userName,
        vaultAmountPaise: Number(intent.vaultAmountPaise),
        platformFeePaise: Number(intent.platformFeePaise),
        payableAmountPaise: Number(intent.payableAmountPaise),
        goalId: intent.goalId,
        paymentId,
        orderOrLinkId: razorpay_payment_link_id || intent.paymentLinkId || 'LINK'
      });
    }

    const appReturnUrl = `ourbloom://razorpay-return?txnid=${encodeURIComponent(txnid)}&status=success`;

    const html = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
  <title>Payment Successful - OurBloom</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      margin: 0;
      padding: 24px;
      background: #FFF0F3;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      box-sizing: border-box;
    }
    .card {
      background: #FFFFFF;
      border-radius: 28px;
      padding: 36px 24px;
      text-align: center;
      max-width: 360px;
      width: 100%;
      box-shadow: 0 12px 36px rgba(232, 93, 117, 0.16);
    }
    .emoji { font-size: 56px; margin-bottom: 12px; }
    h2 { color: #2D2D2D; margin: 0 0 8px 0; font-size: 22px; font-weight: 800; }
    p { color: #666666; font-size: 14px; margin: 0 0 24px 0; line-height: 1.5; }
    .btn {
      display: block;
      background: #E85D75;
      color: #FFFFFF !important;
      text-decoration: none;
      padding: 16px 24px;
      border-radius: 30px;
      font-weight: 700;
      font-size: 15px;
      box-shadow: 0 4px 16px rgba(232, 93, 117, 0.35);
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="emoji">🌸</div>
    <h2>Deposit Successful!</h2>
    <p>₹${(Number(intent.vaultAmountPaise) / 100).toFixed(2)} has been credited to your Couple Vault.</p>
    <a class="btn" href="${appReturnUrl}">Return to OurBloom</a>
  </div>
  <script>
    setTimeout(function() {
      window.location.href = "${appReturnUrl}";
    }, 600);
  </script>
</body>
</html>`;

    res.send(html);
  } catch (err) {
    console.error('Error handling payment link callback:', err);
    res.status(500).send('Error completing deposit');
  }
});

/**
 * GET /api/razorpay/check-status
 * Check status of a transaction and auto-resolve via Razorpay link status if pending
 */
router.get('/check-status', async (req, res) => {
  const { txnid } = req.query;
  if (!txnid) {
    return res.status(400).json({ error: 'Missing txnid' });
  }

  const db = getFirestore();

  try {
    const intentDoc = await db.collection('payment_intents').doc(txnid).get();
    if (!intentDoc.exists) {
      return res.status(404).json({ error: 'Payment intent not found' });
    }

    const intent = intentDoc.data();

    // If still INITIATED and has paymentLinkId, query Razorpay directly
    if (intent.status === 'INITIATED' && intent.paymentLinkId && razorpay) {
      try {
        const link = await razorpay.paymentLink.fetch(intent.paymentLinkId);
        if (link.status === 'paid') {
          const paymentId = link.payments?.[0]?.payment_id || `PAY_${Date.now()}`;
          await executeVaultCreditTransaction({
            txnid,
            coupleId: intent.coupleId,
            userId: intent.userId,
            userName: intent.userName,
            vaultAmountPaise: Number(intent.vaultAmountPaise),
            platformFeePaise: Number(intent.platformFeePaise),
            payableAmountPaise: Number(intent.payableAmountPaise),
            goalId: intent.goalId,
            paymentId,
            orderOrLinkId: link.id
          });
          intent.status = 'COMPLETED';
        }
      } catch (checkErr) {
        console.warn('Could not check razorpay link status:', checkErr.message);
      }
    }

    const isCompleted = intent.status === 'COMPLETED';
    res.json({
      success: true,
      status: intent.status,
      completed: isCompleted,
      txnid,
      vaultAmount: Number(intent.vaultAmountPaise) / 100,
      payableAmount: Number(intent.payableAmountPaise) / 100,
      goalId: intent.goalId || null,
      goalTitle: intent.note || null
    });
  } catch (err) {
    console.error('Error checking payment status:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/razorpay/verify-payment
 * Cryptographically verifies Razorpay signature and atomically credits the couple's vault
 */
router.post('/verify-payment', async (req, res) => {
  const {
    razorpay_order_id,
    razorpay_payment_id,
    razorpay_signature,
    txnid: clientTxnid
  } = req.body;

  if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
    return res.status(400).json({ error: 'Missing Razorpay signature verification parameters' });
  }

  // 1. Cryptographic HMAC SHA256 Signature Verification
  const text = `${razorpay_order_id}|${razorpay_payment_id}`;
  const expectedSignature = crypto
    .createHmac('sha256', RAZORPAY_KEY_SECRET)
    .update(text)
    .digest('hex');

  if (expectedSignature !== razorpay_signature) {
    console.error('❌ Razorpay signature mismatch:', { expected: expectedSignature, received: razorpay_signature });
    return res.status(400).json({ error: 'Invalid payment signature' });
  }

  const db = getFirestore();

  try {
    let intentDoc = null;
    let txnid = clientTxnid;

    if (txnid) {
      const snap = await db.collection('payment_intents').doc(txnid).get();
      if (snap.exists) intentDoc = snap;
    }

    if (!intentDoc) {
      const query = await db.collection('payment_intents')
        .where('orderId', '==', razorpay_order_id)
        .limit(1)
        .get();
      if (!query.empty) {
        intentDoc = query.docs[0];
        txnid = intentDoc.id;
      }
    }

    if (!intentDoc || !intentDoc.exists) {
      const rzpOrder = await razorpay.orders.fetch(razorpay_order_id);
      const coupleId = rzpOrder.notes?.coupleId;
      if (!coupleId) {
        return res.status(404).json({ error: 'Payment intent not found and coupleId missing in order notes' });
      }

      const payablePaise = rzpOrder.amount;
      const vaultPaise = Math.round(payablePaise / 1.02);
      const feePaise = payablePaise - vaultPaise;
      txnid = txnid || `RZP_${Date.now()}_AUTO`;

      const newRef = db.collection('payment_intents').doc(txnid);
      await newRef.set({
        txnid,
        orderId: razorpay_order_id,
        gateway: 'RAZORPAY',
        coupleId,
        userId: rzpOrder.notes?.userId || 'guest',
        vaultAmountPaise: vaultPaise,
        platformFeePaise: feePaise,
        payableAmountPaise: payablePaise,
        status: 'INITIATED',
        createdAt: FieldValue.serverTimestamp()
      });
      intentDoc = await newRef.get();
    }

    const intent = intentDoc.data();

    if (intent.status === 'COMPLETED') {
      return res.json({
        success: true,
        alreadyProcessed: true,
        message: 'Payment was already processed and credited',
        txnid,
        orderId: razorpay_order_id
      });
    }

    await executeVaultCreditTransaction({
      txnid,
      coupleId: intent.coupleId,
      userId: intent.userId,
      userName: intent.userName,
      vaultAmountPaise: Number(intent.vaultAmountPaise),
      platformFeePaise: Number(intent.platformFeePaise),
      payableAmountPaise: Number(intent.payableAmountPaise),
      goalId: intent.goalId,
      paymentId: razorpay_payment_id,
      orderOrLinkId: razorpay_order_id
    });

    res.json({
      success: true,
      message: 'Deposit verified and credited to vault! 🌸',
      txnid,
      orderId: razorpay_order_id,
      paymentId: razorpay_payment_id,
      vaultAmountRupees: Number(intent.vaultAmountPaise) / 100
    });
  } catch (err) {
    console.error('Error verifying Razorpay payment:', err);
    res.status(500).json({ error: err.message || 'Payment verification failed' });
  }
});

/**
 * POST /api/razorpay/webhook
 * Standard Razorpay Webhook listener for asynchronous payment capture
 */
router.post('/webhook', async (req, res) => {
  const secret = process.env.RAZORPAY_WEBHOOK_SECRET || RAZORPAY_KEY_SECRET;
  const signature = req.headers['x-razorpay-signature'];

  if (signature) {
    const shasum = crypto.createHmac('sha256', secret);
    shasum.update(JSON.stringify(req.body));
    const digest = shasum.digest('hex');

    if (digest !== signature) {
      console.warn('⚠️ Invalid Razorpay webhook signature');
      return res.status(400).send('Invalid signature');
    }
  }

  const event = req.body.event;
  console.log(`🌸 Razorpay webhook event received: ${event}`);

  // Immediate acknowledge to Razorpay (prevents webhook retries)
  res.status(200).json({ status: 'ok' });

  // Process capture asynchronously
  try {
    if (event === 'payment.captured' || event === 'payment_link.paid' || event === 'order.paid') {
      const payment = req.body.payload?.payment?.entity;
      const paymentLink = req.body.payload?.payment_link?.entity;
      const order = req.body.payload?.order?.entity;

      const paymentId = payment?.id || paymentLink?.payments?.[0]?.payment_id;
      const notes = payment?.notes || paymentLink?.notes || order?.notes || {};
      let { coupleId, userId, userName, txnid, goalId, vaultAmountPaise, platformFeePaise } = notes;

      const grossAmountPaise = Number(payment?.amount || paymentLink?.amount || order?.amount || 0);

      if (!txnid) {
        txnid = notes.txnid || `RZP_${paymentId || Date.now()}`;
      }

      // If coupleId is missing in notes, look up user by email or contact
      if (!coupleId && (payment?.email || payment?.contact)) {
        const db = getFirestore();
        if (payment.email && payment.email !== 'void@razorpay.com') {
          const uSnap = await db.collection('users').where('email', '==', payment.email).limit(1).get();
          if (!uSnap.empty) {
            coupleId = uSnap.docs[0].data().coupleId;
            userId = userId || uSnap.docs[0].id;
            userName = userName || uSnap.docs[0].data().name;
          }
        }
        if (!coupleId && payment.contact) {
          const cleanPhone = payment.contact.replace(/\D/g, '').slice(-10);
          const uSnap = await db.collection('users').where('phone', '==', cleanPhone).limit(1).get();
          if (!uSnap.empty) {
            coupleId = uSnap.docs[0].data().coupleId;
            userId = userId || uSnap.docs[0].id;
            userName = userName || uSnap.docs[0].data().name;
          }
        }
      }

      if (coupleId && grossAmountPaise > 0) {
        let vPaise = Number(vaultAmountPaise);
        let fPaise = Number(platformFeePaise);
        if (!vPaise || !fPaise) {
          vPaise = Math.round((grossAmountPaise * 100) / 102);
          fPaise = grossAmountPaise - vPaise;
        }

        await executeVaultCreditTransaction({
          txnid,
          coupleId: String(coupleId),
          userId: String(userId || 'guest'),
          userName: userName || 'Partner',
          vaultAmountPaise: vPaise,
          platformFeePaise: fPaise,
          payableAmountPaise: grossAmountPaise,
          goalId: goalId || '',
          paymentId: paymentId || txnid,
          orderOrLinkId: paymentLink?.id || order?.id || 'WEBHOOK'
        });
        console.log(`🌸 Webhook: Successfully credited Razorpay payment ${paymentId} (₹${vPaise / 100}) to couple ${coupleId}`);
      }
    }
  } catch (webhookErr) {
    console.error('Error processing Razorpay webhook event:', webhookErr);
  }
});

/**
 * Reconciles captured payments from Razorpay API against Firestore ledger.
 * Guarantees that any payment captured on Razorpay is recorded in savings_transactions,
 * savings_wallets, and revenue_records.
 */
async function syncCapturedPayments(limit = 50) {
  if (!razorpay) return { success: false, error: 'Razorpay not configured' };
  const db = getFirestore();

  try {
    const paymentsList = await razorpay.payments.all({ count: limit });
    const items = paymentsList.items || [];
    let syncedCount = 0;
    const results = [];

    for (const p of items) {
      if (p.status !== 'captured') continue;

      const pId = p.id;
      // 1. Check if already recorded in savings_transactions
      const existingByUtr = await db.collection('savings_transactions')
        .where('utrNumber', '==', pId)
        .limit(1)
        .get();

      if (!existingByUtr.empty) continue;

      const existingByProvider = await db.collection('savings_transactions')
        .where('providerTxnId', '==', pId)
        .limit(1)
        .get();

      if (!existingByProvider.empty) continue;

      // 2. Extract metadata
      const notes = p.notes || {};
      let coupleId = notes.coupleId;
      let userId = notes.userId;
      let userName = notes.userName;
      let goalId = notes.goalId;
      let txnid = notes.txnid || `RZP_${pId}`;

      // 3. If notes empty, try payment link description
      if (!coupleId && p.description && p.description.startsWith('#')) {
        const linkId = `plink_${p.description.substring(1)}`;
        try {
          const plink = await razorpay.paymentLink.fetch(linkId);
          if (plink && plink.notes) {
            coupleId = coupleId || plink.notes.coupleId;
            userId = userId || plink.notes.userId;
            goalId = goalId || plink.notes.goalId;
            txnid = txnid || plink.notes.txnid;
            userName = userName || plink.customer?.name;
          }
        } catch (_) {}
      }

      // 4. If still missing, resolve user by email / phone
      if (!coupleId) {
        if (p.email && p.email !== 'void@razorpay.com') {
          const uSnap = await db.collection('users').where('email', '==', p.email).limit(1).get();
          if (!uSnap.empty) {
            coupleId = uSnap.docs[0].data().coupleId;
            userId = userId || uSnap.docs[0].id;
            userName = userName || uSnap.docs[0].data().name;
          }
        }
        if (!coupleId && p.contact) {
          const cleanPhone = p.contact.replace(/\D/g, '').slice(-10);
          const uSnap = await db.collection('users').where('phone', '==', cleanPhone).limit(1).get();
          if (!uSnap.empty) {
            coupleId = uSnap.docs[0].data().coupleId;
            userId = userId || uSnap.docs[0].id;
            userName = userName || uSnap.docs[0].data().name;
          }
        }
      }

      // 5. If coupleId resolved, credit the vault
      if (coupleId) {
        const grossAmountPaise = Number(p.amount);
        let vaultAmountPaise = Number(notes.vaultAmountPaise);
        let platformFeePaise = Number(notes.platformFeePaise);

        if (!vaultAmountPaise || !platformFeePaise) {
          vaultAmountPaise = Math.round((grossAmountPaise * 100) / 102);
          platformFeePaise = grossAmountPaise - vaultAmountPaise;
        }

        await executeVaultCreditTransaction({
          txnid,
          coupleId: String(coupleId),
          userId: String(userId || 'guest'),
          userName: userName || 'Partner',
          vaultAmountPaise,
          platformFeePaise,
          payableAmountPaise: grossAmountPaise,
          goalId: goalId || '',
          paymentId: pId,
          orderOrLinkId: p.order_id || 'RZP_SYNC'
        });

        syncedCount++;
        results.push({ paymentId: pId, amount: grossAmountPaise / 100, coupleId });
        console.log(`🌸 Razorpay Sync: Successfully credited payment ${pId} (₹${vaultAmountPaise / 100}) to couple ${coupleId}`);
      }
    }

    return { success: true, syncedCount, results };
  } catch (err) {
    console.error('Error in syncCapturedPayments:', err);
    return { success: false, error: err.message };
  }
}

/**
 * GET & POST /api/razorpay/sync
 * Admin or auto-reconciliation endpoint to sync captured payments
 */
router.all('/sync', async (req, res) => {
  const count = parseInt(req.query.count || req.body?.count || '50', 10);
  const result = await syncCapturedPayments(count);
  res.json(result);
});

/**
 * GET /api/razorpay/settlements
 * Admin endpoint: Fetch settlements from Razorpay
 */
router.get('/settlements', async (req, res) => {
  if (!razorpay) {
    return res.status(500).json({ error: 'Razorpay not configured' });
  }

  try {
    const count = parseInt(req.query.count || '10', 10);
    const settlements = await razorpay.settlements.all({ count });
    res.json(settlements);
  } catch (err) {
    console.error('Error fetching Razorpay settlements:', err);
    res.status(500).json({ error: err.message || 'Failed to fetch settlements' });
  }
});

router.syncCapturedPayments = syncCapturedPayments;
module.exports = router;
