const express = require('express');
const router = express.Router();
const LoveNote = require('../models/LoveNote');
const { upload } = require('../config/cloudinary');
const { GoogleGenerativeAI } = require('@google/generative-ai');

// GET /api/love-notes
router.get('/', async (req, res) => {
  try {
    const notes = await LoveNote.find().sort({ createdAt: -1 });
    res.json(notes);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

let isGeneratingDailyNote = false;

// GET /api/love-notes/daily
router.get('/daily', async (req, res) => {
  try {
    const todayStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    let dailyNote = await LoveNote.findOne({ isDailyAi: true, dateStr: todayStr });

    if (!dailyNote && !isGeneratingDailyNote) {
      isGeneratingDailyNote = true;
      try {
        const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
        const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
        const prompt = `Write a very short (2-3 sentences max) sweet, deeply romantic, and unique daily compliment or love note for my girlfriend Tanaya. Use beautiful poetic language comparing her to flowers, stars, or art. Include a couple of elegant emojis. Do not use placeholders.`;
        
        const result = await model.generateContent(prompt);
        const content = await result.response.text();

        dailyNote = new LoveNote({
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
      // If it's currently generating, just return a fallback temporarily to prevent duplicate generation
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

// POST /api/love-notes (multipart/form-data support for images)
router.post('/', upload.single('image'), async (req, res) => {
  try {
    const { content } = req.body;
    let imageUrl = '';

    if (req.file) {
      imageUrl = req.file.path; // Cloudinary URL
    }

    // Default formatting logic from before
    const dateStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    const newNote = new LoveNote({
      content: content || 'No content provided',
      dateStr,
      imageUrl,
      hasImage: !!imageUrl
    });

    const savedNote = await newNote.save();

    const io = req.app.get('io');
    if (io) io.emit('newNote', savedNote);

    res.status(201).json(savedNote);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/love-notes/:id
router.delete('/:id', async (req, res) => {
  try {
    const note = await LoveNote.findByIdAndDelete(req.params.id);
    if (!note) return res.status(404).json({ error: 'Note not found' });
    
    // Cloudinary destroy would go here
    
    const io = req.app.get('io');
    if (io) io.emit('deleteNote', req.params.id);

    res.json({ message: 'Note deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
