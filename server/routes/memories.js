const express = require('express');
const router = express.Router({ mergeParams: true });
const Memory = require('../models/Memory');
const { upload } = require('../config/cloudinary');

// GET /api/couples/:slug/memories
router.get('/', async (req, res) => {
  try {
    const memories = await Memory.find({ coupleId: req.coupleId }).sort({ createdAt: -1 });
    res.json(memories);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/memories
router.post('/', upload.fields([{ name: 'image', maxCount: 1 }, { name: 'audio', maxCount: 1 }]), async (req, res) => {
  try {
    const { title, dateStr, icon } = req.body;
    
    if (!req.files || !req.files['image']) {
      return res.status(400).json({ error: 'Image file is required' });
    }

    const imageUrl = req.files['image'][0].path;
    let audioUrl = '';
    
    if (req.files['audio']) {
      audioUrl = req.files['audio'][0].path;
    }

    // Random rotation between -3 and 3 degrees for the masonry polaroid effect
    const rotation = Math.floor(Math.random() * 7) - 3;

    const newMemory = new Memory({
      coupleId: req.coupleId,
      title: title || 'A Beautiful Moment',
      dateStr: dateStr || new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).toUpperCase(),
      imageUrl,
      audioUrl,
      rotation,
      icon: icon || 'favorite'
    });

    const savedMemory = await newMemory.save();

    const io = req.app.get('io');
    if (io) {
      io.to(req.coupleSlug).emit('newMemory', savedMemory);
      io.to(req.coupleSlug).emit('notification', {
        type: 'memory_added',
        userId: req.user.userId,
        title: savedMemory.title
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
              "New Memory Added 📸",
              `Your partner just added "${savedMemory.title}"`,
              { type: 'memory', id: savedMemory._id.toString() }
            );
          }
        }
      }
    } catch (pushErr) {
      console.error('Failed to send push notification:', pushErr);
    }

    res.status(201).json(savedMemory);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/couples/:slug/memories/:id
router.delete('/:id', async (req, res) => {
  try {
    const memory = await Memory.findOneAndDelete({ _id: req.params.id, coupleId: req.coupleId });
    if (!memory) return res.status(404).json({ error: 'Memory not found' });
    
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('deleteMemory', req.params.id);

    res.json({ message: 'Memory deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
