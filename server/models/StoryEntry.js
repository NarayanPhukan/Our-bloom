const mongoose = require('mongoose');

const storyEntrySchema = new mongoose.Schema({
  coupleId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Couple',
    required: true,
    index: true,
  },
  type: {
    type: String,
    enum: ['photo', 'message', 'voice_note', 'love_note', 'milestone', 'thumbkiss'],
    required: true,
  },
  title: {
    type: String,
    trim: true,
    default: '',
  },
  caption: {
    type: String,
    trim: true,
    default: '',
  },
  mediaUrl: {
    type: String,
    default: '',
  },
  mediaType: {
    type: String,
    enum: ['image', 'audio', 'video', ''],
    default: '',
  },
  messageId: {
    type: String,
    default: '',
  },
  senderId: {
    type: String,
    default: '',
  },
  senderName: {
    type: String,
    default: '',
  },
  content: {
    type: String,
    default: '',
  },
  location: {
    type: String,
    default: '',
  },
  visibility: {
    type: String,
    enum: ['both', 'private'],
    default: 'both',
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
  updatedAt: {
    type: Date,
    default: Date.now,
  },
});

module.exports = mongoose.model('StoryEntry', storyEntrySchema);
