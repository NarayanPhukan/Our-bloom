const mongoose = require('mongoose');

const memorySchema = new mongoose.Schema(
  {
    coupleId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Couple',
      required: true,
      index: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
    },
    dateStr: {
      type: String,
      required: true,
      trim: true,
    },
    imageUrl: {
      type: String,
      required: true,
    },
    audioUrl: {
      type: String,
      default: '',
    },
    rotation: {
      type: Number,
      default: 0,
    },
    icon: {
      type: String,
      default: 'favorite',
    },
  },
  {
    timestamps: true,
  }
);

module.exports = mongoose.model('Memory', memorySchema);
