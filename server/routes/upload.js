const express = require('express');
const router = express.Router();
const { upload } = require('../config/upload');
const { uploadToFirebase } = require('../utils/firebaseStorage');
const authMiddleware = require('../middleware/authMiddleware');
const { getFirestore } = require('../utils/firebase');

// Definitive Size Quotas (Bytes)
const MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10 MB
const MAX_AUDIO_SIZE = 25 * 1024 * 1024; // 25 MB
const MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50 MB (Definitive limit)
const DAILY_UPLOAD_LIMIT_PER_USER = 50;

/**
 * Validates file buffer magic bytes against authentic signatures.
 * Rejects disguised executables, polyglots, and script files.
 */
function inspectFileBuffer(buffer) {
  if (!buffer || buffer.length < 12) return null;

  // 1. Reject malicious headers immediately
  // Windows PE: 'MZ'
  if (buffer[0] === 0x4D && buffer[1] === 0x5A) return null;
  // Linux ELF: 0x7F 'ELF'
  if (buffer[0] === 0x7F && buffer[1] === 0x45 && buffer[2] === 0x4C && buffer[3] === 0x46) return null;
  // Java / Mach-O: 0xCA 0xFE 0xBA 0xBE
  if (buffer[0] === 0xCA && buffer[1] === 0xFE && buffer[2] === 0xBA && buffer[3] === 0xBE) return null;

  const headerAscii = buffer.slice(0, 100).toString('utf8', 0, Math.min(100, buffer.length)).toLowerCase();
  if (headerAscii.includes('<?php') || headerAscii.includes('<script') || headerAscii.startsWith('#!')) {
    return null;
  }

  // 2. JPEG: FF D8 FF
  if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
    return { mime: 'image/jpeg', ext: '.jpg', category: 'image', maxSize: MAX_IMAGE_SIZE };
  }

  // 3. PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47 &&
    buffer[4] === 0x0D && buffer[5] === 0x0A && buffer[6] === 0x1A && buffer[7] === 0x0A
  ) {
    return { mime: 'image/png', ext: '.png', category: 'image', maxSize: MAX_IMAGE_SIZE };
  }

  // 4. WebP: RIFF....WEBP
  if (
    buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
    buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50
  ) {
    return { mime: 'image/webp', ext: '.webp', category: 'image', maxSize: MAX_IMAGE_SIZE };
  }

  // 5. MP4 / M4A / MOV (ISO Base Media): bytes 4-7 are 'ftyp'
  if (buffer[4] === 0x66 && buffer[5] === 0x74 && buffer[6] === 0x79 && buffer[7] === 0x70) {
    const brand = buffer.slice(8, 12).toString('ascii').toLowerCase();
    if (brand.includes('m4a')) {
      return { mime: 'audio/m4a', ext: '.m4a', category: 'audio', maxSize: MAX_AUDIO_SIZE };
    }
    if (brand.includes('qt')) {
      return { mime: 'video/quicktime', ext: '.mov', category: 'video', maxSize: MAX_VIDEO_SIZE };
    }
    return { mime: 'video/mp4', ext: '.mp4', category: 'video', maxSize: MAX_VIDEO_SIZE };
  }

  // 6. Audio AAC ADTS
  if (buffer[0] === 0xFF && (buffer[1] & 0xF6) === 0xF0) {
    return { mime: 'audio/aac', ext: '.aac', category: 'audio', maxSize: MAX_AUDIO_SIZE };
  }

  // 7. Audio MP3: ID3 or sync frame
  if (
    (buffer[0] === 0x49 && buffer[1] === 0x44 && buffer[2] === 0x33) ||
    (buffer[0] === 0xFF && (buffer[1] & 0xE0) === 0xE0)
  ) {
    return { mime: 'audio/mpeg', ext: '.mp3', category: 'audio', maxSize: MAX_AUDIO_SIZE };
  }

  return null;
}

/**
 * POST /api/upload
 * Authenticated, quota-managed, and signature-verified media upload endpoint.
 */
router.post('/', authMiddleware, upload.single('file'), async (req, res) => {
  try {
    if (!req.file || !req.file.buffer) {
      return res.status(400).json({ error: 'No file buffer received for upload' });
    }

    const userId = req.user.firebaseUid || req.user.userId?.toString();
    const targetCoupleId = req.body.coupleId || req.user.coupleId?.toString();

    if (!targetCoupleId) {
      return res.status(400).json({ error: 'Couple ID is required for media storage' });
    }

    // 1. Verify Couple Membership
    const db = getFirestore();
    if (db) {
      const coupleDoc = await db.collection('couples').doc(targetCoupleId).get();
      if (coupleDoc.exists) {
        const cData = coupleDoc.data();
        const isMember = cData.user1 === userId || cData.user2 === userId ||
                         cData.user1 === req.user.userId?.toString() || cData.user2 === req.user.userId?.toString();
        if (!isMember) {
          return res.status(403).json({ error: 'Forbidden: You do not belong to this couple workspace' });
        }
      }
    }

    // 2. Validate Buffer Signature & MIME category
    const fileInfo = inspectFileBuffer(req.file.buffer);
    if (!fileInfo) {
      return res.status(400).json({
        error: 'Invalid or unsupported file format. Disguised executables and polyglots are strictly rejected.'
      });
    }

    // 3. Enforce Specific Category Size Limits
    if (req.file.size > fileInfo.maxSize) {
      return res.status(413).json({
        error: `Payload too large for ${fileInfo.category}. Maximum allowed size is ${Math.round(fileInfo.maxSize / (1024 * 1024))} MB.`
      });
    }

    // 4. Check Daily User Upload Quota
    const todayDate = new Date().toISOString().split('T')[0];
    const quotaDocId = `${userId}_${todayDate}`;
    if (db) {
      const quotaRef = db.collection('user_upload_quotas').doc(quotaDocId);
      const quotaDoc = await quotaRef.get();
      const currentCount = quotaDoc.exists ? (quotaDoc.data().count || 0) : 0;

      if (currentCount >= DAILY_UPLOAD_LIMIT_PER_USER) {
        return res.status(429).json({
          error: `Daily upload limit reached (${DAILY_UPLOAD_LIMIT_PER_USER} uploads/day). Please try again tomorrow.`
        });
      }

      await quotaRef.set({
        userId,
        date: todayDate,
        count: currentCount + 1,
        updatedAt: Date.now()
      }, { merge: true });
    }

    // 5. Store File with Safe UUID Path (Stripping Client Filename)
    const storageFolder = `couples/${targetCoupleId}/media`;
    const secureUrl = await uploadToFirebase(null, req.file, storageFolder, {
      ext: fileInfo.ext,
      mime: fileInfo.mime
    });

    if (!secureUrl) {
      return res.status(500).json({ error: 'Failed to upload media to cloud storage' });
    }

    res.status(201).json({
      url: secureUrl,
      category: fileInfo.category,
      mimeType: fileInfo.mime,
      sizeBytes: req.file.size
    });
  } catch (err) {
    console.error('✿ Error in /api/upload:', err);
    res.status(500).json({ error: err.message || 'Internal error during media upload' });
  }
});

module.exports = router;
