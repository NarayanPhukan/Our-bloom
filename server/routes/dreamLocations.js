const express = require('express');
const router = express.Router({ mergeParams: true });
const DreamLocation = require('../models/DreamLocation');
const { upload } = require('../config/cloudinary');

// GET /api/couples/:slug/dream-locations
router.get('/', async (req, res) => {
  try {
    const locations = await DreamLocation.find({ coupleId: req.coupleId }).sort({ createdAt: -1 });
    res.json(locations);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/dream-locations
router.post('/', upload.single('image'), async (req, res) => {
  try {
    const { title, description, lat, lng, status } = req.body;
    
    if (!title || !description || lat === undefined || lng === undefined) {
      return res.status(400).json({ error: 'All required fields must be provided' });
    }

    let photoUrl = '';
    if (req.file) {
      photoUrl = req.file.path;
    }

    const newLocation = new DreamLocation({
      coupleId: req.coupleId,
      title,
      description,
      lat,
      lng,
      status: status || 'Dreaming',
      photoUrl
    });

    const savedLocation = await newLocation.save();
    
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('newLocation', savedLocation);

    res.status(201).json(savedLocation);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PUT /api/couples/:slug/dream-locations/:id
router.put('/:id', upload.single('image'), async (req, res) => {
  try {
    const { title, description, status } = req.body;
    const location = await DreamLocation.findOne({ _id: req.params.id, coupleId: req.coupleId });
    
    if (!location) return res.status(404).json({ error: 'Location not found' });
    
    if (title) location.title = title;
    if (description) location.description = description;
    if (status) location.status = status;
    if (req.file) {
      location.photoUrl = req.file.path;
    }

    const updatedLocation = await location.save();
    
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('updateLocation', updatedLocation);

    res.json(updatedLocation);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/couples/:slug/dream-locations/:id
router.delete('/:id', async (req, res) => {
  try {
    const location = await DreamLocation.findOneAndDelete({ _id: req.params.id, coupleId: req.coupleId });
    if (!location) return res.status(404).json({ error: 'Location not found' });
    
    const io = req.app.get('io');
    if (io) io.to(req.coupleSlug).emit('deleteLocation', req.params.id);

    res.json({ message: 'Location deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
