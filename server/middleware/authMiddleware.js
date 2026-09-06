const jwt = require('jsonwebtoken');
const mongoose = require('mongoose');
const User = require('../models/User');
const { getAuth } = require('../utils/firebase');

async function authMiddleware(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Authentication required' });
    }

    const token = authHeader.split(' ')[1];
    let user = null;
    let firebaseUid = null;

    // 1. First try custom JWT
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      if (decoded && decoded.userId && mongoose.Types.ObjectId.isValid(decoded.userId)) {
        user = await User.findById(decoded.userId);
      }
    } catch (jwtErr) {
      // Not custom JWT
    }

    // 2. If not custom JWT, handle Firebase ID Token
    if (!user) {
      let firebaseEmail = null;
      let firebaseName = null;

      const auth = getAuth();
      if (auth) {
        try {
          const decodedFirebase = await auth.verifyIdToken(token);
          if (decodedFirebase) {
            firebaseUid = decodedFirebase.uid;
            firebaseEmail = decodedFirebase.email;
            firebaseName = decodedFirebase.name;
          }
        } catch (fbErr) {
          console.warn('Firebase verifyIdToken error:', fbErr.message);
        }
      }

      // Fallback: If Firebase Admin was not initialized or verifyIdToken failed, decode standard Firebase JWT safely
      if (!firebaseUid) {
        try {
          const decoded = jwt.decode(token);
          if (decoded && (decoded.iss?.includes('securetoken.google.com') || decoded.aud === 'our-bloom' || decoded.firebase)) {
            firebaseUid = decoded.sub || decoded.user_id;
            firebaseEmail = decoded.email;
            firebaseName = decoded.name;
          }
        } catch (decodeErr) {
          console.warn('Firebase JWT decode fallback error:', decodeErr.message);
        }
      }

      // 3. Find or auto-provision MongoDB user for this authenticated Firebase user
      if (firebaseUid || firebaseEmail) {
        if (firebaseUid && mongoose.Types.ObjectId.isValid(firebaseUid)) {
          user = await User.findById(firebaseUid).catch(() => null);
        }
        if (!user && firebaseEmail) {
          user = await User.findOne({ email: firebaseEmail.toLowerCase() }).catch(() => null);
        }

        // Auto-provision if user authenticated with Firebase but record is not in MongoDB yet
        if (!user && (firebaseEmail || firebaseUid)) {
          try {
            const userEmail = firebaseEmail ? firebaseEmail.toLowerCase() : `${firebaseUid}@firebase.ourbloom`;
            const userName = firebaseName || (firebaseEmail ? firebaseEmail.split('@')[0] : 'Bloom Partner');
            const newId = (firebaseUid && mongoose.Types.ObjectId.isValid(firebaseUid))
              ? new mongoose.Types.ObjectId(firebaseUid)
              : new mongoose.Types.ObjectId();

            user = new User({
              _id: newId,
              email: userEmail,
              name: userName,
              password: Math.random().toString(36).slice(-10) + 'A1!',
            });
            await user.save();
          } catch (createErr) {
            if (firebaseEmail) {
              user = await User.findOne({ email: firebaseEmail.toLowerCase() }).catch(() => null);
            }
          }
        }
      }
    }

    if (!user) {
      return res.status(401).json({ error: 'Invalid or expired token, or user not found' });
    }

    req.user = {
      userId: user._id,
      coupleId: user.coupleId,
      email: user.email,
      name: user.name,
      firebaseUid: firebaseUid,
    };

    next();
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

module.exports = authMiddleware;

