const express = require('express');
const jwt = require('jsonwebtoken');
const router = express.Router();
const User = require('../models/User');
const authMiddleware = require('../middleware/authMiddleware');
const { getAuth, getFirestore } = require('../utils/firebase');

function generateToken(user) {
  return jwt.sign(
    {
      userId: user._id,
      coupleId: user.coupleId,
    },
    process.env.JWT_SECRET,
    { expiresIn: '30d' }
  );
}

// POST /api/auth/register
router.post('/register', async (req, res) => {
  try {
    const { email, password, name } = req.body;

    if (!email || !password || !name) {
      return res.status(400).json({ error: 'Email, password, and name are required' });
    }

    const existingUser = await User.findOne({ email: email.toLowerCase() });
    if (existingUser) {
      return res.status(400).json({ error: 'An account with this email already exists' });
    }

    const user = new User({
      email,
      password,
      name,
    });

    await user.save();

    const idStr = user._id.toString();

    // Dual-sync to Firebase Auth & Cloud Firestore
    let firebaseCustomToken = null;
    try {
      const auth = getAuth();
      const db = getFirestore();
      if (auth) {
        try {
          await auth.createUser({
            uid: idStr,
            email: user.email,
            password: password,
            displayName: user.name,
          });
        } catch (fbErr) {
          if (fbErr.code === 'auth/email-already-exists') {
            const existingFb = await auth.getUserByEmail(user.email).catch(() => null);
            if (existingFb && existingFb.uid !== idStr) {
              await auth.deleteUser(existingFb.uid);
              await auth.createUser({
                uid: idStr,
                email: user.email,
                password: password,
                displayName: user.name,
              });
            }
          }
        }
        firebaseCustomToken = await auth.createCustomToken(idStr);
      }

      if (db) {
        await db.collection('users').doc(idStr).set({
          uid: idStr,
          email: user.email,
          name: user.name,
          coupleId: null,
          avatarUrl: '',
          nicknameForPartner: '',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }, { merge: true });
      }
    } catch (syncErr) {
      console.error('Firebase dual-sync error on register:', syncErr.message);
    }

    const token = generateToken(user);
    res.status(201).json({ token, firebaseCustomToken, user: user.toJSON() });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/auth/login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }

    const isMatch = await user.comparePassword(password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }

    const idStr = user._id.toString();

    // Dual-sync to Firebase Auth & Cloud Firestore
    let firebaseCustomToken = null;
    try {
      const auth = getAuth();
      const db = getFirestore();
      if (auth) {
        try {
          await auth.getUser(idStr);
          await auth.updateUser(idStr, { password, displayName: user.name }).catch(() => {});
        } catch (notFound) {
          if (notFound.code === 'auth/user-not-found') {
            try {
              await auth.createUser({
                uid: idStr,
                email: user.email,
                password: password,
                displayName: user.name,
              });
            } catch (createErr) {
              if (createErr.code === 'auth/email-already-exists') {
                const existing = await auth.getUserByEmail(user.email).catch(() => null);
                if (existing && existing.uid !== idStr) {
                  await auth.deleteUser(existing.uid);
                  await auth.createUser({
                    uid: idStr,
                    email: user.email,
                    password: password,
                    displayName: user.name,
                  });
                }
              }
            }
          }
        }
        firebaseCustomToken = await auth.createCustomToken(idStr);
      }

      if (db) {
        await db.collection('users').doc(idStr).set({
          uid: idStr,
          email: user.email,
          name: user.name || '',
          coupleId: user.coupleId ? user.coupleId.toString() : null,
          avatarUrl: user.avatarUrl || '',
          nicknameForPartner: user.nicknameForPartner || '',
          updatedAt: new Date().toISOString(),
        }, { merge: true });
      }
    } catch (fbErr) {
      console.error('Firebase token generation error on login:', fbErr.message);
    }

    const token = generateToken(user);
    res.json({ token, firebaseCustomToken, user: user.toJSON() });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/auth/me
router.get('/me', authMiddleware, async (req, res) => {
  try {
    const user = await User.findById(req.user.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json(user.toJSON());
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT /api/auth/me/nickname
router.put('/me/nickname', authMiddleware, async (req, res) => {
  try {
    const { nicknameForPartner } = req.body;
    const user = await User.findById(req.user.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    user.nicknameForPartner = nicknameForPartner || '';
    await user.save();

    res.json(user.toJSON());
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT /api/auth/me/fcm-token
router.put('/me/fcm-token', authMiddleware, async (req, res) => {
  try {
    const { fcmToken } = req.body;
    const user = await User.findById(req.user.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    user.fcmToken = fcmToken || null;
    await user.save();

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
