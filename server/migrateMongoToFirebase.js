require('dotenv').config();
const mongoose = require('mongoose');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');

// Mongoose Models
const User = require('./models/User');
const Couple = require('./models/Couple');
const DreamLocation = require('./models/DreamLocation');
const LoveNote = require('./models/LoveNote');
const Memory = require('./models/Memory');
const Milestone = require('./models/Milestone');
const Settings = require('./models/Settings');

// Initialize Firebase Admin
const serviceAccount = require('./firebase-service-account.json');
initializeApp({
  credential: cert(serviceAccount)
});
const db = getFirestore();
const auth = getAuth();

async function migrateUsers() {
  console.log('--- Migrating Users to Firebase Auth & Firestore ---');
  const users = await User.find().lean();
  
  const firebaseAuthUsers = [];
  
  for (const user of users) {
    const idStr = user._id.toString();
    
    // 1. Prepare for Firebase Auth Import
    firebaseAuthUsers.push({
      uid: idStr,
      email: user.email,
      passwordHash: Buffer.from(user.password), // Bcrypt hash string to Buffer
      displayName: user.name,
    });
    
    // 2. Prepare for Firestore Users Collection
    const cleanUser = JSON.parse(JSON.stringify(user));
    delete cleanUser._id;
    delete cleanUser.__v;
    delete cleanUser.password; // Don't store password in Firestore
    
    await db.collection('users').doc(idStr).set(cleanUser);
  }
  
  // Import to Auth in batches of 1000
  if (firebaseAuthUsers.length > 0) {
    try {
      const result = await auth.importUsers(firebaseAuthUsers, {
        hash: { algorithm: 'BCRYPT' }
      });
      console.log(`Successfully imported ${result.successCount} users to Firebase Auth.`);
      if (result.failureCount > 0) {
        console.log(`Failed to import ${result.failureCount} users to Auth.`);
        result.errors.forEach(err => console.error(err.error.message));
      }
    } catch (err) {
      console.error('Error importing users to Auth:', err);
    }
  }
  
  console.log(`Migrated ${users.length} user documents to Firestore.`);
}

async function migrateCollection(Model, collectionName) {
  console.log(`\n--- Migrating ${collectionName} ---`);
  const docs = await Model.find().lean();
  let count = 0;
  
  for (const doc of docs) {
    const idStr = doc._id.toString();
    const cleanDoc = JSON.parse(JSON.stringify(doc));
    
    delete cleanDoc._id;
    delete cleanDoc.__v;
    
    await db.collection(collectionName).doc(idStr).set(cleanDoc);
    count++;
  }
  console.log(`Migrated ${count} documents to ${collectionName}.`);
}

async function main() {
  try {
    console.log('Connecting to MongoDB...');
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('Connected successfully.\n');

    await migrateUsers();
    await migrateCollection(Couple, 'couples');
    await migrateCollection(DreamLocation, 'dreamLocations');
    await migrateCollection(LoveNote, 'loveNotes');
    await migrateCollection(Memory, 'memories');
    await migrateCollection(Milestone, 'milestones');
    await migrateCollection(Settings, 'settings');

    console.log('\n✅ Migration completed successfully!');
    process.exit(0);
  } catch (err) {
    console.error('Migration failed:', err);
    process.exit(1);
  }
}

main();
