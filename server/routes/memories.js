const express = require('express');
const router = express.Router();
const Memory = require('../models/Memory');
const { upload } = require('../config/cloudinary');

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
router.post('/', upload.fields([{ name: 'image', maxCount: 1 }, { name: 'audio', maxCount: 1 }]), async (req, res) => {
  try {
    const { title, dateStr, icon } = req.body;
    
    if (!req.files || !req.files['image']) {
      return res.status(400).json({ error: 'Image file is required' });
    }

    // req.files['image'][0].path contains the Cloudinary URL
    const imageUrl = req.files['image'][0].path;
    let audioUrl = '';
    
    if (req.files['audio']) {
      audioUrl = req.files['audio'][0].path;
    }

    // Random rotation between -3 and 3 degrees for the masonry polaroid effect
    const rotation = Math.floor(Math.random() * 7) - 3;

    const newMemory = new Memory({
      title: title || 'A Beautiful Moment',
      dateStr: dateStr || new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).toUpperCase(),
      imageUrl,
      audioUrl,
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
    
    // Note: To delete the image from Cloudinary, you would need to use cloudinary.uploader.destroy(memory.imageUrl's public_id)
    // For now we just delete from DB.
    
    res.json({ message: 'Memory deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
