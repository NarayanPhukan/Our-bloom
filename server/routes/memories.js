const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const Memory = require('../models/Memory');

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
    cb(null, 'memory-' + uniqueSuffix + path.extname(file.originalname));
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

// GET /api/memories
router.get('/', async (req, res) => {
  try {
    const memories = await Memory.find().sort({ createdAt: -1 });
    res.json(memories);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/memories (multipart/form-data)
router.post('/', upload.single('image'), async (req, res) => {
  try {
    const { title, dateStr, icon } = req.body;
    
    if (!req.file) {
      return res.status(400).json({ error: 'Image file is required' });
    }

    const imageUrl = '/uploads/' + req.file.filename;

    // Random rotation between -3 and 3 degrees for the masonry polaroid effect
    const rotation = Math.floor(Math.random() * 7) - 3;

    const newMemory = new Memory({
      title: title || 'A Beautiful Moment',
      dateStr: dateStr || new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).toUpperCase(),
      imageUrl,
      rotation,
      icon: icon || 'favorite'
    });

    const savedMemory = await newMemory.save();
    res.status(201).json(savedMemory);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/memories/:id
router.delete('/:id', async (req, res) => {
  try {
    const memory = await Memory.findByIdAndDelete(req.params.id);
    if (!memory) return res.status(404).json({ error: 'Memory not found' });
    
    // Attempt to delete the file
    const filePath = path.join(__dirname, '..', memory.imageUrl);
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
    }
    
    res.json({ message: 'Memory deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
