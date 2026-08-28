const Couple = require('../models/Couple');

async function coupleMiddleware(req, res, next) {
  try {
    const { slug } = req.params;

    if (!slug) {
      return res.status(400).json({ error: 'Couple slug is required' });
    }

    const couple = await Couple.findOne({ slug }).populate('user1 user2', 'name email nicknameForPartner');
    if (!couple) {
      return res.status(404).json({ error: 'Couple not found' });
    }

    // Verify the logged-in user belongs to this couple
    const userId = req.user.userId.toString();
    const isUser1 = couple.user1 && couple.user1._id.toString() === userId;
    const isUser2 = couple.user2 && couple.user2._id.toString() === userId;

    if (!isUser1 && !isUser2) {
      return res.status(403).json({ error: 'You do not belong to this couple' });
    }

    req.couple = couple;
    req.coupleId = couple._id;
    req.coupleSlug = couple.slug;

    next();
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

module.exports = coupleMiddleware;
