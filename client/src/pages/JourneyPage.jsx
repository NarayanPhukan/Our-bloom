import { useState, useEffect, useRef } from 'react';
import { Link, useParams } from 'react-router-dom';
import { io } from 'socket.io-client';
import Polaroid from '../components/Polaroid';
import Lightbox from '../components/Lightbox';
import { getMemories, getDailyLoveNote, updateHeroImage } from '../api';
import { useAuth } from '../context/AuthContext';

const FALLBACK_MEMORIES = [
  {
    _id: 'fb1',
    src: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAWUZLoXj3sNZUSCxPb7R8XPKCxJf0i0exkVQbAWE5GgcENkLCO8UIAg38PaJn0IVijmkGyPHb-e6-rUe-kIP1-g-lSFKsw8qargFAPZu8liUOIPoun--_Xo69GO6xx3I-z9ovwcHEX9AAYRdjwnNrLpv3EMx2A3SGEhHsE1gWZZs7hSSXx-HosDvLEo-_fKyP5qk5OLoH7sP_rBQOLsHZgLh8U9MSVylMkzn1kJfv55RYOModlY60sgdVGvP7WcVLwbQXmhfQ4Qmp-',
    alt: 'Vintage coffee cups',
    className: 'h-64 rotate-[-2deg] hover:rotate-0'
  },
  {
    _id: 'fb2',
    src: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC27HTkOughqbwxhCpj80gl0MERm4ENkBaTgBlV1jMta8F7Sl-gKjcKJUC9QzvUKffUGITvYyZJzQcRUyC5kKrt7aNMWOKFmpIHAzFo2Pk7T67UNBFshn-E8WFPCZS-M_fjWHnPuRyP3grvCqyEN9Mgbw3ltazcbWrmTPIsunmI6AUGIVJLTNeTlfsi8mE7tImc-9NWxCuVEwQ2vI7-jqWzdtzB1M7h7T3k2aHG-3KD8WyZ0C3_EPFzjwbAkPklaIwx-EKjivNYAmzw',
    alt: "Two people's feet on cobblestone",
    className: 'h-64 rotate-[3deg] hover:rotate-0 mt-4'
  },
  {
    _id: 'fb3',
    src: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBw0NsgfDJgEB4oZMysKx1sd6sCEzLNGfyQj8iXm-L6lI8prdWquHMFNyl2_jPdEeYPrOFnHqk8JZLRlqPVj5x5WlnDsbyj4lne9sOkLJ79HD9ZZzUnkfMfe_9lMPidsXQOxOv8QKe2a3f2CHMAvZEDtXFsP-3rA8e1u961NDK5XSCiWnaDNonoQAfATEoD0Bzovxabw_O6yozXKd7SzEJWTvAZsj_cMiZHHsRcobf21kr1fKZy9605WYJQVx60tiV-jS_aJbCT04vX',
    alt: 'Park bench autumn',
    className: 'h-64 rotate-[-1deg] hover:rotate-0'
  },
  {
    _id: 'fb4',
    src: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAx41K94YOH8SZcM2Ezr0-8QVGj20Z6YpVAO31tp--_BiEVl5XXsMTeM5VvqyKZBCJWLoZyZQWhwwE2AtsOUZgwpltKga5Ju80izeObo93UNisvfE4pwglYB5ro_g_5HoAlS-DId87yITFvIJGRjhfdoUxdj3UFtdZ7G8D_USHorec_KK_AEvH4mfUzm2ZO2DHmMXMYW5ixp9bRqkt81Q4P4RwpGoIdc_3CnGLwGj2BGa5xR8FnF7YtcvKdKo9fHqJrXYntoVegy-Gl',
    alt: 'Handwritten note',
    className: 'h-64 rotate-[4deg] hover:rotate-0 mt-2'
  }
];

export default function JourneyPage() {
  const { couple, user } = useAuth();
  const { slug } = useParams();
  const [recentMemories, setRecentMemories] = useState([]);
  const [selectedMemory, setSelectedMemory] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [dailyNote, setDailyNote] = useState(null);
  const [heroImage, setHeroImage] = useState(couple?.heroImageUrl || '/images/journey-bg.jpg');
  const [isUploadingHero, setIsUploadingHero] = useState(false);
  const fileInputRef = useRef(null);
  
  useEffect(() => {
    if (couple?.heroImageUrl) {
      setHeroImage(couple.heroImageUrl);
    }
  }, [couple]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Dynamic couple data
  const startDate = couple ? new Date(couple.startDate) : new Date();
  if (couple && couple.startTime) {
    const [h, m] = couple.startTime.split(':');
    startDate.setHours(parseInt(h) || 0, parseInt(m) || 0, 0);
  }
  const startTime = startDate.getTime();
  const diff = Math.max(0, now - startTime);
  
  const totalHours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor((diff / (1000 * 60 * 60)) % 24);
  const minutes = Math.floor((diff / 1000 / 60) % 60);
  const seconds = Math.floor((diff / 1000) % 60);

  const currentDate = new Date(now);
  const monthsDiff = (currentDate.getFullYear() - startDate.getFullYear()) * 12 + (currentDate.getMonth() - startDate.getMonth());
  const adjustedMonthsDiff = currentDate.getDate() < startDate.getDate() ? monthsDiff - 1 : monthsDiff;
  const monthText = adjustedMonthsDiff === 1 ? '1 Month' : `${adjustedMonthsDiff} Months`;

  // Get partner info and nicknames
  const partner = couple && user ? (
    couple.user1?._id === user._id ? couple.user2 : couple.user1
  ) : null;
  const myNicknameForPartner = user?.nicknameForPartner || partner?.name || 'My Love';
  const partnerNicknameForMe = partner?.nicknameForPartner || user?.name || '';

  useEffect(() => {
    if (!slug) return;

    const fetchRecentMemories = async () => {
      try {
        const { data } = await getMemories(slug);
        if (data && data.length > 0) {
          const formatted = data.slice(0, 4).map((mem, index) => {
            const isLocal = mem.imageUrl.startsWith('/uploads');
            const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
            const imgSrc = isLocal ? `${baseUrl}${mem.imageUrl}` : mem.imageUrl;
            
            let className = 'h-64 hover:rotate-0';
            if (index === 0) className += ' rotate-[-2deg]';
            if (index === 1) className += ' rotate-[3deg] mt-4';
            if (index === 2) className += ' rotate-[-1deg]';
            if (index === 3) className += ' rotate-[4deg] mt-2';

            return {
              _id: mem._id,
              src: imgSrc,
              alt: mem.title,
              className
            };
          });
          setRecentMemories(formatted);
        }
      } catch (err) {
        console.error('Failed to fetch recent memories for preview', err);
      }
    };

    const fetchDailyNote = async () => {
      try {
        const { data } = await getDailyLoveNote(slug);
        setDailyNote(data);
      } catch (err) {
        console.error('Failed to fetch daily note', err);
      }
    };

    fetchRecentMemories();
    fetchDailyNote();

    // Socket listener for real-time hero image update
    const socketUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
    const socket = io(socketUrl, {
      auth: { token: user ? localStorage.getItem('bloom_token') : null, coupleSlug: slug }
    });
    
    socket.on('updateHeroImage', (newUrl) => {
      setHeroImage(newUrl);
    });

    return () => socket.disconnect();
  }, [slug]);

  const handleHeroUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setIsUploadingHero(true);
    try {
      const formData = new FormData();
      formData.append('image', file);
      
      const { data } = await updateHeroImage(slug, formData);
      setHeroImage(data.heroImageUrl);
    } catch (err) {
      console.error('Failed to upload hero image', err);
    } finally {
      setIsUploadingHero(false);
    }
  };

  const displayMemories = recentMemories.length > 0 ? recentMemories : FALLBACK_MEMORIES;

  return (
    <>
      {/* Hero Section */}
      <section className="relative min-h-[921px] flex items-center justify-center overflow-hidden px-5 md:px-margin-desktop -mt-32">
        <div className="relative z-20 max-w-4xl text-center space-y-8 animate-fade-in mt-20">
          <div className="inline-block px-4 py-1.5 rounded-full bg-primary-container/30 text-primary font-label-sm uppercase tracking-widest mb-4">
            Our Journey: The Story Continues
          </div>
          <h1 className="font-display-lg text-display-lg-mobile md:text-display-lg text-on-surface">
            Happy {monthText}, <span className="text-primary italic">my beautiful {myNicknameForPartner}.</span>
          </h1>
          <p className="max-w-2xl mx-auto font-body-lg text-body-lg text-on-surface-variant/80 leading-relaxed">
            {couple?.specialPhrase || `Every day with you is a new adventure. Thank you for being the most beautiful part of my everyday journey.`}
          </p>
          <div className="flex flex-col md:flex-row items-center justify-center gap-4 pt-4">
            <Link
              to={`/c/${slug}/memories`}
              className="px-10 py-4 bg-primary text-on-primary rounded-full font-body-md hover:bg-secondary transition-all duration-300 shadow-xl shadow-primary/10"
            >
              Explore Our Memories
            </Link>
            <Link
              to={`/c/${slug}/love-notes`}
              className="px-10 py-4 border-2 border-secondary text-secondary rounded-full font-body-md hover:bg-secondary/5 transition-all duration-300 bg-transparent"
            >
              Read Love Notes
            </Link>
          </div>
        </div>

        {/* Floating Hero Image (Asymmetric Placement) */}
        <div className="absolute -right-20 top-1/2 -translate-y-1/2 hidden lg:block w-[400px] h-[600px] rotate-3 opacity-90 z-10">
          <Polaroid
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuA7rTYtGBinZKFVZNhoMKumWxgfgRJOPezD8tt5YcK49bllW3wBt0xZMu0744m5O_9g2fmap4BLbJP1QQWfpwmN0ICt6Lzb1ROt0-akJwE_6grVRg-S_m7LF6bFYUQOrh_saBGc2ihnyphsjD7bee13qcevlJWiU0odKKYYAbsi-P0aIQKZhFBZlWqRozQxgmoEnZjEqbXGI-cHPpBszGZpM1P5VeR2sC_sIdExe-jVN_QPXi8gBt9zjOwIMbpBo0bcVT97LykC4ekS"
            alt="White lilies macro"
            className="w-full h-full p-4"
          />
        </div>
      </section>

      {/* The Journey Bento */}
      <section className="py-24 px-5 md:px-margin-desktop max-w-container-max mx-auto relative z-20">
        <div className="text-center mb-16 space-y-2">
          <h2 className="font-headline-md text-headline-md text-on-surface">
            Our Journey Together
          </h2>
          <p className="text-on-surface-variant italic">
            {days} days of choosing you.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 auto-rows-[280px]">
          {/* Large Feature Card */}
          <div className="md:col-span-8 md:row-span-2 rounded-[24px] relative overflow-hidden group shadow-lg border border-white/40 bg-surface">
            <img
              className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
              src={heroImage}
              alt="Beautiful memory"
              loading="lazy"
            />
            {/* Edit Hero Image Button */}
            <div className="absolute top-4 right-4 z-30 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
              <button 
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploadingHero}
                className="w-10 h-10 rounded-full bg-surface/80 backdrop-blur-md shadow-glass flex items-center justify-center text-primary hover:bg-surface hover:scale-105 transition-all cursor-pointer border border-white/50"
                title="Change background picture"
              >
                <span className={`material-symbols-outlined text-[20px] ${isUploadingHero ? 'animate-spin' : ''}`}>
                  {isUploadingHero ? 'sync' : 'photo_camera'}
                </span>
              </button>
              <input 
                type="file" 
                ref={fileInputRef} 
                onChange={handleHeroUpload} 
                accept="image/*" 
                className="hidden" 
              />
            </div>
            <div className="absolute inset-x-6 bottom-6 p-6 rounded-[20px] bg-white/50 backdrop-blur-md border border-white/60 shadow-glass flex flex-col justify-end">
              <span className="font-label-sm text-primary uppercase tracking-wider font-bold">
                Our Journey: The Story Continues
              </span>
              <h3 className="font-headline-md text-headline-md mt-1 text-on-surface">
                Where it all started
              </h3>
              <p className="mt-2 text-on-surface-variant max-w-md font-medium leading-relaxed">
                The first time our eyes met, I knew there was a story waiting to
                be written. This is only the first chapter of a masterpiece.
              </p>
            </div>
          </div>

          {/* Dynamic Timer Grid Item */}
          <div className="md:col-span-4 md:row-span-2 bg-tertiary-container rounded-[24px] p-8 flex flex-col justify-center items-center text-center space-y-8">
            <div className="w-16 h-16 rounded-full bg-white/50 flex items-center justify-center text-primary shrink-0">
              <span className="material-symbols-outlined text-4xl">
                favorite
              </span>
            </div>
            
            <div className="space-y-4 w-full">
              <div className="font-headline-md text-headline-md text-on-surface">Time Together</div>
              
              <div className="flex gap-2 justify-center items-baseline text-primary">
                <div className="flex flex-col items-center">
                  <span className="font-display-lg text-4xl">{days}</span>
                  <span className="font-label-sm uppercase text-on-surface-variant text-[10px]">Days</span>
                </div>
                <span className="font-display-lg text-2xl mb-4 opacity-50">:</span>
                <div className="flex flex-col items-center">
                  <span className="font-display-lg text-4xl">{hours.toString().padStart(2, '0')}</span>
                  <span className="font-label-sm uppercase text-on-surface-variant text-[10px]">Hrs</span>
                </div>
                <span className="font-display-lg text-2xl mb-4 opacity-50">:</span>
                <div className="flex flex-col items-center">
                  <span className="font-display-lg text-4xl">{minutes.toString().padStart(2, '0')}</span>
                  <span className="font-label-sm uppercase text-on-surface-variant text-[10px]">Min</span>
                </div>
                <span className="font-display-lg text-2xl mb-4 opacity-50">:</span>
                <div className="flex flex-col items-center">
                  <span className="font-display-lg text-4xl">{seconds.toString().padStart(2, '0')}</span>
                  <span className="font-label-sm uppercase text-on-surface-variant text-[10px]">Sec</span>
                </div>
              </div>
            </div>

            <div className="w-full h-[1px] bg-on-surface-variant/20 shrink-0"></div>

            <div className="space-y-3 w-full">
              <div className="font-headline-md text-headline-md text-on-surface text-sm">
                Since {startDate.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
              </div>
            </div>
          </div>

          {/* Metric Cards */}
          <div className="md:col-span-6 bg-tertiary-container rounded-[24px] p-8 flex flex-col justify-center items-center text-center space-y-4">
            <div className="w-16 h-16 rounded-full bg-white/50 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-4xl">
                calendar_month
              </span>
            </div>
            <div>
              <div className="font-headline-md text-headline-md text-on-surface">{days}</div>
              <div className="font-label-sm uppercase tracking-widest text-on-tertiary-container mt-1">
                Days as {partnerNicknameForMe || user?.name} & {myNicknameForPartner}
              </div>
            </div>
          </div>
          
          <div className="md:col-span-6 bg-primary-container/40 rounded-[24px] p-8 flex flex-col justify-center items-center text-center space-y-4">
            <div className="w-16 h-16 rounded-full bg-white/50 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-4xl">
                favorite
              </span>
            </div>
            <div>
              <div className="font-headline-md text-headline-md text-on-surface">{totalHours}+</div>
              <div className="font-label-sm uppercase tracking-widest text-on-primary-container mt-1">
                Hours of loving you
              </div>
            </div>
          </div>

          {/* Daily AI Note Widget */}
          {dailyNote && (
            <div className="md:col-span-12 glass-panel rounded-[24px] p-8 md:p-12 border border-primary/20 flex flex-col md:flex-row items-center gap-8 relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-8 opacity-10 pointer-events-none group-hover:scale-110 transition-transform duration-700">
                <span className="material-symbols-outlined text-[120px] text-primary">auto_awesome</span>
              </div>
              <div className="w-16 h-16 md:w-24 md:h-24 rounded-full bg-primary-container flex items-center justify-center text-primary shrink-0 z-10 shadow-glow-primary">
                <span className="material-symbols-outlined text-4xl md:text-5xl">mark_email_unread</span>
              </div>
              <div className="text-center md:text-left z-10">
                <span className="font-label-sm text-primary uppercase tracking-widest font-bold">Daily Love Note • {dailyNote.dateStr}</span>
                <p className="font-headline-md text-xl md:text-2xl text-on-surface mt-2 leading-relaxed">
                  "{dailyNote.content}"
                </p>
                <div className="mt-4 flex items-center justify-center md:justify-start gap-2">
                  <div className="w-6 h-[1px] bg-primary/50"></div>
                  <span className="font-label-sm text-on-surface-variant italic">From {dailyNote.author}</span>
                </div>
              </div>
            </div>
          )}

          {/* Memories Preview Section */}
          <div className="md:col-span-12 mt-12">
            <div className="flex flex-col md:flex-row justify-between md:items-end gap-4 mb-8">
              <div>
                <h2 className="font-headline-md text-headline-md text-on-surface">
                  Cherished Memories
                </h2>
                <p className="text-on-surface-variant">
                  Fragments of our favorite moments.
                </p>
              </div>
              <Link
                to={`/c/${slug}/memories`}
                className="text-primary font-body-md border-b border-primary hover:text-secondary hover:border-secondary transition-all"
              >
                View All Gallery
              </Link>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {displayMemories.map((mem) => (
                <Polaroid
                  key={mem._id}
                  className={mem.className}
                  src={mem.src}
                  alt={mem.alt}
                  onClick={() => setSelectedMemory(mem)}
                />
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Love Note Section */}
      <section className="py-24 relative z-20">
        <div className="max-w-3xl mx-auto px-5 md:px-margin-desktop text-center">
          <div className="glass-card p-12 rounded-[32px] shadow-2xl shadow-primary/5 space-y-8 bg-surface-container-lowest/80 border border-primary/10">
            <span className="material-symbols-outlined text-primary text-5xl opacity-40">
              format_quote
            </span>
            <h2 className="font-headline-md text-headline-md text-primary">
              A Note Just For You
            </h2>
            <p className="font-body-lg text-body-lg italic leading-relaxed text-on-surface">
              "{couple?.specialPhrase || 'Every moment with you is a moment I treasure. Here is to forever growing together.'}"
            </p>
            <div className="w-24 h-[1px] bg-outline-variant mx-auto"></div>
            <p className="font-headline-md text-primary">— Forever Yours, {partnerNicknameForMe || user?.name}</p>
          </div>
        </div>
      </section>
      {selectedMemory && (
        <Lightbox 
          imageSrc={selectedMemory.src}
          title={selectedMemory.alt}
          onClose={() => setSelectedMemory(null)}
        />
      )}
    </>
  );
}
