import { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';

export default function Header() {
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { to: '/', label: 'Journey' },
    { to: '/memories', label: 'Memories' },
    { to: '/love-notes', label: 'Love Notes' },
  ];

  return (
    <header className="fixed top-0 w-full z-50 bg-surface/80 backdrop-blur-md shadow-[0_20px_40px_rgba(222,191,194,0.08)] transition-all duration-300">
      <nav className={`flex justify-between items-center px-6 md:px-margin-desktop max-w-container-max mx-auto transition-all duration-300 ${isScrolled ? 'py-2' : 'py-4'}`}>
        {/* Logo */}
        <Link
          to="/"
          className="font-headline-md text-headline-md text-primary hover:opacity-80 transition-opacity duration-300"
        >
          Our Bloom
        </Link>

        {/* Desktop Nav */}
        <div className="hidden md:flex space-x-8 items-center">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`font-body-md text-body-md transition-all duration-300 ease-in-out ${
                location.pathname === link.to
                  ? 'text-primary border-b-2 border-primary pb-1'
                  : 'text-on-surface-variant hover:text-secondary'
              }`}
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Actions */}
        <div className="flex items-center space-x-4">
          <button className="text-primary hover:text-secondary transition-colors duration-300">
            <span
              className="material-symbols-outlined"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              favorite
            </span>
          </button>

          {/* Mobile Menu Toggle */}
          <button
            className="md:hidden text-primary"
            onClick={() => setMobileOpen(!mobileOpen)}
          >
            <span className="material-symbols-outlined">
              {mobileOpen ? 'close' : 'menu'}
            </span>
          </button>
        </div>
      </nav>

      {/* Mobile Menu */}
      {mobileOpen && (
        <div className="md:hidden bg-surface/95 backdrop-blur-lg border-t border-outline-variant/30 animate-[slideInUp_0.3s_ease-out]">
          <div className="flex flex-col px-6 py-4 space-y-4">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setMobileOpen(false)}
                className={`font-body-md text-body-md py-2 transition-all duration-300 ${
                  location.pathname === link.to
                    ? 'text-primary font-semibold'
                    : 'text-on-surface-variant hover:text-secondary'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </div>
        </div>
      )}
    </header>
  );
}
