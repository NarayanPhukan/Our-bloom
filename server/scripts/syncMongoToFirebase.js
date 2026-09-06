require('dotenv').config();
const mongoose = require('mongoose');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');

const User = require('../models/User');
const Couple = require('../models/Couple');
const Milestone = require('../models/Milestone');
const LoveNote = require('../models/LoveNote');
const Memory = require('../models/Memory');

// Initialize Firebase Admin
const serviceAccount = require('../firebase-service-account.json');
const app = initializeApp({
  credential: cert(serviceAccount)
});
const db = getFirestore(app);
const auth = getAuth(app);

async function syncAll() {
  console.log('Connecting to MongoDB...');
  await mongoose.connect(process.env.MONGODB_URI);
  console.log('Connected to MongoDB.\n');

  console.log('--- Step 1: Syncing Users to Firebase Auth & Firestore ---');
  const mongoUsers = await User.find().lean();
  console.log(`Found ${mongoUsers.length} MongoDB users.`);

  for (const u of mongoUsers) {
    const idStr = u._id.toString();
    const email = u.email.toLowerCase();

    // Check if user already exists in Firebase Auth
    let existingFbUser = null;
    try {
      existingFbUser = await auth.getUserByEmail(email);
    } catch (e) {
      if (e.code !== 'auth/user-not-found') {
        console.error(`Error checking Firebase Auth for ${email}:`, e);
      }
    }

    // If exists with a different UID, delete the stale record so we can create with matching UID
    if (existingFbUser && existingFbUser.uid !== idStr) {
      console.log(`Removing stale Firebase Auth user for ${email} (old uid: ${existingFbUser.uid})`);
      await auth.deleteUser(existingFbUser.uid);
      existingFbUser = null;
    }

    // Import user to Firebase Auth with bcrypt hash if not present
    if (!existingFbUser) {
      try {
        console.log(`Importing ${email} to Firebase Auth with UID ${idStr}...`);
        const result = await auth.importUsers([
          {
            uid: idStr,
            email: email,
            passwordHash: Buffer.from(u.password),
            displayName: u.name,
          }
        ], {
          hash: { algorithm: 'BCRYPT' }
        });
        if (result.errors && result.errors.length > 0) {
          console.error(`Failed to import ${email}:`, result.errors[0].error);
        } else {
          console.log(`✓ Imported ${email} to Firebase Auth`);
        }
      } catch (err) {
        console.error(`Error importing ${email} to Firebase Auth:`, err);
      }
    } else {
      console.log(`Firebase Auth user for ${email} already has matching UID ${idStr}`);
    }

    // Prepare Firestore user document
    const firestoreUserData = {
      uid: idStr,
      email: email,
      name: u.name || '',
      nicknameForPartner: u.nicknameForPartner || '',
      coupleId: u.coupleId ? u.coupleId.toString() : null,
      avatarUrl: u.avatarUrl || '',
      fcmToken: u.fcmToken || null,
      createdAt: u.createdAt ? new Date(u.createdAt).toISOString() : new Date().toISOString(),
      updatedAt: u.updatedAt ? new Date(u.updatedAt).toISOString() : new Date().toISOString(),
    };

    await db.collection('users').doc(idStr).set(firestoreUserData, { merge: true });
    console.log(`✓ Synced Firestore users/${idStr} (${email})`);
  }

  console.log('\n--- Step 2: Syncing Couples to Firestore ---');
  const mongoCouples = await Couple.find().lean();
  console.log(`Found ${mongoCouples.length} MongoDB couples.`);

  for (const c of mongoCouples) {
    const cIdStr = c._id.toString();
    const firestoreCoupleData = {
      id: cIdStr,
      slug: c.slug,
      user1: c.user1 ? c.user1.toString() : '',
      user2: c.user2 ? c.user2.toString() : '',
      inviteCode: c.inviteCode,
      startDate: c.startDate ? new Date(c.startDate).toISOString() : '',
      startTime: c.startTime || '00:00',
      specialPhrase: c.specialPhrase || '',
      spotifyTrackId: c.spotifyTrackId || '4O2N861eOnF9q8EtpH8IJu',
      heroImageUrl: c.heroImageUrl || '/images/journey-bg.jpg',
      chatBackgroundUrl: c.chatBackgroundUrl || '',
      createdAt: c.createdAt ? new Date(c.createdAt).toISOString() : new Date().toISOString(),
      updatedAt: c.updatedAt ? new Date(c.updatedAt).toISOString() : new Date().toISOString(),
    };

    await db.collection('couples').doc(cIdStr).set(firestoreCoupleData, { merge: true });
    console.log(`✓ Synced Firestore couples/${cIdStr} (${c.slug})`);

    // Sync milestones for this couple
    const milestones = await Milestone.find({ coupleId: c._id }).lean();
    for (const m of milestones) {
      const mId = m._id.toString();
      const mData = {
        ...m,
        coupleId: cIdStr,
        createdAt: m.createdAt ? new Date(m.createdAt).toISOString() : new Date().toISOString(),
      };
      delete mData._id;
      delete mData.__v;
      await db.collection('milestones').doc(mId).set(mData, { merge: true });
    }
    if (milestones.length > 0) {
      console.log(`  ✓ Synced ${milestones.length} milestones for couple ${cIdStr}`);
    }
  }

  console.log('\n✅ All MongoDB users and couples are fully synced to Firebase Auth and Firestore!');
  process.exit(0);
}

syncAll().catch((err) => {
  console.error('Fatal sync error:', err);
  process.exit(1);
});
