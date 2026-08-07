const express = require('express');
const router = express.Router();
const Settings = require('../models/Settings');

// GET /api/settings/spotify
router.get('/spotify', async (req, res) => {
  try {
    let settings = await Settings.findOne();
    if (!settings) {
      settings = await Settings.create({ spotifyTrackId: '4O2N861eOnF9q8EtpH8IJu' });
    }
    res.json(settings);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT /api/settings/spotify
router.put('/spotify', async (req, res) => {
  try {
    const { spotifyTrackId } = req.body;
    let settings = await Settings.findOne();
    if (!settings) {
      settings = new Settings();
    }
    settings.spotifyTrackId = spotifyTrackId;
    await settings.save();
    
    // Emit socket event for real-time updates
    const io = req.app.get('io');
    if (io) io.emit('updateSpotify', settings.spotifyTrackId);
    
    res.json(settings);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
