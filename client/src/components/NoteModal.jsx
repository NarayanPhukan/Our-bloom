import { useState } from 'react';

export default function NoteModal({ isOpen, onClose, onSubmit }) {
  const [content, setContent] = useState('');
  const [author, setAuthor] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!content.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit({ content: content.trim(), author: author.trim() || 'Anonymous' });
      setContent('');
      setAuthor('');
      onClose();
    } catch (err) {
      console.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 modal-overlay"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface rounded-[24px] petal-shadow w-full max-w-lg p-8 transform transition-all duration-500 animate-[slideInUp_0.4s_ease-out]">
        {/* Header */}
        <div className="flex justify-between items-center mb-6">
          <h2 className="font-headline-md text-headline-md text-primary">
            Write a Love Note
          </h2>
          <button
            onClick={onClose}
            className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center hover:bg-primary-container transition-colors duration-300"
          >
            <span className="material-symbols-outlined text-on-surface-variant">
              close
            </span>
          </button>
        </div>

        {/* Decorative */}
        <div className="flex justify-center mb-6 opacity-30">
          <span className="material-symbols-outlined text-[48px] text-primary-fixed-dim animate-float">
            edit_note
          </span>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="font-label-sm text-label-sm text-secondary uppercase tracking-widest mb-2 block">
              Your words
            </label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Pour your heart out..."
              maxLength={500}
              rows={4}
              className="w-full px-4 py-3 rounded-xl bg-surface-container-low border border-outline-variant/50 text-on-surface font-body-md text-body-md placeholder:text-on-surface-variant/40 focus:outline-none focus:ring-2 focus:ring-primary-fixed-dim/50 focus:border-primary transition-all duration-300 resize-none"
            />
            <span className="text-[11px] text-on-surface-variant/50 mt-1 block text-right">
              {content.length}/500
            </span>
          </div>

          <div>
            <label className="font-label-sm text-label-sm text-secondary uppercase tracking-widest mb-2 block">
              Signed with love
            </label>
            <input
              type="text"
              value={author}
              onChange={(e) => setAuthor(e.target.value)}
              placeholder="Your name (optional)"
              className="w-full px-4 py-3 rounded-xl bg-surface-container-low border border-outline-variant/50 text-on-surface font-body-md text-body-md placeholder:text-on-surface-variant/40 focus:outline-none focus:ring-2 focus:ring-primary-fixed-dim/50 focus:border-primary transition-all duration-300"
            />
          </div>

          <button
            type="submit"
            disabled={!content.trim() || submitting}
            className="w-full py-3.5 bg-primary text-on-primary rounded-full font-body-md hover:bg-secondary transition-all duration-300 transform hover:scale-[1.02] disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:scale-100 disabled:hover:bg-primary"
          >
            {submitting ? (
              <span className="flex items-center justify-center gap-2">
                <span className="material-symbols-outlined animate-spin text-[18px]">
                  progress_activity
                </span>
                Sending...
              </span>
            ) : (
              'Send with Love'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
