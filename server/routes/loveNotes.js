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
    
    const note = await generateDailyNoteForCouple(coupleId, coupleSlug);
    res.json(note);
  } catch (err) {
    console.error('Error fetching/generating daily love note:', err);
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/love-notes
router.post('/', upload.single('image'), async (req, res) => {
  try {
    const { content } = req.body;
    let imageUrl = '';

    if (req.file) {
      imageUrl = req.file.path;
    }

    const dateStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    const newNote = new LoveNote({
      coupleId: req.coupleId,
      content: content || 'No content provided',
      dateStr,
      imageUrl,
      hasImage: !!imageUrl
    });

    const savedNote = await newNote.save();

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
