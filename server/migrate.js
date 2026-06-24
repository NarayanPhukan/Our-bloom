require('dotenv').config();
const mongoose = require('mongoose');
const { cloudinary } = require('./config/cloudinary');
const Memory = require('./models/Memory');
const LoveNote = require('./models/LoveNote');
const fs = require('fs');
const path = require('path');

async function migrateImages() {
  console.log('✿ Starting migration to Cloudinary...');

  if (!process.env.CLOUDINARY_CLOUD_NAME || !process.env.MONGODB_URI) {
    console.error('Missing required environment variables (Cloudinary or MongoDB).');
    process.exit(1);
  }

  try {
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('✿ Connected to DB');

    // Migrate Memories
    const memories = await Memory.find({ imageUrl: { $regex: '^/uploads/' } });
    console.log(`Found ${memories.length} memories to migrate.`);
    
    for (let mem of memories) {
      const localPath = path.join(__dirname, mem.imageUrl);
      if (fs.existsSync(localPath)) {
        console.log(`Uploading memory: ${mem.title}...`);
        const result = await cloudinary.uploader.upload(localPath, { folder: 'ourbloom' });
        mem.imageUrl = result.secure_url;
        await mem.save();
        console.log(`Successfully migrated: ${mem.imageUrl}`);
      } else {
        console.warn(`Local file not found for memory: ${localPath}`);
      }
    }

    // Migrate Love Notes
    const notes = await LoveNote.find({ imageUrl: { $regex: '^/uploads/' } });
    console.log(`Found ${notes.length} love notes to migrate.`);
    
    for (let note of notes) {
      const localPath = path.join(__dirname, note.imageUrl);
      if (fs.existsSync(localPath)) {
        console.log(`Uploading love note image...`);
        const result = await cloudinary.uploader.upload(localPath, { folder: 'ourbloom' });
        note.imageUrl = result.secure_url;
        await note.save();
        console.log(`Successfully migrated: ${note.imageUrl}`);
      } else {
        console.warn(`Local file not found for love note: ${localPath}`);
      }
    }

    console.log('✿ Migration complete!');
    process.exit(0);
  } catch (error) {
    console.error('Migration failed:', error);
    process.exit(1);
  }
}

migrateImages();
