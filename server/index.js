require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const http = require('http');
const jwt = require('jsonwebtoken');
const { Server } = require('socket.io');

const authRoutes = require('./routes/auth');
const coupleRoutes = require('./routes/couples');
const milestoneRoutes = require('./routes/milestones');
const loveNoteRoutes = require('./routes/loveNotes');
const memoryRoutes = require('./routes/memories');
const dreamLocationRoutes = require('./routes/dreamLocations');
const settingsRoutes = require('./routes/settings');
const uploadRoutes = require('./routes/upload');
const payuRoutes = require('./routes/payu');
const authMiddleware = require('./middleware/authMiddleware');
const coupleMiddleware = require('./middleware/coupleMiddleware');
const { initAnniversaryEmailJob } = require('./jobs/anniversaryEmail');
const { initDailyLoveNoteJob, generateDailyNoteForCouple, setIo: setDailyLoveNoteIo } = require('./jobs/dailyLoveNote');
const { broadcastAppUpdate } = require('./services/updateBroadcast');

// Initialize Firebase Admin via unified utility
const { 
  getFirestore, 
  getMessaging, 
  getBucket, 
  sendPushNotification: sendPushToToken 
} = require('./utils/firebase');

const db = getFirestore();
const messaging = getMessaging();
const bucket = getBucket();

const sendPushNotification = async (userId, title, body, data = {}) => {
  if (!db) return;
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (userDoc.exists && userDoc.data().fcmToken) {
      await sendPushToToken(userDoc.data().fcmToken, title, body, data);
      console.log(`✿ Push notification sent to user ${userId}`);
    }
  } catch (err) {
    const isUnregistered = 
      err?.code === 'messaging/registration-token-not-registered' ||
      err?.errorInfo?.code === 'messaging/registration-token-not-registered' ||
      err?.details?.some?.(d => d.errorCode === 'UNREGISTERED') ||
      err?.message?.includes('NotRegistered') ||
      err?.status === 404;

    if (isUnregistered) {
      console.warn(`✿ Stale FCM token for user ${userId} is unregistered/expired. Clearing from database.`);
      try {
        await db.collection('users').doc(userId).update({ fcmToken: null });
      } catch (_) {}
    } else {
      console.error(`✿ Error sending push notification to user ${userId}:`, err?.message || err);
    }
  }
};

const coupleAnthems = new Map();

const notifyPartner = async (coupleId, authorUid, title, body, data = {}) => {
  if (!db) return false;
  try {
    const coupleDoc = await db.collection('couples').doc(coupleId).get();
    if (coupleDoc.exists) {
      const { user1, user2 } = coupleDoc.data();
      const partner = authorUid === user1 ? user2 : (authorUid === user2 ? user1 : user2);
      if (partner) {
        await sendPushNotification(partner, title, body, data);
        return true;
      }
    }
  } catch (e) {
    console.error('Error notifying partner', e);
  }
  return false;
};

const setupFirestoreListeners = () => {
  if (!db) return;
  console.log('✿ Setting up Firestore real-time listeners for push notifications...');

  db.collection('heartbeats').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added') {
        const hb = change.doc.data();
        const age = Date.now() - (hb.createdAt || 0);
        if (age < 120000) {
          notifyPartner(
            hb.coupleId,
            hb.senderId,
            `${hb.senderName || 'Your Love'} sent you a Heartbeat ❤️`,
            'Thinking of you right now... tap to send one back!',
            { type: 'heartbeat', senderName: hb.senderName || 'Your Love' }
          );
        }
      }
    });
  });
  
  db.collection('loveNotes').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added') {
        const note = change.doc.data();
        // Skip AI daily notes here to avoid duplicate or misattributed notifications
        // (daily notes are dispatched with rich previews and proper partner resolution in generateDailyNoteForCouple)
        if (note.isDailyAi) {
          return;
        }
        if (note.createdAt && (Date.now() - new Date(note.createdAt).getTime() < 120000)) {
          notifyPartner(note.coupleId, note.author, 'New Love Note! 💌', 'Your partner left you a sweet note.', {
            type: 'note',
            action: 'open_love_notes',
            coupleId: note.coupleId,
            noteId: change.doc.id
          });
        }
      }
    });
  });

  db.collection('memories').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added') {
        const mem = change.doc.data();
        if (mem.createdAt && (Date.now() - new Date(mem.createdAt).getTime() < 120000)) {
          notifyPartner(mem.coupleId, mem.authorId || '', 'New Memory! 📸', 'Your partner just added a new memory to the gallery.');
        }
      }
    });
  });

  db.collection('chat_messages').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added') {
        const msg = change.doc.data();
        // Skip if client already dispatched direct FCM push
        if (msg.pushSent) return;

        const age = Date.now() - (msg.timestamp || 0);
        if (age < 120000) {
          let bodyText = 'New message';
          if (msg.text && msg.text.trim().length > 0) {
            bodyText = msg.text.trim();
          } else if (msg.audioUrl) {
            bodyText = '🎙️ Voice message';
          } else if (msg.imageUrl) {
            bodyText = '📷 Photo';
          }

          const sender = msg.senderName || 'Your Love';
          notifyPartner(
            msg.coupleId,
            msg.senderId,
            sender,
            bodyText,
            { 
              type: 'chat', 
              senderName: sender,
              messageText: msg.text || '',
              imageUrl: msg.imageUrl || '',
              audioUrl: msg.audioUrl || '',
              messageId: change.doc.id,
              coupleId: msg.coupleId || '',
              senderId: msg.senderId || ''
            }
          );
        }
      }
    });
  });

  db.collection('couples').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added' || change.type === 'modified') {
        const coupleId = change.doc.id;
        const couple = change.doc.data();
        const newTrack = couple.spotifyTrackId;
        
        if (coupleAnthems.has(coupleId)) {
          const oldTrack = coupleAnthems.get(coupleId);
          if (oldTrack !== newTrack && newTrack) {
            sendPushNotification(couple.user1, 'Anthem Updated 🎵', 'Your couple anthem was just updated!');
            if (couple.user2) {
              sendPushNotification(couple.user2, 'Anthem Updated 🎵', 'Your couple anthem was just updated!');
            }
          }
        }
        coupleAnthems.set(coupleId, newTrack);
      }
    });
  });

  db.collection('video_calls').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      const callData = change.doc.data();
      if (!callData) return;
      const coupleId = change.doc.id;
      // When a call is initiated: status === 'calling'
      if ((change.type === 'added' || change.type === 'modified') && callData.status === 'calling') {
        const age = Date.now() - (callData.timestamp || 0);
        if (age < 60000) { // Call created within 60s
          const callerName = callData.callerName || 'Your Love';
          notifyPartner(
            coupleId,
            callData.callerId,
            callerName,
            'Incoming Video Call 📹',
            {
              type: 'video_call',
              coupleId: coupleId,
              callerId: callData.callerId || '',
              callerName: callerName,
              callerAvatar: callData.callerAvatar || '',
              status: 'calling',
              timestamp: String(callData.timestamp || Date.now())
            }
          );
        }
      }
    });
  });

  // Listen for real-time app update release broadcasts via Firestore
  db.collection('app_updates').doc('latest').onSnapshot(async (doc) => {
    if (!doc.exists) return;
    const data = doc.data();
    if (data && data.triggerBroadcast === true) {
      console.log(`✿ Firestore triggered update broadcast for v${data.versionName} (code ${data.versionCode})`);
      try {
        await db.collection('app_updates').doc('latest').update({ triggerBroadcast: false });
        await broadcastAppUpdate(data);
      } catch (err) {
        console.error('✿ Error running Firestore-triggered update broadcast:', err.message);
      }
    }
  });
};
setupFirestoreListeners();

// Automatically verify release manifest on server startup / Render deploy
const checkAndBroadcastNewRelease = async () => {
  if (!db) return;
  try {
    let manifest = null;
    const candidatePaths = [
      path.join(__dirname, 'public/updates/app-update.json'),
      path.join(__dirname, '../app-update.json'),
      path.join(__dirname, 'app-update.json')
    ];
    for (const p of candidatePaths) {
      if (fs.existsSync(p)) {
        try {
          manifest = JSON.parse(fs.readFileSync(p, 'utf8'));
          if (manifest && manifest.versionCode) break;
        } catch (_) {}
      }
    }
    if (!manifest || !manifest.versionCode) return;

    const latestDoc = await db.collection('app_updates').doc('latest').get();
    const firestoreCode = latestDoc.exists ? (latestDoc.data()?.versionCode || 0) : 0;
    
    if (manifest.versionCode > firestoreCode) {
      console.log(`✿ New release manifest detected on deploy! Local code=${manifest.versionCode} > Firestore code=${firestoreCode}. Broadcasting update...`);
      await broadcastAppUpdate(manifest);
    } else {
      console.log(`✿ Release manifest is current (Local code=${manifest.versionCode}, Firestore code=${firestoreCode})`);
    }
  } catch (err) {
    console.error('✿ Error checking auto-manifest release:', err.message);
  }
};
checkAndBroadcastNewRelease();


const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'DELETE', 'PUT']
  }
});
app.set('io', io);
app.set('bucket', bucket);
setDailyLoveNoteIo(io);

// Socket.io with JWT authentication and couple rooms
io.on('connection', (socket) => {
  const token = socket.handshake.auth.token;
  
  if (token) {
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      socket.userId = decoded.userId;
      
      // Join couple room if user has a couple slug
      if (socket.handshake.auth.coupleSlug) {
        socket.join(socket.handshake.auth.coupleSlug);
        console.log(`✿ User ${decoded.userId} joined room: ${socket.handshake.auth.coupleSlug}`);
      }
    } catch (err) {
      console.log('✿ Socket auth failed:', err.message);
    }
  }

  console.log('✿ Client connected via Socket.io');
  
  socket.on('joinCouple', (slug) => {
    if (slug) {
      socket.join(slug);
      console.log(`✿ Socket joined room: ${slug}`);
    }
  });

  socket.on('sendHeartbeat', (data) => {
    const slug = data?.slug || socket.handshake.auth.coupleSlug;
    if (slug) {
      socket.to(slug).emit('heartbeat', data);
      console.log(`✿ Heartbeat relayed to room: ${slug}`);
    }
  });

  socket.on('disconnect', () => {
    console.log('✿ Client disconnected');
  });
});

const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Static files
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/updates', express.static(path.join(__dirname, 'public/updates')));
// Public routes (no auth)
app.use('/api/auth', authRoutes);

// Generic upload endpoint
app.use('/api/upload', uploadRoutes);

// PayU payment gateway endpoints (create payment, callbacks, webhooks)
app.use('/api/payu', payuRoutes);

// Couple management routes (auth required)
app.use('/api/couples', coupleRoutes);

// Couple-scoped data routes (auth + couple middleware)
app.use('/api/couples/:slug/milestones', authMiddleware, coupleMiddleware, milestoneRoutes);
app.use('/api/couples/:slug/love-notes', authMiddleware, coupleMiddleware, loveNoteRoutes);
app.use('/api/couples/:slug/memories', authMiddleware, coupleMiddleware, memoryRoutes);
app.use('/api/couples/:slug/dream-locations', authMiddleware, coupleMiddleware, dreamLocationRoutes);
app.use('/api/couples/:slug/settings', authMiddleware, coupleMiddleware, settingsRoutes);

// FCM push dispatch endpoint (securely uses backend Firebase Admin credentials)
app.post('/api/fcm/send', async (req, res) => {
  try {
    const { token, title, body, data } = req.body;
    if (!token) return res.status(400).json({ error: 'token is required' });

    const success = await sendPushToToken(token, title, body, data || {});
    res.json({ success });
  } catch (err) {
    console.error('✿ Error in /api/fcm/send:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// Instant chat push notification endpoint (wakes up Render & guarantees real-time FCM dispatch)
app.post('/api/chat/notify', async (req, res) => {
  try {
    const { coupleId, senderId, senderName, messageId, text, imageUrl, audioUrl } = req.body;
    if (!coupleId) {
      return res.status(400).json({ error: 'coupleId required' });
    }

    let bodyText = 'New message';
    if (text && text.trim().length > 0) {
      bodyText = text.trim();
    } else if (imageUrl) {
      bodyText = '📷 Photo';
    } else if (audioUrl) {
      bodyText = '🎙️ Voice message';
    }

    const sender = senderName || 'Your Love';
    await notifyPartner(
      coupleId,
      senderId || '',
      sender,
      bodyText,
      {
        type: 'chat',
        senderName: sender,
        messageText: text || '',
        imageUrl: imageUrl || '',
        audioUrl: audioUrl || '',
        messageId: messageId || '',
        coupleId: coupleId,
        senderId: senderId || ''
      }
    );
    res.json({ success: true });
  } catch (err) {
    console.error('✿ Error in /api/chat/notify:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// Instant delivery receipt confirmation endpoint (guarantees WhatsApp double tick)
app.post('/api/chat/delivered', async (req, res) => {
  try {
    const { messageId, coupleId } = req.body;
    if (!messageId) {
      return res.status(400).json({ error: 'messageId required' });
    }

    if (db) {
      const now = Date.now();
      await db.collection('chat_messages').doc(messageId).update({
        isDelivered: true,
        delivered: true,
        deliveredAt: now
      });
      console.log(`✿ Message ${messageId} confirmed delivered via backend`);
      return res.json({ success: true });
    }
    res.status(500).json({ error: 'Database unavailable' });
  } catch (err) {
    // If document already deleted or updated, don't crash
    console.warn('✿ /api/chat/delivered warn:', err.message);
    res.json({ success: false, error: err.message });
  }
});

// Instant video call push notification endpoint (wakes up Render & dispatches FCM call push)
app.post('/api/call/notify', async (req, res) => {
  try {
    const { coupleId, callerId, callerName, callerAvatar } = req.body;
    if (!coupleId) return res.status(400).json({ error: 'coupleId required' });

    const name = callerName || 'Your Love';
    console.log(`✿ Instant call notify trigger received for couple ${coupleId} by ${callerId}`);
    await notifyPartner(
      coupleId,
      callerId || '',
      name,
      'Incoming Video Call 📹',
      {
        type: 'video_call',
        coupleId: coupleId,
        callerId: callerId || '',
        callerName: name,
        callerAvatar: callerAvatar || '',
        status: 'calling',
        timestamp: String(Date.now())
      }
    );
    res.json({ success: true });
  } catch (err) {
    console.error('✿ Error in /api/call/notify:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// App update manifest endpoint
app.get('/api/app-update', (req, res) => {
  try {
    const updatePath = path.join(__dirname, 'public/updates/app-update.json');
    if (fs.existsSync(updatePath)) {
      const data = JSON.parse(fs.readFileSync(updatePath, 'utf8'));
      return res.json(data);
    }
  } catch (e) {
    console.error('Error reading app-update.json:', e.message);
  }
  res.status(404).json({ error: 'Update info not available' });
});

// App update broadcast endpoint
app.post('/api/app-update/broadcast', async (req, res) => {
  try {
    let manifest = req.body;
    if (!manifest || !manifest.versionCode) {
      const updatePath = path.join(__dirname, 'public/updates/app-update.json');
      if (fs.existsSync(updatePath)) {
        manifest = JSON.parse(fs.readFileSync(updatePath, 'utf8'));
      }
    }

    if (!manifest || !manifest.versionCode) {
      return res.status(400).json({ error: 'No update manifest found or provided' });
    }

    const result = await broadcastAppUpdate(manifest);
    res.json(result);
  } catch (err) {
    console.error('Error broadcasting update:', err);
    res.status(500).json({ error: err.message });
  }
});

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Daily Love Note endpoint (available to mobile app & web client)
app.get('/api/couples/:idOrSlug/daily-love-note', async (req, res) => {
  try {
    const { idOrSlug } = req.params;
    const requestingUserId = req.query.userId || req.query.requestingUserId || null;
    const note = await generateDailyNoteForCouple(idOrSlug, idOrSlug, {}, {
      requestingUserId,
      io: app.get('io')
    });
    res.json(note || {});
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Legal & Compliance policy routes (PayU, Payment Gateways & Store Compliance)
const legalPath = path.join(__dirname, 'public/legal');

app.get('/', (req, res) => {
  res.sendFile(path.join(legalPath, 'index.html'));
});

app.get(['/privacy', '/privacy-policy'], (req, res) => {
  res.sendFile(path.join(legalPath, 'privacy.html'));
});

app.get(['/terms', '/terms-and-conditions'], (req, res) => {
  res.sendFile(path.join(legalPath, 'terms.html'));
});

app.get(['/refund-policy', '/refunds', '/cancellation-refund', '/cancellation-refund-policy'], (req, res) => {
  res.sendFile(path.join(legalPath, 'refund.html'));
});

app.get(['/shipping-policy', '/shipping', '/shipping-delivery', '/shipping-delivery-policy'], (req, res) => {
  res.sendFile(path.join(legalPath, 'shipping.html'));
});

app.get(['/contact', '/contact-us'], (req, res) => {
  res.sendFile(path.join(legalPath, 'contact.html'));
});

app.get(['/about', '/about-us'], (req, res) => {
  res.sendFile(path.join(legalPath, 'about.html'));
});

// Connect to MongoDB and start server
mongoose
  .connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/ourbloom')
  .then(async () => {
    console.log('✿ Connected to MongoDB');
    
    // Only listen if we are not in a serverless environment
    if (process.env.NODE_ENV !== 'production' || process.env.RENDER) {
      server.listen(PORT, () => {
        console.log(`✿ Server running on http://localhost:${PORT}`);
        initAnniversaryEmailJob();
        initDailyLoveNoteJob();
        setInterval(checkAndBroadcastNewRelease, 60 * 60 * 1000);

        // Keepalive self-ping on Render free tier to prevent 50s cold-start sleep
        if (process.env.RENDER) {
          const https = require('https');
          setInterval(() => {
            https.get('https://our-bloom.onrender.com/api/health', () => {}).on('error', () => {});
          }, 10 * 60 * 1000); // every 10 minutes
        }
      });
    }
  })
  .catch((err) => {
    console.error('✿ Error connecting to MongoDB', err);
  });

module.exports = app;
