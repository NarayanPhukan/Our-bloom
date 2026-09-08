const express = require('express');
const router = express.Router({ mergeParams: true });
const LoveNote = require('../models/LoveNote');
const { upload } = require('../config/upload');
const { uploadToFirebase } = require('../utils/firebaseStorage');
const { GoogleGenerativeAI } = require('@google/generative-ai');

// The original couple's ID (Narayan & Tanaya) — set by migration
// Used to gate the AI daily love note feature
const ORIGINAL_COUPLE_SLUG = 'narayan-tanaya';

// GET /api/couples/:slug/love-notes
router.get('/', async (req, res) => {
  try {
    const notes = await LoveNote.find({ coupleId: req.coupleId }).sort({ createdAt: -1 });
    res.json(notes);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

const { generateDailyNoteForCouple, getTodayDateStr } = require('../jobs/dailyLoveNote');

// GET /api/couples/:slug/love-notes/daily
router.get('/daily', async (req, res) => {
  try {
    const coupleId = req.coupleId;
    const coupleSlug = req.coupleSlug;
    const requestingUserId = req.user?.userId;
    
    const note = await generateDailyNoteForCouple(coupleId, coupleSlug, {}, {
      requestingUserId,
      io: req.app.get('io')
    });
    res.json(note);
  } catch (err) {
    console.error('Error fetching/generating daily love note:', err);
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/love-notes
router.post('/', upload.fields([{ name: 'image', maxCount: 1 }, { name: 'audio', maxCount: 1 }]), async (req, res) => {
  try {
    const { content, audioDuration } = req.body;
    let imageUrl = req.body.imageUrl || '';
    let audioUrl = req.body.audioUrl || '';

    const bucket = req.app.get('bucket');

    if (req.files?.image?.[0]) {
      const imgFile = req.files.image[0];
      if (bucket) {
        imageUrl = (await uploadToFirebase(bucket, imgFile, 'loveNotes_images')) || imgFile.path || '';
      } else {
        imageUrl = imgFile.path || '';
      }
    } else if (req.file) {
      imageUrl = req.file.path || '';
    }

    if (req.files?.audio?.[0]) {
      const audioFile = req.files.audio[0];
      if (bucket) {
        audioUrl = (await uploadToFirebase(bucket, audioFile, 'loveNotes_audio')) || '';
      }
    }

    const dateStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    const newNote = new LoveNote({
      coupleId: req.coupleId,
      content: content || (audioUrl ? '🎙️ Voice Love Note' : 'No content provided'),
      dateStr,
      imageUrl,
      audioUrl,
      audioDuration: parseInt(audioDuration, 10) || 0,
      hasImage: !!imageUrl,
      author: req.user?.name || 'Your Love'
    });

    const savedNote = await newNote.save();

    // Dual-sync to Firestore loveNotes collection
    try {
      const { getFirestore } = require('../utils/firebase');
      const db = getFirestore();
      if (db) {
        await db.collection('loveNotes').doc(savedNote._id.toString()).set({
          coupleId: req.coupleId.toString(),
          content: savedNote.content,
          dateStr: savedNote.dateStr,
          imageUrl: savedNote.imageUrl || '',
          audioUrl: savedNote.audioUrl || '',
          audioDuration: savedNote.audioDuration || 0,
          author: savedNote.author || req.user?.name || 'Your Love',
          isDailyAi: false,
          createdAt: new Date().toISOString()
        }, { merge: true });
      }
    } catch (fsErr) {
      console.warn('✿ Firestore dual-sync warning for loveNote:', fsErr.message);
    }

    const io = req.app.get('io');
    if (io) {
      io.to(req.coupleSlug).emit('newNote', savedNote);
      io.to(req.coupleSlug).emit('notification', {
        type: 'note_added',
        userId: req.user.userId,
        title: 'a love note'
      });
    }

    // Try to send push notification
    try {
      const Couple = require('../models/Couple');
      const User = require('../models/User');
      const { sendPushNotification } = require('../utils/firebase');
      
      const couple = await Couple.findById(req.coupleId);
      if (couple) {
        // Find partner ID
        const partnerId = couple.user1.toString() === req.user.userId.toString() ? couple.user2 : couple.user1;
        if (partnerId) {
          const partner = await User.findById(partnerId);
          if (partner && partner.fcmToken) {
            await sendPushNotification(
              partner.fcmToken,
              "New Love Note 💌",
              `Your partner just dropped a love note for you!`,
              { type: 'note', id: savedNote._id.toString() }
            );
          }
        }
      }
    } catch (pushErr) {
      console.error('Failed to send push notification:', pushErr);
    }

    res.status(201).json(savedNote);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/couples/:slug/love-notes/:id
router.delete('/:id', async (req, res) => {
  try {
    const note = await LoveNote.findOneAndDelete({ _id: req.params.id, coupleId: req.coupleId });
    if (!note) return res.status(404).json({ error: 'Note not found' });
    
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('deleteNote', req.params.id);

    res.json({ message: 'Note deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
