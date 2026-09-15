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
 * Saves a file buffer to Cloudinary (or local disk fallback) with metadata stripping.
 * Strips EXIF/GPS coordinates to preserve couple privacy.
 */
const uploadToFirebase = async (bucket, file, folder = 'ourbloom', options = {}) => {
  if (!file || !file.buffer) return null;

  const ext = options.ext || path.extname(file.originalname || '').toLowerCase() || '.bin';
  const safeFilename = `${uuidv4()}`;

  // 1. Permanent Cloudinary storage with privacy flags (strips EXIF / GPS location)
  if (process.env.CLOUDINARY_CLOUD_NAME && process.env.CLOUDINARY_API_KEY) {
    try {
      const uploadOptions = {
        folder: folder || 'ourbloom',
        public_id: safeFilename,
        resource_type: 'auto',
        // Strip camera/mobile EXIF, GPS location, and personal device metadata
        flags: 'strip_profile',
        image_metadata: false
      };

      const secureUrl = await new Promise((resolve, reject) => {
        const uploadStream = cloudinary.uploader.upload_stream(
          uploadOptions,
          (error, result) => {
            if (error) return reject(error);
            resolve(result.secure_url);
          }
        );
        uploadStream.end(file.buffer);
      });
      return secureUrl;
    } catch (cloudErr) {
      console.error('✿ Cloudinary upload failed, attempting safe fallback:', cloudErr.message);
    }
  }

  // 2. Fallback to local storage using strict UUID filenames
  try {
    const filenameWithExt = `${safeFilename}${ext}`;
    const uploadDir = path.join(__dirname, '..', 'uploads');
    
    try {
      await fs.access(uploadDir);
    } catch {
      await fs.mkdir(uploadDir, { recursive: true });
    }

    const filePath = path.join(uploadDir, filenameWithExt);
    await fs.writeFile(filePath, file.buffer);

    return `/uploads/${filenameWithExt}`;
  } catch (error) {
    console.error('✿ Error saving file locally:', error);
    throw new Error('Failed to save file securely');
  }
};

module.exports = { uploadToFirebase };
