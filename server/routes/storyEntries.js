const express = require('express');
const router = express.Router({ mergeParams: true });
const StoryEntry = require('../models/StoryEntry');
const { getFirestore } = require('../utils/firebase');

/**
 * GET /api/couples/:slug/story-entries
 * Retrieves relationship archive story entries for this couple.
 */
router.get('/', async (req, res) => {
  try {
    const entries = await StoryEntry.find({ coupleId: req.coupleId })
      .sort({ createdAt: -1 })
      .limit(100);
    res.json(entries);
  } catch (err) {
    console.error('Error fetching story entries:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/couples/:slug/story-entries
 * Unified save endpoint: saves moments, chats, love notes, or photos to the shared story.
 */
router.post('/', async (req, res) => {
  try {
    const { type, title, caption, mediaUrl, mediaType, messageId, content, location, visibility } = req.body;

    if (!type) {
      return res.status(400).json({ error: 'Story entry type is required' });
    }

    const storyEntry = new StoryEntry({
      coupleId: req.coupleId,
      type,
      title: title || '',
      caption: caption || '',
      mediaUrl: mediaUrl || '',
      mediaType: mediaType || '',
      messageId: messageId || '',
      senderId: req.user.firebaseUid || req.user.userId?.toString(),
      senderName: req.user.name || 'Partner',
      content: content || '',
      location: location || '',
      visibility: visibility || 'both',
      createdAt: new Date(),
      updatedAt: new Date(),
    });

    await storyEntry.save();

    // Dual-sync to Firestore subcollection /couples/{coupleId}/storyEntries/{id}
    const db = getFirestore();
    if (db) {
      const cIdStr = req.coupleId.toString();
      await db.collection('couples').doc(cIdStr)
        .collection('storyEntries').doc(storyEntry._id.toString())
        .set({
          id: storyEntry._id.toString(),
          coupleId: cIdStr,
          type: storyEntry.type,
          title: storyEntry.title,
          caption: storyEntry.caption,
          mediaUrl: storyEntry.mediaUrl,
          mediaType: storyEntry.mediaType,
          messageId: storyEntry.messageId,
          senderId: storyEntry.senderId,
          senderName: storyEntry.senderName,
          content: storyEntry.content,
          location: storyEntry.location,
          visibility: storyEntry.visibility,
          createdAt: Date.now(),
        }, { merge: true })
        .catch(fsErr => console.warn('Firestore story sync warning:', fsErr.message));
    }

    res.status(201).json(storyEntry);
  } catch (err) {
    console.error('Error creating story entry:', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
