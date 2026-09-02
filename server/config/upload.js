const multer = require('multer');

// Configure Multer to use memory storage
const storage = multer.memoryStorage();

// Set limits and configuration
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 10 * 1024 * 1024, // 10 MB maximum file size
  }
});

module.exports = { upload };
