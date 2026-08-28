const mongoose = require('mongoose');

const loveNoteSchema = new mongoose.Schema(
  {
    coupleId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Couple',
      required: true,
      index: true,
    },
    content: {
      type: String,
      required: true,
      trim: true,
    },
    author: {
      type: String,
      trim: true,
      default: 'Anonymous',
    },
    imageUrl: {
      type: String,
      default: '',
    },
    dateStr: {
      type: String,
      default: '',
    },
    isDailyAi: {
      type: Boolean,
      default: false,
    }
  },
  {
    timestamps: true,
  }
);

module.exports = mongoose.model('LoveNote', loveNoteSchema);
