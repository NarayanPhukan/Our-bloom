const mongoose = require('mongoose');

const loveNoteSchema = new mongoose.Schema(
  {
    content: {
      type: String,
      required: true,
      trim: true,
      maxlength: 500,
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
