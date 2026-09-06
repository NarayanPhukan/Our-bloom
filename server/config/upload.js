const multer = require('multer');

// Configure Multer to use memory storage
const storage = multer.memoryStorage();

// Set limits and configuration
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 25 * 1024 * 1024, // 25 MB maximum file size
  }
});

module.exports = { upload };
