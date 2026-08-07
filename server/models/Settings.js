const mongoose = require('mongoose');

const settingsSchema = new mongoose.Schema({
  spotifyTrackId: {
    type: String,
    default: '4O2N861eOnF9q8EtpH8IJu'
  }
}, { timestamps: true });

module.exports = mongoose.model('Settings', settingsSchema);
