import { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import { isNativeApp } from '../utils/capacitorPlugins';
import logoImage from '../assets/logo.jpg';

export default function Header() {
  const location = useLocation();
  const { slug } = useParams();
  const { user, couple, logout, updateNickname } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const [showNicknameEdit, setShowNicknameEdit] = useState(false);
  const [nicknameInput, setNicknameInput] = useState(user?.nicknameForPartner || '');
  const [showNotifications, setShowNotifications] = useState(false);
  const { notifications, unreadCount, markAllAsRead } = useNotifications();
  const menuRef = useRef(null);
  const isNative = isNativeApp();

  const partner = couple && user ? (
    couple.user1?._id === user._id ? couple.user2 : couple.user1
  ) : null;
  const partnerNicknameForMe = partner?.nicknameForPartner || user?.name;

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Close mobile menu on outside click
  useEffect(() => {
    if (!mobileOpen) return;
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMobileOpen(false);
      }
    };
    // Small delay to avoid closing immediately on the toggle button
    const timer = setTimeout(() => {
      document.addEventListener('touchstart', handleClickOutside);
      document.addEventListener('click', handleClickOutside);
    }, 100);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('touchstart', handleClickOutside);
      document.removeEventListener('click', handleClickOutside);
    };
  }, [mobileOpen]);

  // Close mobile menu on route change
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

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
    <header className="fixed top-0 w-full z-50 bg-surface/80 backdrop-blur-md shadow-[0_20px_40px_rgba(222,191,194,0.08)] transition-all duration-300" style={{ paddingTop: 'env(safe-area-inset-top, 0px)' }}>
      <nav className={`flex justify-between items-center gap-8 px-6 md:px-margin-desktop max-w-container-max mx-auto transition-all duration-300 ${isScrolled ? 'py-2' : 'py-4'}`}>
        {/* Logo */}
        <div className="flex-shrink-0">
          <Link
            to={`/c/${slug}`}
            className="flex items-center gap-2 font-headline-md text-headline-md text-primary hover:opacity-80 transition-opacity duration-300 whitespace-nowrap"
          >
            <img src={logoImage} alt="Our Bloom Logo" className="w-8 h-8 rounded-full object-cover shadow-sm" />
            Our Bloom
          </Link>
        </div>

        {/* Desktop Nav */}
        <div className="hidden lg:flex justify-center space-x-6 items-center flex-1">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`font-body-md text-body-md transition-all duration-300 ease-in-out whitespace-nowrap ${
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
        <div className="flex justify-end items-center space-x-2 md:space-x-4 flex-shrink-0">
          {/* Notifications */}
          <div className="relative">
            <button
              onClick={() => {
                setShowNotifications(!showNotifications);
                setShowNicknameEdit(false);
                if (unreadCount > 0 && !showNotifications) {
                  markAllAsRead();
                }
              }}
              className="text-on-surface-variant hover:text-primary transition-colors duration-300 relative p-2 min-w-[44px] min-h-[44px] flex items-center justify-center"
              title="Notifications"
            >
              <span className="material-symbols-outlined text-[24px]">notifications</span>
              {unreadCount > 0 && (
                <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-error rounded-full shadow-sm animate-pulse"></span>
              )}
            </button>

            {showNotifications && (
              <div className="absolute right-0 top-full mt-2 bg-surface rounded-2xl shadow-xl border border-primary/10 w-80 z-50 overflow-hidden">
                <div className="p-4 border-b border-outline-variant/30 bg-surface-container-lowest">
                  <h3 className="font-label-sm text-primary uppercase tracking-wider font-bold">Recent Updates</h3>
                </div>
                <div className="max-h-[300px] overflow-y-auto">
                  {notifications.length > 0 ? (
                    notifications.map((notif) => (
                      <div key={notif.id} className="p-4 border-b border-outline-variant/10 hover:bg-surface-variant/20 transition-colors">
                        <div className="flex items-start gap-3">
                          <span className="material-symbols-outlined text-primary mt-0.5">
                            {notif.type === 'memory_added' ? 'photo_camera' : notif.type === 'note_added' ? 'mail' : 'favorite'}
                          </span>
                          <div>
                            <p className="text-sm text-on-surface leading-tight mb-1">{notif.message}</p>
                            <p className="text-[10px] text-on-surface-variant uppercase tracking-wider">
                              {new Date(notif.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </p>
                          </div>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="p-6 text-center text-on-surface-variant text-sm italic">
                      No new updates yet.
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Nickname edit */}
          <div className="relative">
            <button
              onClick={() => {
                setShowNicknameEdit(!showNicknameEdit);
                setShowNotifications(false);
              }}
              className="hidden md:flex text-primary hover:text-secondary transition-colors duration-300 items-center gap-1 whitespace-nowrap p-2 min-w-[44px] min-h-[44px]"
              title="Edit nickname for your partner"
            >
              <span
                className="material-symbols-outlined"
                style={{ fontVariationSettings: "'FILL' 1" }}
              >
                favorite
              </span>
              <span className="hidden lg:inline text-xs font-label-sm truncate max-w-[100px]">{partnerNicknameForMe}</span>
            </button>

            {showNicknameEdit && (
              <div className="fixed md:absolute inset-x-4 md:inset-x-auto top-20 md:right-0 md:top-full md:mt-2 bg-surface rounded-2xl shadow-xl border border-primary/10 p-4 md:w-72 z-50">
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
                  <button onClick={() => setShowNicknameEdit(false)} className="text-xs px-3 py-2 text-on-surface-variant hover:bg-surface-variant rounded-full transition-colors min-h-[44px]">Cancel</button>
                  <button onClick={handleNicknameSave} className="text-xs px-4 py-2 bg-primary text-on-primary rounded-full hover:bg-secondary transition-colors min-h-[44px]">Save</button>
                </div>
              </div>
            )}
          </div>

          {/* Logout (Desktop) */}
          <button
            onClick={logout}
            className="hidden md:flex text-on-surface-variant hover:text-error transition-colors duration-300 p-2 min-w-[44px] min-h-[44px] items-center justify-center"
            title="Logout"
          >
            <span className="material-symbols-outlined text-[24px]">logout</span>
          </button>

          {/* Download App (Desktop only, web only) */}
          {!isNative && (
            <a
              href="/OurBloom.apk"
              download
              className="hidden lg:flex items-center gap-1.5 bg-primary text-on-primary px-3 py-1.5 rounded-full hover:bg-secondary transition-colors duration-300 shadow-glow-primary font-label-sm tracking-wide uppercase whitespace-nowrap min-h-[44px]"
            >
              <span className="material-symbols-outlined text-[18px]">download</span>
              <span>Download</span>
            </a>
          )}

          {/* Mobile Menu Toggle */}
          <button
            className="md:hidden text-primary p-2 min-w-[44px] min-h-[44px] flex items-center justify-center"
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
        <>
          {/* Overlay to close on outside tap */}
          <div 
            className="fixed inset-0 bg-black/10 md:hidden z-[-1]" 
            onClick={() => setMobileOpen(false)} 
          />
          <div ref={menuRef} className="md:hidden bg-surface/95 backdrop-blur-lg border-t border-outline-variant/30 animate-[slideInUp_0.3s_ease-out]">
            <div className="flex flex-col px-6 py-4 space-y-1">
              {navLinks.map((link) => (
                <Link
                  key={link.to}
                  to={link.to}
                  onClick={() => setMobileOpen(false)}
                  className={`font-body-md text-body-md py-3 px-2 rounded-xl transition-all duration-300 min-h-[44px] flex items-center ${
                    isActive(link)
                      ? 'text-primary font-semibold bg-primary/5'
                      : 'text-on-surface-variant hover:text-secondary hover:bg-surface-variant/30'
                  }`}
                >
                  {link.label}
                </Link>
              ))}
              
              <hr className="border-outline-variant/20 my-2" />
              
              <button 
                onClick={() => { setShowNicknameEdit(!showNicknameEdit); setMobileOpen(false); }}
                className="flex items-center gap-3 text-primary font-body-md py-3 px-2 text-left rounded-xl hover:bg-primary/5 transition-colors min-h-[44px]"
              >
                <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>favorite</span>
                Edit Partner's Nickname
              </button>

              <button 
                onClick={logout}
                className="flex items-center gap-3 text-error font-body-md py-3 px-2 text-left rounded-xl hover:bg-error/5 transition-colors min-h-[44px]"
              >
                <span className="material-symbols-outlined">logout</span>
                Logout
              </button>
            </div>
          </div>
        </>
      )}
    </header>
  );
}
