const jwt = require('jsonwebtoken');
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

    // 1. First try custom JWT
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      user = await User.findById(decoded.userId);
    } catch (jwtErr) {
      // 2. If not standard JWT, try Firebase ID Token
      const auth = getAuth();
      if (auth) {
        try {
          const decodedFirebase = await auth.verifyIdToken(token);
          if (decodedFirebase && decodedFirebase.uid) {
            user = await User.findById(decodedFirebase.uid);
            if (!user && decodedFirebase.email) {
              user = await User.findOne({ email: decodedFirebase.email.toLowerCase() });
            }
          }
        } catch (fbErr) {
          // Token is neither valid JWT nor valid Firebase token
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
    };

    next();
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

module.exports = authMiddleware;
