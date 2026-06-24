const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const LoveNote = require('../models/LoveNote');

// Configure multer storage
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadPath = path.join(__dirname, '../uploads');
    if (!fs.existsSync(uploadPath)) {
      fs.mkdirSync(uploadPath, { recursive: true });
    }
    cb(null, uploadPath);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1e9);
    cb(null, 'lovenote-' + uniqueSuffix + path.extname(file.originalname));
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 10 * 1024 * 1024 }, // 10MB limit
  fileFilter: (req, file, cb) => {
    const allowedTypes = /jpeg|jpg|png|gif|webp/;
    const extname = allowedTypes.test(path.extname(file.originalname).toLowerCase());
    const mimetype = allowedTypes.test(file.mimetype);
    if (extname && mimetype) {
      return cb(null, true);
    }
    cb(new Error('Images only!'));
  },
});

// GET all love notes (newest first)
router.get('/', async (req, res) => {
  try {
    const notes = await LoveNote.find().sort({ createdAt: -1 });
    res.json(notes);
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// POST create love note
router.post('/', upload.single('image'), async (req, res) => {
  let imageUrl = '';
  if (req.file) {
    imageUrl = '/uploads/' + req.file.filename;
  }

  const note = new LoveNote({
    content: req.body.content,
    author: req.body.author || 'Anonymous',
    imageUrl: imageUrl
  });

  try {
    const newNote = await note.save();
    res.status(201).json(newNote);
  } catch (err) {
    res.status(400).json({ message: err.message });
  }
});

// DELETE love note
router.delete('/:id', async (req, res) => {
  try {
    const note = await LoveNote.findById(req.params.id);
    if (!note) return res.status(404).json({ message: 'Note not found' });
    
    // Attempt to delete the file if it exists
    if (note.imageUrl) {
      const filePath = path.join(__dirname, '..', note.imageUrl);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
    }

    await note.deleteOne();
    res.json({ message: 'Note deleted' });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

module.exports = router;
