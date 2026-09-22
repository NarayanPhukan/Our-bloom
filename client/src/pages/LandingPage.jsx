import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import OurBloomLogo from '../components/OurBloomLogo';
import lisbonTripImg from '../assets/memories/lisbon_trip.jpg';
import sundayTogetherImg from '../assets/memories/sunday_together.jpg';
import mumbaiDayImg from '../assets/memories/mumbai_day.jpg';
import morningCoffeeImg from '../assets/memories/morning_coffee.jpg';
import cookingTogetherImg from '../assets/memories/cooking_together.jpg';

export default function LandingPage() {
  const { user, couple } = useAuth();
  const portalDestination = user && couple?.slug ? `/c/${couple.slug}` : (user ? '/setup' : '/login');

  return (
    <div className="min-h-screen bg-white text-[#0F172A] flex flex-col antialiased selection:bg-blue-100 selection:text-blue-900 font-sans">
      {/* Navigation Bar */}
      <header className="sticky top-0 z-50 bg-white/95 backdrop-blur-md border-b border-slate-100">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          {/* Brand Logo */}
          <Link to="/" className="flex items-center group">
            <OurBloomLogo variant="primary" iconSize="w-6 h-7" textSize="text-xl" />
          </Link>

          {/* Navigation Links */}
          <nav className="hidden md:flex items-center gap-9 text-sm font-medium text-slate-600">
            <a href="#how-it-works" className="hover:text-slate-900 transition-colors">How It Works</a>
            <a href="#features" className="hover:text-slate-900 transition-colors">Features</a>
            <a href="#memories" className="hover:text-slate-900 transition-colors">Memories</a>
            <a href="#vault" className="hover:text-slate-900 transition-colors">Vault</a>
            <a href="#story" className="hover:text-slate-900 transition-colors">Our Story</a>
            <a href="#security" className="hover:text-slate-900 transition-colors">Security</a>
          </nav>

          {/* Action Buttons */}
          <div className="flex items-center gap-4">
            <Link
              to={user ? portalDestination : '/login'}
              className="text-sm font-medium text-slate-700 hover:text-slate-900 transition-colors hidden sm:inline-block"
            >
              {user ? 'My Space' : 'Log In'}
            </Link>

            <Link
              to={portalDestination}
              className="inline-flex items-center justify-center px-4 sm:px-5 py-2.5 rounded-xl bg-[#0F1E36] text-white font-medium text-sm hover:bg-slate-800 transition-colors shadow-sm"
            >
              {user ? 'Open Dashboard' : 'Get Started'}
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="pt-12 sm:pt-20 pb-20 sm:pb-28 px-6 overflow-hidden">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
          
          {/* Left Hero Column */}
          <div className="lg:col-span-6 space-y-8">
            {/* Status Pill Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-slate-200/90 bg-slate-50/70 text-xs font-medium text-slate-600 shadow-xs">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>Private · Secure · Built for two</span>
            </div>

            {/* Main Headline */}
            <h1 className="text-5xl sm:text-6xl lg:text-[68px] font-extrabold tracking-tight text-[#0F172A] leading-[1.08]">
              Build something<br />
              worth<br />
              remembering.
            </h1>

            {/* Subtitle */}
            <p className="text-lg sm:text-[19px] text-slate-500 font-normal leading-relaxed max-w-lg">
              OurBloom is a private space for couples to preserve memories, coordinate plans, and manage a shared life — with the clarity and care it deserves.
            </p>

            {/* Action Buttons */}
            <div className="flex flex-wrap items-center gap-4 pt-2">
              <Link
                to={portalDestination}
                className="inline-flex items-center justify-center gap-2 px-7 py-3.5 rounded-xl bg-[#0F1E36] text-white font-semibold text-sm hover:bg-slate-800 transition-colors shadow-sm"
              >
                <span>Get Started</span>
                <span className="text-base leading-none">→</span>
              </Link>

              <a
                href="#how-it-works"
                className="inline-flex items-center justify-center px-6 py-3.5 rounded-xl bg-white border border-slate-200 text-slate-700 font-semibold text-sm hover:bg-slate-50 transition-colors shadow-xs"
              >
                Explore OurBloom
              </a>
            </div>

            {/* Value Checkmarks */}
            <div className="pt-2 flex flex-wrap items-center gap-6 text-xs sm:text-sm text-slate-500 font-medium">
              <span className="flex items-center gap-1.5">
                <span className="text-emerald-500 font-bold">✓</span> End-to-end encrypted
              </span>
              <span className="flex items-center gap-1.5">
                <span className="text-emerald-500 font-bold">✓</span> No advertising
              </span>
              <span className="flex items-center gap-1.5">
                <span className="text-emerald-500 font-bold">✓</span> Cancel anytime
              </span>
            </div>
          </div>

          {/* Right Hero Column: Pixel-Perfect Browser Mockup */}
          <div className="lg:col-span-6 relative">
            <div className="relative w-full max-w-xl mx-auto lg:max-w-none">
              
              {/* Floating End-to-End Encrypted Badge */}
              <div className="absolute -top-3 right-6 sm:right-8 z-20 inline-flex items-center gap-1.5 bg-[#0F1E36] text-white text-[11px] font-semibold px-3.5 py-1.5 rounded-full shadow-lg">
                <span>End-to-end encrypted</span>
              </div>

              {/* Browser Window Frame */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-2xl overflow-hidden">
                {/* Window Chrome Header */}
                <div className="px-4 py-3 bg-slate-50/90 border-b border-slate-100 flex items-center">
                  {/* Traffic Light Dots */}
                  <div className="flex items-center gap-1.5 w-16">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#EF4444]/80"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-[#F59E0B]/80"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-[#10B981]/80"></span>
                  </div>
                  {/* Centered Address Bar */}
                  <div className="flex-1 flex justify-center">
                    <div className="bg-white/90 border border-slate-200/70 rounded-md px-6 py-0.5 text-[11px] font-mono text-slate-400 shadow-2xs">
                      ourbloom.app
                    </div>
                  </div>
                  <div className="w-16"></div>
                </div>

                {/* Window Body */}
                <div className="flex bg-white">
                  {/* Slim Icon Sidebar */}
                  <div className="w-12 sm:w-14 border-r border-slate-100 p-2 sm:p-2.5 flex flex-col items-center gap-4 py-4 bg-slate-50/40">
                    <div className="w-8 h-8 rounded-lg bg-blue-50 text-[#2563EB] flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">home</span>
                    </div>
                    <div className="w-8 h-8 rounded-lg text-slate-400 hover:text-slate-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">image</span>
                    </div>
                    <div className="w-8 h-8 rounded-lg text-slate-400 hover:text-slate-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">calendar_today</span>
                    </div>
                    <div className="w-8 h-8 rounded-lg text-slate-400 hover:text-slate-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">account_balance_wallet</span>
                    </div>
                  </div>

                  {/* Main Dashboard Space */}
                  <div className="flex-1 p-5 sm:p-6 space-y-4">
                    {/* Couple Header */}
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-bold text-slate-900 text-sm">Marcus &amp; Elena</h3>
                        <p className="text-[11px] text-slate-400">Together since April 2021</p>
                      </div>
                      <div className="flex items-center -space-x-1.5">
                        <div className="w-7 h-7 rounded-full bg-[#3B82F6] text-white font-bold text-[11px] flex items-center justify-center ring-2 ring-white">
                          M
                        </div>
                        <div className="w-7 h-7 rounded-full bg-[#F59E0B] text-white font-bold text-[11px] flex items-center justify-center ring-2 ring-white">
                          E
                        </div>
                      </div>
                    </div>

                    {/* Memory Card (Kyoto) */}
                    <div className="border border-slate-100 rounded-xl p-3 sm:p-3.5 bg-[#F8FAFC]/80 flex items-start gap-3">
                      <div className="w-10 h-10 rounded-lg bg-slate-200/70 flex-shrink-0 flex items-center justify-center text-slate-400">
                        <span className="material-symbols-outlined text-[20px]">photo_library</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <h4 className="font-semibold text-xs text-slate-900">Kyoto · March 2025</h4>
                        <p className="text-[11px] text-slate-500 truncate mt-0.5">
                          Cherry blossom walk — Philosopher's Path · 47 photos
                        </p>
                        <div className="flex items-center gap-2 mt-2">
                          <span className="text-[10px] font-medium px-2 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-100">
                            Memory
                          </span>
                          <span className="text-[10px] text-slate-400">3 days ago</span>
                        </div>
                      </div>
                    </div>

                    {/* Two Side-by-Side Cards: Upcoming & Shared Vault */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {/* Upcoming Card */}
                      <div className="border border-slate-100 rounded-xl p-3 bg-white space-y-2 shadow-2xs">
                        <span className="text-[9px] font-bold text-slate-400 tracking-wider uppercase block">
                          UPCOMING
                        </span>
                        <div className="flex items-start gap-2.5">
                          <div className="w-7 h-7 rounded-md bg-emerald-50 text-emerald-600 flex items-center justify-center flex-shrink-0">
                            <span className="material-symbols-outlined text-[15px]">event</span>
                          </div>
                          <div>
                            <h5 className="font-semibold text-xs text-slate-800 leading-tight">Anniversary dinner</h5>
                            <p className="text-[10px] text-slate-400 mt-0.5">Oct 5 · 14 days</p>
                          </div>
                        </div>
                      </div>

                      {/* Shared Vault Card */}
                      <div className="border border-slate-100 rounded-xl p-3 bg-white space-y-1.5 shadow-2xs">
                        <span className="text-[9px] font-bold text-slate-400 tracking-wider uppercase block">
                          SHARED VAULT
                        </span>
                        <div className="font-bold text-sm text-slate-900 leading-none">
                          ₹4,200
                        </div>
                        <p className="text-[10px] text-slate-400 truncate">
                          of ₹8,000 · Japan trip
                        </p>
                        <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden mt-1">
                          <div className="h-full bg-[#0F1E36] rounded-full w-[52%]"></div>
                        </div>
                      </div>
                    </div>

                    {/* Recent Activity Feed */}
                    <div className="border-t border-slate-100 pt-3 space-y-2">
                      <span className="text-[9px] font-bold text-slate-400 tracking-wider uppercase block">
                        RECENT ACTIVITY
                      </span>
                      <div className="space-y-2 text-xs">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="w-4 h-4 rounded-full bg-[#F59E0B] text-white text-[9px] font-bold flex items-center justify-center">E</span>
                            <span className="text-slate-700 text-[11px]">
                              <strong className="font-semibold text-slate-900">Elena</strong> added a note to Kyoto trip
                            </span>
                          </div>
                          <span className="text-[10px] text-slate-400 font-medium">2m</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="w-4 h-4 rounded-full bg-[#3B82F6] text-white text-[9px] font-bold flex items-center justify-center">M</span>
                            <span className="text-slate-700 text-[11px]">
                              <strong className="font-semibold text-slate-900">Marcus</strong> logged €340 — flights
                            </span>
                          </div>
                          <span className="text-[10px] text-slate-400 font-medium">1h</span>
                        </div>
                      </div>
                    </div>

                  </div>
                </div>
              </div>

            </div>
          </div>

        </div>
      </section>

      {/* Introduction Section — "The Two of You" */}
      <section id="introduction" className="py-20 sm:py-28 px-6 border-t border-slate-100 bg-white">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-14 items-center">
          
          {/* Left Column: Copy & Value Pillars */}
          <div className="lg:col-span-6 space-y-8">
            <div className="space-y-4">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest block">
                THE TWO OF YOU
              </span>
              <h2 className="text-4xl sm:text-5xl lg:text-[54px] font-extrabold tracking-tight text-[#0F172A] leading-[1.12]">
                A private space for<br className="hidden sm:inline" />
                what you build together.
              </h2>
              <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed max-w-lg pt-2">
                OurBloom brings the meaningful parts of a relationship into one private space — memories, shared experiences, important moments, and plans for the future. Yours alone.
              </p>
            </div>

            {/* Three Feature Rows with Icons */}
            <div className="space-y-6 pt-2">
              {/* Row 1 */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-slate-100/90 text-slate-600 flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-[20px]">photo_library</span>
                </div>
                <div>
                  <h3 className="font-bold text-slate-900 text-sm sm:text-base">Memory archive</h3>
                  <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                    Preserve moments the way they actually happened.
                  </p>
                </div>
              </div>

              {/* Row 2 */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-slate-100/90 text-slate-600 flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-[20px]">calendar_today</span>
                </div>
                <div>
                  <h3 className="font-bold text-slate-900 text-sm sm:text-base">Shared timeline</h3>
                  <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                    Keep track of upcoming moments and milestones.
                  </p>
                </div>
              </div>

              {/* Row 3 */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-slate-100/90 text-slate-600 flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-[20px]">location_on</span>
                </div>
                <div>
                  <h3 className="font-bold text-slate-900 text-sm sm:text-base">Fully private</h3>
                  <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                    No social feed. No algorithm. Just the two of you.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Card Mockup */}
          <div className="lg:col-span-6">
            <div className="max-w-md mx-auto lg:max-w-none rounded-3xl border border-slate-200/90 bg-white shadow-xl p-6 sm:p-8 space-y-6">
              
              {/* Header: Avatars + Names + Mini Actions */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex items-center -space-x-1.5">
                    <div className="w-8 h-8 rounded-full bg-[#3B82F6] text-white font-bold text-xs flex items-center justify-center ring-2 ring-white">
                      M
                    </div>
                    <div className="w-8 h-8 rounded-full bg-[#F59E0B] text-white font-bold text-xs flex items-center justify-center ring-2 ring-white">
                      E
                    </div>
                  </div>
                  <div>
                    <h4 className="font-bold text-slate-900 text-sm">Marcus &amp; Elena</h4>
                    <p className="text-[11px] text-slate-400">Year 4 together</p>
                  </div>
                </div>

                {/* Subtle Action Placeholders */}
                <div className="flex items-center gap-1.5">
                  <div className="w-7 h-7 rounded-lg bg-slate-100/80"></div>
                  <div className="w-7 h-7 rounded-lg bg-slate-100/80"></div>
                  <div className="w-7 h-7 rounded-lg bg-slate-100/80"></div>
                </div>
              </div>

              {/* Memory Feed */}
              <div className="space-y-3">
                <span className="text-[10px] font-bold text-slate-400 tracking-wider uppercase block">
                  RECENT MEMORIES
                </span>

                {/* Memory 1: Lisbon weekend */}
                <div className="border border-slate-100 rounded-2xl p-3.5 sm:p-4 bg-slate-50/50 hover:bg-slate-50 transition-colors flex items-start gap-3.5 sm:gap-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-200/70 flex-shrink-0 flex items-center justify-center text-slate-400">
                    <span className="material-symbols-outlined text-[20px]">photo_library</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <h5 className="font-bold text-xs sm:text-sm text-slate-900">Lisbon weekend</h5>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      September 2026 · 31 photos · 2 notes
                    </p>
                    <span className="inline-block mt-2 text-[10px] font-medium px-2 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-100/60">
                      Trip
                    </span>
                  </div>
                </div>

                {/* Memory 2: First apartment, unpacked */}
                <div className="border border-slate-100 rounded-2xl p-3.5 sm:p-4 bg-slate-50/50 hover:bg-slate-50 transition-colors flex items-start gap-3.5 sm:gap-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-200/70 flex-shrink-0 flex items-center justify-center text-slate-400">
                    <span className="material-symbols-outlined text-[20px]">photo_library</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <h5 className="font-bold text-xs sm:text-sm text-slate-900">First apartment, unpacked</h5>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      August 2026 · 12 photos
                    </p>
                    <span className="inline-block mt-2 text-[10px] font-medium px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-100/60">
                      Milestone
                    </span>
                  </div>
                </div>

                {/* Memory 3: Marcus's birthday dinner */}
                <div className="border border-slate-100 rounded-2xl p-3.5 sm:p-4 bg-slate-50/50 hover:bg-slate-50 transition-colors flex items-start gap-3.5 sm:gap-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-200/70 flex-shrink-0 flex items-center justify-center text-slate-400">
                    <span className="material-symbols-outlined text-[20px]">photo_library</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <h5 className="font-bold text-xs sm:text-sm text-slate-900">Marcus's birthday dinner</h5>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      July 2026 · 8 photos · 1 voice note
                    </p>
                    <span className="inline-block mt-2 text-[10px] font-medium px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100/60">
                      Occasion
                    </span>
                  </div>
                </div>
              </div>

              {/* Bottom Card: Next Milestone */}
              <div className="bg-[#14233D] rounded-2xl p-4 sm:p-5 text-white flex items-center justify-between shadow-xs">
                <div className="space-y-0.5">
                  <span className="text-[9px] font-bold text-slate-400 tracking-wider uppercase block">
                    NEXT MILESTONE
                  </span>
                  <h5 className="font-bold text-sm sm:text-base text-white">
                    Anniversary · Oct 5
                  </h5>
                  <p className="text-xs text-slate-400">13 days away</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-white/10 text-slate-300 flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-[20px]">calendar_today</span>
                </div>
              </div>

            </div>
          </div>

        </div>
      </section>

      {/* How It Works Section */}
      <section id="how-it-works" className="py-20 sm:py-28 px-6 border-t border-slate-100 bg-white">
        <div className="max-w-7xl mx-auto space-y-16">
          
          {/* Section Header */}
          <div className="max-w-2xl space-y-3">
            <span className="text-[11px] font-bold text-[#2563EB] uppercase tracking-widest block">
              HOW IT WORKS
            </span>
            <h2 className="text-4xl sm:text-5xl font-serif font-bold tracking-tight text-[#0F172A] leading-tight">
              Start with the two of you.
            </h2>
            <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed pt-1">
              Create your space, bring your partner in, and start building something together.
            </p>
          </div>

          {/* 3 Step Mockup Cards Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-start">
            
            {/* STEP 01 */}
            <div className="flex flex-col justify-between h-full">
              {/* Step indicator */}
              <div className="flex flex-col items-start gap-1 mb-6">
                <span className="w-2.5 h-2.5 rounded-full border-2 border-slate-400 block"></span>
                <span className="text-[10px] font-mono text-slate-400">01</span>
              </div>

              {/* Mockup Card */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-md overflow-hidden flex flex-col justify-between">
                {/* Dark Browser Top Bar */}
                <div className="bg-[#14233D] px-4 py-3 flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                    <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                    <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                  </div>
                  <div className="w-20 h-1.5 bg-white/10 rounded-full"></div>
                  <div className="w-6"></div>
                </div>

                {/* Card Body */}
                <div className="p-5 space-y-4">
                  {/* User avatar and placeholder lines */}
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-400">
                      <span className="material-symbols-outlined text-[20px]">person</span>
                    </div>
                    <div className="space-y-1.5">
                      <div className="w-20 h-2 bg-slate-100 rounded-full"></div>
                      <div className="w-28 h-1.5 bg-slate-100 rounded-full"></div>
                    </div>
                  </div>

                  {/* Input form */}
                  <div className="space-y-3 pt-1">
                    <div>
                      <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                        SPACE NAME
                      </span>
                      <div className="border border-blue-500 rounded-xl px-3.5 py-2 bg-white text-xs text-slate-900 font-medium shadow-2xs flex items-center justify-between">
                        <span>Our Space</span>
                        <span className="w-0.5 h-3.5 bg-blue-500 animate-pulse"></span>
                      </div>
                    </div>

                    <div>
                      <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                        VISIBILITY
                      </span>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          className="flex-1 border border-blue-500 bg-white text-blue-600 rounded-xl py-2 px-3 text-xs font-semibold flex items-center justify-center gap-1.5 shadow-2xs"
                        >
                          <span className="material-symbols-outlined text-[14px]">lock</span>
                          <span>Private</span>
                        </button>
                        <button
                          type="button"
                          className="flex-1 border border-slate-200 bg-slate-50 text-slate-400 rounded-xl py-2 px-3 text-xs font-medium flex items-center justify-center"
                        >
                          <span>Shared</span>
                        </button>
                      </div>
                    </div>

                    <button
                      type="button"
                      className="w-full bg-[#14233D] hover:bg-[#1E3256] text-white py-2.5 rounded-xl text-xs font-semibold text-center mt-2 shadow-xs transition-colors"
                    >
                      Create space
                    </button>
                  </div>
                </div>
              </div>

              {/* Bottom description */}
              <div className="mt-6 space-y-1.5">
                <h3 className="font-serif text-xl font-bold text-[#0F172A]">
                  Create your space
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                  Create your OurBloom account and set up your private shared space.
                </p>
              </div>
            </div>

            {/* STEP 02 */}
            <div className="flex flex-col justify-between h-full">
              {/* Step indicator */}
              <div className="flex flex-col items-start gap-1 mb-6">
                <span className="w-2.5 h-2.5 rounded-full border-2 border-slate-400 block"></span>
                <span className="text-[10px] font-mono text-slate-400">02</span>
              </div>

              {/* Mockup Card */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-md overflow-hidden flex flex-col justify-between">
                {/* Dark Top Bar */}
                <div className="bg-[#14233D] px-4 py-3 flex items-center justify-between text-white">
                  <span className="font-semibold text-xs text-white">Pair your partner</span>
                  <div className="w-5 h-5 rounded-full bg-white/10 flex items-center justify-center text-slate-300 text-xs">
                    ✕
                  </div>
                </div>

                {/* Card Body */}
                <div className="p-5 space-y-3.5">
                  <p className="text-xs text-slate-500 text-center leading-relaxed">
                    Share this code with your partner to connect your accounts.
                  </p>

                  {/* Pairing Code Card */}
                  <div className="bg-[#14233D] rounded-2xl p-4 text-center space-y-3">
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-widest block">
                      PAIRING CODE
                    </span>
                    <div className="flex items-center justify-center gap-1.5 sm:gap-2">
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">B</span>
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">7</span>
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">K</span>
                      <span className="text-slate-400 font-bold px-1">—</span>
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">4</span>
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">Q</span>
                      <span className="w-7 sm:w-8 h-8 rounded-lg bg-white/10 text-white font-mono font-bold text-xs flex items-center justify-center border border-white/10">2</span>
                    </div>
                    <span className="text-[10px] text-slate-400 block">Expires in 24 hours</span>
                  </div>

                  {/* Waiting pill */}
                  <div className="bg-slate-50 border border-slate-100 rounded-xl p-2.5 flex items-center gap-2.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                    <span className="text-[11px] text-slate-600 font-medium">Waiting for partner...</span>
                  </div>

                  {/* Copy button */}
                  <button
                    type="button"
                    className="w-full border border-slate-200 rounded-xl py-2 px-3 text-xs font-semibold text-slate-700 flex items-center justify-center gap-1.5 hover:bg-slate-50 transition-colors"
                  >
                    <span className="material-symbols-outlined text-[15px]">content_copy</span>
                    <span>Copy pairing code</span>
                  </button>
                </div>
              </div>

              {/* Bottom description */}
              <div className="mt-6 space-y-1.5">
                <h3 className="font-serif text-xl font-bold text-[#0F172A]">
                  Invite your partner
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                  Connect your partner and turn your individual account into a shared space for two.
                </p>
              </div>
            </div>

            {/* STEP 03 */}
            <div className="flex flex-col justify-between h-full">
              {/* Step indicator */}
              <div className="flex flex-col items-start gap-1 mb-6">
                <span className="w-2.5 h-2.5 rounded-full border-2 border-slate-400 block"></span>
                <span className="text-[10px] font-mono text-slate-400">03</span>
              </div>

              {/* Mockup Card */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-md overflow-hidden flex flex-col justify-between">
                {/* Dark Top Bar */}
                <div className="bg-[#14233D] px-4 py-3 flex items-center justify-between text-white">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[15px] text-blue-400">water_drop</span>
                    <span className="font-semibold text-xs text-white">OurBloom</span>
                  </div>
                  <div className="flex items-center -space-x-1">
                    <div className="w-5 h-5 rounded-full bg-white/20"></div>
                    <div className="w-5 h-5 rounded-full bg-white/30"></div>
                  </div>
                </div>

                {/* Card Body */}
                <div className="p-5 space-y-2.5">
                  <div className="flex items-center justify-between pb-1">
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">
                      YOUR SPACE
                    </span>
                    <span className="text-[10px] font-semibold text-blue-600 hover:underline cursor-pointer">
                      See all
                    </span>
                  </div>

                  {/* Item 1: Weekend in Lisbon */}
                  <div className="bg-rose-50/70 border border-rose-100/60 rounded-xl p-2.5 flex items-center justify-between hover:bg-rose-50 transition-colors">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2 h-2 rounded-full bg-rose-500"></span>
                      <div>
                        <h5 className="font-bold text-xs text-slate-900 leading-tight">Weekend in Lisbon</h5>
                        <p className="text-[10px] text-slate-400 mt-0.5">3 photos added</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs font-bold">›</span>
                  </div>

                  {/* Item 2: Summer goals */}
                  <div className="bg-blue-50/70 border border-blue-100/60 rounded-xl p-2.5 flex items-center justify-between hover:bg-blue-50 transition-colors">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                      <div>
                        <h5 className="font-bold text-xs text-slate-900 leading-tight">Summer goals</h5>
                        <p className="text-[10px] text-slate-400 mt-0.5">4 of 7 complete</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs font-bold">›</span>
                  </div>

                  {/* Item 3: Apartment fund */}
                  <div className="bg-emerald-50/70 border border-emerald-100/60 rounded-xl p-2.5 flex items-center justify-between hover:bg-emerald-50 transition-colors">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                      <div>
                        <h5 className="font-bold text-xs text-slate-900 leading-tight">Apartment fund</h5>
                        <p className="text-[10px] text-slate-400 mt-0.5">₹1,240 saved</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs font-bold">›</span>
                  </div>

                  {/* Add something new dashed button */}
                  <div className="border border-dashed border-slate-200 rounded-xl p-2 flex items-center justify-center gap-1.5 text-[11px] text-slate-400 hover:border-slate-300 hover:text-slate-600 transition-colors cursor-pointer">
                    <span className="material-symbols-outlined text-[13px]">add_circle</span>
                    <span>Add something new...</span>
                  </div>
                </div>
              </div>

              {/* Bottom description */}
              <div className="mt-6 space-y-1.5">
                <h3 className="font-serif text-xl font-bold text-[#0F172A]">
                  Build your story
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                  Start saving memories, sharing experiences, and working toward the things you want to build together.
                </p>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* Core Features Section — "Built for Two" */}
      <section id="features" className="py-20 sm:py-28 px-6 bg-white border-t border-slate-100">
        <div className="max-w-7xl mx-auto space-y-12">
          
          {/* Section Header */}
          <div className="max-w-2xl space-y-4">
            <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest block">
              BUILT FOR TWO
            </span>
            <h2 className="text-4xl sm:text-5xl lg:text-[54px] font-extrabold tracking-tight text-[#0F172A] leading-[1.12]">
              Everything that matters,<br className="hidden sm:inline" />
              together.
            </h2>
            <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed pt-1">
              OurBloom brings the important parts of your shared life into one private space — from the moments you want to remember to the things you're building together.
            </p>
          </div>

          {/* 2x2 Core Feature Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            
            {/* 01 — MEMORIES */}
            <div className="rounded-3xl border border-slate-200/90 bg-white p-7 sm:p-8 space-y-6 flex flex-col justify-between shadow-2xs hover:border-slate-300 transition-colors">
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  01 — MEMORIES
                </span>
                <h3 className="font-bold text-xl sm:text-2xl text-[#0F172A]">
                  Keep the moments that matter.
                </h3>
                <p className="text-sm text-slate-500 leading-relaxed max-w-md">
                  Save the photos, places, and memories that make your story yours — organized the way you actually think about them.
                </p>
              </div>

              {/* Mockup for Memories */}
              <div className="space-y-3 pt-2">
                {/* 3 Photo Tiles */}
                <div className="flex items-stretch gap-3">
                  {/* Tile 1: Lisbon */}
                  <div className="flex-1 bg-slate-100/80 rounded-2xl p-3.5 flex flex-col justify-between h-32 border border-slate-200/40">
                    <div className="w-6 h-6 rounded text-slate-400">
                      <span className="material-symbols-outlined text-[20px]">image</span>
                    </div>
                    <div>
                      <span className="font-bold text-xs text-slate-800 block">Lisbon</span>
                      <span className="text-[10px] text-slate-400">Sep 2026</span>
                    </div>
                  </div>

                  {/* Tile 2: Amalfi */}
                  <div className="w-24 bg-slate-100/80 rounded-2xl p-3.5 flex flex-col justify-between h-32 border border-slate-200/40">
                    <div className="w-6 h-6 rounded text-slate-400">
                      <span className="material-symbols-outlined text-[20px]">photo_camera</span>
                    </div>
                    <div>
                      <span className="font-bold text-xs text-slate-800 block">Amalfi</span>
                      <span className="text-[10px] text-slate-400">Jul 2026</span>
                    </div>
                  </div>

                  {/* Tile 3: Kyoto */}
                  <div className="flex-1 bg-slate-100/80 rounded-2xl p-3.5 flex flex-col justify-between h-32 border border-slate-200/40">
                    <div className="w-6 h-6 rounded text-slate-400">
                      <span className="material-symbols-outlined text-[20px]">filter_hdr</span>
                    </div>
                    <div>
                      <span className="font-bold text-xs text-slate-800 block">Kyoto</span>
                      <span className="text-[10px] text-slate-400">Mar 2025</span>
                    </div>
                  </div>
                </div>

                {/* Detailed Memory Item */}
                <div className="border border-slate-100 rounded-2xl p-3.5 bg-slate-50/50 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-slate-200/70 flex items-center justify-center text-slate-400">
                      <span className="material-symbols-outlined text-[18px]">photo_library</span>
                    </div>
                    <div>
                      <h4 className="font-bold text-xs text-slate-900 leading-tight">
                        Lisbon weekend · Sep 2026
                      </h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        31 photos · 2 notes added
                      </p>
                    </div>
                  </div>
                  <span className="text-[10px] font-medium px-2 py-0.5 rounded bg-blue-50 text-blue-600 border border-blue-100/60">
                    Trip
                  </span>
                </div>
              </div>
            </div>

            {/* 02 — SHARED SPACE */}
            <div className="rounded-3xl border border-slate-200/90 bg-white p-7 sm:p-8 space-y-6 flex flex-col justify-between shadow-2xs hover:border-slate-300 transition-colors">
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  02 — SHARED SPACE
                </span>
                <h3 className="font-bold text-xl sm:text-2xl text-[#0F172A]">
                  Your space, together.
                </h3>
                <p className="text-sm text-slate-500 leading-relaxed max-w-md">
                  A private home designed around the two of you — your recent activity, upcoming moments, and everything in one place.
                </p>
              </div>

              {/* Mockup for Shared Space */}
              <div className="border border-slate-100 rounded-2xl p-4 sm:p-5 bg-slate-50/40 space-y-4 pt-4">
                {/* Header with Avatars and 3 dots */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="flex items-center -space-x-1.5">
                      <div className="w-7 h-7 rounded-full bg-[#3B82F6] text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
                        M
                      </div>
                      <div className="w-7 h-7 rounded-full bg-[#F59E0B] text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
                        E
                      </div>
                    </div>
                    <div>
                      <h4 className="font-bold text-xs text-slate-900">Marcus &amp; Elena</h4>
                      <p className="text-[10px] text-slate-400">Year 4</p>
                    </div>
                  </div>
                  <span className="text-slate-400 font-bold text-sm tracking-widest">···</span>
                </div>

                {/* Activity Bullet List */}
                <div className="space-y-2.5 text-xs">
                  {/* Row 1 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
                      <span className="text-slate-700 text-[11px]">Elena added to Lisbon album</span>
                    </div>
                    <span className="text-[10px] text-slate-400">2m ago</span>
                  </div>

                  {/* Row 2 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                      <span className="text-slate-700 text-[11px]">Vault deposit · €200</span>
                    </div>
                    <span className="text-[10px] text-slate-400">1h ago</span>
                  </div>

                  {/* Row 3 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-slate-800"></span>
                      <span className="text-slate-700 text-[11px]">Anniversary reminder set</span>
                    </div>
                    <span className="text-[10px] text-slate-400">Yesterday</span>
                  </div>
                </div>

                {/* Next up row */}
                <div className="border-t border-slate-100 pt-3 flex items-center justify-between">
                  <div>
                    <span className="text-[9px] uppercase tracking-wider text-slate-400 font-bold block">
                      Next up
                    </span>
                    <h5 className="font-semibold text-xs text-slate-900 mt-0.5">
                      Anniversary dinner · Oct 5
                    </h5>
                  </div>
                  <span className="text-[10px] font-medium px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-600 border border-blue-100/60">
                    13 days
                  </span>
                </div>
              </div>
            </div>

            {/* 03 — VAULT */}
            <div className="rounded-3xl border border-slate-200/90 bg-white p-7 sm:p-8 space-y-6 flex flex-col justify-between shadow-2xs hover:border-slate-300 transition-colors">
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  03 — VAULT
                </span>
                <h3 className="font-bold text-xl sm:text-2xl text-[#0F172A]">
                  Build towards what's next.
                </h3>
                <p className="text-sm text-slate-500 leading-relaxed max-w-md">
                  A transparent mutual vault for joint ambitions — vacations, weddings, or milestones with certified bank-grade ledger reconciliation.
                </p>
              </div>

              {/* Mockup for Vault */}
              <div className="border border-slate-100 rounded-2xl p-4 sm:p-5 bg-slate-50/40 space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block">
                      SHARED GOAL · JAPAN &amp; ALPS
                    </span>
                    <div className="flex items-baseline gap-2 mt-1">
                      <span className="font-extrabold text-lg text-slate-900">₹6,400</span>
                      <span className="text-xs text-slate-400 font-normal">of ₹10,000 target</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md">
                    64% Funded
                  </span>
                </div>

                {/* Progress bar */}
                <div className="w-full h-2 bg-slate-200/70 rounded-full overflow-hidden">
                  <div className="h-full bg-[#0F1E36] rounded-full w-[64%]"></div>
                </div>

                {/* Partner Contribution Breakdown */}
                <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-100 text-xs">
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 rounded-full bg-[#3B82F6] text-white text-[9px] font-bold flex items-center justify-center">M</div>
                    <div>
                      <p className="text-[10px] text-slate-400">Marcus (50%)</p>
                      <p className="font-bold text-slate-800 text-xs">₹3,200</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 rounded-full bg-[#F59E0B] text-white text-[9px] font-bold flex items-center justify-center">E</div>
                    <div>
                      <p className="text-[10px] text-slate-400">Elena (50%)</p>
                      <p className="font-bold text-slate-800 text-xs">₹3,200</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* 04 — GIFTS & EXPERIENCES */}
            <div className="rounded-3xl border border-slate-200/90 bg-white p-7 sm:p-8 space-y-6 flex flex-col justify-between shadow-2xs hover:border-slate-300 transition-colors">
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  04 — GIFTS &amp; EXPERIENCES
                </span>
                <h3 className="font-bold text-xl sm:text-2xl text-[#0F172A]">
                  Celebrate every milestone.
                </h3>
                <p className="text-sm text-slate-500 leading-relaxed max-w-md">
                  Keep track of wishlists, surprise notes, and shared experiences so you never miss an opportunity to show affection.
                </p>
              </div>

              {/* Mockup for Gifts & Experiences */}
              <div className="border border-slate-100 rounded-2xl p-4 sm:p-5 bg-slate-50/40 space-y-3">
                {/* Wishlist item 1 */}
                <div className="bg-white border border-slate-100 rounded-xl p-3 flex items-center justify-between shadow-2xs">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-500 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">featured_seasonal_and_gifts</span>
                    </div>
                    <div>
                      <h5 className="font-bold text-xs text-slate-900 leading-tight">Pottery Masterclass for Two</h5>
                      <p className="text-[10px] text-slate-400 mt-0.5">Saved by Elena · Lisbon wishlist</p>
                    </div>
                  </div>
                  <span className="text-[10px] font-medium px-2 py-0.5 rounded bg-rose-50 text-rose-600 border border-rose-100/60">
                    Experience
                  </span>
                </div>

                {/* Wishlist item 2 */}
                <div className="bg-white border border-slate-100 rounded-xl p-3 flex items-center justify-between shadow-2xs">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                      <span className="material-symbols-outlined text-[18px]">favorite</span>
                    </div>
                    <div>
                      <h5 className="font-bold text-xs text-slate-900 leading-tight">Handwritten Anniversary Note</h5>
                      <p className="text-[10px] text-slate-400 mt-0.5">Opens Oct 5 · Secret surprise</p>
                    </div>
                  </div>
                  <span className="text-[10px] font-medium px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100/60">
                    Encrypted
                  </span>
                </div>

                {/* Bottom status note */}
                <div className="pt-2 flex items-center justify-between text-[11px] text-slate-500">
                  <span className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                    2 items in mutual wishlist
                  </span>
                  <span className="font-mono text-slate-400">Synced</span>
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* Dedicated Memories Section — "Keep the moments that matter." */}
      <section id="memories" className="py-20 sm:py-28 px-6 bg-white border-t border-slate-100">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Column: Photo Collage Grid */}
          <div className="lg:col-span-6 space-y-4">
            {/* Top Large Photo Card */}
            <div className="relative rounded-2xl overflow-hidden shadow-md group h-72 sm:h-80 bg-slate-900">
              <img
                src={lisbonTripImg}
                alt="Our First Trip - Lisbon"
                className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-700 brightness-[0.92]"
              />
              {/* Private Pill Badge */}
              <div className="absolute top-4 left-4 z-10 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/40 backdrop-blur-md border border-white/20 text-white text-[11px] font-medium">
                <span className="material-symbols-outlined text-[13px]">lock</span>
                <span>Private</span>
              </div>
              {/* Bottom Gradient Info Overlay */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-transparent flex flex-col justify-end p-5 text-white">
                <div className="flex items-end justify-between">
                  <div className="space-y-1">
                    <h4 className="text-base sm:text-lg font-bold text-white leading-tight">
                      Our First Trip
                    </h4>
                    <div className="flex items-center gap-3 text-xs text-white/80 font-normal">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px]">location_on</span>
                        Lisbon, Portugal
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px]">photo_camera</span>
                        24
                      </span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="flex items-center -space-x-1.5">
                      <div className="w-6 h-6 rounded-full bg-white/30 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[9px] font-bold">M</div>
                      <div className="w-6 h-6 rounded-full bg-white/50 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[9px] font-bold">E</div>
                    </div>
                    <span className="text-[11px] font-mono text-white/70">Jun 2025</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom Two Side-by-Side Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Card 1: Sunday Together */}
              <div className="relative rounded-2xl overflow-hidden shadow-md group h-52 bg-slate-900">
                <img
                  src={sundayTogetherImg}
                  alt="Sunday Together"
                  className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-700 brightness-[0.92]"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/10 to-transparent flex flex-col justify-end p-4 text-white">
                  <div className="flex items-end justify-between">
                    <div className="space-y-0.5">
                      <h5 className="text-xs sm:text-sm font-bold text-white leading-tight">
                        Sunday Together
                      </h5>
                      <div className="flex items-center gap-1 text-[11px] text-white/80">
                        <span className="material-symbols-outlined text-[13px]">photo_camera</span>
                        <span>11</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <div className="flex items-center -space-x-1">
                        <div className="w-5 h-5 rounded-full bg-white/30 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[8px] font-bold">M</div>
                        <div className="w-5 h-5 rounded-full bg-white/50 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[8px] font-bold">E</div>
                      </div>
                      <span className="text-[10px] font-mono text-white/70">Aug 2025</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Card 2: A Day Worth Remembering */}
              <div className="relative rounded-2xl overflow-hidden shadow-md group h-52 bg-slate-900">
                <img
                  src={mumbaiDayImg}
                  alt="A Day Worth Remembering"
                  className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-700 brightness-[0.92]"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/10 to-transparent flex flex-col justify-end p-4 text-white">
                  <div className="flex items-end justify-between">
                    <div className="space-y-0.5">
                      <h5 className="text-xs sm:text-sm font-bold text-white leading-tight">
                        A Day Worth Remembering
                      </h5>
                      <div className="flex items-center gap-2 text-[11px] text-white/80">
                        <span className="flex items-center gap-0.5">
                          <span className="material-symbols-outlined text-[12px]">location_on</span>
                          Mumbai
                        </span>
                        <span className="flex items-center gap-0.5">
                          <span className="material-symbols-outlined text-[12px]">photo_camera</span>
                          8
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <div className="flex items-center -space-x-1">
                        <div className="w-5 h-5 rounded-full bg-white/30 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[8px] font-bold">M</div>
                        <div className="w-5 h-5 rounded-full bg-white/50 backdrop-blur-sm border border-white/40 flex items-center justify-center text-[8px] font-bold">E</div>
                      </div>
                      <span className="text-[10px] font-mono text-white/70">Sep 2025</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Copy & App Mockup */}
          <div className="lg:col-span-6 space-y-8">
            <div className="space-y-4">
              <span className="text-[11px] font-bold text-[#2563EB] uppercase tracking-widest block">
                YOUR STORY
              </span>
              <h2 className="text-4xl sm:text-5xl font-serif font-bold tracking-tight text-[#0F172A] leading-[1.15]">
                Keep the moments<br />
                that matter.
              </h2>
              <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed pt-1 max-w-lg">
                Save the photos, moments, and memories you want to keep close — organized in one private space that belongs to the two of you.
              </p>
            </div>

            {/* In-App Mockup Window */}
            <div className="rounded-2xl border border-slate-200/90 bg-white shadow-xl overflow-hidden max-w-md mx-auto lg:max-w-none">
              {/* Dark Window Top Bar */}
              <div className="bg-[#14233D] px-4 py-3 flex items-center justify-between text-white">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                  <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                  <span className="w-2 h-2 rounded-full bg-slate-400/60"></span>
                </div>
                <span className="text-xs text-slate-300 font-medium">Our Memories</span>
                <div className="w-6"></div>
              </div>

              {/* Window Body */}
              <div className="p-5 space-y-3.5">
                {/* Header */}
                <div className="flex items-center justify-between pb-1">
                  <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">
                    RECENT
                  </span>
                  <span className="text-[10px] font-semibold text-blue-600 hover:underline cursor-pointer">
                    See all
                  </span>
                </div>

                {/* Featured Photo Banner */}
                <div className="relative rounded-xl overflow-hidden h-36 bg-slate-100 group">
                  <img
                    src={morningCoffeeImg}
                    alt="Sunday Morning"
                    className="w-full h-full object-cover object-center brightness-[0.88]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent flex items-end justify-between p-3.5 text-white">
                    <h5 className="font-bold text-xs text-white">Sunday Morning</h5>
                    <span className="text-[10px] text-white/80">Today</span>
                  </div>
                </div>

                {/* Memory List Rows */}
                <div className="space-y-2 pt-1">
                  {/* Item 1 */}
                  <div className="flex items-center justify-between p-2 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                      <div>
                        <h6 className="font-semibold text-xs text-slate-900">Our First Trip</h6>
                        <p className="text-[10px] text-slate-400">24 photos · Jun 2025</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs font-bold">›</span>
                  </div>

                  {/* Item 2 */}
                  <div className="flex items-center justify-between p-2 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2 h-2 rounded-full bg-slate-400"></span>
                      <div>
                        <h6 className="font-semibold text-xs text-slate-900">Sunday Together</h6>
                        <p className="text-[10px] text-slate-400">11 photos · Aug 2025</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs font-bold">›</span>
                  </div>
                </div>

                {/* Footer Privacy Note */}
                <div className="border-t border-slate-100 pt-3 flex items-center gap-2 text-xs text-slate-400">
                  <span className="material-symbols-outlined text-[15px]">lock</span>
                  <span>Visible only to the two of you</span>
                </div>
              </div>
            </div>

            {/* Action Button */}
            <div className="pt-2">
              <a
                href="#how-it-works"
                className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-xl border border-slate-300 bg-white text-slate-800 font-semibold text-xs sm:text-sm hover:bg-slate-50 transition-colors shadow-2xs"
              >
                <span>See how Memories works</span>
                <span className="text-base leading-none">→</span>
              </a>
            </div>
          </div>

        </div>
      </section>

      {/* Dedicated Vault Section — "Save for something that matters." */}
      <section id="vault" className="py-20 sm:py-28 px-6 bg-[#F8FAFC] border-t border-slate-100">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Column: Vault Card Mockup */}
          <div className="lg:col-span-6">
            <div className="max-w-md mx-auto lg:max-w-none">
              
              {/* Card Container */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-xl overflow-hidden">
                
                {/* Dark Header with Balance */}
                <div className="bg-[#14233D] px-6 pt-5 pb-6 text-white space-y-4">
                  {/* Top Bar */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[16px] text-blue-400">water_drop</span>
                      <span className="font-semibold text-xs text-white tracking-wide">OurBloom</span>
                    </div>
                    <span className="text-slate-400 text-xs font-mono">Vault</span>
                  </div>

                  {/* Balance Display */}
                  <div className="space-y-1 pt-1">
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-widest block">
                      SHARED VAULT BALANCE
                    </span>
                    <h3 className="text-3xl sm:text-4xl font-bold tracking-tight text-white font-sans">
                      ₹24,500
                    </h3>
                    <p className="text-[10px] text-slate-400 font-mono">Demonstration data only</p>
                  </div>
                </div>

                {/* White Body */}
                <div className="p-6 space-y-6 bg-white">
                  
                  {/* Shared Goal Section */}
                  <div className="space-y-2">
                    <div className="flex items-start justify-between">
                      <div>
                        <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block">
                          SHARED GOAL
                        </span>
                        <h4 className="font-bold text-sm sm:text-base text-slate-900 mt-0.5">
                          Weekend Getaway
                        </h4>
                      </div>
                      <div className="text-right">
                        <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block">
                          TARGET
                        </span>
                        <h4 className="font-bold text-sm sm:text-base text-slate-900 mt-0.5 font-mono">
                          ₹40,000
                        </h4>
                      </div>
                    </div>

                    {/* Blue Progress Bar */}
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden mt-3">
                      <div className="h-full bg-[#2563EB] rounded-full w-[61%]"></div>
                    </div>

                    {/* Progress Stats */}
                    <div className="flex items-center justify-between text-[11px] pt-1 font-medium">
                      <span className="text-slate-400 font-mono">₹24,500 of ₹40,000</span>
                      <span className="text-[#2563EB] font-semibold">61% contributed</span>
                    </div>
                  </div>

                  {/* Contributions Breakdown */}
                  <div className="space-y-3 pt-2 border-t border-slate-100">
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block">
                      CONTRIBUTIONS
                    </span>
                    
                    {/* You */}
                    <div className="flex items-center text-xs">
                      <span className="text-slate-500 w-14 font-medium">You</span>
                      <div className="flex-1 mx-3 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                        <div className="h-full bg-[#14233D] rounded-full w-[55%]"></div>
                      </div>
                      <span className="font-bold text-slate-800 font-mono text-right w-16">₹13,000</span>
                    </div>

                    {/* Partner */}
                    <div className="flex items-center text-xs">
                      <span className="text-slate-500 w-14 font-medium">Partner</span>
                      <div className="flex-1 mx-3 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                        <div className="h-full bg-slate-300 rounded-full w-[45%]"></div>
                      </div>
                      <span className="font-bold text-slate-800 font-mono text-right w-16">₹11,500</span>
                    </div>
                  </div>

                  {/* Recent Activity */}
                  <div className="space-y-3 pt-2 border-t border-slate-100">
                    <div className="flex items-center justify-between">
                      <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">
                        RECENT ACTIVITY
                      </span>
                      <span className="text-[10px] font-semibold text-blue-600 hover:underline cursor-pointer">
                        View all
                      </span>
                    </div>

                    <div className="space-y-2.5 text-xs">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-semibold text-slate-800">You contributed</p>
                          <p className="text-[10px] text-slate-400">Today</p>
                        </div>
                        <span className="font-bold text-slate-900 font-mono">+₹2,000</span>
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-semibold text-slate-800">Partner contributed</p>
                          <p className="text-[10px] text-slate-400">Yesterday</p>
                        </div>
                        <span className="font-bold text-slate-900 font-mono">+₹1,500</span>
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-semibold text-slate-800">You contributed</p>
                          <p className="text-[10px] text-slate-400">14 Sep</p>
                        </div>
                        <span className="font-bold text-slate-900 font-mono">+₹3,500</span>
                      </div>
                    </div>
                  </div>

                </div>
              </div>

              {/* Caption */}
              <p className="text-xs text-slate-400 text-center mt-3 font-normal">
                Your shared balance, clearly presented.
              </p>

            </div>
          </div>

          {/* Right Column: Copy & Value Pillars */}
          <div className="lg:col-span-6 space-y-8">
            <div className="space-y-4">
              <span className="text-[11px] font-bold text-[#2563EB] uppercase tracking-widest block">
                BUILD TOGETHER
              </span>
              <h2 className="text-4xl sm:text-5xl font-serif font-bold tracking-tight text-[#0F172A] leading-[1.15]">
                Save for something that<br />
                matters.
              </h2>
              <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed pt-1 max-w-lg">
                With the OurBloom Vault, couples can set money aside together for the things they're planning and looking forward to.
              </p>
            </div>

            {/* Three Feature Rows */}
            <div className="space-y-6 pt-2">
              {/* Row 1: Transparent */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-white text-[#2563EB] flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">schedule</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Transparent</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    See what's happening with your shared funds — every contribution, clearly shown.
                  </p>
                </div>
              </div>

              {/* Row 2: Shared */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-white text-[#2563EB] flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">group</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Shared</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    Both partners can participate in building toward the goal, at their own pace.
                  </p>
                </div>
              </div>

              {/* Row 3: Purposeful */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-white text-[#2563EB] flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">star</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Purposeful</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    Save toward something you've decided to build together — nothing more.
                  </p>
                </div>
              </div>
            </div>

            {/* CTA Button */}
            <div className="pt-2">
              <Link
                to={portalDestination}
                className="inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-xl bg-[#0F1E36] text-white font-semibold text-sm hover:bg-slate-800 transition-colors shadow-sm"
              >
                <span>Explore the Vault</span>
                <span className="text-base leading-none">→</span>
              </Link>
            </div>
          </div>

        </div>
      </section>

      {/* Our Story / Why OurBloom Section */}
      <section id="story" className="py-20 sm:py-28 px-6 bg-white border-t border-slate-100">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Column: Visual Collage with Couple Cooking Photo & Micro-Cards */}
          <div className="lg:col-span-6 space-y-4">
            {/* Top Lifestyle Photo Card */}
            <div className="relative rounded-2xl overflow-hidden shadow-md border border-slate-200/80 group">
              <img
                src={cookingTogetherImg}
                alt="Couple cooking together"
                className="w-full h-64 sm:h-80 object-cover object-center group-hover:scale-[1.01] transition-transform duration-500"
              />
              {/* Floating Pill Overlay in Bottom Left */}
              <div className="absolute bottom-4 left-4 inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-black/45 backdrop-blur-md border border-white/20 text-white shadow-sm">
                <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                <span className="text-xs font-semibold tracking-wide">OurBloom</span>
              </div>
            </div>

            {/* Subtle Vertical Connector */}
            <div className="hidden sm:block pl-8 -my-1">
              <div className="w-px h-4 bg-blue-200"></div>
            </div>

            {/* 2x2 Micro-cards Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              
              {/* Card 1: Saved Memory */}
              <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs hover:border-slate-300 transition-colors">
                <div className="flex items-center gap-2 text-slate-700">
                  <div className="w-6 h-6 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                    <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 21c-4.418 0-8-5.373-8-10a8 8 0 1116 0c0 4.627-3.582 10-8 10z" />
                      <circle cx="12" cy="11" r="3" strokeWidth="2.5" />
                    </svg>
                  </div>
                  <span className="text-[11px] font-semibold tracking-wide">Saved Memory</span>
                </div>
                <h4 className="text-sm font-bold text-slate-900 mt-2.5">Lisbon, September</h4>
                <p className="text-xs text-slate-500 mt-0.5">First trip abroad together</p>
              </div>

              {/* Card 2: Upcoming Plan */}
              <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs hover:border-slate-300 transition-colors">
                <div className="flex items-center gap-2 text-slate-700">
                  <div className="w-6 h-6 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                    <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                      <line x1="16" y1="2" x2="16" y2="6" strokeLinecap="round" />
                      <line x1="8" y1="2" x2="8" y2="6" strokeLinecap="round" />
                      <line x1="3" y1="10" x2="21" y2="10" />
                    </svg>
                  </div>
                  <span className="text-[11px] font-semibold tracking-wide">Upcoming Plan</span>
                </div>
                <h4 className="text-sm font-bold text-slate-900 mt-2.5">Weekend in the mountains</h4>
                <p className="text-xs text-slate-500 mt-0.5">3 weeks away · Both confirmed</p>
              </div>

              {/* Card 3: Shared Goal */}
              <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs hover:border-slate-300 transition-colors">
                <div className="flex items-center gap-2 text-slate-700">
                  <div className="w-6 h-6 rounded-full bg-amber-50 text-amber-500 flex items-center justify-center shrink-0">
                    <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
                    </svg>
                  </div>
                  <span className="text-[11px] font-semibold tracking-wide">Shared Goal</span>
                </div>
                <h4 className="text-sm font-bold text-slate-900 mt-2.5">Move somewhere new</h4>
                <div className="mt-2.5 h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div className="h-full bg-blue-600 rounded-full w-[62%]"></div>
                </div>
                <p className="text-[11px] text-slate-400 mt-1.5 font-medium">62% of steps done</p>
              </div>

              {/* Card 4: Note */}
              <div className="bg-[#FFFDF5] rounded-2xl p-4 border border-amber-200/80 shadow-xs hover:border-amber-300 transition-colors">
                <div className="flex items-center gap-2 text-amber-800">
                  <div className="w-6 h-6 rounded-full bg-amber-100/70 text-amber-700 flex items-center justify-center shrink-0">
                    <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L6.832 19.82a4.5 4.5 0 01-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 011.13-1.897L16.863 4.487z" />
                    </svg>
                  </div>
                  <span className="text-[11px] font-semibold tracking-wide">Note</span>
                </div>
                <p className="italic text-xs sm:text-[13px] font-serif text-[#92400E] leading-relaxed mt-2.5">
                  &ldquo;Don&apos;t forget &mdash; the little things count too.&rdquo;
                </p>
              </div>

            </div>
          </div>

          {/* Right Column: Headline, Narrative & Value Rows */}
          <div className="lg:col-span-6 space-y-6">
            {/* Eyebrow */}
            <div className="inline-flex items-center gap-2">
              <span className="flex items-center -space-x-1">
                <span className="w-2.5 h-2.5 rounded-full bg-[#0F1E36]"></span>
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600"></span>
              </span>
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Why OurBloom
              </span>
            </div>

            {/* Headline */}
            <h2 className="text-3xl sm:text-4xl lg:text-[42px] font-bold text-[#0F1E36] tracking-tight leading-[1.18]">
              Built around what matters between two people.
            </h2>

            {/* Narrative Subtitle */}
            <p className="text-base sm:text-lg text-slate-500 leading-relaxed max-w-xl">
              OurBloom was created to bring the everyday parts of a shared life into one thoughtful private space &mdash; the memories, plans, experiences, and things you&apos;re building together.
            </p>

            {/* Three Pillar List */}
            <div className="mt-8 border-t border-slate-100">
              
              {/* Pillar 1: Remember */}
              <div className="py-5 sm:py-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-baseline gap-2 sm:gap-6">
                <span className="font-semibold text-slate-900 text-sm sm:text-base w-28 shrink-0">
                  Remember
                </span>
                <span className="text-slate-500 text-sm sm:text-base leading-relaxed">
                  Keep the moments that become part of your story.
                </span>
              </div>

              {/* Pillar 2: Build */}
              <div className="py-5 sm:py-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-baseline gap-2 sm:gap-6">
                <span className="font-semibold text-slate-900 text-sm sm:text-base w-28 shrink-0">
                  Build
                </span>
                <span className="text-slate-500 text-sm sm:text-base leading-relaxed">
                  Create plans and goals that belong to both of you.
                </span>
              </div>

              {/* Pillar 3: Share */}
              <div className="py-5 sm:py-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-baseline gap-2 sm:gap-6">
                <span className="font-semibold text-slate-900 text-sm sm:text-base w-28 shrink-0">
                  Share
                </span>
                <span className="text-slate-500 text-sm sm:text-base leading-relaxed">
                  Bring the meaningful parts of everyday life into one private space.
                </span>
              </div>

            </div>

            {/* Bottom Sign-off */}
            <div className="pt-4 flex items-center gap-2.5 text-xs sm:text-sm text-slate-400 font-medium">
              <span className="flex items-center -space-x-0.5">
                <span className="w-2 h-2 rounded-full bg-[#0F1E36]"></span>
                <span className="w-2 h-2 rounded-full bg-blue-600"></span>
              </span>
              <span>OurBloom &mdash; a private space for two</span>
            </div>

          </div>

        </div>
      </section>

      {/* Security & Privacy Section — "Private By Design" */}
      <section id="security" className="py-20 sm:py-28 px-6 bg-white border-t border-slate-100">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Column: Interactive Privacy Mockup */}
          <div className="lg:col-span-6">
            <div className="relative max-w-md mx-auto lg:max-w-none">
              
              {/* Floating Dark Pill Badge */}
              <div className="absolute -bottom-3.5 right-6 sm:right-8 z-20 inline-flex items-center gap-1.5 bg-[#0F1E36] text-white text-[11px] font-semibold px-4 py-2 rounded-full shadow-xl">
                <span>Visible only to you two</span>
              </div>

              {/* Browser Window Frame */}
              <div className="rounded-2xl border border-slate-200/90 bg-white shadow-xl overflow-hidden">
                {/* Top Address Bar Chrome */}
                <div className="px-4 py-3 bg-slate-50/80 border-b border-slate-100 flex items-center">
                  <div className="flex items-center gap-1.5 w-16">
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-300"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-300"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-300"></span>
                  </div>
                  <div className="flex-1 flex justify-center">
                    <div className="bg-slate-100/90 border border-slate-200/50 rounded-md px-5 py-1 text-[11px] font-mono text-slate-500 flex items-center gap-1.5 shadow-2xs">
                      <span className="material-symbols-outlined text-[13px] text-slate-400">lock</span>
                      <span>ourbloom.app / shared-space</span>
                    </div>
                  </div>
                  <div className="w-16"></div>
                </div>

                {/* Window Body */}
                <div className="p-6 space-y-5 bg-white">
                  {/* Couple Header */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="flex items-center -space-x-1.5">
                        <div className="w-7 h-7 rounded-full bg-[#2563EB] text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
                          A
                        </div>
                        <div className="w-7 h-7 rounded-full bg-purple-600 text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
                          M
                        </div>
                      </div>
                      <div>
                        <h4 className="font-bold text-slate-900 text-xs sm:text-sm">Alex &amp; Morgan</h4>
                        <p className="text-[10px] text-slate-400 flex items-center gap-1">
                          <span className="text-red-500">❤️</span> 3 years together
                        </p>
                      </div>
                    </div>

                    {/* Private space tag */}
                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 border border-blue-200/80 text-blue-700 text-[10px] font-semibold">
                      <span className="w-1.5 h-1.5 rounded-full bg-blue-600"></span>
                      <span>Private space</span>
                    </div>
                  </div>

                  {/* Our Memories */}
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">
                        OUR MEMORIES
                      </span>
                      <span className="text-[10px] font-semibold text-blue-600 hover:underline cursor-pointer">
                        See all
                      </span>
                    </div>

                    {/* 3 memory tiles */}
                    <div className="grid grid-cols-3 gap-2.5">
                      {/* Tile 1: Amalfi Trip */}
                      <div className="border border-blue-100 bg-blue-50/50 rounded-xl p-3 flex flex-col items-center justify-center text-center space-y-1.5 h-20">
                        <span className="material-symbols-outlined text-[18px] text-blue-500">image</span>
                        <span className="font-semibold text-[11px] text-slate-800 leading-tight">Amalfi Trip</span>
                      </div>

                      {/* Tile 2: Anniversary */}
                      <div className="border border-pink-100 bg-pink-50/50 rounded-xl p-3 flex flex-col items-center justify-center text-center space-y-1.5 h-20">
                        <span className="material-symbols-outlined text-[18px] text-pink-500">photo_library</span>
                        <span className="font-semibold text-[11px] text-slate-800 leading-tight">Anniversary</span>
                      </div>

                      {/* Tile 3: Home */}
                      <div className="border border-emerald-100 bg-emerald-50/50 rounded-xl p-3 flex flex-col items-center justify-center text-center space-y-1.5 h-20">
                        <span className="material-symbols-outlined text-[18px] text-emerald-500">holiday_village</span>
                        <span className="font-semibold text-[11px] text-slate-800 leading-tight">Home</span>
                      </div>
                    </div>
                  </div>

                  {/* Recent Activity */}
                  <div className="space-y-2.5 pt-1">
                    <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider block">
                      RECENT ACTIVITY
                    </span>

                    <div className="space-y-2 text-xs">
                      <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50/60">
                        <div className="flex items-center gap-2.5">
                          <div className="w-5 h-5 rounded-full bg-[#2563EB] text-white text-[9px] font-bold flex items-center justify-center">A</div>
                          <span className="text-slate-700 text-[11px]">Added a memory to Amalfi Trip</span>
                        </div>
                        <span className="text-[10px] text-slate-400">2h ago</span>
                      </div>

                      <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50/60">
                        <div className="flex items-center gap-2.5">
                          <div className="w-5 h-5 rounded-full bg-purple-600 text-white text-[9px] font-bold flex items-center justify-center">M</div>
                          <span className="text-slate-700 text-[11px]">Updated shared wishlist</span>
                        </div>
                        <span className="text-[10px] text-slate-400">Yesterday</span>
                      </div>
                    </div>
                  </div>

                  {/* Space controls */}
                  <div className="border border-slate-100 rounded-xl p-3 bg-slate-50/50 flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs font-semibold text-slate-800">
                      <span className="material-symbols-outlined text-[16px] text-blue-600">verified_user</span>
                      <span>Space controls</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        className="bg-white border border-slate-200 text-slate-700 text-[10px] font-medium px-2.5 py-1 rounded-md shadow-2xs hover:bg-slate-50 transition-colors"
                      >
                        Notifications
                      </button>
                      <button
                        type="button"
                        className="bg-white border border-slate-200 text-slate-700 text-[10px] font-medium px-2.5 py-1 rounded-md shadow-2xs hover:bg-slate-50 transition-colors"
                      >
                        Access
                      </button>
                    </div>
                  </div>

                </div>
              </div>

            </div>
          </div>

          {/* Right Column: Copy & Value Pillars */}
          <div className="lg:col-span-6 space-y-8">
            <div className="space-y-4">
              {/* Private By Design Tag */}
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-blue-200/80 bg-blue-50/60 text-[#2563EB] text-xs font-semibold">
                <span className="material-symbols-outlined text-[15px]">verified_user</span>
                <span>PRIVATE BY DESIGN</span>
              </div>

              {/* Main Headline */}
              <h2 className="text-4xl sm:text-5xl font-bold tracking-tight text-[#0F172A] leading-[1.12]">
                Your space should<br />
                stay yours.
              </h2>

              {/* Subtitle */}
              <p className="text-base sm:text-lg text-slate-500 font-normal leading-relaxed pt-1 max-w-lg">
                OurBloom is designed to keep your shared memories, moments, and personal experiences within your private space — with clear controls and thoughtful product design.
              </p>
            </div>

            {/* Three Value Points */}
            <div className="space-y-6 pt-2">
              {/* Point 1: Private */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-slate-50/60 text-slate-500 flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">visibility_off</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Private</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    Your shared space is designed for the two people who belong in it.
                  </p>
                </div>
              </div>

              {/* Point 2: Controlled */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-slate-50/60 text-slate-500 flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">tune</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Controlled</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    Account and shared-space actions should be understandable and intentional.
                  </p>
                </div>
              </div>

              {/* Point 3: Transparent */}
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl border border-slate-200/80 bg-slate-50/60 text-slate-500 flex items-center justify-center flex-shrink-0 shadow-2xs">
                  <span className="material-symbols-outlined text-[20px]">info</span>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-sm sm:text-base text-slate-900">Transparent</h3>
                  <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                    Important actions and information are presented clearly, not hidden behind complicated interfaces.
                  </p>
                </div>
              </div>
            </div>

            {/* Bottom Statement / Philosophy */}
            <div className="pt-8 border-t border-slate-100 flex items-start gap-3">
              <span className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0 mt-1.5"></span>
              <p className="text-xs text-slate-400 font-normal leading-relaxed">
                We don't make promises we can't keep. OurBloom is built around clarity, control, and thoughtful defaults — not marketing language.
              </p>
            </div>
          </div>

        </div>
      </section>

      {/* Final Call to Action Section */}
      <section className="py-20 sm:py-28 px-6 bg-white border-t border-slate-100">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Column: Heading, Copy, Buttons & Micro Disclaimer */}
          <div className="lg:col-span-6 space-y-6">
            {/* Eyebrow */}
            <div className="flex items-center gap-2">
              <span className="w-4 h-0.5 bg-blue-600 rounded-full"></span>
              <span className="text-xs font-bold uppercase tracking-wider text-blue-600">
                Start Together
              </span>
            </div>

            {/* Headline */}
            <h2 className="text-4xl sm:text-5xl lg:text-[52px] font-bold text-[#0F1E36] tracking-tight leading-[1.12]">
              Make space for <br className="hidden sm:inline" />what matters.
            </h2>

            {/* Subtitle */}
            <p className="text-base sm:text-lg text-slate-500 leading-relaxed max-w-lg">
              Bring your memories, plans, experiences, and shared goals into one private space built for the two of you.
            </p>

            {/* Action Buttons */}
            <div className="pt-2 flex flex-wrap items-center gap-4">
              <Link
                to={portalDestination}
                className="inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-xl bg-[#0F1E36] text-white font-semibold text-sm hover:bg-slate-800 transition-colors shadow-sm"
              >
                <span>Get Started</span>
                <span className="text-base leading-none">&rarr;</span>
              </Link>

              <a
                href="#features"
                className="inline-flex items-center justify-center px-6 py-3.5 rounded-xl bg-white text-slate-800 font-semibold text-sm border border-slate-200/90 hover:bg-slate-50 transition-colors shadow-2xs"
              >
                Explore OurBloom
              </a>
            </div>

            {/* Micro disclaimer */}
            <p className="text-xs text-slate-400 font-normal">
              A private space for two. No setup fees, no commitments.
            </p>
          </div>

          {/* Right Column: Floating Couple App Mockup Card */}
          <div className="lg:col-span-6 flex flex-col items-center lg:items-end">
            <div className="w-full max-w-md">
              {/* App Card */}
              <div className="rounded-2xl border border-slate-200/80 bg-white shadow-xl shadow-slate-200/50 overflow-hidden">
                
                {/* Dark Header */}
                <div className="bg-[#0F1E36] px-5 py-3.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                    <span className="text-xs font-semibold text-white tracking-wide">OurBloom</span>
                  </div>
                  <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/10 text-slate-300 text-[10px] font-medium border border-white/10">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                    <span>Private space</span>
                  </div>
                </div>

                {/* Couple Header */}
                <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-3 bg-white">
                  <div className="flex items-center">
                    <span className="w-8 h-8 rounded-full bg-blue-600 text-white font-bold text-xs flex items-center justify-center ring-2 ring-white z-10">
                      J
                    </span>
                    <span className="w-8 h-8 rounded-full bg-[#0F1E36] text-white font-bold text-xs flex items-center justify-center ring-2 ring-white -ml-2.5">
                      S
                    </span>
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900 leading-tight">Jamie &amp; Sam</h4>
                    <p className="text-[11px] text-slate-400 mt-0.5 font-normal">Shared space &middot; 2 members</p>
                  </div>
                </div>

                {/* Items List */}
                <div className="p-4 space-y-2.5 bg-white">
                  
                  {/* Item 1: Kyoto trip */}
                  <div className="p-3 rounded-xl border border-slate-100 bg-[#F8FAFC]/60 hover:bg-[#F8FAFC] transition-colors flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                          <circle cx="8.5" cy="8.5" r="1.5" />
                          <polyline points="21 15 16 10 5 21" />
                        </svg>
                      </div>
                      <div>
                        <h5 className="text-xs font-bold text-slate-900 leading-tight">Kyoto trip</h5>
                        <p className="text-[11px] text-slate-400 mt-0.5">Memory &middot; 14 photos</p>
                      </div>
                    </div>
                    <span className="text-[10px] text-slate-400 font-medium">3d ago</span>
                  </div>

                  {/* Item 2: New apartment search */}
                  <div className="p-3 rounded-xl border border-slate-100 bg-[#F8FAFC]/60 hover:bg-[#F8FAFC] transition-colors flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                          <line x1="16" y1="2" x2="16" y2="6" />
                          <line x1="8" y1="2" x2="8" y2="6" />
                          <line x1="3" y1="10" x2="21" y2="10" />
                        </svg>
                      </div>
                      <div>
                        <h5 className="text-xs font-bold text-slate-900 leading-tight">New apartment search</h5>
                        <p className="text-[11px] text-slate-400 mt-0.5">Goal &middot; 4 of 7 steps done</p>
                      </div>
                    </div>
                    <span className="text-[10px] text-slate-400 font-medium">Active</span>
                  </div>

                  {/* Item 3: Activity Row */}
                  <div className="pt-2 px-1 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2.5">
                      <span className="w-5 h-5 rounded-full bg-blue-600 text-white text-[10px] font-bold flex items-center justify-center shrink-0">
                        J
                      </span>
                      <span className="text-xs text-slate-600">Added a note to Kyoto trip</span>
                    </div>
                    <span className="text-[10px] text-slate-400 font-medium">1h ago</span>
                  </div>

                </div>

              </div>

              {/* Caption underneath */}
              <p className="text-xs text-slate-400 text-center mt-4 tracking-wide">
                Your shared space, waiting.
              </p>
            </div>
          </div>

        </div>
      </section>

      {/* Footer */}
      <footer className="py-10 px-6 bg-white border-t border-slate-100 text-xs text-slate-400">
        <div className="max-w-7xl mx-auto space-y-6">
          {/* Main Footer Row matching mockup */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            {/* Left: Brand Logo */}
            <Link to="/" className="flex items-center group">
              <OurBloomLogo variant="primary" iconSize="w-5 h-6" textSize="text-sm font-bold" />
            </Link>

            {/* Center: Tagline */}
            <p className="text-slate-400 text-xs sm:text-sm font-normal text-center">
              A private space for the two of you.
            </p>

            {/* Right: Copyright */}
            <span className="text-slate-400 text-xs font-normal">
              &copy; {new Date().getFullYear()} OurBloom
            </span>
          </div>

          {/* Legal Compliance Links */}
          <div className="pt-4 border-t border-slate-100 flex flex-wrap items-center justify-center gap-6 text-[11px] text-slate-400">
            <Link to="/about" className="hover:text-slate-600 transition-colors">About Us</Link>
            <Link to="/terms" className="hover:text-slate-600 transition-colors">Terms &amp; Conditions</Link>
            <Link to="/privacy" className="hover:text-slate-600 transition-colors">Privacy Policy</Link>
            <Link to="/refund" className="hover:text-slate-600 transition-colors">Refund Policy</Link>
            <Link to="/shipping" className="hover:text-slate-600 transition-colors">Delivery Policy</Link>
            <Link to="/contact" className="hover:text-slate-600 transition-colors">Contact Us</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
