const cloudinary = require('cloudinary').v2;
const { v4: uuidv4 } = require('uuid');
const path = require('path');
const fs = require('fs').promises;

if (process.env.CLOUDINARY_CLOUD_NAME && process.env.CLOUDINARY_API_KEY && process.env.CLOUDINARY_API_SECRET) {
  cloudinary.config({
    cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
    api_key: process.env.CLOUDINARY_API_KEY,
    api_secret: process.env.CLOUDINARY_API_SECRET
  });
}

/**
 * Saves a file buffer to Cloudinary (or local disk fallback) and returns the public URL
 */
const uploadToFirebase = async (bucket, file, folder = 'ourbloom') => {
  if (!file) return null;

  // 1. Try Cloudinary first for permanent cloud storage
  if (process.env.CLOUDINARY_CLOUD_NAME && process.env.CLOUDINARY_API_KEY) {
    try {
      const secureUrl = await new Promise((resolve, reject) => {
        const uploadStream = cloudinary.uploader.upload_stream(
          { folder: folder || 'ourbloom', resource_type: 'auto' },
          (error, result) => {
            if (error) return reject(error);
            resolve(result.secure_url);
          }
        );
        uploadStream.end(file.buffer);
      });
      console.log('✿ File uploaded to Cloudinary successfully:', secureUrl);
      return secureUrl;
    } catch (cloudErr) {
      console.error('✿ Cloudinary upload failed, falling back to local storage:', cloudErr.message);
    }
  }

  // 2. Fallback to local storage
  try {
    const ext = path.extname(file.originalname);
    const filename = `${uuidv4()}${ext}`;
    const uploadDir = path.join(__dirname, '..', 'uploads');
    
    try {
      await fs.access(uploadDir);
    } catch {
      await fs.mkdir(uploadDir, { recursive: true });
    }

    const filePath = path.join(uploadDir, filename);
    await fs.writeFile(filePath, file.buffer);

    return `/uploads/${filename}`;
  } catch (error) {
    console.error('✿ Error saving file locally:', error);
    throw new Error('Failed to save file');
  }
};

module.exports = { uploadToFirebase };
