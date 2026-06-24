import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import Polaroid from '../components/Polaroid';
import { getMemories } from '../api';

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
  const [recentMemories, setRecentMemories] = useState([]);

  useEffect(() => {
    const fetchRecentMemories = async () => {
      try {
        const { data } = await getMemories();
        if (data && data.length > 0) {
          // Format the DB memories to match the visual props needed for the grid
          const formatted = data.slice(0, 4).map((mem, index) => {
            const isLocal = mem.imageUrl.startsWith('/uploads');
            const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
            const imgSrc = isLocal ? `${baseUrl}${mem.imageUrl}` : mem.imageUrl;
            
            // Apply predefined classes to mimic the original bento aesthetic
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
    fetchRecentMemories();
  }, []);

  const displayMemories = recentMemories.length > 0 ? recentMemories : FALLBACK_MEMORIES;

  return (
    <>
      {/* Hero Section */}
      <section className="relative min-h-[921px] flex items-center justify-center overflow-hidden px-5 md:px-margin-desktop -mt-32">
        <div className="relative z-20 max-w-4xl text-center space-y-8 animate-fade-in mt-20">
          <div className="inline-block px-4 py-1.5 rounded-full bg-primary-container/30 text-primary font-label-sm uppercase tracking-widest mb-4">
            Chapter One: The First Moon
          </div>
          <h1 className="font-display-lg text-display-lg-mobile md:text-display-lg text-on-surface">
            Happy 1 Month, <span className="text-primary italic">My Love.</span>
          </h1>
          <p className="max-w-2xl mx-auto font-body-lg text-body-lg text-on-surface-variant/80 leading-relaxed">
            This past month has been like watching a single lily bloom in slow
            motion—delicate, intentional, and breathtakingly beautiful. Every
            shared laugh and quiet moment has woven a tapestry of memories that
            I will carry in my heart forever. Thank you for being the most
            beautiful part of my everyday journey.
          </p>
          <div className="flex flex-col md:flex-row items-center justify-center gap-4 pt-4">
            <Link
              to="/memories"
              className="px-10 py-4 bg-primary text-on-primary rounded-full font-body-md hover:bg-secondary transition-all duration-300 shadow-xl shadow-primary/10"
            >
              Explore Our Memories
            </Link>
            <Link
              to="/love-notes"
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
            Thirty days of choosing you.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 auto-rows-[280px]">
          {/* Large Feature Card */}
          <div className="md:col-span-8 md:row-span-2 glass-card rounded-[24px] p-8 flex flex-col justify-end relative overflow-hidden group">
            <img
              className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-105 opacity-40"
              src="/images/journey-bg.jpg"
              alt="Beautiful memory"
              loading="lazy"
            />
            <div className="relative z-10">
              <span className="font-label-sm text-primary uppercase tracking-wider">
                The Beginning
              </span>
              <h3 className="font-headline-md text-headline-md mt-2 text-on-surface">
                Where it all started
              </h3>
              <p className="mt-4 text-on-surface-variant max-w-md">
                The first time our eyes met, I knew there was a story waiting to
                be written. This month was only the first chapter of a masterpiece.
              </p>
            </div>
          </div>

          {/* Small Grid Items */}
          <div className="md:col-span-4 bg-tertiary-container rounded-[24px] p-8 flex flex-col justify-center items-center text-center space-y-4">
            <div className="w-16 h-16 rounded-full bg-white/50 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-4xl">
                calendar_month
              </span>
            </div>
            <div>
              <div className="font-headline-md text-headline-md text-on-surface">30</div>
              <div className="font-label-sm uppercase tracking-widest text-on-tertiary-container">
                Days of Joy
              </div>
            </div>
          </div>
          
          <div className="md:col-span-4 bg-primary-container/40 rounded-[24px] p-8 flex flex-col justify-center items-center text-center space-y-4">
            <div className="w-16 h-16 rounded-full bg-white/50 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-4xl">
                favorite
              </span>
            </div>
            <div>
              <div className="font-headline-md text-headline-md text-on-surface">720+</div>
              <div className="font-label-sm uppercase tracking-widest text-on-primary-container">
                Hours of Love
              </div>
            </div>
          </div>

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
                to="/memories"
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
              "If I had a flower for every time I thought of you, I could walk in
              my garden forever. This month has been that garden, and I am so
              grateful to be walking through it with you. Here is to many more
              months of blooming together."
            </p>
            <div className="w-24 h-[1px] bg-outline-variant mx-auto"></div>
            <p className="font-headline-md text-primary">— Forever Yours</p>
          </div>
        </div>
      </section>
    </>
  );
}
