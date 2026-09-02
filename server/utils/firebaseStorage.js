const { v4: uuidv4 } = require('uuid');
const path = require('path');

/**
 * Uploads a file buffer to Firebase Storage
 * @param {Object} bucket - Firebase Admin Storage bucket instance
 * @param {Object} file - File object from multer (req.file)
 * @param {String} folder - Folder name in storage (e.g. 'memories', 'couples')
 * @returns {Promise<String|null>} - Public URL of the uploaded file, or null if no file
 */
const uploadToFirebase = async (bucket, file, folder) => {
  if (!file || !bucket) return null;

  try {
    const ext = path.extname(file.originalname);
    const filename = `${folder}/${uuidv4()}${ext}`;
    const fileRef = bucket.file(filename);
    const downloadToken = uuidv4();

    await fileRef.save(file.buffer, {
      metadata: {
        contentType: file.mimetype,
        metadata: {
          firebaseStorageDownloadTokens: downloadToken
        }
      },
    });

    // Return the public URL using Firebase's standard download URL format
    const encodedFilename = encodeURIComponent(filename);
    return `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodedFilename}?alt=media&token=${downloadToken}`;
  } catch (error) {
    console.error('✿ Error uploading to Firebase Storage:', error);
    throw new Error('Failed to upload file to Firebase Storage');
  }
};

module.exports = { uploadToFirebase };
