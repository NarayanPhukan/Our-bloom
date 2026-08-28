import { useState, useEffect } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Header() {
  const location = useLocation();
  const { slug } = useParams();
  const { user, logout, updateNickname } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const [showNicknameEdit, setShowNicknameEdit] = useState(false);
  const [nicknameInput, setNicknameInput] = useState(user?.nicknameForPartner || '');

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { to: `/c/${slug}`, label: 'Journey', exact: true },
    { to: `/c/${slug}/memories`, label: 'Memories' },
    { to: `/c/${slug}/love-notes`, label: 'Love Notes' },
    { to: `/c/${slug}/map`, label: 'Dream Map ✨' },
  ];

  const isActive = (link) => {
    if (link.exact) return location.pathname === link.to;
    return location.pathname.startsWith(link.to);
  };

  const handleNicknameSave = async () => {
    try {
      await updateNickname(nicknameInput);
      setShowNicknameEdit(false);
    } catch (err) {
      console.error('Failed to update nickname', err);
    }
  };

  return (
    <header className="fixed top-0 w-full z-50 bg-surface/80 backdrop-blur-md shadow-[0_20px_40px_rgba(222,191,194,0.08)] transition-all duration-300">
      <nav className={`flex justify-between items-center px-6 md:px-margin-desktop max-w-container-max mx-auto transition-all duration-300 ${isScrolled ? 'py-2' : 'py-4'}`}>
        {/* Logo */}
        <Link
          to={`/c/${slug}`}
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
                isActive(link)
                  ? 'text-primary border-b-2 border-primary pb-1'
                  : 'text-on-surface-variant hover:text-secondary'
              }`}
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Actions */}
        <div className="flex items-center space-x-3">
          {/* Nickname edit */}
          <div className="relative">
            <button
              onClick={() => setShowNicknameEdit(!showNicknameEdit)}
              className="text-primary hover:text-secondary transition-colors duration-300 flex items-center gap-1"
              title="Edit nickname for your partner"
            >
              <span
                className="material-symbols-outlined"
                style={{ fontVariationSettings: "'FILL' 1" }}
              >
                favorite
              </span>
              <span className="hidden md:inline text-xs font-label-sm">{user?.name}</span>
            </button>

            {showNicknameEdit && (
              <div className="absolute right-0 top-full mt-2 bg-surface rounded-2xl shadow-xl border border-primary/10 p-4 w-72 z-50">
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">
                  Nickname for your partner
                </label>
                <input
                  type="text"
                  value={nicknameInput}
                  onChange={(e) => setNicknameInput(e.target.value)}
                  placeholder="e.g. Kuchupuchu"
                  className="w-full bg-surface-container border border-outline-variant rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-primary mb-3"
                />
                <div className="flex gap-2 justify-end">
                  <button onClick={() => setShowNicknameEdit(false)} className="text-xs px-3 py-1.5 text-on-surface-variant hover:bg-surface-variant rounded-full transition-colors">Cancel</button>
                  <button onClick={handleNicknameSave} className="text-xs px-4 py-1.5 bg-primary text-on-primary rounded-full hover:bg-secondary transition-colors">Save</button>
                </div>
              </div>
            )}
          </div>

          {/* Logout */}
          <button
            onClick={logout}
            className="text-on-surface-variant hover:text-error transition-colors duration-300"
            title="Logout"
          >
            <span className="material-symbols-outlined text-[20px]">logout</span>
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
                  isActive(link)
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
