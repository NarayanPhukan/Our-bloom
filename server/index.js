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
