const mongoose = require('mongoose');
require('dotenv').config();

const LoveNote = require('./models/LoveNote');

async function cleanUp() {
  try {
    await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/ourbloom');
    console.log('Connected to DB');

    const todayStr = new Date().toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });

    const result = await LoveNote.deleteMany({ isDailyAi: true });
    console.log(`Deleted ${result.deletedCount} daily notes.`);

  } catch (err) {
    console.error(err);
  } finally {
    mongoose.connection.close();
  }
}

cleanUp();
