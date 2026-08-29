import { useState, useEffect } from 'react';
import { io } from 'socket.io-client';
import { getSpotifySettings, updateSpotifySettings } from './api';
import { BrowserRouter as Router, Routes, Route, Navigate, useParams } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import Header from './components/Header';
import Footer from './components/Footer';
import PetalEffect from './components/PetalEffect';
import ProtectedRoute from './components/ProtectedRoute';
import JourneyPage from './pages/JourneyPage';
import MemoriesPage from './pages/MemoriesPage';
import LoveNotesPage from './pages/LoveNotesPage';
import MapPage from './pages/MapPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import SetupPage from './pages/SetupPage';

const SpotifyPlayer = () => {
  const { couple, token } = useAuth();
  const { slug } = useParams();
  const [isOpen, setIsOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [trackId, setTrackId] = useState('4O2N861eOnF9q8EtpH8IJu');
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    if (!slug) return;

    const fetchSettings = async () => {
      try {
        const { data } = await getSpotifySettings(slug);
        if (data && data.spotifyTrackId) setTrackId(data.spotifyTrackId);
      } catch (err) {
        console.error('Failed to fetch spotify settings', err);
      }
    };
    fetchSettings();

    const socketUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
    const socket = io(socketUrl, {
      auth: { token, coupleSlug: slug }
    });
    socket.on('updateSpotify', (newTrackId) => {
      setTrackId(newTrackId);
    });

    return () => socket.disconnect();
  }, [slug, token]);

  const handleSave = async () => {
    let newId = inputValue.trim();
    if (!newId) return;
    
    if (newId.includes('spotify.com/track/')) {
      const parts = newId.split('spotify.com/track/')[1];
      newId = parts.split('?')[0];
    }
    
    try {
      await updateSpotifySettings(slug, { spotifyTrackId: newId });
      setTrackId(newId);
      setIsEditing(false);
      setInputValue('');
    } catch (err) {
      console.error('Failed to update spotify settings');
    }
  };

  return (
    <div className={`fixed bottom-6 left-6 z-[2000] transition-transform duration-500 ease-in-out ${isOpen ? 'translate-y-0' : 'translate-y-[calc(100%-60px)] hover:translate-y-[calc(100%-66px)]'}`}>
      <div className={`backdrop-blur-xl rounded-[28px] overflow-hidden flex flex-col w-[320px] transition-all duration-500 border border-white/60 ${isOpen ? 'bg-surface/90 shadow-[0_20px_40px_rgba(222,191,194,0.3)]' : 'bg-surface/70 shadow-lg'} relative`}>
        <button 
          onClick={() => setIsOpen(!isOpen)}
          className="w-full flex items-center justify-between p-4 px-5 bg-gradient-to-r from-primary/10 to-transparent hover:from-primary/20 hover:to-primary/5 transition-all cursor-pointer h-[60px]"
        >
          <div className="flex items-center gap-3">
            <div className={`flex items-center justify-center w-8 h-8 rounded-full transition-all duration-300 ${isOpen ? 'bg-primary text-on-primary shadow-glow-primary' : 'bg-primary/10 text-primary'}`}>
               <span className={`material-symbols-outlined text-[18px] ${isOpen ? 'animate-pulse' : ''}`}>music_note</span>
            </div>
            <span className="font-label-sm uppercase tracking-widest text-primary font-bold">Our Anthem</span>
          </div>
          <span className="material-symbols-outlined text-primary/70 transition-transform duration-500" style={{ transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}>
            expand_less
          </span>
        </button>

        {isOpen && (
          <button 
             onClick={(e) => { e.stopPropagation(); setIsEditing(!isEditing); }}
             className="absolute top-4 right-14 p-1 rounded-full hover:bg-primary/20 text-primary transition-colors flex items-center justify-center"
          >
             <span className="material-symbols-outlined text-[18px]">settings</span>
          </button>
        )}

        <div className="p-3 pt-1 h-[168px] bg-white/30 relative">
          {isEditing ? (
            <div className="flex flex-col gap-2 p-3 bg-surface/90 rounded-xl h-full border border-primary/20">
              <label className="text-[10px] font-bold uppercase text-primary tracking-widest">Update Anthem</label>
              <input 
                 className="w-full bg-surface-container-highest border border-outline-variant rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-primary text-on-surface"
                 placeholder="Paste Spotify Link or ID"
                 value={inputValue}
                 onChange={(e) => setInputValue(e.target.value)}
              />
              <div className="flex gap-2 justify-end mt-auto">
                 <button onClick={() => setIsEditing(false)} className="text-xs text-on-surface-variant font-bold px-3 py-2 hover:bg-outline-variant/20 rounded-full transition-colors">CANCEL</button>
                 <button onClick={handleSave} className="text-xs bg-primary text-on-primary rounded-full px-4 py-2 font-bold shadow-glow-primary hover:bg-secondary transition-colors">SAVE</button>
              </div>
            </div>
          ) : (
            <iframe 
              style={{ borderRadius: '16px' }} 
              src={`https://open.spotify.com/embed/track/${trackId}?utm_source=generator&theme=0`} 
              width="100%" 
              height="152" 
              frameBorder="0" 
              allowFullScreen="" 
              allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" 
              loading="lazy"
            ></iframe>
          )}
        </div>
      </div>
    </div>
  );
};

function CoupleLayout() {
  return (
    <div className="bg-lily-pattern text-on-background min-h-screen flex flex-col relative overflow-x-hidden">
      <Header />
      <PetalEffect />
      <SpotifyPlayer />
      <main className="pt-32 pb-20 flex-1">
        <Routes>
          <Route index element={<JourneyPage />} />
          <Route path="memories" element={<MemoriesPage />} />
          <Route path="love-notes" element={<LoveNotesPage />} />
          <Route path="map" element={<MapPage />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}

function AppRedirect() {
  const { user, couple, loading } = useAuth();
  
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-lily-pattern">
        <span className="material-symbols-outlined text-[48px] text-primary animate-spin">filter_vintage</span>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" replace />;
  if (!couple) return <Navigate to="/setup" replace />;
  return <Navigate to={`/c/${couple.slug}`} replace />;
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <NotificationProvider>
          <Routes>
            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Setup (authenticated but no couple) */}
            <Route path="/setup" element={<SetupPage />} />

            {/* Couple routes (authenticated + has couple) */}
            <Route path="/c/:slug/*" element={
              <ProtectedRoute>
                <CoupleLayout />
              </ProtectedRoute>
            } />

            {/* Root redirect */}
            <Route path="/" element={<AppRedirect />} />
            <Route path="*" element={<AppRedirect />} />
          </Routes>
        </NotificationProvider>
      </AuthProvider>
    </Router>
  );
}

export default App;
