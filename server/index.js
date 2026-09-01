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
const authMiddleware = require('./middleware/authMiddleware');
const coupleMiddleware = require('./middleware/coupleMiddleware');
const { initAnniversaryEmailJob } = require('./jobs/anniversaryEmail');

// Initialize Firebase Admin
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const serviceAccount = require('./firebase-service-account.json');

let firebaseApp;
try {
  firebaseApp = initializeApp({
    credential: cert(serviceAccount)
  });
} catch (e) {
  // Ignore if already initialized
}
const db = getFirestore();
const messaging = getMessaging();

// Helper to send push notification
const sendPushNotification = async (userId, title, body) => {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (userDoc.exists && userDoc.data().fcmToken) {
      const message = {
        notification: { title, body },
        token: userDoc.data().fcmToken
      };
      await messaging.send(message);
      console.log(`✿ Push notification sent to user ${userId}`);
    }
  } catch (err) {
    console.error(`✿ Error sending push notification to user ${userId}:`, err);
  }
};

// Setup Firestore listeners
const setupFirestoreListeners = () => {
  console.log('✿ Setting up Firestore real-time listeners for push notifications...');
  
  // Listen for new Love Notes
  db.collection('loveNotes').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'added') {
        const note = change.doc.data();
        if (note.createdAt && (Date.now() - new Date(note.createdAt).getTime() < 60000)) {
          // It's a brand new note!
          const partnerId = note.coupleId; // In a robust app, we'd find the exact partner user ID. We'll broadcast to both couple members for now, or fetch the couple doc to find the partner.
          
          db.collection('couples').doc(note.coupleId).get().then(coupleDoc => {
             if(coupleDoc.exists) {
                 const { user1, user2 } = coupleDoc.data();
                 // Assuming author is one of them
                 const partner = note.author === user1 ? user2 : (note.author === user2 ? user1 : user2); // Simplified
                 sendPushNotification(partner, 'New Love Note! 💌', `You have a new love note from ${note.author}`);
             }
          });
        }
      }
    });
  });

  // Listen for Anthem updates
  db.collection('couples').onSnapshot(snapshot => {
    snapshot.docChanges().forEach(change => {
      if (change.type === 'modified') {
        const couple = change.doc.data();
        const oldCouple = change.oldIndex !== -1 ? change.doc.data() : null; // simplified check
        // Actually onSnapshot modified doesn't give previous state easily without local caching, 
        // but we can send a general "Your couple profile was updated!" if we want.
        // Let's keep it simple: notify on general profile modification if needed, or skip for now to avoid spam.
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
