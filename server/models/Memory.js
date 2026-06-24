const mongoose = require('mongoose');

const memorySchema = new mongoose.Schema(
  {
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
