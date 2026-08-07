const express = require('express');
const router = express.Router();
const DreamLocation = require('../models/DreamLocation');

const { upload } = require('../config/cloudinary');

// GET /api/dream-locations
router.get('/', async (req, res) => {
  try {
    const locations = await DreamLocation.find().sort({ createdAt: -1 });
    res.json(locations);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/dream-locations (multipart/form-data)
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
      title,
      description,
      lat,
      lng,
      status: status || 'Dreaming',
      photoUrl
    });

    const savedLocation = await newLocation.save();
    
    const io = req.app.get('io');
    if (io) io.emit('newLocation', savedLocation);

    res.status(201).json(savedLocation);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// PUT /api/dream-locations/:id (multipart/form-data)
router.put('/:id', upload.single('image'), async (req, res) => {
  try {
    const { title, description, status } = req.body;
    const location = await DreamLocation.findById(req.params.id);
    
    if (!location) return res.status(404).json({ error: 'Location not found' });
    
    if (title) location.title = title;
    if (description) location.description = description;
    if (status) location.status = status;
    if (req.file) {
      location.photoUrl = req.file.path;
    }

    const updatedLocation = await location.save();
    
    const io = req.app.get('io');
    if (io) io.emit('updateLocation', updatedLocation);

    res.json(updatedLocation);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// DELETE /api/dream-locations/:id
router.delete('/:id', async (req, res) => {
  try {
    const location = await DreamLocation.findByIdAndDelete(req.params.id);
    if (!location) return res.status(404).json({ error: 'Location not found' });
    
    const io = req.app.get('io');
    if (io) io.emit('deleteLocation', req.params.id);

    res.json({ message: 'Location deleted' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
