require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');
const http = require('http');
const { Server } = require('socket.io');

const milestoneRoutes = require('./routes/milestones');
const loveNoteRoutes = require('./routes/loveNotes');
const settingsRoutes = require('./routes/settings');
const seedDatabase = require('./seed');
const { initAnniversaryEmailJob } = require('./jobs/anniversaryEmail');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'DELETE']
  }
});
app.set('io', io);

io.on('connection', (socket) => {
  console.log('✿ Client connected via Socket.io');
  socket.on('disconnect', () => {
    console.log('✿ Client disconnected');
  });
});

const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API Routes
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/api/milestones', milestoneRoutes);
app.use('/api/love-notes', loveNoteRoutes);
app.use('/api/memories', require('./routes/memories'));
app.use('/api/dream-locations', require('./routes/dreamLocations'));
app.use('/api/settings', settingsRoutes);

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
    await seedDatabase();
    
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
