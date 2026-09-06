const multer = require('multer');

// Configure Multer to use memory storage
const storage = multer.memoryStorage();

// Set limits and configuration
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 50 * 1024 * 1024, // 50 MB maximum file size for original quality photos
  }
});

module.exports = { upload };
