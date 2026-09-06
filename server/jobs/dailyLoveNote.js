const cron = require('node-cron');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const { getFirestore } = require('../utils/firebase');
const LoveNote = require('../models/LoveNote');

function getTodayDateStr() {
  return new Date().toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  });
}

async function generateDailyNoteForCouple(idOrSlug, coupleSlug, coupleData = {}) {
  try {
    const todayStr = getTodayDateStr();
    const db = getFirestore();
    let resolvedId = idOrSlug;
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

    // 3. Save to Firestore
    if (db) {
      const docRef = await db.collection('loveNotes').add(notePayload);
      console.log(`✿ Gemini daily love note saved to Firestore (${docRef.id}) for couple ${resolvedId} (${todayStr})`);
    }

    // 4. Save to MongoDB if available
    try {
      const mongoNote = new LoveNote(notePayload);
      await mongoNote.save();
    } catch (mErr) {
      // Non-fatal if MongoDB has separate schema/connection
    }

    return notePayload;
  } catch (err) {
    console.error(`✿ Error generating daily love note for couple ${coupleId}:`, err.message);
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
  getTodayDateStr
};
