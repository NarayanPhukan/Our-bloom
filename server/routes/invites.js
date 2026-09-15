const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const authMiddleware = require('../middleware/authMiddleware');
const { getFirestore } = require('../utils/firebase');

/**
 * POST /api/invites/create
 * Creates a protected single-use couple invitation code with 48h expiration.
 * Invites are stored in Firestore with client direct read/write disabled.
 */
router.post('/create', authMiddleware, async (req, res) => {
  try {
    const callerUid = req.user.firebaseUid || req.user.userId?.toString();
    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ error: 'Firestore unavailable' });
    }

    // Check if user already belongs to an active couple
    const userDoc = await db.collection('users').doc(callerUid).get();
    if (userDoc.exists && userDoc.data().coupleId) {
      return res.status(400).json({ error: 'You are already part of an active couple workspace' });
    }

    // Generate secure alphanumeric code: BLOOM-XXXX-YYYY
    const randomHex = crypto.randomBytes(4).toString('hex').toUpperCase();
    const code = `BLOOM-${randomHex.slice(0, 4)}-${randomHex.slice(4, 8)}`;
    const now = Date.now();
    const expiresAt = now + (48 * 60 * 60 * 1000); // 48-hour expiration window

    await db.collection('invites').doc(code).set({
      code,
      createdBy: callerUid,
      creatorName: req.user.name || userDoc.data()?.name || 'Partner',
      creatorAvatarUrl: userDoc.data()?.avatarUrl || '',
      createdAt: now,
      expiresAt,
      isRedeemed: false
    });

    res.status(201).json({
      code,
      expiresAt,
      message: 'Invitation code generated successfully (valid for 48 hours).'
    });
  } catch (err) {
    console.error('✿ Error creating invite code:', err);
    res.status(500).json({ error: err.message || 'Failed to create invite' });
  }
});

/**
 * POST /api/invites/validate
 * Validates an invite code and returns sanitized partner preview metadata.
 * Private personal identifiers (email, phone, UID) are never exposed.
 */
router.post('/validate', authMiddleware, async (req, res) => {
  try {
    const { code } = req.body;
    if (!code) {
      return res.status(400).json({ valid: false, error: 'Invite code is required' });
    }

    const cleanCode = String(code).trim().toUpperCase();
    const db = getFirestore();
    if (!db) {
      return res.status(500).json({ valid: false, error: 'Firestore unavailable' });
    }

    const inviteDoc = await db.collection('invites').doc(cleanCode).get();
    if (!inviteDoc.exists) {
      return res.status(404).json({ valid: false, error: 'Invitation code not found' });
    }

    const inviteData = inviteDoc.data();
    if (inviteData.isRedeemed) {
      return res.status(400).json({ valid: false, error: 'This invitation code has already been used' });
    }

    if (inviteData.expiresAt && inviteData.expiresAt < Date.now()) {
      return res.status(400).json({ valid: false, error: 'This invitation code has expired' });
    }

    // Return sanitized public profile preview only
    res.json({
      valid: true,
      partnerName: inviteData.creatorName || 'Partner',
      partnerAvatarUrl: inviteData.creatorAvatarUrl || '',
      expiresAt: inviteData.expiresAt
    });
  } catch (err) {
    console.error('✿ Error validating invite code:', err);
    res.status(500).json({ valid: false, error: err.message || 'Validation failed' });
  }
});

module.exports = router;
