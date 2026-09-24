import { useState, useEffect, useRef, useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { io } from 'socket.io-client';
import Lightbox from '../components/Lightbox';
import PullToRefresh from '../components/PullToRefresh';
import Toast from '../components/Toast';
import NoteModal from '../components/NoteModal';
import { 
  getMemories, 
  getDailyLoveNote, 
  getLoveNotes,
  getMilestones, 
  createMilestone,
  createMemory,
  createLoveNote,
  getDreamLocations,
  updateHeroImage 
} from '../api';
import { useAuth } from '../context/AuthContext';
import { tapFeedback, successFeedback, errorFeedback } from '../utils/useHaptic';

import lisbonTripImg from '../assets/memories/lisbon_trip.jpg';
import sundayTogetherImg from '../assets/memories/sunday_together.jpg';
import mumbaiDayImg from '../assets/memories/mumbai_day.jpg';
import morningCoffeeImg from '../assets/memories/morning_coffee.jpg';
import cookingTogetherImg from '../assets/memories/cooking_together.jpg';

const FALLBACK_MEMORIES = [
  {
    _id: 'fb-lisbon',
    src: lisbonTripImg,
    title: 'Sunset in Lisbon',
    dateStr: 'Golden Hour Memories',
    category: 'Travel'
  },
  {
    _id: 'fb-sunday',
    src: sundayTogetherImg,
    title: 'Sunday Morning Light',
    dateStr: 'Lazy Weekends Together',
    category: 'Home'
  },
  {
    _id: 'fb-cooking',
    src: cookingTogetherImg,
    title: 'Homemade Pasta Night',
    dateStr: 'Laughs & Flour in the Kitchen',
    category: 'Cozy Moments'
  },
  {
    _id: 'fb-coffee',
    src: morningCoffeeImg,
    title: 'Morning Coffee Ritual',
    dateStr: 'Our Favorite Corner Cafe',
    category: 'Daily Love'
  },
  {
    _id: 'fb-mumbai',
    src: mumbaiDayImg,
    title: 'Streets of Mumbai',
    dateStr: 'Exploring the Old City',
    category: 'Adventures'
  }
];

const FALLBACK_MILESTONES = [
  {
    _id: 'ms-1',
    day: 1,
    label: 'Day 1',
    title: 'The Day Our Story Began',
    body: 'The spark that turned two separate paths into one beautiful, shared sanctuary.',
    icon: 'favorite'
  },
  {
    _id: 'ms-100',
    day: 100,
    label: 'Day 100',
    title: '100 Days of Smiles',
    body: 'A century of inside jokes, midnight conversations, and falling deeper in love.',
    icon: 'celebration'
  },
  {
    _id: 'ms-365',
    day: 365,
    label: '1 Year',
    title: '365 Days Around the Sun',
    body: 'Our first complete orbit together. Every season sweeter than the one before.',
    icon: 'local_florist'
  }
];

const FALLBACK_DREAMS = [
  {
    _id: 'dl-1',
    title: 'Amalfi Coast Road Trip',
    description: 'Winding cliffside drives, pastel villages, and swimming in secluded coves.',
    status: 'Dreaming',
    tag: 'Road Trip'
  },
  {
    _id: 'dl-2',
    title: 'Kyoto in Cherry Blossom Season',
    description: 'Walking hand in hand through the historic streets under soft pink canopies.',
    status: 'Dreaming',
    tag: 'Bucket List'
  },
  {
    _id: 'dl-3',
    title: 'Stargazing in Ladakh',
    description: 'Camping under a crystalline galaxy of stars with hot cocoa and cozy blankets.',
    status: 'Booked',
    tag: 'Upcoming'
  }
];

const STANDARD_MILESTONES = [30, 50, 100, 200, 300, 365, 500, 730, 1000, 1500, 2000, 2500, 3650];

const COUPLE_VIBES = [
  { id: 'in_love', emoji: '🥰', label: 'Deeply in love' },
  { id: 'missing_you', emoji: '🤍', label: 'Thinking of you' },
  { id: 'date_ready', emoji: '✨', label: 'Date night excited' },
  { id: 'cozy', emoji: '☕', label: 'Warm & cozy' },
  { id: 'blooming', emoji: '🌸', label: 'In full bloom' },
];

export default function JourneyPage() {
  const { couple, user } = useAuth();
  const { slug } = useParams();

  // State
  const [recentMemories, setRecentMemories] = useState([]);
  const [milestones, setMilestones] = useState([]);
  const [dreamLocations, setDreamLocations] = useState([]);
  const [dailyNote, setDailyNote] = useState(null);
  const [allNotes, setAllNotes] = useState([]);
  const [selectedMemory, setSelectedMemory] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [heroImage, setHeroImage] = useState(couple?.heroImageUrl || lisbonTripImg);
  const [isUploadingHero, setIsUploadingHero] = useState(false);
  const [toast, setToast] = useState(null);

  // Modals & Actions
  const [isNoteModalOpen, setIsNoteModalOpen] = useState(false);
  const [isMilestoneModalOpen, setIsMilestoneModalOpen] = useState(false);
  const [isMemoryModalOpen, setIsMemoryModalOpen] = useState(false);
  const [floatingHearts, setFloatingHearts] = useState([]);

  // New Milestone Form State
  const [newMilestoneTitle, setNewMilestoneTitle] = useState('');
  const [newMilestoneDay, setNewMilestoneDay] = useState('');
  const [newMilestoneBody, setNewMilestoneBody] = useState('');
  const [newMilestoneIcon, setNewMilestoneIcon] = useState('favorite');
  const [submittingMilestone, setSubmittingMilestone] = useState(false);

  // New Memory Form State
  const [memoryTitle, setMemoryTitle] = useState('');
  const [memoryDateStr, setMemoryDateStr] = useState('');
  const [memoryFile, setMemoryFile] = useState(null);
  const [memoryPreviewUrl, setMemoryPreviewUrl] = useState('');
  const [submittingMemory, setSubmittingMemory] = useState(false);

  // Today's Vibe State
  const [selectedVibe, setSelectedVibe] = useState(() => {
    try {
      return localStorage.getItem(`bloom_vibe_${slug}`) || 'in_love';
    } catch {
      return 'in_love';
    }
  });

  const heroFileInputRef = useRef(null);
  const memoryFileInputRef = useRef(null);

  // Live timer
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Update hero if couple changes
  useEffect(() => {
    if (couple?.heroImageUrl) {
      setHeroImage(couple.heroImageUrl);
    }
  }, [couple]);

  // Date calculations
  const startDate = useMemo(() => {
    const d = couple ? new Date(couple.startDate) : new Date();
    if (couple && couple.startTime) {
      const [h, m] = couple.startTime.split(':');
      d.setHours(parseInt(h) || 0, parseInt(m) || 0, 0);
    }
    return d;
  }, [couple]);

  const startTime = startDate.getTime();
  const diff = Math.max(0, now - startTime);

  const totalHours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor((diff / (1000 * 60 * 60)) % 24);
  const minutes = Math.floor((diff / 1000 / 60) % 60);
  const seconds = Math.floor((diff / 1000) % 60);

  const currentDate = new Date(now);
  const monthsDiff = (currentDate.getFullYear() - startDate.getFullYear()) * 12 + (currentDate.getMonth() - startDate.getMonth());
  const adjustedMonthsDiff = currentDate.getDate() < startDate.getDate() ? Math.max(0, monthsDiff - 1) : Math.max(0, monthsDiff);
  const monthText = adjustedMonthsDiff === 1 ? '1 Month' : `${adjustedMonthsDiff} Months`;

  // Milestone Progress
  const nextMilestoneDays = useMemo(() => {
    return STANDARD_MILESTONES.find(m => m > days) || (Math.floor(days / 500) + 1) * 500;
  }, [days]);

  const prevMilestoneDays = useMemo(() => {
    return [...STANDARD_MILESTONES].reverse().find(m => m <= days) || 0;
  }, [days]);

  const daysToNext = Math.max(1, nextMilestoneDays - days);
  const milestoneProgress = Math.min(100, Math.max(4, Math.round(((days - prevMilestoneDays) / Math.max(1, nextMilestoneDays - prevMilestoneDays)) * 100)));
  const nextMilestoneLabel = nextMilestoneDays === 365 
    ? '1 Year Anniversary' 
    : nextMilestoneDays === 730 
    ? '2 Year Anniversary' 
    : `${nextMilestoneDays} Days of Us`;

  // Partner names
  const partner = couple && user ? (
    couple.user1?._id === user._id ? couple.user2 : couple.user1
  ) : null;
  const myNicknameForPartner = user?.nicknameForPartner || partner?.name || 'My Love';
  const partnerNicknameForMe = partner?.nicknameForPartner || user?.name || 'My Darling';
  const userInitials = (user?.name || 'U').charAt(0).toUpperCase();
  const partnerInitials = (partner?.name || myNicknameForPartner || 'P').charAt(0).toUpperCase();

  // Load Data
  const loadDashboardData = async () => {
    if (!slug) return;
    try {
      const [memRes, noteRes, allNotesRes, msRes, dreamRes] = await Promise.allSettled([
        getMemories(slug),
        getDailyLoveNote(slug),
        getLoveNotes(slug),
        getMilestones(slug),
        getDreamLocations(slug)
      ]);

      if (memRes.status === 'fulfilled' && memRes.value.data?.length > 0) {
        const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
        const formatted = memRes.value.data.slice(0, 8).map((mem) => {
          const isLocal = mem.imageUrl.startsWith('/uploads');
          return {
            _id: mem._id,
            src: isLocal ? `${baseUrl}${mem.imageUrl}` : mem.imageUrl,
            title: mem.title || 'Untitled Memory',
            dateStr: mem.dateStr || 'Special Moment',
            category: 'Moment'
          };
        });
        setRecentMemories(formatted);
      }

      if (noteRes.status === 'fulfilled' && noteRes.value.data) {
        setDailyNote(noteRes.value.data);
      }

      if (allNotesRes.status === 'fulfilled' && allNotesRes.value.data) {
        setAllNotes(allNotesRes.value.data);
      }

      if (msRes.status === 'fulfilled' && msRes.value.data?.length > 0) {
        setMilestones(msRes.value.data);
      }

      if (dreamRes.status === 'fulfilled' && dreamRes.value.data?.length > 0) {
        setDreamLocations(dreamRes.value.data);
      }
    } catch (err) {
      console.error('Failed to load dashboard data', err);
    }
  };

  useEffect(() => {
    loadDashboardData();

    // Socket listeners for real-time updates
    const socketUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
    const socket = io(socketUrl, {
      auth: { token: user ? localStorage.getItem('bloom_token') : null, coupleSlug: slug }
    });

    socket.on('updateHeroImage', (newUrl) => {
      setHeroImage(newUrl);
    });

    socket.on('newNote', (note) => {
      setDailyNote({
        content: note.content,
        author: note.author,
        dateStr: 'Just now'
      });
      setAllNotes(prev => [note, ...prev]);
    });

    return () => socket.disconnect();
  }, [slug]);

  // Handle Hero Image Upload
  const handleHeroUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    tapFeedback();
    setIsUploadingHero(true);
    try {
      const formData = new FormData();
      formData.append('image', file);
      
      const { data } = await updateHeroImage(slug, formData);
      setHeroImage(data.heroImageUrl);
      setToast({ message: 'Sanctuary cover photo updated! ✨', type: 'success' });
      successFeedback();
    } catch (err) {
      console.error('Failed to upload hero image', err);
      setToast({ message: 'Could not upload cover picture', type: 'error' });
      errorFeedback();
    } finally {
      setIsUploadingHero(false);
    }
  };

  // Trigger floating hearts shower
  const handleSendLove = () => {
    tapFeedback();
    const emojis = ['❤️', '💖', '💕', '🥰', '✨', '💐', '💌'];
    const newHearts = Array.from({ length: 12 }, (_, i) => ({
      id: Date.now() + i,
      left: Math.random() * 84 + 8,
      size: Math.random() * 22 + 22,
      delay: Math.random() * 0.4,
      emoji: emojis[Math.floor(Math.random() * emojis.length)]
    }));

    setFloatingHearts(prev => [...prev, ...newHearts]);
    setToast({ message: `Sent your love to ${myNicknameForPartner}! 💕`, type: 'success' });

    setTimeout(() => {
      setFloatingHearts(prev => prev.filter(h => !newHearts.some(nh => nh.id === h.id)));
    }, 3200);
  };

  // Handle Mood/Vibe change
  const handleSelectVibe = (vibeId, vibeLabel) => {
    tapFeedback();
    setSelectedVibe(vibeId);
    try {
      localStorage.setItem(`bloom_vibe_${slug}`, vibeId);
    } catch {}
    setToast({ message: `Today's vibe: "${vibeLabel}" ✨`, type: 'success' });
  };

  // Submit Quick Love Note
  const handleQuickNoteSubmit = async ({ content, author }) => {
    try {
      const data = new FormData();
      data.append('content', content);
      data.append('author', author || user?.name || 'With love');
      const res = await createLoveNote(slug, data);
      setDailyNote({
        content: res.data.content,
        author: res.data.author,
        dateStr: 'Today'
      });
      setAllNotes(prev => [res.data, ...prev]);
      setToast({ message: 'Love note sealed & sent ♡', type: 'success' });
      successFeedback();
      setIsNoteModalOpen(false);
    } catch (err) {
      console.error('Failed to create love note', err);
      setToast({ message: 'Could not send note — try again', type: 'error' });
      errorFeedback();
    }
  };

  // Submit Milestone
  const handleCreateMilestone = async (e) => {
    e.preventDefault();
    if (!newMilestoneTitle.trim()) return;
    setSubmittingMilestone(true);
    tapFeedback();

    try {
      const calculatedDay = parseInt(newMilestoneDay) || Math.max(1, days);
      const data = {
        day: calculatedDay,
        label: `Day ${calculatedDay}`,
        title: newMilestoneTitle.trim(),
        body: newMilestoneBody.trim() || `Celebrated our milestone together on Day ${calculatedDay}.`,
        icon: newMilestoneIcon,
        colorScheme: 'primary'
      };

      const res = await createMilestone(slug, data);
      setMilestones(prev => [...prev, res.data].sort((a, b) => a.day - b.day));
      setNewMilestoneTitle('');
      setNewMilestoneDay('');
      setNewMilestoneBody('');
      setIsMilestoneModalOpen(false);
      setToast({ message: 'Milestone etched into your journey! 🎯', type: 'success' });
      successFeedback();
    } catch (err) {
      console.error('Failed to save milestone', err);
      setToast({ message: 'Could not create milestone', type: 'error' });
      errorFeedback();
    } finally {
      setSubmittingMilestone(false);
    }
  };

  // Submit Memory
  const handleCreateMemory = async (e) => {
    e.preventDefault();
    if (!memoryFile || !memoryTitle.trim()) return;
    setSubmittingMemory(true);
    tapFeedback();

    try {
      const formData = new FormData();
      formData.append('image', memoryFile);
      formData.append('title', memoryTitle.trim());
      formData.append('dateStr', memoryDateStr.trim() || new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }));

      const res = await createMemory(slug, formData);
      const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
      const isLocal = res.data.imageUrl.startsWith('/uploads');
      const newMem = {
        _id: res.data._id,
        src: isLocal ? `${baseUrl}${res.data.imageUrl}` : res.data.imageUrl,
        title: res.data.title,
        dateStr: res.data.dateStr,
        category: 'Moment'
      };

      setRecentMemories(prev => [newMem, ...prev]);
      setMemoryTitle('');
      setMemoryDateStr('');
      setMemoryFile(null);
      setMemoryPreviewUrl('');
      setIsMemoryModalOpen(false);
      setToast({ message: 'New memory saved to your vault! 📸', type: 'success' });
      successFeedback();
    } catch (err) {
      console.error('Failed to upload memory', err);
      setToast({ message: 'Could not save memory', type: 'error' });
      errorFeedback();
    } finally {
      setSubmittingMemory(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setMemoryFile(file);
      setMemoryPreviewUrl(URL.createObjectURL(file));
    }
  };

  // Display collections with rich fallbacks so couple's space NEVER feels empty
  const displayMemories = recentMemories.length > 0 ? recentMemories : FALLBACK_MEMORIES;
  const displayMilestones = milestones.length > 0 ? milestones : FALLBACK_MILESTONES;
  const displayDreams = dreamLocations.length > 0 ? dreamLocations : FALLBACK_DREAMS;

  const formattedStartDate = startDate.toLocaleDateString('en-US', { 
    month: 'long', 
    day: 'numeric', 
    year: 'numeric' 
  });

  return (
    <PullToRefresh onRefresh={loadDashboardData}>
      <div className="relative min-h-screen text-[#0A192F] pb-16">
        
        {/* Floating Heart Reactions Canvas */}
        <div className="fixed inset-0 pointer-events-none z-[120] overflow-hidden">
          {floatingHearts.map((heart) => (
            <div
              key={heart.id}
              className="absolute bottom-16 animate-float-up-fade"
              style={{
                left: `${heart.left}%`,
                fontSize: `${heart.size}px`,
                animationDelay: `${heart.delay}s`
              }}
            >
              {heart.emoji}
            </div>
          ))}
        </div>

        {/* Outer Container */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
          
          {/* ============================================================ */}
          {/* 1. COUPLE SANCTUARY HERO BANNER (Intimate, Cinematic, Vibrant) */}
          {/* ============================================================ */}
          <div className="relative rounded-3xl overflow-hidden shadow-xl border border-slate-200/90 bg-[#0A192F] text-white">
            {/* Background Cover Image with Rich Gradient Tint */}
            <div className="absolute inset-0">
              <img
                src={heroImage}
                alt="Sanctuary Cover"
                className="w-full h-full object-cover opacity-60 scale-100 hover:scale-105 transition-transform duration-1000 ease-out"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#0A192F] via-[#0A192F]/65 to-black/30" />
              <div className="absolute inset-0 bg-radial-gradient from-transparent to-[#0A192F]/60" />
            </div>

            {/* Change Cover Photo Button */}
            <div className="absolute top-4 right-4 sm:top-6 sm:right-6 z-20">
              <button
                onClick={() => heroFileInputRef.current?.click()}
                disabled={isUploadingHero}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-full bg-black/40 hover:bg-black/60 backdrop-blur-md border border-white/20 text-xs font-medium text-white transition-all shadow-md active:scale-95"
                title="Change cover picture"
              >
                <span className={`material-symbols-outlined text-[16px] ${isUploadingHero ? 'animate-spin' : ''}`}>
                  {isUploadingHero ? 'sync' : 'photo_camera'}
                </span>
                <span className="hidden sm:inline">Change Cover</span>
              </button>
              <input
                type="file"
                ref={heroFileInputRef}
                onChange={handleHeroUpload}
                accept="image/*"
                className="hidden"
              />
            </div>

            {/* Hero Main Content */}
            <div className="relative z-10 px-6 sm:px-10 pt-10 pb-8 sm:pb-10 flex flex-col justify-between min-h-[360px] sm:min-h-[400px]">
              
              {/* Top Row: Live Connection Indicator */}
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/10 backdrop-blur-md border border-white/15 text-xs font-medium text-slate-200 shadow-sm">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                  <span>Private Couple Sanctuary</span>
                  <span className="text-white/40">•</span>
                  <span className="text-blue-300 font-semibold">{days} Days Strong</span>
                </div>

                <div className="text-xs text-slate-300 font-medium hidden md:flex items-center gap-1.5 bg-black/30 backdrop-blur-sm px-3 py-1.5 rounded-full border border-white/10">
                  <span className="material-symbols-outlined text-[15px] text-pink-400">favorite</span>
                  <span>Since {formattedStartDate}</span>
                </div>
              </div>

              {/* Center Content: Partner Badges & Names */}
              <div className="my-auto py-6 space-y-4 max-w-3xl">
                
                {/* Overlapping Partner Discs */}
                <div className="flex items-center gap-3">
                  <div className="flex -space-x-3 items-center">
                    <div className="w-12 h-12 sm:w-14 sm:h-14 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 border-2 border-white flex items-center justify-center text-white font-bold text-lg sm:text-xl shadow-lg">
                      {userInitials}
                    </div>
                    <div className="w-12 h-12 sm:w-14 sm:h-14 rounded-full bg-gradient-to-tr from-rose-500 to-pink-500 border-2 border-white flex items-center justify-center text-white font-bold text-lg sm:text-xl shadow-lg">
                      {partnerInitials}
                    </div>
                  </div>
                  <div className="w-8 h-8 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center border border-white/30 text-rose-300 animate-pulse">
                    <span className="material-symbols-outlined text-[18px]">favorite</span>
                  </div>
                </div>

                {/* Romantic Headline */}
                <div>
                  <h1 className="text-2xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-white leading-tight">
                    {partnerNicknameForMe || user?.name || 'My Love'} &amp; {myNicknameForPartner}
                  </h1>
                  <p className="mt-2 text-sm sm:text-base text-slate-300 max-w-2xl font-light leading-relaxed">
                    {couple?.specialPhrase || `Every day with you is our favorite chapter. Forever growing, blooming, and loving without limits.`}
                  </p>
                </div>
              </div>

              {/* Bottom Row: Quick Interactive Action Buttons */}
              <div className="flex flex-wrap items-center justify-between gap-4 pt-4 border-t border-white/10">
                <div className="flex items-center gap-2 text-xs sm:text-sm text-slate-300 font-medium">
                  <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-blue-500/20 text-blue-300">
                    <span className="material-symbols-outlined text-[14px]">auto_awesome</span>
                  </span>
                  <span>Happy {monthText} together</span>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={handleSendLove}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-rose-500 to-pink-600 hover:from-rose-600 hover:to-pink-700 text-white font-medium text-sm transition-all shadow-lg shadow-rose-500/25 active:scale-95 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px]">favorite</span>
                    <span>Send Love ❤️</span>
                  </button>

                  <Link
                    to={`/c/${slug}/memories`}
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/15 hover:bg-white/25 backdrop-blur-md text-white font-medium text-sm transition-all border border-white/20 active:scale-95"
                  >
                    <span className="material-symbols-outlined text-[18px]">photo_library</span>
                    <span className="hidden sm:inline">Our Gallery</span>
                  </Link>
                </div>
              </div>

            </div>
          </div>

          {/* ============================================================ */}
          {/* 2. COUPLE QUICK ACTION COMMAND DOCK */}
          {/* ============================================================ */}
          <div className="bg-white rounded-2xl p-3 sm:p-4 shadow-sm border border-slate-200/80">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-3">
              
              {/* Action 1: Add Memory */}
              <button
                onClick={() => setIsMemoryModalOpen(true)}
                className="flex items-center justify-center gap-2.5 p-3 rounded-xl bg-slate-50 hover:bg-blue-50/80 hover:text-blue-700 border border-slate-100 transition-all text-xs sm:text-sm font-semibold text-slate-700 active:scale-[0.98] group cursor-pointer"
              >
                <span className="w-8 h-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center group-hover:bg-blue-600 group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-[18px]">add_a_photo</span>
                </span>
                <span>Add Memory</span>
              </button>

              {/* Action 2: Write Love Note */}
              <button
                onClick={() => setIsNoteModalOpen(true)}
                className="flex items-center justify-center gap-2.5 p-3 rounded-xl bg-slate-50 hover:bg-rose-50/80 hover:text-rose-700 border border-slate-100 transition-all text-xs sm:text-sm font-semibold text-slate-700 active:scale-[0.98] group cursor-pointer"
              >
                <span className="w-8 h-8 rounded-lg bg-rose-100 text-rose-600 flex items-center justify-center group-hover:bg-rose-600 group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-[18px]">edit_note</span>
                </span>
                <span>Write Love Note</span>
              </button>

              {/* Action 3: New Milestone */}
              <button
                onClick={() => setIsMilestoneModalOpen(true)}
                className="flex items-center justify-center gap-2.5 p-3 rounded-xl bg-slate-50 hover:bg-amber-50/80 hover:text-amber-700 border border-slate-100 transition-all text-xs sm:text-sm font-semibold text-slate-700 active:scale-[0.98] group cursor-pointer"
              >
                <span className="w-8 h-8 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center group-hover:bg-amber-600 group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-[18px]">flag</span>
                </span>
                <span>New Milestone</span>
              </button>

              {/* Action 4: Dream Map */}
              <Link
                to={`/c/${slug}/map`}
                className="flex items-center justify-center gap-2.5 p-3 rounded-xl bg-slate-50 hover:bg-emerald-50/80 hover:text-emerald-700 border border-slate-100 transition-all text-xs sm:text-sm font-semibold text-slate-700 active:scale-[0.98] group"
              >
                <span className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center group-hover:bg-emerald-600 group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-[18px]">pin_drop</span>
                </span>
                <span>Dream Map ✨</span>
              </Link>

            </div>
          </div>

          {/* ============================================================ */}
          {/* 3. TODAY'S COUPLE VIBE SELECTOR */}
          {/* ============================================================ */}
          <div className="bg-gradient-to-r from-blue-50/70 via-indigo-50/40 to-slate-50 rounded-2xl p-4 sm:p-5 border border-blue-100 shadow-xs">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 mb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-blue-600 text-[20px]">favorite_border</span>
                <h3 className="text-sm font-bold text-slate-800">Today's Couple Pulse</h3>
                <span className="text-xs text-slate-500">— How is your heart feeling today?</span>
              </div>
              <span className="text-[11px] font-medium text-slate-400">Tap to share vibe with {myNicknameForPartner}</span>
            </div>

            <div className="flex flex-wrap gap-2">
              {COUPLE_VIBES.map((v) => {
                const isActive = selectedVibe === v.id;
                return (
                  <button
                    key={v.id}
                    onClick={() => handleSelectVibe(v.id, v.label)}
                    className={`inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium transition-all active:scale-95 cursor-pointer ${
                      isActive
                        ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/30'
                        : 'bg-white hover:bg-slate-100 text-slate-700 border border-slate-200/80'
                    }`}
                  >
                    <span>{v.emoji}</span>
                    <span>{v.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* ============================================================ */}
          {/* 4. DENSE, VIBRANT BENTO SANCTUARY GRID */}
          {/* ============================================================ */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            
            {/* Card 1: Real-Time Heartbeat & Time Together (Col 12 / Lg 5) */}
            <div className="lg:col-span-5 bg-[#0A192F] text-white rounded-3xl p-6 sm:p-8 shadow-md border border-slate-800 relative overflow-hidden flex flex-col justify-between min-h-[380px]">
              
              {/* Subtle Ambient Radial Glow */}
              <div className="absolute -top-20 -right-20 w-56 h-56 bg-blue-600/20 rounded-full blur-3xl pointer-events-none" />
              <div className="absolute -bottom-20 -left-20 w-56 h-56 bg-indigo-600/15 rounded-full blur-3xl pointer-events-none" />

              <div>
                {/* Header */}
                <div className="flex items-center justify-between pb-4 border-b border-white/10">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-lg bg-white/10 flex items-center justify-center text-rose-400">
                      <span className="material-symbols-outlined text-[18px]">favorite</span>
                    </span>
                    <div>
                      <h3 className="text-sm font-bold text-white tracking-wide uppercase">Relationship Clock</h3>
                      <p className="text-[11px] text-slate-400">Ticking live every second</p>
                    </div>
                  </div>
                  <span className="text-xs px-2.5 py-1 rounded-full bg-blue-500/20 text-blue-300 font-semibold border border-blue-400/30">
                    Day {days}
                  </span>
                </div>

                {/* 4-Unit Counter Grid */}
                <div className="grid grid-cols-4 gap-2 sm:gap-3 my-6 text-center">
                  <div className="p-3 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
                    <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">{days}</div>
                    <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider mt-1">Days</div>
                  </div>
                  <div className="p-3 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
                    <div className="text-2xl sm:text-3xl font-extrabold text-blue-300 tracking-tight">{hours.toString().padStart(2, '0')}</div>
                    <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider mt-1">Hours</div>
                  </div>
                  <div className="p-3 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
                    <div className="text-2xl sm:text-3xl font-extrabold text-blue-300 tracking-tight">{minutes.toString().padStart(2, '0')}</div>
                    <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider mt-1">Mins</div>
                  </div>
                  <div className="p-3 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
                    <div className="text-2xl sm:text-3xl font-extrabold text-rose-400 tracking-tight">{seconds.toString().padStart(2, '0')}</div>
                    <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider mt-1">Secs</div>
                  </div>
                </div>

                {/* Milestone Progress Bar */}
                <div className="space-y-2 p-4 rounded-2xl bg-white/5 border border-white/10">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-medium text-slate-300">Road to {nextMilestoneLabel}</span>
                    <span className="font-bold text-blue-400">{daysToNext} days left</span>
                  </div>
                  <div className="w-full h-2.5 rounded-full bg-white/10 overflow-hidden">
                    <div 
                      className="h-full bg-gradient-to-r from-blue-500 to-indigo-400 rounded-full transition-all duration-500"
                      style={{ width: `${milestoneProgress}%` }}
                    />
                  </div>
                  <div className="flex justify-between text-[11px] text-slate-400 font-medium pt-1">
                    <span>Day {prevMilestoneDays}</span>
                    <span>{milestoneProgress}% Completed</span>
                    <span>Day {nextMilestoneDays}</span>
                  </div>
                </div>
              </div>

              {/* Card Footer */}
              <div className="pt-4 border-t border-white/10 flex items-center justify-between text-xs text-slate-400">
                <span>{totalHours.toLocaleString()} Total Hours of Love</span>
                <span className="text-emerald-400 font-medium">Synchronized</span>
              </div>

            </div>

            {/* Card 2: Today's Daily Love Note / Letterbox (Col 12 / Lg 7) */}
            <div className="lg:col-span-7 bg-white rounded-3xl p-6 sm:p-8 shadow-sm border border-slate-200/80 flex flex-col justify-between min-h-[380px] relative overflow-hidden">
              
              {/* Subtle Letterbox Stamp / Emblem */}
              <div className="absolute top-4 right-4 sm:top-6 sm:right-6 opacity-10 pointer-events-none">
                <span className="material-symbols-outlined text-[100px] text-rose-500">mark_email_unread</span>
              </div>

              <div>
                {/* Header */}
                <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">mail</span>
                    </span>
                    <div>
                      <h3 className="text-sm font-bold text-slate-900 tracking-wide uppercase">Today's Daily Love Note</h3>
                      <p className="text-[11px] text-slate-500">
                        {dailyNote?.dateStr || new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
                      </p>
                    </div>
                  </div>

                  <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-rose-50 text-rose-600 text-xs font-semibold">
                    <span className="material-symbols-outlined text-[14px]">auto_awesome</span>
                    <span>Daily Letter</span>
                  </span>
                </div>

                {/* Love Note Body */}
                <div className="my-6 relative pl-6 border-l-2 border-rose-300">
                  <span className="material-symbols-outlined absolute -left-3 -top-2 text-rose-400 text-lg bg-white rounded-full">
                    format_quote
                  </span>
                  <p className="text-base sm:text-lg text-slate-800 font-serif italic leading-relaxed">
                    "{dailyNote?.content || `Whatever our souls are made of, yours and mine are the exact same. Thank you for making every ordinary day feel extraordinary.`}"
                  </p>
                  <div className="mt-4 flex items-center gap-2 text-xs font-medium text-slate-500">
                    <span>— Forever yours,</span>
                    <span className="text-rose-600 font-semibold">{dailyNote?.author || myNicknameForPartner}</span>
                  </div>
                </div>
              </div>

              {/* Note Action Footer */}
              <div className="pt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
                <div className="text-xs text-slate-500">
                  {allNotes.length > 0 ? `${allNotes.length} notes in your love archive` : 'Your love archive is blooming'}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setIsNoteModalOpen(true)}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs transition-all shadow-sm active:scale-95 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[16px]">reply</span>
                    <span>Reply with Note</span>
                  </button>

                  <Link
                    to={`/c/${slug}/love-notes`}
                    className="inline-flex items-center gap-1 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition-all"
                  >
                    <span>View All</span>
                    <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                  </Link>
                </div>
              </div>

            </div>

            {/* Card 3: Journey Chapters & Milestones (Col 12 / Lg 7) */}
            <div className="lg:col-span-7 bg-white rounded-3xl p-6 sm:p-8 shadow-sm border border-slate-200/80 space-y-6">
              
              {/* Header */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <span className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[18px]">emoji_events</span>
                  </span>
                  <div>
                    <h3 className="text-sm font-bold text-slate-900 tracking-wide uppercase">Journey Milestones</h3>
                    <p className="text-[11px] text-slate-500">Key chapters etched into your story</p>
                  </div>
                </div>

                <button
                  onClick={() => setIsMilestoneModalOpen(true)}
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-800 font-semibold text-xs transition-colors cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[15px]">add</span>
                  <span>Add Chapter</span>
                </button>
              </div>

              {/* Milestones Timeline */}
              <div className="space-y-4">
                {displayMilestones.slice(0, 4).map((ms, idx) => (
                  <div
                    key={ms._id || idx}
                    className="flex items-start gap-4 p-4 rounded-2xl bg-slate-50/70 hover:bg-blue-50/40 border border-slate-100 transition-colors"
                  >
                    <div className="w-10 h-10 rounded-xl bg-white shadow-xs border border-slate-200/70 flex items-center justify-center text-blue-600 shrink-0 mt-0.5">
                      <span className="material-symbols-outlined text-[20px]">{ms.icon || 'star'}</span>
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <h4 className="text-sm font-bold text-slate-900 truncate">{ms.title}</h4>
                        <span className="text-[11px] font-semibold px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-700 shrink-0">
                          {ms.label || `Day ${ms.day}`}
                        </span>
                      </div>
                      <p className="text-xs text-slate-600 mt-1 line-clamp-2 leading-relaxed">
                        {ms.body}
                      </p>
                    </div>
                  </div>
                ))}
              </div>

              {/* Add Custom Prompt */}
              <div className="text-center pt-2">
                <button
                  onClick={() => setIsMilestoneModalOpen(true)}
                  className="text-xs text-blue-600 hover:text-blue-700 font-medium inline-flex items-center gap-1 hover:underline cursor-pointer"
                >
                  <span>+ Create an upcoming anniversary or relationship goal</span>
                </button>
              </div>

            </div>

            {/* Card 4: Dream Destinations & Travel Bucket List (Col 12 / Lg 5) */}
            <div className="lg:col-span-5 bg-white rounded-3xl p-6 sm:p-8 shadow-sm border border-slate-200/80 space-y-6">
              
              {/* Header */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <span className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[18px]">travel_explore</span>
                  </span>
                  <div>
                    <h3 className="text-sm font-bold text-slate-900 tracking-wide uppercase">Dream Destinations</h3>
                    <p className="text-[11px] text-slate-500">Shared bucket list to explore together</p>
                  </div>
                </div>

                <Link
                  to={`/c/${slug}/map`}
                  className="text-xs font-semibold text-emerald-700 hover:text-emerald-800 inline-flex items-center gap-1"
                >
                  <span>Dream Map</span>
                  <span className="material-symbols-outlined text-[14px]">open_in_new</span>
                </Link>
              </div>

              {/* Bucket List Items */}
              <div className="space-y-3">
                {displayDreams.slice(0, 3).map((item, idx) => (
                  <div
                    key={item._id || idx}
                    className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 hover:border-emerald-200 transition-all"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <h4 className="text-xs sm:text-sm font-bold text-slate-900 truncate">{item.title}</h4>
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full shrink-0 ${
                        item.status === 'Visited'
                          ? 'bg-emerald-100 text-emerald-700'
                          : item.status === 'Booked'
                          ? 'bg-blue-100 text-blue-700'
                          : 'bg-purple-100 text-purple-700'
                      }`}>
                        {item.status || 'Dreaming'}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 line-clamp-2">
                      {item.description}
                    </p>
                  </div>
                ))}
              </div>

              {/* Open Map CTA */}
              <Link
                to={`/c/${slug}/map`}
                className="w-full py-3 rounded-xl bg-emerald-50 hover:bg-emerald-100/80 text-emerald-800 text-xs font-semibold flex items-center justify-center gap-2 transition-colors"
              >
                <span className="material-symbols-outlined text-[16px]">pin_drop</span>
                <span>Open Interactive Couple Map</span>
              </Link>

            </div>

            {/* Card 5: Cherished Moments Reel (Col 12 - Full Width Gallery) */}
            <div className="lg:col-span-12 bg-white rounded-3xl p-6 sm:p-8 shadow-sm border border-slate-200/80 space-y-6">
              
              {/* Header */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">photo_camera</span>
                    </span>
                    <h3 className="text-base font-bold text-slate-900 tracking-wide">Cherished Moments</h3>
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5">Capturing the genuine essence of your days together</p>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => setIsMemoryModalOpen(true)}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-medium text-xs transition-all shadow-sm active:scale-95 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[16px]">add_photo_alternate</span>
                    <span>Upload Moment</span>
                  </button>

                  <Link
                    to={`/c/${slug}/memories`}
                    className="inline-flex items-center gap-1 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition-colors"
                  >
                    <span>View All Gallery</span>
                    <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                  </Link>
                </div>
              </div>

              {/* Photo Cards Grid */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 sm:gap-6">
                {displayMemories.slice(0, 4).map((mem) => (
                  <div
                    key={mem._id}
                    onClick={() => setSelectedMemory(mem)}
                    className="group relative rounded-2xl overflow-hidden aspect-[4/5] bg-slate-100 shadow-xs border border-slate-200/80 cursor-pointer hover:shadow-lg transition-all duration-300 hover:-translate-y-1"
                  >
                    <img
                      src={mem.src}
                      alt={mem.title}
                      loading="lazy"
                      className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                    />

                    {/* Gradient Overlay */}
                    <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-80 group-hover:opacity-90 transition-opacity" />

                    {/* Badge */}
                    <div className="absolute top-3 left-3">
                      <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-black/40 backdrop-blur-md text-white border border-white/20">
                        {mem.category || 'Memory'}
                      </span>
                    </div>

                    {/* Content */}
                    <div className="absolute inset-x-3 bottom-3 text-white">
                      <h4 className="text-xs sm:text-sm font-bold truncate leading-tight">{mem.title}</h4>
                      <p className="text-[10px] text-slate-300 mt-0.5 truncate">{mem.dateStr}</p>
                    </div>

                    {/* Hover Zoom Icon */}
                    <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
                      <span className="w-10 h-10 rounded-full bg-white/30 backdrop-blur-md flex items-center justify-center text-white border border-white/40 shadow-md">
                        <span className="material-symbols-outlined text-[20px]">zoom_in</span>
                      </span>
                    </div>
                  </div>
                ))}
              </div>

            </div>

            {/* Card 6: Keepsake Love Vault Pledge (Col 12) */}
            <div className="lg:col-span-12 rounded-3xl p-8 sm:p-10 bg-gradient-to-br from-slate-900 via-[#0A192F] to-[#0F1E36] text-white shadow-xl relative overflow-hidden border border-slate-800 text-center">
              
              <div className="max-w-2xl mx-auto space-y-4 relative z-10">
                <span className="material-symbols-outlined text-rose-400 text-3xl">favorite</span>
                
                <h3 className="text-lg sm:text-xl font-bold tracking-tight text-white">
                  Our Sacred Sanctuary
                </h3>

                <p className="text-sm sm:text-base text-slate-300 font-serif italic leading-relaxed">
                  "{couple?.specialPhrase || 'Every moment with you is a moment I treasure. Here is to forever growing, blooming, and writing our greatest adventure.'}"
                </p>

                <div className="w-16 h-0.5 bg-rose-500/50 mx-auto" />

                <p className="text-xs uppercase tracking-widest text-slate-400 font-semibold">
                  Forever &amp; Always • {partnerNicknameForMe || user?.name} &amp; {myNicknameForPartner}
                </p>

                <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/10 text-[11px] text-slate-300 border border-white/10 mt-2">
                  <span className="material-symbols-outlined text-[13px] text-emerald-400">lock</span>
                  <span>Private, end-to-end protected couple vault</span>
                </div>
              </div>

            </div>

          </div>

        </div>

        {/* ============================================================ */}
        {/* MODAL 1: WRITE LOVE NOTE MODAL */}
        {/* ============================================================ */}
        <NoteModal
          isOpen={isNoteModalOpen}
          onClose={() => setIsNoteModalOpen(false)}
          onSubmit={handleQuickNoteSubmit}
        />

        {/* ============================================================ */}
        {/* MODAL 2: ADD MILESTONE MODAL */}
        {/* ============================================================ */}
        {isMilestoneModalOpen && (
          <div 
            className="fixed inset-0 z-[110] flex items-center justify-center p-4 modal-overlay"
            onClick={(e) => e.target === e.currentTarget && setIsMilestoneModalOpen(false)}
          >
            <div className="bg-white rounded-3xl w-full max-w-md p-6 sm:p-8 shadow-2xl border border-slate-200 animate-[slideInUp_0.3s_ease-out]">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-amber-600 text-2xl">flag</span>
                  <h3 className="text-lg font-bold text-slate-900">Add Milestone</h3>
                </div>
                <button
                  onClick={() => setIsMilestoneModalOpen(false)}
                  className="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-600"
                >
                  <span className="material-symbols-outlined text-sm">close</span>
                </button>
              </div>

              <form onSubmit={handleCreateMilestone} className="space-y-4 mt-4">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                    Milestone Title
                  </label>
                  <input
                    type="text"
                    required
                    value={newMilestoneTitle}
                    onChange={(e) => setNewMilestoneTitle(e.target.value)}
                    placeholder="e.g. First Trip to the Beach, Moved In Together"
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                      Relationship Day #
                    </label>
                    <input
                      type="number"
                      value={newMilestoneDay}
                      onChange={(e) => setNewMilestoneDay(e.target.value)}
                      placeholder={`Current: ${days}`}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                      Icon Symbol
                    </label>
                    <select
                      value={newMilestoneIcon}
                      onChange={(e) => setNewMilestoneIcon(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                    >
                      <option value="favorite">❤️ Heart</option>
                      <option value="celebration">🎉 Celebration</option>
                      <option value="flight">✈️ Flight / Travel</option>
                      <option value="home">🏡 Home</option>
                      <option value="local_florist">🌸 Blossom</option>
                      <option value="emoji_events">🏆 Trophy</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                    Story / Notes
                  </label>
                  <textarea
                    rows={3}
                    value={newMilestoneBody}
                    onChange={(e) => setNewMilestoneBody(e.target.value)}
                    placeholder="Write a sweet memory or note about this special milestone..."
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                  />
                </div>

                <button
                  type="submit"
                  disabled={submittingMilestone || !newMilestoneTitle.trim()}
                  className="w-full py-3 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:bg-slate-300 text-white font-semibold text-sm transition-all shadow-sm cursor-pointer"
                >
                  {submittingMilestone ? 'Saving Milestone...' : 'Save Milestone'}
                </button>
              </form>
            </div>
          </div>
        )}

        {/* ============================================================ */}
        {/* MODAL 3: ADD MEMORY MODAL */}
        {/* ============================================================ */}
        {isMemoryModalOpen && (
          <div 
            className="fixed inset-0 z-[110] flex items-center justify-center p-4 modal-overlay"
            onClick={(e) => e.target === e.currentTarget && setIsMemoryModalOpen(false)}
          >
            <div className="bg-white rounded-3xl w-full max-w-md p-6 sm:p-8 shadow-2xl border border-slate-200 animate-[slideInUp_0.3s_ease-out]">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-blue-600 text-2xl">add_photo_alternate</span>
                  <h3 className="text-lg font-bold text-slate-900">Add Cherished Memory</h3>
                </div>
                <button
                  onClick={() => setIsMemoryModalOpen(false)}
                  className="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-600"
                >
                  <span className="material-symbols-outlined text-sm">close</span>
                </button>
              </div>

              <form onSubmit={handleCreateMemory} className="space-y-4 mt-4">
                {/* File picker */}
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                    Select Photograph
                  </label>
                  <input
                    type="file"
                    ref={memoryFileInputRef}
                    onChange={handleFileChange}
                    accept="image/*"
                    required
                    className="hidden"
                  />
                  
                  {memoryPreviewUrl ? (
                    <div 
                      onClick={() => memoryFileInputRef.current?.click()}
                      className="relative h-44 rounded-2xl overflow-hidden border border-slate-200 cursor-pointer group"
                    >
                      <img src={memoryPreviewUrl} alt="Preview" className="w-full h-full object-cover" />
                      <div className="absolute inset-0 bg-black/40 flex items-center justify-center text-white text-xs font-semibold opacity-0 group-hover:opacity-100 transition-opacity">
                        Tap to change photo
                      </div>
                    </div>
                  ) : (
                    <div
                      onClick={() => memoryFileInputRef.current?.click()}
                      className="h-36 rounded-2xl border-2 border-dashed border-slate-300 hover:border-blue-400 bg-slate-50 hover:bg-blue-50/50 flex flex-col items-center justify-center cursor-pointer transition-colors p-4 text-center"
                    >
                      <span className="material-symbols-outlined text-3xl text-slate-400 mb-1">cloud_upload</span>
                      <span className="text-xs font-semibold text-slate-700">Click to choose image</span>
                      <span className="text-[10px] text-slate-400 mt-0.5">JPG, PNG, WEBP</span>
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                    Caption / Memory Title
                  </label>
                  <input
                    type="text"
                    required
                    value={memoryTitle}
                    onChange={(e) => setMemoryTitle(e.target.value)}
                    placeholder="e.g. Picnic by the lake"
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-600 mb-1">
                    Date or Location (optional)
                  </label>
                  <input
                    type="text"
                    value={memoryDateStr}
                    onChange={(e) => setMemoryDateStr(e.target.value)}
                    placeholder="e.g. October 14, 2024 • Goa"
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <button
                  type="submit"
                  disabled={submittingMemory || !memoryFile || !memoryTitle.trim()}
                  className="w-full py-3 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white font-semibold text-sm transition-all shadow-sm cursor-pointer"
                >
                  {submittingMemory ? 'Uploading to Vault...' : 'Save to Memories'}
                </button>
              </form>
            </div>
          </div>
        )}

        {/* Lightbox for viewing photos */}
        {selectedMemory && (
          <Lightbox
            imageSrc={selectedMemory.src}
            title={selectedMemory.title}
            onClose={() => setSelectedMemory(null)}
          />
        )}

        {/* Toast Notifications */}
        {toast && (
          <Toast
            message={toast.message}
            type={toast.type}
            onClose={() => setToast(null)}
          />
        )}

      </div>
    </PullToRefresh>
  );
}
