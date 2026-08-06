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

// GET /api/love-notes/daily
router.get('/daily', async (req, res) => {
  try {
    const todayStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    let dailyNote = await LoveNote.findOne({ isDailyAi: true, dateStr: todayStr });

    if (!dailyNote) {
      const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
      const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
      const prompt = `Write a very short (2-3 sentences max) sweet, deeply romantic, and unique daily compliment or love note for my girlfriend Tanaya. Use beautiful poetic language comparing her to flowers, stars, or art. Include a couple of elegant emojis. Do not use placeholders.`;
      
      const result = await model.generateContent(prompt);
      const content = await result.response.text();

      dailyNote = new LoveNote({
        content: content,
        author: 'Gemini ✨',
        dateStr: todayStr,
        isDailyAi: true
      });
      await dailyNote.save();
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
    
    res.json({ message: 'Note deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
