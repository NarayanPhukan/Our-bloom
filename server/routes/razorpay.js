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
    // 2. Fetch payment intent by orderId or txnid
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
      // Fetch order details from Razorpay to reconstruct intent
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
    const coupleId = intent.coupleId;
    const vaultAmountPaise = Number(intent.vaultAmountPaise);
    const platformFeePaise = Number(intent.platformFeePaise);
    const payableAmountPaise = Number(intent.payableAmountPaise);
    const userId = intent.userId || 'guest';
    const userName = intent.userName || 'Partner';
    const goalId = intent.goalId || null;

    // Check if already completed
    if (intent.status === 'COMPLETED') {
      return res.json({
        success: true,
        alreadyProcessed: true,
        message: 'Payment was already processed and credited',
        txnid,
        orderId: razorpay_order_id
      });
    }

    // 3. Execute atomic transaction to credit the vault & record revenue
    await db.runTransaction(async (transaction) => {
      // A. Check ledger duplication
      const ledgerRef = db.collection('savings_transactions').doc(`LEDGER_${txnid}`);
      const ledgerDoc = await transaction.get(ledgerRef);
      if (ledgerDoc.exists) return;

      // B. Update Wallet Balance
      const walletRef = db.collection('savings_wallets').doc(coupleId);
      const walletDoc = await transaction.get(walletRef);
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

      // C. Update Goal (if specified)
      if (goalId) {
        const goalRef = db.collection('savings_goals').doc(goalId);
        const goalDoc = await transaction.get(goalRef);
        if (goalDoc.exists) {
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
      }

      // D. Record Savings Transaction
      transaction.set(ledgerRef, {
        ledgerId: `LEDGER_${txnid}`,
        requestId: txnid,
        coupleId,
        userId,
        userName,
        type: 'deposit',
        vaultAmountPaise,
        platformFeePaise,
        payableAmountPaise,
        platformFeeBps: CONFIG.PLATFORM_FEE_BPS,
        pricingModel: 'FEE_ON_TOP',
        currency: 'INR',
        providerTxnId: razorpay_payment_id,
        providerBankReference: razorpay_order_id,
        status: 'COMPLETED',
        grossAmountPaise: payableAmountPaise,
        netVaultCreditPaise: vaultAmountPaise,
        settlementStatus: 'PENDING',
        amountPaise: vaultAmountPaise,
        amount: vaultAmountPaise / 100,
        grossAmount: payableAmountPaise / 100,
        platformFee: platformFeePaise / 100,
        netVaultCredit: vaultAmountPaise / 100,
        txnid,
        platformTransactionId: txnid,
        utrNumber: razorpay_payment_id,
        goalId: goalId || '',
        paymentMethod: 'Razorpay',
        timestamp: Date.now(),
        createdAt: FieldValue.serverTimestamp()
      });

      // E. Record Platform Fee & Company Revenue
      if (platformFeePaise > 0) {
        const revRef = db.collection('revenue_records').doc(`REV_RZP_${txnid}`);
        transaction.set(revRef, {
          eventId: `REV_RZP_${txnid}`,
          source: 'SAVINGS_TRANSACTION',
          sourceTransactionId: txnid,
          type: 'FEE',
          title: '2% Deposit Platform Fee',
          grossAmountPaise: platformFeePaise,
          netRevenuePaise: platformFeePaise,
          coupleId,
          userId,
          userName,
          paymentMethod: 'Razorpay',
          referenceId: razorpay_payment_id,
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
          providerReference: razorpay_payment_id,
          timestamp: FieldValue.serverTimestamp()
        });
      }

      // F. Update Intent to COMPLETED
      transaction.update(db.collection('payment_intents').doc(txnid), {
        status: 'COMPLETED',
        paymentId: razorpay_payment_id,
        completedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });
    });

    res.json({
      success: true,
      message: 'Deposit verified and credited to vault! 🌸',
      txnid,
      orderId: razorpay_order_id,
      paymentId: razorpay_payment_id,
      vaultAmountRupees: vaultAmountPaise / 100
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

  // Immediate acknowledge
  res.status(200).json({ status: 'ok' });
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

module.exports = router;
