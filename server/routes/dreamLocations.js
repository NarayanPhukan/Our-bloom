const express = require('express');
const router = express.Router();
const DreamLocation = require('../models/DreamLocation');

// GET /api/dream-locations
router.get('/', async (req, res) => {
  try {
    const locations = await DreamLocation.find().sort({ createdAt: -1 });
    res.json(locations);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/dream-locations
router.post('/', async (req, res) => {
  try {
    const { title, description, lat, lng } = req.body;
    
    if (!title || !description || lat === undefined || lng === undefined) {
      return res.status(400).json({ error: 'All fields are required' });
    }

    const newLocation = new DreamLocation({
      title,
      description,
      lat,
      lng
    });

    const savedLocation = await newLocation.save();
    res.status(201).json(savedLocation);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/dream-locations/:id
router.delete('/:id', async (req, res) => {
  try {
    const location = await DreamLocation.findByIdAndDelete(req.params.id);
    if (!location) return res.status(404).json({ error: 'Location not found' });
    res.json({ message: 'Location deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
