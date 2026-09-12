import { Link } from 'react-router-dom';

export default function LegalContainer({ title, lastUpdated, children }) {
  return (
    <div className="min-h-screen bg-lily-pattern text-on-background flex flex-col justify-between">
      {/* Top Navigation */}
      <header className="w-full border-b border-primary/10 bg-surface/80 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-5 py-4 flex items-center justify-between flex-wrap gap-4">
          <Link to="/" className="font-serif text-xl font-bold text-primary flex items-center gap-2">
            🌸 Our Bloom
          </Link>
          <nav className="flex items-center gap-4 text-xs font-medium text-on-surface-variant flex-wrap">
            <Link to="/about" className="hover:text-primary transition-colors">About</Link>
            <Link to="/privacy" className="hover:text-primary transition-colors">Privacy</Link>
            <Link to="/terms" className="hover:text-primary transition-colors">Terms</Link>
            <Link to="/refund-policy" className="hover:text-primary transition-colors">Refunds</Link>
            <Link to="/shipping-policy" className="hover:text-primary transition-colors">Delivery</Link>
            <Link to="/contact" className="hover:text-primary transition-colors">Contact</Link>
          </nav>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-5 py-12 flex-1 w-full">
        <div className="bg-surface/90 backdrop-blur-md border border-primary/10 rounded-[32px] p-8 md:p-12 shadow-xl shadow-primary/5">
          <h1 className="font-serif text-3xl md:text-4xl text-on-surface font-bold mb-3">{title}</h1>
          {lastUpdated && (
            <p className="text-xs text-on-surface-variant pb-6 mb-8 border-b border-outline-variant/30">
              {lastUpdated}
            </p>
          )}
          <div className="legal-content text-sm md:text-base leading-relaxed text-on-surface/90 space-y-6">
            {children}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full border-t border-primary/10 bg-surface/60 py-8 px-5">
        <div className="max-w-4xl mx-auto text-center text-xs text-on-surface-variant/70 space-y-3">
          <div className="flex flex-wrap justify-center gap-3">
            <Link to="/about" className="hover:text-primary">About</Link>
            <span>•</span>
            <Link to="/privacy" className="hover:text-primary">Privacy Policy</Link>
            <span>•</span>
            <Link to="/terms" className="hover:text-primary">Terms &amp; Conditions</Link>
            <span>•</span>
            <Link to="/refund-policy" className="hover:text-primary">Refund Policy</Link>
            <span>•</span>
            <Link to="/shipping-policy" className="hover:text-primary">Delivery</Link>
            <span>•</span>
            <Link to="/contact" className="hover:text-primary">Contact Support</Link>
          </div>
          <p>© 2026 Our Bloom. With love, forever &amp; always. Registered Entity: Our Bloom, India.</p>
        </div>
      </footer>
    </div>
  );
}
