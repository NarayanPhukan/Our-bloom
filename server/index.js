require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');

const milestoneRoutes = require('./routes/milestones');
const loveNoteRoutes = require('./routes/loveNotes');
const seedDatabase = require('./seed');

const app = express();
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

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Connect to MongoDB and start server
mongoose
  .connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/ourbloom')
  .then(async () => {
    console.log('✿ Connected to MongoDB');
    await seedDatabase();
    app.listen(PORT, () => {
      console.log(`✿ Server running on http://localhost:${PORT}`);
    });
  })
  .catch((err) => {
    console.error('MongoDB connection error:', err.message);
    process.exit(1);
  });
