import { useEffect } from 'react';

export default function Lightbox({ imageSrc, title, date, onClose }) {
  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleEsc);
    document.body.style.overflow = 'hidden'; // Prevent scrolling
    
    return () => {
      window.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = 'unset';
    };
  }, [onClose]);

  if (!imageSrc) return null;

  return (
    <div 
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-md animate-fade-in p-4"
      onClick={onClose}
    >
      <button 
        className="absolute top-6 right-6 text-white/70 hover:text-white transition-colors p-2"
        onClick={onClose}
      >
        <span className="material-symbols-outlined text-4xl">close</span>
      </button>

      <div 
        className="relative max-w-5xl max-h-[90vh] flex flex-col items-center bg-surface p-4 rounded-xl shadow-2xl"
        onClick={(e) => e.stopPropagation()} // Prevent closing when clicking the image container
      >
        <img 
          src={imageSrc} 
          alt={title || "Memory"} 
          className="max-w-full max-h-[75vh] object-contain rounded-md"
        />
        {(title || date) && (
          <div className="mt-4 text-center w-full">
            {title && <h3 className="font-headline-md text-2xl text-on-surface">{title}</h3>}
            {date && <p className="font-label-sm text-primary uppercase tracking-widest mt-2">{date}</p>}
          </div>
        )}
      </div>
    </div>
  );
}
