const { v4: uuidv4 } = require('uuid');
const path = require('path');
const fs = require('fs').promises;

/**
 * Saves a file buffer to the local disk and returns the relative URL
 * (Maintained original function name to avoid breaking imports)
 * @param {Object} bucket - Unused (maintained for backwards compatibility)
 * @param {Object} file - File object from multer (req.file)
 * @param {String} folder - Folder name in storage (e.g. 'memories', 'couples')
 * @returns {Promise<String|null>} - Local URL path of the uploaded file, or null if no file
 */
const uploadToFirebase = async (bucket, file, folder) => {
  if (!file) return null;

  try {
    const ext = path.extname(file.originalname);
    const filename = `${uuidv4()}${ext}`; // We ignore 'folder' for now to keep it simple, or we can use it
    const uploadDir = path.join(__dirname, '..', 'uploads');
    
    // Ensure the uploads directory exists
    try {
      await fs.access(uploadDir);
    } catch {
      await fs.mkdir(uploadDir, { recursive: true });
    }

    const filePath = path.join(uploadDir, filename);
    await fs.writeFile(filePath, file.buffer);

    // Return the relative URL which will be served by express.static
    return `/uploads/${filename}`;
  } catch (error) {
    console.error('✿ Error saving file locally:', error);
    throw new Error('Failed to save file');
  }
};

module.exports = { uploadToFirebase };
