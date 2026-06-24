# Our Bloom 🌸

A romantic journey timeline — a MERN (MongoDB, Express, React, Node.js) application that beautifully preserves your first month of love through milestones, memories, and love notes.

## Project Structure

```
our-bloom/
├── client/                 # React + Vite frontend
│   ├── src/
│   │   ├── api/            # Axios API client
│   │   ├── components/     # Reusable UI components
│   │   │   ├── Header.jsx
│   │   │   ├── Footer.jsx
│   │   │   ├── MilestoneCard.jsx
│   │   │   ├── NoteModal.jsx
│   │   │   ├── PetalEffect.jsx
│   │   │   └── Toast.jsx
│   │   ├── data/           # Fallback data (offline mode)
│   │   ├── pages/          # Route pages
│   │   │   ├── JourneyPage.jsx
│   │   │   ├── MemoriesPage.jsx
│   │   │   └── LoveNotesPage.jsx
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── index.css
│   ├── tailwind.config.js
│   └── package.json
├── server/                 # Express + MongoDB backend
│   ├── models/
│   │   ├── Milestone.js
│   │   └── LoveNote.js
│   ├── routes/
│   │   ├── milestones.js
│   │   └── loveNotes.js
│   ├── seed.js
│   ├── index.js
│   ├── .env
│   └── package.json
└── package.json            # Root scripts
```

## Prerequisites

- **Node.js** 18+
- **MongoDB** (local or Atlas)

## Quick Start

### 1. Install MongoDB

If you don't have MongoDB installed locally:

```bash
# Ubuntu/Debian
sudo apt install -y mongodb-org

# macOS
brew tap mongodb/brew && brew install mongodb-community

# Or use MongoDB Atlas (cloud) — update MONGODB_URI in server/.env
```

### 2. Start MongoDB

```bash
sudo systemctl start mongod
# or
mongod --dbpath ./data
```

### 3. Install Dependencies

```bash
# From the project root
cd client && npm install
cd ../server && npm install
```

### 4. Run the App

```bash
# Terminal 1 — Start server (API on port 5000)
cd server && npm run dev

# Terminal 2 — Start client (UI on port 5173)
cd client && npm run dev
```

Or from the root:

```bash
npm run dev
```

### 5. Open in Browser

Visit **http://localhost:5173**

## Features

- 🌹 **Journey Timeline** — Beautiful alternating timeline with scroll-reveal animations
- 📸 **Memories Gallery** — Masonry grid with lightbox image viewer
- 💌 **Love Notes** — Write and read love notes (persisted in MongoDB)
- 🌸 **Petal Rain** — Ambient falling petal animation
- 📱 **Fully Responsive** — Mobile-first with hamburger nav
- 🎨 **Material Design 3** — Custom color palette with glass-morphism cards

## API Endpoints

| Method | Endpoint           | Description           |
|--------|--------------------|-----------------------|
| GET    | /api/milestones    | List all milestones   |
| POST   | /api/milestones    | Create milestone      |
| PUT    | /api/milestones/:id| Update milestone      |
| DELETE | /api/milestones/:id| Delete milestone      |
| GET    | /api/love-notes    | List all love notes   |
| POST   | /api/love-notes    | Create love note      |
| DELETE | /api/love-notes/:id| Delete love note      |
| GET    | /api/health        | Health check          |

## Offline Mode

The frontend includes **fallback data** — if MongoDB/server is unavailable, the app still renders the 4 original milestone cards with images.
# Our-bloom
