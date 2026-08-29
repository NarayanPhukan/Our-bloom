const express = require('express');
const router = express.Router({ mergeParams: true });
const LoveNote = require('../models/LoveNote');
const { upload } = require('../config/cloudinary');
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

let isGeneratingDailyNote = false;

// GET /api/couples/:slug/love-notes/daily
router.get('/daily', async (req, res) => {
  try {
    // AI daily love note is exclusive to the original couple
    if (req.coupleSlug !== ORIGINAL_COUPLE_SLUG) {
      return res.json(null);
    }

    const todayStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    let dailyNote = await LoveNote.findOne({ coupleId: req.coupleId, isDailyAi: true, dateStr: todayStr });

    if (!dailyNote && !isGeneratingDailyNote) {
      isGeneratingDailyNote = true;
      try {
        const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
        const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
        const prompt = `Write a very short (2-3 sentences max) sweet, deeply romantic, and unique daily compliment or love note for my girlfriend Tanaya. Use beautiful poetic language comparing her to flowers, stars, or art. Include a couple of elegant emojis. Do not use placeholders.`;
        
        const result = await model.generateContent(prompt);
        const content = await result.response.text();

        dailyNote = new LoveNote({
          coupleId: req.coupleId,
          content: content,
          author: 'Kuchupuchu ✨',
          dateStr: todayStr,
          isDailyAi: true
        });
        await dailyNote.save();
      } finally {
        isGeneratingDailyNote = false;
      }
    } else if (!dailyNote && isGeneratingDailyNote) {
      return res.json({
        content: "My love for you grows stronger with every passing second...",
        author: 'Kuchupuchu ✨',
        dateStr: todayStr,
        isDailyAi: true
      });
    }

    res.json(dailyNote);
  } catch (err) {
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
