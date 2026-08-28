const mongoose = require('mongoose');

function generateInviteCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = 'BLOOM-';
  for (let i = 0; i < 4; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}

function generateSlug(name1, name2) {
  const clean = (n) => n.toLowerCase().replace(/[^a-z0-9]/g, '');
  return `${clean(name1)}-${clean(name2)}`;
}

const coupleSchema = new mongoose.Schema(
  {
    slug: {
      type: String,
      unique: true,
      required: true,
      lowercase: true,
      trim: true,
    },
    user1: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
    },
    user2: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null,
    },
    inviteCode: {
      type: String,
      unique: true,
      required: true,
    },
    startDate: {
      type: Date,
      required: true,
    },
    startTime: {
      type: String,
      default: '00:00',
    },
    specialPhrase: {
      type: String,
      trim: true,
      default: '',
    },
    spotifyTrackId: {
      type: String,
      default: '4O2N861eOnF9q8EtpH8IJu',
    },
    heroImageUrl: {
      type: String,
      default: '/images/journey-bg.jpg',
    },
  },
  {
    timestamps: true,
  }
);

coupleSchema.statics.generateInviteCode = generateInviteCode;
coupleSchema.statics.generateSlug = generateSlug;

module.exports = mongoose.model('Couple', coupleSchema);
