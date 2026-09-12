const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { getFirestore } = require('../utils/firebase');

const PAYU_KEY = process.env.PAYU_KEY || 'gejLUv';
const PAYU_SALT = process.env.PAYU_SALT || 'VmEuk1TyFepTJ4z8WBcip0vcId520YFi';
const PAYU_MODE = (process.env.PAYU_MODE || 'test').toLowerCase();
const PAYU_ACTION_URL = PAYU_MODE === 'live' 
  ? 'https://secure.payu.in/_payment' 
  : 'https://test.payu.in/_payment';

const BASE_URL = process.env.PAYU_BASE_URL || 'https://our-bloom.onrender.com';
const CLIENT_URL = process.env.CLIENT_URL || 'https://our-bloom-gamma.vercel.app';

/**
 * Generates PayU payment hash
 * Formula: sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT)
 */
function generatePayUHash(params) {
  const { txnid, amount, productinfo, firstname, email, udf1 = '', udf2 = '', udf3 = '', udf4 = '', udf5 = '' } = params;
  const formattedAmount = parseFloat(amount).toFixed(2);
  const hashString = `${PAYU_KEY}|${txnid}|${formattedAmount}|${productinfo}|${firstname}|${email}|${udf1}|${udf2}|${udf3}|${udf4}|${udf5}||||||${PAYU_SALT}`;
  return crypto.createHash('sha512').update(hashString).digest('hex');
}

/**
 * Verifies PayU response hash
 * Formula: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
 * Or with additionalCharges if present: sha512(additionalCharges|SALT|status...)
 */
function verifyPayUResponseHash(body) {
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
 * Direct hosted checkout URL callable from Android app.
 * Opens PayU Hosted Checkout automatically.
 */
router.get('/checkout', (req, res) => {
  try {
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

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      return res.status(400).send('Invalid amount for deposit.');
    }

    if (!coupleId) {
      return res.status(400).send('Missing coupleId for deposit.');
    }

    const txnid = `OB_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
    const productinfo = (goalTitle ? `Vault ${goalTitle}` : 'Our Bloom Vault').replace(/[^a-zA-Z0-9 ]/g, '').trim().substring(0, 50) || 'Our Bloom Vault';
    const cleanFirstName = String(userName || 'Partner').replace(/[^a-zA-Z0-9]/g, '').trim() || 'Partner';
    const cleanEmail = (String(userEmail).trim() && userEmail.includes('@')) ? userEmail.trim() : 'support@ourbloom.app';
    const digitsOnly = String(userPhone || '').replace(/\D/g, '');
    const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : '9999999999';
    const cleanNote = String(note || '').replace(/[|\n\r]/g, ' ').trim().substring(0, 100);

    const params = {
      key: PAYU_KEY,
      txnid,
      amount: parsedAmount.toFixed(2),
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
        <title>Connecting to PayU...</title>
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background: #FFF8F9;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            padding: 20px;
            box-sizing: border-box;
            text-align: center;
            color: #2D2D2D;
          }
          .card {
            background: #FFFFFF;
            padding: 36px 24px;
            border-radius: 28px;
            box-shadow: 0 12px 40px rgba(232, 93, 117, 0.15);
            max-width: 400px;
            width: 100%;
            border: 1px solid #FCE4EC;
          }
          .spinner {
            width: 50px;
            height: 50px;
            border: 4px solid #FCE4EC;
            border-top: 4px solid #E85D75;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
          }
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
          h2 { font-size: 20px; margin: 0 0 8px; color: #E85D75; }
          p { font-size: 14px; color: #666; margin: 0 0 20px; }
          .amount-tag {
            display: inline-block;
            background: #FFF0F3;
            color: #E85D75;
            font-weight: bold;
            font-size: 22px;
            padding: 8px 24px;
            border-radius: 30px;
            margin-bottom: 24px;
          }
          .btn-continue {
            background: #E85D75;
            color: #FFFFFF;
            border: none;
            padding: 14px 28px;
            border-radius: 25px;
            font-size: 15px;
            font-weight: bold;
            cursor: pointer;
            box-shadow: 0 4px 14px rgba(232, 93, 117, 0.3);
          }
        </style>
      </head>
      <body onload="document.forms['payuForm'].submit()">
        <div class="card">
          <div class="spinner"></div>
          <h2>Securing Your Payment...</h2>
          <p>Redirecting to PayU Secure Gateway for Couple Savings Vault 🌸</p>
          <div class="amount-tag">₹${parsedAmount.toFixed(2)}</div>
          <form name="payuForm" method="POST" action="${PAYU_ACTION_URL}">
            ${Object.entries(params).map(([k, v]) => `<input type="hidden" name="${k}" value="${v.toString().replace(/"/g, '&quot;')}" />`).join('\n            ')}
            <noscript>
              <button type="submit" class="btn-continue">Tap to Proceed to PayU</button>
            </noscript>
          </form>
        </div>
      </body>
      </html>
    `);
  } catch (err) {
    console.error('✿ Error in /api/payu/checkout:', err);
    res.status(500).send('Error initiating payment: ' + err.message);
  }
});

/**
 * POST /api/payu/create-payment
 * JSON endpoint for programmatic session creation
 */
router.post('/create-payment', async (req, res) => {
  try {
    const { 
      amount, 
      coupleId, 
      userId, 
      userName = 'Partner', 
      userEmail = 'support@ourbloom.app', 
      userPhone = '9999999999',
      note = 'Couple Vault Deposit', 
      goalId = '', 
      goalTitle = '' 
    } = req.body;

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      return res.status(400).json({ error: 'Valid positive amount is required' });
    }

    if (!coupleId) {
      return res.status(400).json({ error: 'coupleId is required' });
    }

    const txnid = `OB_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
    const productinfo = (goalTitle ? `Vault ${goalTitle}` : 'Our Bloom Vault').replace(/[^a-zA-Z0-9 ]/g, '').trim().substring(0, 50) || 'Our Bloom Vault';
    const cleanFirstName = String(userName || 'Partner').replace(/[^a-zA-Z0-9]/g, '').trim() || 'Partner';
    const cleanEmail = (String(userEmail).trim() && userEmail.includes('@')) ? userEmail.trim() : 'support@ourbloom.app';
    const digitsOnly = String(userPhone || '').replace(/\D/g, '');
    const cleanPhone = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : '9999999999';
    const cleanNote = String(note || '').replace(/[|\n\r]/g, ' ').trim().substring(0, 100);

    const params = {
      key: PAYU_KEY,
      txnid,
      amount: parsedAmount.toFixed(2),
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

    res.json({
      success: true,
      actionUrl: PAYU_ACTION_URL,
      params,
      mode: PAYU_MODE
    });
  } catch (err) {
    console.error('✿ Error in /api/payu/create-payment:', err);
    res.status(500).json({ error: err.message || 'Failed to create payment session' });
  }
});

/**
 * POST /api/payu/success
 * Webhook / Callback handler when payment is approved by PayU
 */
router.post('/success', async (req, res) => {
  console.log('✿ PayU Success Callback received:', {
    txnid: req.body.txnid,
    status: req.body.status,
    mihpayid: req.body.mihpayid,
    amount: req.body.amount
  });

  const isVerified = verifyPayUResponseHash(req.body);
  if (!isVerified) {
    console.error('⚠️ PayU response hash verification failed!');
    return res.status(400).send(`
      <!DOCTYPE html>
      <html>
      <head><title>Payment Verification Failed</title><meta name="viewport" content="width=device-width, initial-scale=1"></head>
      <body style="font-family: sans-serif; text-align: center; padding: 40px; background: #FFF0F3;">
        <h2 style="color: #D32F2F;">⚠️ Payment Hash Verification Failed</h2>
        <p>The transaction signature could not be verified securely. No funds were credited.</p>
        <a href="${CLIENT_URL}" style="display:inline-block; margin-top:20px; padding:12px 24px; background:#E85D75; color:#fff; border-radius:25px; text-decoration:none;">Return to Our Bloom</a>
      </body>
      </html>
    `);
  }

  const {
    txnid,
    amount: amountStr,
    mihpayid: utrNumber,
    udf1: coupleId,
    udf2: userId,
    udf3: userName = 'Partner',
    udf4: goalId = '',
    udf5: note = ''
  } = req.body;

  const amount = parseFloat(amountStr) || 0.0;

  // Credit the Couple's Savings Vault in Firestore
  const db = getFirestore();
  if (db && coupleId && amount > 0) {
    try {
      const walletRef = db.collection('savings_wallets').doc(coupleId);
      const walletDoc = await walletRef.get();

      let totalBalance = amount;
      let user1Total = 0;
      let user2Total = 0;
      let user1Id = userId || '';
      let user1Name = userName || 'Partner';
      let user2Id = '';
      let user2Name = '';

      if (walletDoc.exists) {
        const data = walletDoc.data();
        const isUser1 = !data.user1Id || data.user1Id === userId;
        totalBalance = (data.totalBalance || 0) + amount;
        user1Total = isUser1 ? (data.user1Total || 0) + amount : (data.user1Total || 0);
        user2Total = !isUser1 ? (data.user2Total || 0) + amount : (data.user2Total || 0);
        user1Id = data.user1Id || user1Id;
        user1Name = data.user1Name || user1Name;
        user2Id = data.user2Id || '';
        user2Name = data.user2Name || '';
      }

      await walletRef.set({
        coupleId,
        user1Id,
        user1Name,
        user2Id,
        user2Name,
        totalBalance,
        user1Total,
        user2Total,
        currency: '₹',
        lastUpdated: Date.now()
      }, { merge: true });

      // Update goal if applicable
      if (goalId) {
        try {
          const goalRef = db.collection('savings_goals').doc(goalId);
          const goalDoc = await goalRef.get();
          if (goalDoc.exists) {
            const currentAmount = (goalDoc.data().currentAmount || 0) + amount;
            const targetAmount = goalDoc.data().targetAmount || 0;
            await goalRef.update({
              currentAmount,
              isCompleted: targetAmount > 0 && currentAmount >= targetAmount
            });
          }
        } catch (goalErr) {
          console.warn('Could not update savings goal balance:', goalErr.message);
        }
      }

      // Record Savings Transaction
      const verifiedUtr = utrNumber || txnid || `PAYU-${Date.now()}`;
      await db.collection('savings_transactions').add({
        coupleId,
        userId: userId || '',
        userName: userName || 'Partner',
        type: 'deposit',
        amount,
        utrNumber: verifiedUtr,
        goalId: goalId || '',
        goalTitle: '',
        note: note || 'PayU Online Payment',
        category: 'Savings',
        paymentMethod: 'PayU Hosted',
        timestamp: Date.now()
      });

      // Add Admin Alert
      await db.collection('admin_alerts').add({
        type: 'deposit',
        coupleId,
        userId: userId || '',
        userName: userName || 'Partner',
        amount,
        utrNumber: verifiedUtr,
        note: `PayU Hosted Payment (${txnid})`,
        timestamp: Date.now(),
        status: 'VERIFIED'
      });

      console.log(`✿ Successfully credited ₹${amount} via PayU to couple ${coupleId}. UTR: ${verifiedUtr}`);

      // Notify partner via push notification if available
      try {
        const coupleDoc = await db.collection('couples').doc(coupleId).get();
        if (coupleDoc.exists) {
          const coupleData = coupleDoc.data();
          const partnerId = userId === coupleData.user1 ? coupleData.user2 : (userId === coupleData.user2 ? coupleData.user1 : coupleData.user2);
          if (partnerId) {
            const partnerDoc = await db.collection('users').doc(partnerId).get();
            if (partnerDoc.exists && partnerDoc.data().fcmToken) {
              const { sendPushNotification } = require('../utils/firebase');
              await sendPushNotification(
                partnerDoc.data().fcmToken,
                'Vault Deposit Received! 🌸💰',
                `${userName || 'Your partner'} added ₹${amount.toFixed(2)} to your shared Couple's Vault!`,
                { type: 'savings_deposit', coupleId, amount: String(amount) }
              );
            }
          }
        }
      } catch (pushErr) {
        console.warn('✿ Could not send push notification for vault deposit:', pushErr.message);
      }
    } catch (dbErr) {
      console.error('✿ Error updating savings wallet on PayU success:', dbErr);
    }
  }

  // Render celebratory response page
  res.send(`
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
        .details { background: #F9FBF9; border: 1px solid #C8E6C9; border-radius: 16px; padding: 18px; text-align: left; margin-bottom: 28px; font-size: 14px; }
        .row { display: flex; justify-content: space-between; margin-bottom: 8px; }
        .row:last-child { margin-bottom: 0; }
        .label { color: #666; }
        .val { font-weight: bold; color: #2D2D2D; }
        .btn { display: inline-block; background: #E85D75; color: #FFFFFF; text-decoration: none; padding: 14px 32px; border-radius: 30px; font-weight: bold; font-size: 16px; box-shadow: 0 4px 14px rgba(232, 93, 117, 0.3); transition: transform 0.2s; }
        .btn:hover { transform: translateY(-2px); }
        .note { font-size: 13px; color: #888; margin-top: 16px; }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="icon">🌸✨</div>
        <h1>Payment Successful!</h1>
        <p>Your deposit has been verified and added to your Couple's Savings Vault.</p>
        
        <div class="details">
          <div class="row"><span class="label">Amount Paid:</span><span class="val">₹${amount.toFixed(2)}</span></div>
          <div class="row"><span class="label">PayU Payment ID:</span><span class="val">${utrNumber || txnid}</span></div>
          <div class="row"><span class="label">Order Ref:</span><span class="val">${txnid}</span></div>
          <div class="row"><span class="label">Status:</span><span class="val" style="color:#2E7D32;">✓ VERIFIED</span></div>
        </div>

        <a href="intent://vault#Intent;scheme=ourbloom;package=com.ourbloom.app;end" class="btn">Return to Our Bloom 💖</a>
        <p class="note">Your vault balance has updated live in the app. You can safely close this screen.</p>
      </div>
    </body>
    </html>
  `);
});

/**
 * POST /api/payu/failure
 * Webhook / Callback handler when payment is declined or aborted by PayU
 */
router.post('/failure', (req, res) => {
  console.log('✿ PayU Failure / Cancelled Callback:', {
    txnid: req.body.txnid,
    status: req.body.status,
    error: req.body.error_Message || req.body.unmappedstatus
  });

  const txnid = req.body.txnid || '';
  const errorMsg = req.body.error_Message || req.body.unmappedstatus || 'Transaction was not completed.';

  res.send(`
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
        .note { font-size: 13px; color: #888; margin-top: 16px; }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="icon">🌸💔</div>
        <h1>Payment Incomplete</h1>
        <p>${errorMsg}</p>
        <p style="font-size:13px; color:#888;">No funds were deducted. You can try again anytime.</p>
        <a href="intent://vault#Intent;scheme=ourbloom;package=com.ourbloom.app;end" class="btn">Return to Our Bloom</a>
        <p class="note">You can safely close this screen and return to the app.</p>
      </div>
    </body>
    </html>
  `);
});

module.exports = router;
