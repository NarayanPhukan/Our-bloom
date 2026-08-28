const express = require('express');
const router = express.Router({ mergeParams: true });
const Milestone = require('../models/Milestone');

// GET all milestones for this couple (sorted by day)
router.get('/', async (req, res) => {
  try {
    const milestones = await Milestone.find({ coupleId: req.coupleId }).sort({ day: 1 });
    res.json(milestones);
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// GET single milestone
router.get('/:id', async (req, res) => {
  try {
    const milestone = await Milestone.findOne({ _id: req.params.id, coupleId: req.coupleId });
    if (!milestone) return res.status(404).json({ message: 'Milestone not found' });
    res.json(milestone);
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// POST create milestone
router.post('/', async (req, res) => {
  const milestone = new Milestone({
    coupleId: req.coupleId,
    day: req.body.day,
    label: req.body.label,
    title: req.body.title,
    body: req.body.body,
    imageUrl: req.body.imageUrl || '',
    icon: req.body.icon || 'local_florist',
    iconFill: req.body.iconFill || false,
    colorScheme: req.body.colorScheme || 'primary',
    aspectRatio: req.body.aspectRatio || 'video',
  });

  try {
    const newMilestone = await milestone.save();
    res.status(201).json(newMilestone);
  } catch (err) {
    res.status(400).json({ message: err.message });
  }
});

// PUT update milestone
router.put('/:id', async (req, res) => {
  try {
    const milestone = await Milestone.findOne({ _id: req.params.id, coupleId: req.coupleId });
    if (!milestone) return res.status(404).json({ message: 'Milestone not found' });

    Object.keys(req.body).forEach((key) => {
      if (req.body[key] !== undefined && key !== 'coupleId') {
        milestone[key] = req.body[key];
      }
    });

    const updated = await milestone.save();
    res.json(updated);
  } catch (err) {
    res.status(400).json({ message: err.message });
  }
});

// DELETE milestone
router.delete('/:id', async (req, res) => {
  try {
    const milestone = await Milestone.findOne({ _id: req.params.id, coupleId: req.coupleId });
    if (!milestone) return res.status(404).json({ message: 'Milestone not found' });
    await milestone.deleteOne();
    res.json({ message: 'Milestone deleted' });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

module.exports = router;
