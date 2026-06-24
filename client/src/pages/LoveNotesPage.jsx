import { useState, useEffect, useRef } from 'react';
import { getLoveNotes, createLoveNote, deleteLoveNote } from '../api';
import Toast from '../components/Toast';

export default function LoveNotesPage() {
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [draftContent, setDraftContent] = useState('');
  const [draftImage, setDraftImage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchNotes();
  }, []);

  const fetchNotes = async () => {
    try {
      const { data } = await getLoveNotes();
      setNotes(data);
    } catch {
      setNotes([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!draftContent.trim() && !draftImage) return;
    setSubmitting(true);
    try {
      const data = new FormData();
      if (draftContent.trim()) {
        data.append('content', draftContent.trim());
      }
      data.append('author', 'With love');
      if (draftImage) {
        data.append('image', draftImage);
      }

      const res = await createLoveNote(data);
      setNotes([res.data, ...notes]);
      setDraftContent('');
      setDraftImage(null);
      setToast({ message: 'Your note has bloomed ♡', type: 'success' });
    } catch {
      setToast({ message: 'Could not save — try again', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteLoveNote(id);
      setNotes(notes.filter((n) => n._id !== id));
    } catch {
      setToast({ message: 'Could not delete', type: 'error' });
    }
  };

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
    });
  };

  return (
    <>
      {/* Hero Section */}
      <section className="max-w-container-max mx-auto px-5 md:px-margin-desktop text-center mb-20">
        <h1 className="font-display-lg text-display-lg-mobile md:text-display-lg text-primary mb-6 italic">
          A Secret Garden of Whispers
        </h1>
        <p className="font-body-lg text-body-lg text-on-surface-variant max-w-2xl mx-auto opacity-80">
          Every bloom tells a story. Here lie the delicate petals of our
          journey—the notes, the thoughts, and the quiet moments that grew
          between us this past month.
        </p>
      </section>

      {/* Bento / Masonry Grid */}
      <section className="max-w-container-max mx-auto px-5 md:px-margin-desktop">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-gutter items-start">
          {/* Dynamic User Notes from DB */}
          {notes.map((note) => {
            if (note.imageUrl) {
              const isLocal = note.imageUrl.startsWith('/uploads');
              const imgSrc = isLocal ? `http://localhost:5000${note.imageUrl}` : note.imageUrl;
              return (
                <div key={note._id} className="love-note-card bg-surface-container-lowest p-4 pb-16 rounded-[4px] shadow-[0_10px_30px_rgba(0,0,0,0.05)] relative group">
                  <div className="w-full aspect-square overflow-hidden bg-surface-variant mb-6">
                    <img className="w-full h-full object-cover" src={imgSrc} alt="Love note attached image" loading="lazy" />
                  </div>
                  {note.content && (
                    <p className="font-headline-md text-on-surface-variant/80 text-center italic text-xl break-words">
                      "{note.content}"
                    </p>
                  )}
                  <button onClick={() => handleDelete(note._id)} className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity bg-surface/80 rounded-full p-2 text-error shadow-md hover:bg-surface-container-highest">
                    <span className="material-symbols-outlined text-[16px]">close</span>
                  </button>
                  <div className="absolute bottom-4 right-4 text-outline/50 font-label-sm uppercase tracking-widest text-[10px]">
                    {note.createdAt && formatDate(note.createdAt)}
                  </div>
                </div>
              );
            }

            return (
              <div
                key={note._id}
                className="love-note-card glass-panel p-8 rounded-[24px] shadow-[0_40px_80px_rgba(222,191,194,0.12)] border-l-4 border-l-secondary-fixed-dim group relative"
              >
                <div className="flex justify-between items-start mb-6">
                  <span
                    className="material-symbols-outlined text-secondary"
                    style={{ fontVariationSettings: "'FILL' 1" }}
                  >
                    eco
                  </span>
                  {note.createdAt && (
                    <span className="text-label-sm font-label-sm text-outline uppercase tracking-widest">
                      {formatDate(note.createdAt)}
                    </span>
                  )}
                </div>
                <p className="font-body-lg text-body-lg text-on-surface-variant italic mb-6 leading-relaxed whitespace-pre-wrap break-words">
                  "{note.content}"
                </p>
                <div className="w-full h-[1px] bg-outline-variant/30 mb-4"></div>
                <div className="flex justify-between items-center">
                  <div className="text-right font-headline-md text-primary/60 text-lg">
                    — {note.author}
                  </div>
                  <button
                    onClick={() => handleDelete(note._id)}
                    className="opacity-0 group-hover:opacity-100 transition-opacity duration-300 text-on-surface-variant/30 hover:text-error"
                  >
                    <span className="material-symbols-outlined text-[16px]">
                      close
                    </span>
                  </button>
                </div>
              </div>
            );
          })}



          {/* Note 4: Small Accent */}
          <div className="love-note-card bg-secondary-container/30 p-8 rounded-[24px] flex flex-col justify-center items-center text-center border border-secondary-fixed-dim/20">
            <span className="material-symbols-outlined text-secondary text-5xl mb-4">
              compost
            </span>
            <p className="font-headline-md text-headline-md text-secondary italic mb-2">
              Wild &amp; Free
            </p>
            <p className="font-body-md text-body-md text-on-secondary-container/80">
              Our love isn't a garden with walls, it's a meadow that knows no
              bounds.
            </p>
          </div>

        </div>
      </section>

      {/* New Note Section */}
      <section className="max-w-container-max mx-auto px-5 md:px-margin-desktop mb-32 mt-20">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-gutter max-w-4xl mx-auto items-center">
          {/* Note 5: Interactive Draft */}
          <div className="love-note-card glass-panel p-8 rounded-[24px] shadow-[0_40px_80px_rgba(222,191,194,0.12)]">
            <div className="mb-6">
              <span className="material-symbols-outlined text-primary-fixed-dim">
                edit_note
              </span>
            </div>
            <textarea
              className="w-full bg-transparent border-none focus:ring-0 focus:outline-none font-headline-md text-headline-md text-primary italic placeholder:text-primary-fixed-dim h-48 custom-scrollbar resize-none"
              placeholder="Type a note to keep..."
              value={draftContent}
              onChange={(e) => setDraftContent(e.target.value)}
              maxLength={500}
            ></textarea>
            <div className="flex justify-between items-center mt-6">
              <span className="text-label-sm font-label-sm text-outline italic">
                {submitting ? 'Planting your note...' : 'Saving to our garden...'}
              </span>
              <button
                onClick={handleSubmit}
                disabled={(!draftContent.trim() && !draftImage) || submitting}
                className="bg-primary text-on-primary px-6 py-2 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-all transform hover:scale-105 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:scale-100"
              >
                Bloom
              </button>
            </div>
          </div>

          {/* Note 6: Interactive Image Uploader */}
          <div 
            onClick={() => fileInputRef.current?.click()}
            className="love-note-card bg-surface-container-lowest p-4 pb-16 rounded-[4px] shadow-[0_10px_30px_rgba(0,0,0,0.05)] rotate-3 cursor-pointer hover:rotate-0 hover:scale-105 transition-all duration-300 group max-w-xs mx-auto w-full"
          >
            <div className="w-full aspect-square overflow-hidden bg-surface-variant mb-6 relative flex flex-col items-center justify-center border-2 border-dashed border-primary/20 group-hover:border-primary/50 transition-colors">
              {draftImage ? (
                <img
                  className="w-full h-full object-cover"
                  src={URL.createObjectURL(draftImage)}
                  alt="Draft memory preview"
                />
              ) : (
                <>
                  <span className="material-symbols-outlined text-primary/40 text-6xl mb-2 group-hover:text-primary transition-colors">
                    add_photo_alternate
                  </span>
                  <p className="font-label-sm text-primary/60 uppercase tracking-widest text-center px-4 group-hover:text-primary transition-colors">
                    Attach a Photo
                  </p>
                </>
              )}
            </div>
            <p className="font-headline-md text-on-surface-variant/50 text-center italic text-xl group-hover:text-primary transition-colors">
              {draftImage ? 'Ready to bloom...' : 'Click to add picture'}
            </p>
            <input 
              type="file" 
              accept="image/*" 
              className="hidden" 
              ref={fileInputRef} 
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) setDraftImage(e.target.files[0]);
              }} 
            />
          </div>
        </div>
      </section>

      {/* Floating Decoration */}
      <div className="fixed bottom-10 right-10 z-40 hidden md:block pointer-events-none">
        <div className="relative w-32 h-32 animate-pulse">
          <div className="absolute inset-0 bg-primary-fixed-dim/20 rounded-full blur-2xl"></div>
          <span className="material-symbols-outlined text-primary/30 text-8xl absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rotate-12">
            filter_vintage
          </span>
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </>
  );
}
