const mongoose = require('mongoose');

const milestoneSchema = new mongoose.Schema(
  {
    day: {
      type: Number,
      required: true,
    },
    label: {
      type: String,
      required: true,
      trim: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
    },
    body: {
      type: String,
      required: true,
      trim: true,
    },
    imageUrl: {
      type: String,
      default: '',
    },
    icon: {
      type: String,
      default: 'local_florist',
    },
    iconFill: {
      type: Boolean,
      default: false,
    },
    colorScheme: {
      type: String,
      enum: ['primary', 'secondary', 'tertiary'],
      default: 'primary',
    },
    aspectRatio: {
      type: String,
      enum: ['video', 'square', '4/5'],
      default: 'video',
    },
  },
  {
    timestamps: true,
  }
);

milestoneSchema.index({ day: 1 });

module.exports = mongoose.model('Milestone', milestoneSchema);
