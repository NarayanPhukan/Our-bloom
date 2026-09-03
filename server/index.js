require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');
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
const authMiddleware = require('./middleware/authMiddleware');
const coupleMiddleware = require('./middleware/coupleMiddleware');
const { initAnniversaryEmailJob } = require('./jobs/anniversaryEmail');

// Initialize Firebase Admin
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { getStorage } = require('firebase-admin/storage');
let serviceAccount;
try {
  serviceAccount = require('./firebase-service-account.json');
} catch (e) {
  const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (rawServiceAccount) {
    serviceAccount = JSON.parse(rawServiceAccount);
  }
}

let firebaseApp;
let db = null;
let messaging = null;
let bucket = null;

if (serviceAccount) {
  try {
    firebaseApp = initializeApp({
      credential: cert(serviceAccount),
      storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'our-bloom.firebasestorage.app'
    });
    db = getFirestore();
    messaging = getMessaging();
    bucket = getStorage().bucket();
  } catch (e) {
    console.error('✿ Firebase initialization failed', e);
  }
} else {
  console.warn('✿ Firebase credentials missing. Push notifications and storage disabled.');
}

// Helper to send push notification
const sendPushNotification = async (userId, title, body, data = {}) => {
  if (!db || !messaging) return;
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (userDoc.exists && userDoc.data().fcmToken) {
      const isHeartbeat = data.type === 'heartbeat';
      const message = {
        notification: { title, body },
        data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
        android: {
          priority: 'high',
          notification: {
            channelId: isHeartbeat ? 'ourbloom_heartbeat_channel' : 'ourbloom_fcm_channel',
            priority: 'max',
            sound: 'default',
            defaultVibrateTimings: !isHeartbeat,
            vibrateTimingsMillis: isHeartbeat ? [0, 120, 80, 240] : undefined
          }
        },
        token: userDoc.data().fcmToken
      };
      await messaging.send(message);
      console.log(`✿ Push notification sent to user ${userId}`);
    }
  } catch (err) {
    console.error(`✿ Error sending push notification to user ${userId}:`, err);
  }
};

const coupleAnthems = new Map();

const setupFirestoreListeners = () => {
  if (!db) return;
  console.log('✿ Setting up Firestore real-time listeners for push notifications...');

  const notifyPartner = async (coupleId, authorUid, title, body, data = {}) => {
    try {
      const coupleDoc = await db.collection('couples').doc(coupleId).get();
      if (coupleDoc.exists) {
        const { user1, user2 } = coupleDoc.data();
        const partner = authorUid === user1 ? user2 : (authorUid === user2 ? user1 : user2);
        if (partner) {
          sendPushNotification(partner, title, body, data);
        }
      }
    } catch (e) {
      console.error('Error notifying partner', e);
    }
  };

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
        if (note.createdAt && (Date.now() - new Date(note.createdAt).getTime() < 120000)) {
          notifyPartner(note.coupleId, note.author, 'New Love Note! 💌', 'Your partner left you a sweet note.');
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
        const age = Date.now() - (msg.timestamp || 0);
        if (age < 120000) {
          const bodyText = msg.text ? msg.text : (msg.imageUrl ? '📷 Sent a photo' : 'New message');
          notifyPartner(
            msg.coupleId,
            msg.senderId,
            `${msg.senderName || 'Your Love'} 💬`,
            bodyText,
            { type: 'chat', senderName: msg.senderName || 'Your Love', messageId: change.doc.id }
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
};
setupFirestoreListeners();

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

// Couple management routes (auth required)
app.use('/api/couples', coupleRoutes);

// Couple-scoped data routes (auth + couple middleware)
app.use('/api/couples/:slug/milestones', authMiddleware, coupleMiddleware, milestoneRoutes);
app.use('/api/couples/:slug/love-notes', authMiddleware, coupleMiddleware, loveNoteRoutes);
app.use('/api/couples/:slug/memories', authMiddleware, coupleMiddleware, memoryRoutes);
app.use('/api/couples/:slug/dream-locations', authMiddleware, coupleMiddleware, dreamLocationRoutes);
app.use('/api/couples/:slug/settings', authMiddleware, coupleMiddleware, settingsRoutes);

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Root route
app.get('/', (req, res) => {
  res.send('✿ Our Bloom API is running beautifully!');
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
      });
    }
  })
  .catch((err) => {
    console.error('✿ Error connecting to MongoDB', err);
  });

module.exports = app;
