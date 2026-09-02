const express = require('express');
const router = express.Router();
const { upload } = require('../config/upload');
const { uploadToFirebase } = require('../utils/firebaseStorage');

// POST /api/upload
// General purpose upload endpoint for the Android app
router.post('/', upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded' });
    }

    // Pass null for bucket as we no longer use it
    const localUrl = await uploadToFirebase(null, req.file, 'uploads');
    
    // Return the relative URL which the Android app will store
    res.status(201).json({ url: localUrl });
  } catch (err) {
    console.error('✿ Error in /api/upload:', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
