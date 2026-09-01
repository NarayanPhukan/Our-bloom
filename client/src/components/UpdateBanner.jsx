import { useState } from 'react';

export default function UpdateBanner({ onUpdate }) {
  const [dismissed, setDismissed] = useState(false);

  if (dismissed) return null;

  return (
    <div className="fixed bottom-20 left-4 right-4 md:left-auto md:right-6 md:w-80 z-[1999] animate-[slideInUp_0.4s_ease-out]">
      <div className="glass-panel bg-surface/95 backdrop-blur-xl px-5 py-4 rounded-2xl shadow-2xl border border-primary/20 flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-primary-container flex items-center justify-center text-primary shrink-0">
          <span className="material-symbols-outlined text-xl">system_update</span>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-on-surface">Update available</p>
          <p className="text-[11px] text-on-surface-variant truncate">Tap to get the latest version</p>
        </div>
        <div className="flex gap-1 shrink-0">
          <button 
            onClick={() => setDismissed(true)}
            className="text-on-surface-variant hover:text-primary p-2 transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <span className="material-symbols-outlined text-lg">close</span>
          </button>
          <button 
            onClick={onUpdate}
            className="bg-primary text-on-primary px-4 py-2 rounded-full text-xs font-bold uppercase tracking-wider hover:bg-secondary transition-colors min-h-[44px]"
          >
            Update
          </button>
        </div>
      </div>
    </div>
  );
}
