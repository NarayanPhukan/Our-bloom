const express = require('express');
const router = express.Router();
const Couple = require('../models/Couple');
const User = require('../models/User');
const Milestone = require('../models/Milestone');
const authMiddleware = require('../middleware/authMiddleware');
const coupleMiddleware = require('../middleware/coupleMiddleware');
const { upload } = require('../config/upload');
const { uploadToFirebase } = require('../utils/firebaseStorage');

// Default milestones to seed for new couples
const defaultMilestoneTemplates = [
  {
    day: 1,
    label: 'Day 01 — The Beginning',
    title: 'When It All Started',
    body: 'The very first day of our story. A moment we will treasure forever.',
    icon: 'local_florist',
    iconFill: false,
    colorScheme: 'primary',
    aspectRatio: 'video',
  },
  {
    day: 7,
    label: 'Day 07 — One Week',
    title: 'Seven Days of Us',
    body: 'A week of getting to know each other, of sweet messages and stolen glances.',
    icon: 'water_drop',
    iconFill: false,
    colorScheme: 'secondary',
    aspectRatio: '4/5',
  },
  {
    day: 30,
    label: 'Day 30 — One Month',
    title: 'Our First Month',
    body: 'Thirty days of choosing each other, every single day. This is only the beginning.',
    icon: 'favorite',
    iconFill: true,
    colorScheme: 'primary',
    aspectRatio: 'square',
  },
];

// POST /api/couples — Create a new couple
router.post('/', authMiddleware, async (req, res) => {
  try {
    const { startDate, startTime, specialPhrase } = req.body;

    if (!startDate) {
      return res.status(400).json({ error: 'Anniversary date is required' });
    }

    // Check if user already has a couple
    const user = await User.findById(req.user.userId);
    if (user.coupleId) {
      return res.status(400).json({ error: 'You are already part of a couple' });
    }

    // Generate unique invite code
    let inviteCode;
    let codeExists = true;
    while (codeExists) {
      inviteCode = Couple.generateInviteCode();
      codeExists = await Couple.findOne({ inviteCode });
    }

    // Generate slug from user's name + "bloom"
    let slug = Couple.generateSlug(user.name, 'bloom');
    const slugExists = await Couple.findOne({ slug });
    if (slugExists) {
      slug = `${slug}-${Date.now().toString(36)}`;
    }

    const couple = new Couple({
      slug,
      user1: user._id,
      inviteCode,
      startDate: new Date(startDate),
      startTime: startTime || '00:00',
      specialPhrase: specialPhrase || '',
    });

    await couple.save();

    // Update user's coupleId
    user.coupleId = couple._id;
    await user.save();

    // Seed default milestones for the new couple
    const milestones = defaultMilestoneTemplates.map((m) => ({
      ...m,
      coupleId: couple._id,
    }));
    await Milestone.insertMany(milestones);

    // Populate user info before returning
    await couple.populate('user1', 'name email nicknameForPartner');

    res.status(201).json(couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/join — Join a couple by invite code
router.post('/join', authMiddleware, async (req, res) => {
  try {
    const { inviteCode } = req.body;

    if (!inviteCode) {
      return res.status(400).json({ error: 'Invite code is required' });
    }

    const user = await User.findById(req.user.userId);
    if (user.coupleId) {
      return res.status(400).json({ error: 'You are already part of a couple' });
    }

    const couple = await Couple.findOne({ inviteCode: inviteCode.toUpperCase() });
    if (!couple) {
      return res.status(404).json({ error: 'Invalid invite code' });
    }

    if (couple.user2) {
      return res.status(400).json({ error: 'This couple already has two partners' });
    }

    if (couple.user1.toString() === user._id.toString()) {
      return res.status(400).json({ error: 'You cannot join your own couple' });
    }

    // Update slug to include both names
    const user1 = await User.findById(couple.user1);
    const newSlug = Couple.generateSlug(user1.name, user.name);
    const slugExists = await Couple.findOne({ slug: newSlug, _id: { $ne: couple._id } });
    couple.slug = slugExists ? `${newSlug}-${Date.now().toString(36)}` : newSlug;

    couple.user2 = user._id;
    await couple.save();

    user.coupleId = couple._id;
    await user.save();

    await couple.populate('user1 user2', 'name email nicknameForPartner');

    res.json(couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/couples/by-id/:id — Get couple by MongoDB ID (for initial auth flow)
router.get('/by-id/:id', authMiddleware, async (req, res) => {
  try {
    const couple = await Couple.findById(req.params.id).populate('user1 user2', 'name email nicknameForPartner');
    if (!couple) {
      return res.status(404).json({ error: 'Couple not found' });
    }

    // Verify the user belongs to this couple
    const userId = req.user.userId.toString();
    const isUser1 = couple.user1 && couple.user1._id.toString() === userId;
    const isUser2 = couple.user2 && couple.user2._id.toString() === userId;
    if (!isUser1 && !isUser2) {
      return res.status(403).json({ error: 'You do not belong to this couple' });
    }

    res.json(couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/couples/:slug — Get couple profile
router.get('/:slug', authMiddleware, coupleMiddleware, async (req, res) => {
  try {
    res.json(req.couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/hero-image — Upload new hero image
router.post('/:slug/hero-image', authMiddleware, coupleMiddleware, upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'Image file is required' });
    }

    const bucket = req.app.get('bucket');
    const heroImageUrl = await uploadToFirebase(bucket, req.file, 'couples');
    
    if (!heroImageUrl) {
      return res.status(500).json({ error: 'Failed to upload image' });
    }

    const couple = req.couple;
    couple.heroImageUrl = heroImageUrl;
    await couple.save();

    // Broadcast to partner via socket.io
    const io = req.app.get('io');
    if (io) {
      io.to(couple.slug).emit('updateHeroImage', couple.heroImageUrl);
    }

    res.json(couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT /api/couples/:slug — Update couple settings
router.put('/:slug', authMiddleware, coupleMiddleware, async (req, res) => {
  try {
    const { spotifyTrackId, specialPhrase, startDate, startTime } = req.body;
    const couple = req.couple;

    if (spotifyTrackId !== undefined) couple.spotifyTrackId = spotifyTrackId;
    if (specialPhrase !== undefined) couple.specialPhrase = specialPhrase;
    if (startDate !== undefined) couple.startDate = new Date(startDate);
    if (startTime !== undefined) couple.startTime = startTime;

    await couple.save();
    await couple.populate('user1 user2', 'name email nicknameForPartner');

    // Emit socket event for spotify updates
    const io = req.app.get('io');
    if (io && spotifyTrackId !== undefined) {
      io.to(couple.slug).emit('updateSpotify', couple.spotifyTrackId);
    }

    res.json(couple);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/couples/:slug/heartbeat — Send real-time heartbeat / "thinking of you" ping
router.post('/:slug/heartbeat', authMiddleware, coupleMiddleware, async (req, res) => {
  try {
    const couple = req.couple;
    const sender = await User.findById(req.user.userId);
    const partnerId = couple.user1.toString() === req.user.userId.toString() ? couple.user2 : couple.user1;

    let partner = null;
    if (partnerId) {
      partner = await User.findById(partnerId);
    }

    const senderName = (partner && partner.nicknameForPartner) ? partner.nicknameForPartner : (sender ? sender.name : 'Your love');
    const timestamp = Date.now();

    // 1. Broadcast via Socket.io to the couple room
    const io = req.app.get('io');
    if (io) {
      io.to(couple.slug).emit('heartbeat', {
        senderId: req.user.userId,
        senderName,
        timestamp
      });
      io.to(couple.slug).emit('notification', {
        type: 'heartbeat',
        userId: req.user.userId,
        senderName,
        timestamp
      });
    }

    // 2. Dispatch FCM push notification to partner
    if (partner && partner.fcmToken) {
      const { sendPushNotification } = require('../utils/firebase');
      sendPushNotification(
        partner.fcmToken,
        `${senderName} sent you a Heartbeat ❤️`,
        'Thinking of you right now... tap to send one back!',
        {
          type: 'heartbeat',
          senderName: String(senderName),
          timestamp: String(timestamp)
        }
      ).catch(err => console.error('Heartbeat push error:', err));
    }

    res.json({ success: true, message: 'Heartbeat sent', timestamp });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
