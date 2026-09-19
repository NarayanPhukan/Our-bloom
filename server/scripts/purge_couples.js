require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const mongoose = require('mongoose');
const fb = require('../utils/firebase');

const PROTECTED_COUPLE_ID = '6a91c996e82f78851994a8bc';
const PROTECTED_USER_IDS = new Set([
  '6a91c995e82f78851994a8ba',
  '6a91c995e82f78851994a8bb'
]);
const PROTECTED_EMAILS = new Set([
  'narayanphukan30@gmail.com',
  'tanayaburagohain44@gmail.com'
]);

async function purge() {
  console.log('🛡️ Starting Safe Couple & User Purge...');
  console.log(`Protected Couple ID: ${PROTECTED_COUPLE_ID}`);
  console.log(`Protected User IDs: ${Array.from(PROTECTED_USER_IDS).join(', ')}`);
  console.log(`Protected Emails: ${Array.from(PROTECTED_EMAILS).join(', ')}\n`);

  const db = fb.getFirestore();
  const auth = fb.getAuth();

  if (!db || !auth) {
    throw new Error('Firebase Firestore or Auth not initialized.');
  }

  // 1. Purge Scoped Collections in Firestore
  const scopedCollections = [
    'chat_messages',
    'loveNotes',
    'milestones',
    'memories',
    'dreamLocations',
    'heartbeats'
  ];

  for (const colName of scopedCollections) {
    const snap = await db.collection(colName).get();
    let deletedCount = 0;
    let keptCount = 0;
    const batchSize = 400;
    let batch = db.batch();
    let inBatch = 0;

    for (const doc of snap.docs) {
      const data = doc.data();
      const coupleId = data.coupleId || data.couple_id;

      if (coupleId === PROTECTED_COUPLE_ID) {
        keptCount++;
      } else {
        batch.delete(doc.ref);
        inBatch++;
        deletedCount++;

        if (inBatch >= batchSize) {
          await batch.commit();
          batch = db.batch();
          inBatch = 0;
        }
      }
    }

    if (inBatch > 0) {
      await batch.commit();
    }

    console.log(`✓ Firestore [${colName}]: Deleted ${deletedCount}, Kept ${keptCount}`);
  }

  // 2. Purge Non-Protected Couples in Firestore
  const couplesSnap = await db.collection('couples').get();
  let couplesDeleted = 0;
  let couplesKept = 0;
  for (const doc of couplesSnap.docs) {
    if (doc.id === PROTECTED_COUPLE_ID) {
      couplesKept++;
    } else {
      console.log(`Deleting Firestore Couple: ${doc.id} (slug: ${doc.data().slug || 'n/a'})`);
      await doc.ref.delete();
      couplesDeleted++;
    }
  }
  console.log(`✓ Firestore [couples]: Deleted ${couplesDeleted}, Kept ${couplesKept}`);

  // 3. Purge Non-Protected Users in Firestore
  const usersSnap = await db.collection('users').get();
  let usersDeleted = 0;
  let usersKept = 0;
  for (const doc of usersSnap.docs) {
    const data = doc.data();
    const email = (data.email || '').toLowerCase().trim();

    if (PROTECTED_USER_IDS.has(doc.id) || PROTECTED_EMAILS.has(email)) {
      usersKept++;
    } else {
      console.log(`Deleting Firestore User: ${doc.id} (${email})`);
      await doc.ref.delete();
      usersDeleted++;
    }
  }
  console.log(`✓ Firestore [users]: Deleted ${usersDeleted}, Kept ${usersKept}`);

  // 4. Purge Firebase Auth Users
  const authUsersResult = await auth.listUsers(500);
  let authDeleted = 0;
  let authKept = 0;
  for (const u of authUsersResult.users) {
    const email = (u.email || '').toLowerCase().trim();
    if (PROTECTED_USER_IDS.has(u.uid) || PROTECTED_EMAILS.has(email)) {
      authKept++;
    } else {
      console.log(`Deleting Firebase Auth User: UID=${u.uid} (${email})`);
      await auth.deleteUser(u.uid);
      authDeleted++;
    }
  }
  console.log(`✓ Firebase Auth: Deleted ${authDeleted}, Kept ${authKept}`);

  // 5. Purge MongoDB (if URI configured)
  if (process.env.MONGODB_URI) {
    console.log('\n🍃 Connecting to MongoDB for cleanup...');
    try {
      await mongoose.connect(process.env.MONGODB_URI);
      const mdb = mongoose.connection.db;

      // Couples
      const mCouplesDel = await mdb.collection('couples').deleteMany({
        _id: { $ne: new mongoose.Types.ObjectId(PROTECTED_COUPLE_ID) }
      });
      console.log(`✓ MongoDB [couples]: Deleted ${mCouplesDel.deletedCount}`);

      // Users
      const mUsersDel = await mdb.collection('users').deleteMany({
        _id: {
          $nin: Array.from(PROTECTED_USER_IDS).map(id => new mongoose.Types.ObjectId(id))
        },
        email: {
          $nin: Array.from(PROTECTED_EMAILS)
        }
      });
      console.log(`✓ MongoDB [users]: Deleted ${mUsersDel.deletedCount}`);

      // Scoped collections
      const mCollections = ['memories', 'milestones', 'lovenotes', 'dreamlocations'];
      for (const col of mCollections) {
        try {
          const res = await mdb.collection(col).deleteMany({
            $and: [
              { coupleId: { $ne: PROTECTED_COUPLE_ID } },
              { coupleId: { $ne: new mongoose.Types.ObjectId(PROTECTED_COUPLE_ID) } },
              { couple: { $ne: new mongoose.Types.ObjectId(PROTECTED_COUPLE_ID) } }
            ]
          });
          console.log(`✓ MongoDB [${col}]: Deleted ${res.deletedCount}`);
        } catch (e) {
          console.log(`MongoDB [${col}] notice: ${e.message}`);
        }
      }

      await mongoose.disconnect();
    } catch (mErr) {
      console.error('MongoDB purge error:', mErr.message);
    }
  }

  console.log('\n🎉 Clean-up completed successfully!');
}

purge().then(() => {
  process.exit(0);
}).catch(err => {
  console.error('\n❌ Purge failed:', err);
  process.exit(1);
});
