import { useState, useEffect } from 'react';

export default function OfflineBanner() {
  const [isOffline, setIsOffline] = useState(!navigator.onLine);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const goOffline = () => { setIsOffline(true); setDismissed(false); };
    const goOnline = () => setIsOffline(false);
    
    window.addEventListener('offline', goOffline);
    window.addEventListener('online', goOnline);
    
    return () => {
      window.removeEventListener('offline', goOffline);
      window.removeEventListener('online', goOnline);
    };
  }, []);

  if (!isOffline || dismissed) return null;

  return (
    <div className="fixed top-0 left-0 right-0 z-[9999] flex justify-center pt-[calc(env(safe-area-inset-top,0px)+4px)] px-4 animate-[slideInUp_0.3s_ease-out]">
      <div className="glass-panel bg-surface/95 backdrop-blur-xl px-5 py-3 rounded-2xl shadow-xl border border-primary/20 flex items-center gap-3 max-w-sm w-full">
        <span className="material-symbols-outlined text-primary text-xl">cloud_off</span>
        <div className="flex-1">
          <p className="text-sm font-medium text-on-surface">You're offline</p>
          <p className="text-[11px] text-on-surface-variant">Showing cached data</p>
        </div>
        <button 
          onClick={() => setDismissed(true)}
          className="text-on-surface-variant hover:text-primary p-1 transition-colors"
        >
          <span className="material-symbols-outlined text-lg">close</span>
        </button>
      </div>
    </div>
  );
}
