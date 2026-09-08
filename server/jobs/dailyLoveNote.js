const cron = require('node-cron');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const { getFirestore, sendPushNotification } = require('../utils/firebase');
const LoveNote = require('../models/LoveNote');

let ioInstance = null;

function setIo(io) {
  ioInstance = io;
}

function getIo() {
  return ioInstance;
}

function getTodayDateStr() {
  return new Date().toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  });
}

async function getFcmTokenForUser(userId, db) {
  if (!userId) return null;
  const uidStr = String(userId);

  // 1. Check Firestore direct document lookup
  if (db) {
    try {
      const userDoc = await db.collection('users').doc(uidStr).get();
      if (userDoc.exists && userDoc.data()?.fcmToken) {
        return userDoc.data().fcmToken;
      }
    } catch (_) {}

    // 2. Query Firestore where uid == uidStr
    try {
      const qUid = await db.collection('users').where('uid', '==', uidStr).limit(1).get();
      if (!qUid.empty && qUid.docs[0].data()?.fcmToken) {
        return qUid.docs[0].data().fcmToken;
      }
    } catch (_) {}

    // 3. Query Firestore where _id == uidStr
    try {
      const qId = await db.collection('users').where('_id', '==', uidStr).limit(1).get();
      if (!qId.empty && qId.docs[0].data()?.fcmToken) {
        return qId.docs[0].data().fcmToken;
      }
    } catch (_) {}
  }

  // 4. Check MongoDB User model
  try {
    const User = require('../models/User');
    const mongoose = require('mongoose');
    let mUser = null;
    if (mongoose.Types.ObjectId.isValid(uidStr)) {
      mUser = await User.findById(uidStr);
    }
    if (!mUser) {
      mUser = await User.findOne({ email: uidStr });
    }
    if (mUser && mUser.fcmToken) {
      return mUser.fcmToken;
    }
  } catch (_) {}

  return null;
}

async function generateDailyNoteForCouple(idOrSlug, coupleSlug, coupleData = {}, options = {}) {
  let resolvedId = idOrSlug;
  try {
    const todayStr = getTodayDateStr();
    const db = getFirestore();
    let resolvedSlug = coupleSlug || idOrSlug;

    // Resolve couple document if needed (e.g. if slug passed as idOrSlug)
    if (db && idOrSlug) {
      try {
        const docDirect = await db.collection('couples').doc(idOrSlug).get();
        if (docDirect.exists) {
          resolvedId = docDirect.id;
          resolvedSlug = docDirect.data()?.slug || resolvedSlug;
        } else {
          const slugQuery = await db.collection('couples').where('slug', '==', idOrSlug).limit(1).get();
          if (!slugQuery.empty) {
            resolvedId = slugQuery.docs[0].id;
            resolvedSlug = slugQuery.docs[0].data()?.slug || idOrSlug;
          }
        }
      } catch (rErr) {
        // Fall back to provided parameters
      }
    }

    // 1. Check if today's note already exists in Firestore
    if (db) {
      const existingSnap = await db.collection('loveNotes')
        .where('coupleId', '==', resolvedId)
        .where('isDailyAi', '==', true)
        .where('dateStr', '==', todayStr)
        .limit(1)
        .get();

      if (!existingSnap.empty) {
        return existingSnap.docs[0].data();
      }
    }

    // 2. Generate romantic note with Gemini
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      console.warn('✿ GEMINI_API_KEY missing, cannot generate daily love note');
      return null;
    }

    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });

    const isOriginalCouple = (resolvedSlug === 'narayan-tanaya' || resolvedId === '6a91c996e82f78851994a8bc');
    let prompt;
    if (isOriginalCouple) {
      prompt = `Write a very short (2-3 sentences max) sweet, deeply romantic, and unique daily compliment or love note for my girlfriend Tanaya. Use beautiful poetic language comparing her to flowers, stars, or art. Include a couple of elegant emojis. Do not use placeholders.`;
    } else {
      prompt = `Write a very short (2-3 sentences max) sweet, deeply romantic, and unique daily compliment or love note for a couple in love. Use beautiful poetic language comparing love to blooming flowers, starry nights, or art. Include a couple of elegant emojis. Do not use placeholders.`;
    }

    const result = await model.generateContent(prompt);
    const content = (await result.response.text()).trim();

    const notePayload = {
      coupleId: resolvedId,
      content: content,
      author: 'Kuchupuchu ✨',
      dateStr: todayStr,
      isDailyAi: true,
      imageUrl: '',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    let docRef = null;

    // 3. Save to Firestore
    if (db) {
      docRef = await db.collection('loveNotes').add(notePayload);
      console.log(`✿ Gemini daily love note saved to Firestore (${docRef.id}) for couple ${resolvedId} (${todayStr})`);
    }

    // 4. Save to MongoDB if available
    try {
      const mongoNote = new LoveNote(notePayload);
      await mongoNote.save();
    } catch (mErr) {
      // Non-fatal if MongoDB has separate schema/connection
    }

    // 5. Send push notification to the partner (or both partners if automated cron)
    try {
      let user1 = coupleData?.user1 || '';
      let user2 = coupleData?.user2 || '';

      if (db && (!user1 || !user2)) {
        try {
          let coupleDoc = await db.collection('couples').doc(resolvedId).get();
          if (!coupleDoc.exists && resolvedSlug) {
            const q = await db.collection('couples').where('slug', '==', resolvedSlug).limit(1).get();
            if (!q.empty) coupleDoc = q.docs[0];
          }
          if (coupleDoc && coupleDoc.exists) {
            const cData = coupleDoc.data();
            user1 = user1 || cData.user1;
            user2 = user2 || cData.user2;
            resolvedSlug = resolvedSlug || cData.slug;
          }
        } catch (cErr) {
          console.warn('✿ Could not fetch couple from Firestore for daily note notification:', cErr.message);
        }
      }

      if (!user1 || !user2) {
        try {
          const Couple = require('../models/Couple');
          const mongoose = require('mongoose');
          let mongoCouple = null;
          if (mongoose.Types.ObjectId.isValid(resolvedId)) {
            mongoCouple = await Couple.findById(resolvedId);
          }
          if (!mongoCouple && resolvedSlug) {
            mongoCouple = await Couple.findOne({ slug: resolvedSlug });
          }
          if (mongoCouple) {
            user1 = user1 || mongoCouple.user1?.toString();
            user2 = user2 || mongoCouple.user2?.toString();
            resolvedSlug = resolvedSlug || mongoCouple.slug;
          }
        } catch (_) {}
      }

      const requestingUserId = options.requestingUserId ? String(options.requestingUserId) : null;
      let targetUserIds = [];

      const u1Str = user1 ? String(user1) : '';
      const u2Str = user2 ? String(user2) : '';

      if (requestingUserId) {
        // If a specific user triggered this generation on-demand, notify their partner
        if (requestingUserId === u1Str && u2Str) {
          targetUserIds = [u2Str];
        } else if (requestingUserId === u2Str && u1Str) {
          targetUserIds = [u1Str];
        } else {
          targetUserIds = [u1Str, u2Str].filter(id => id && id !== requestingUserId);
        }
      } else {
        // Automated background generation (cron job or startup check): notify both partners
        targetUserIds = [u1Str, u2Str].filter(Boolean);
      }

      const previewContent = content.length > 120 ? `${content.substring(0, 117)}...` : content;
      const pushTitle = 'Daily Love Note Has Bloomed 🌸';
      const pushBody = previewContent;
      const pushData = {
        type: 'daily_note',
        action: 'open_love_notes',
        coupleId: String(resolvedId),
        slug: String(resolvedSlug),
        dateStr: String(todayStr),
        noteId: String(docRef?.id || '')
      };

      for (const targetId of targetUserIds) {
        const fcmToken = await getFcmTokenForUser(targetId, db);
        if (fcmToken) {
          await sendPushNotification(fcmToken, pushTitle, pushBody, pushData);
          console.log(`✿ Sent daily love note push notification to partner ${targetId}`);
        } else {
          console.log(`✿ No FCM token found for partner ${targetId}`);
        }
      }

      // Broadcast real-time Socket.io event if connected
      const activeIo = options.io || getIo();
      if (activeIo && resolvedSlug) {
        activeIo.to(resolvedSlug).emit('dailyNote', notePayload);
        activeIo.to(resolvedSlug).emit('notification', {
          type: 'daily_note_generated',
          coupleId: resolvedId,
          title: pushTitle,
          message: previewContent,
          dateStr: todayStr
        });
      }
    } catch (notifErr) {
      console.error('✿ Error sending daily love note partner notification:', notifErr?.message || notifErr);
    }

    return notePayload;
  } catch (err) {
    console.error(`✿ Error generating daily love note for couple ${resolvedId || idOrSlug}:`, err?.message || err);
    return null;
  }
}

async function runDailyLoveNotes() {
  console.log('✿ Running automated Gemini Daily Love Note job...');
  const db = getFirestore();
  if (!db) return;

  try {
    const couplesSnap = await db.collection('couples').get();
    for (const doc of couplesSnap.docs) {
      const couple = doc.data();
      const coupleId = doc.id;
      const slug = couple.slug || coupleId;
      await generateDailyNoteForCouple(coupleId, slug, couple);
      await new Promise(res => setTimeout(res, 1000));
    }
  } catch (err) {
    console.error('✿ Error in runDailyLoveNotes:', err);
  }
}

function initDailyLoveNoteJob() {
  console.log('✿ Initializing Gemini Automated Daily Love Note Cron Job (runs every day at 00:01)...');

  // Cron schedule: at 00:01 every day
  cron.schedule('1 0 * * *', () => {
    runDailyLoveNotes();
  });

  // Run a check on startup to ensure today's note exists
  setTimeout(() => {
    runDailyLoveNotes();
  }, 4000);
}

module.exports = {
  initDailyLoveNoteJob,
  generateDailyNoteForCouple,
  getTodayDateStr,
  setIo,
  getIo
};
