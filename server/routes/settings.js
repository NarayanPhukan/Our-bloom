const express = require('express');
const router = express.Router({ mergeParams: true });

// GET /api/couples/:slug/settings/spotify
router.get('/spotify', async (req, res) => {
  try {
    res.json({ spotifyTrackId: req.couple.spotifyTrackId });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT /api/couples/:slug/settings/spotify
router.put('/spotify', async (req, res) => {
  try {
    const { spotifyTrackId } = req.body;
    const couple = req.couple;

    couple.spotifyTrackId = spotifyTrackId;
    await couple.save();
    
    // Emit socket event for real-time updates
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('updateSpotify', couple.spotifyTrackId);
    
    res.json({ spotifyTrackId: couple.spotifyTrackId });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
