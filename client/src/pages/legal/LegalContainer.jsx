import { Link } from 'react-router-dom';
import OurBloomLogo from '../../components/OurBloomLogo';

export default function LegalContainer({ title, lastUpdated, children }) {
  return (
    <div className="min-h-screen bg-[#F8FAFC] text-[#0A192F] flex flex-col justify-between selection:bg-blue-100 selection:text-blue-900">
      {/* Top Navigation */}
      <header className="w-full border-b border-slate-200 bg-white/90 backdrop-blur-md sticky top-0 z-50 shadow-sm">
        <div className="max-w-5xl mx-auto px-5 py-3.5 flex items-center justify-between flex-wrap gap-4">
          <Link to="/" className="flex items-center">
            <OurBloomLogo variant="primary" iconSize="w-5 h-6" textSize="text-base" />
          </Link>

          <nav className="flex items-center gap-4 text-xs font-semibold text-slate-600 flex-wrap">
            <Link to="/" className="hover:text-blue-600 transition-colors">Home</Link>
            <Link to="/about" className="hover:text-blue-600 transition-colors">About</Link>
            <Link to="/privacy" className="hover:text-blue-600 transition-colors">Privacy</Link>
            <Link to="/terms" className="hover:text-blue-600 transition-colors">Terms</Link>
            <Link to="/refund-policy" className="hover:text-blue-600 transition-colors">Refunds</Link>
            <Link to="/shipping-policy" className="hover:text-blue-600 transition-colors">Delivery</Link>
            <Link to="/contact" className="hover:text-blue-600 transition-colors">Contact</Link>
          </nav>
        </div>
      </header>

      {/* Main Legal Content Container */}
      <main className="max-w-4xl mx-auto px-5 py-10 md:py-14 flex-1 w-full">
        <div className="bg-white border border-slate-200 rounded-2xl p-8 md:p-12 shadow-xl shadow-slate-200/50 space-y-6">
          <div className="border-b border-slate-100 pb-5">
            <div className="inline-flex items-center gap-1.5 bg-blue-50 text-blue-700 px-3 py-0.5 rounded-full text-xs font-semibold mb-3 border border-blue-200">
              <span className="material-symbols-outlined text-[14px]">policy</span>
              Official Legal Disclosure
            </div>
            <h1 className="text-2xl sm:text-3xl md:text-4xl font-extrabold text-[#0F2744] tracking-tight">{title}</h1>
            {lastUpdated && (
              <p className="text-xs text-slate-500 mt-2 font-medium">
                {lastUpdated}
              </p>
            )}
          </div>

          <div className="legal-content text-sm md:text-base leading-relaxed text-slate-700 space-y-6">
            {children}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full border-t border-navy-800 bg-[#0A192F] text-slate-400 py-10 px-5 text-xs">
        <div className="max-w-4xl mx-auto text-center space-y-4">
          <div className="flex flex-wrap justify-center gap-3 font-medium">
            <Link to="/" className="hover:text-white">Home</Link>
            <span>•</span>
            <Link to="/about" className="hover:text-white">About Us</Link>
            <span>•</span>
            <Link to="/privacy" className="hover:text-white">Privacy Policy</Link>
            <span>•</span>
            <Link to="/terms" className="hover:text-white">Terms &amp; Conditions</Link>
            <span>•</span>
            <Link to="/refund-policy" className="hover:text-white">Refund Policy</Link>
            <span>•</span>
            <Link to="/shipping-policy" className="hover:text-white">Delivery Policy</Link>
            <span>•</span>
            <Link to="/contact" className="hover:text-white">Contact &amp; Grievance</Link>
          </div>
          <p className="text-[11px] text-slate-500">
            © 2026 Our Bloom. All rights reserved. Registered Entity: Our Bloom, India.
          </p>
        </div>
      </footer>
    </div>
  );
}
