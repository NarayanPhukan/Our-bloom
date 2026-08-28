import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Navigate } from 'react-router-dom';
import { createCouple, joinCouple } from '../api';

export default function SetupPage() {
  const { user, token, setCouple, setUser, logout, loading, couple } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState(null); // 'create' | 'join'
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [inviteCodeResult, setInviteCodeResult] = useState('');

  // Create form
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('');
  const [specialPhrase, setSpecialPhrase] = useState('');

  // Join form
  const [inviteCode, setInviteCode] = useState('');

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-lily-pattern">
        <span className="material-symbols-outlined text-[48px] text-primary animate-spin">filter_vintage</span>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (couple && !inviteCodeResult) {
    return <Navigate to={`/c/${couple.slug}`} replace />;
  }

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const { data } = await createCouple({ startDate, startTime, specialPhrase }, token);
      setCouple(data);
      setUser((prev) => ({ ...prev, coupleId: data._id }));
      setInviteCodeResult(data.inviteCode);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create couple');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleJoin = async (e) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const { data } = await joinCouple(inviteCode, token);
      setCouple(data);
      setUser((prev) => ({ ...prev, coupleId: data._id }));
      navigate(`/c/${data.slug}`);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to join couple');
    } finally {
      setIsSubmitting(false);
    }
  };

  // After creating — show the invite code
  if (inviteCodeResult) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-lily-pattern px-5">
        <div className="w-full max-w-md text-center">
          <div className="glass-panel p-10 rounded-[32px] shadow-2xl shadow-primary/5 border border-primary/10 space-y-8">
            <div className="w-20 h-20 rounded-full bg-primary-container flex items-center justify-center text-primary mx-auto shadow-glow-primary">
              <span className="material-symbols-outlined text-4xl">celebration</span>
            </div>

            <div>
              <h2 className="font-headline-md text-2xl text-on-surface mb-2">Your Bloom is Ready!</h2>
              <p className="text-on-surface-variant font-body-md">
                Share this invite code with your partner so they can join your garden:
              </p>
            </div>

            <div className="bg-primary-container/40 rounded-2xl p-6 border border-primary/20">
              <p className="font-display-lg text-3xl text-primary tracking-widest select-all">{inviteCodeResult}</p>
            </div>

            <p className="text-on-surface-variant text-sm italic">
              Your partner needs to create an account first, then enter this code to join.
            </p>

            <button
              className="w-full bg-primary text-on-primary py-4 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-all"
              // This will reload and redirect properly via auth context
              onClick={() => window.location.href = '/'}
            >
              Enter Your Garden
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-lily-pattern px-5 relative overflow-hidden">
      <div className="absolute top-10 left-10 opacity-10 pointer-events-none">
        <span className="material-symbols-outlined text-[140px] text-primary rotate-6">park</span>
      </div>

      <div className="w-full max-w-lg relative z-10">
        {/* Logo */}
        <div className="text-center mb-10">
          <h1 className="font-display-lg text-display-lg-mobile text-primary">Welcome, {user?.name}!</h1>
          <p className="text-on-surface-variant font-body-md mt-2 italic">
            Let's set up your garden together
          </p>
        </div>

        {/* Mode Selection */}
        {!mode && (
          <div className="space-y-4">
            <button
              onClick={() => setMode('create')}
              className="w-full glass-panel p-8 rounded-[24px] border border-primary/10 hover:border-primary/30 transition-all group cursor-pointer text-left"
            >
              <div className="flex items-start gap-5">
                <div className="w-14 h-14 rounded-full bg-primary-container flex items-center justify-center text-primary shrink-0 group-hover:shadow-glow-primary transition-shadow">
                  <span className="material-symbols-outlined text-2xl">add_circle</span>
                </div>
                <div>
                  <h3 className="font-headline-md text-xl text-on-surface mb-1">Create a Bloom</h3>
                  <p className="text-on-surface-variant font-body-md">
                    Start a new love story. You'll get an invite code to share with your partner.
                  </p>
                </div>
              </div>
            </button>

            <button
              onClick={() => setMode('join')}
              className="w-full glass-panel p-8 rounded-[24px] border border-secondary/10 hover:border-secondary/30 transition-all group cursor-pointer text-left"
            >
              <div className="flex items-start gap-5">
                <div className="w-14 h-14 rounded-full bg-secondary-container flex items-center justify-center text-secondary shrink-0 group-hover:shadow-glow-secondary transition-shadow">
                  <span className="material-symbols-outlined text-2xl">link</span>
                </div>
                <div>
                  <h3 className="font-headline-md text-xl text-on-surface mb-1">Join a Bloom</h3>
                  <p className="text-on-surface-variant font-body-md">
                    Your partner already created a garden? Enter their invite code to join.
                  </p>
                </div>
              </div>
            </button>

            <div className="text-center mt-6">
              <button onClick={logout} className="text-on-surface-variant font-body-sm hover:text-primary transition-colors">
                ← Sign out
              </button>
            </div>
          </div>
        )}

        {/* Create Form */}
        {mode === 'create' && (
          <div className="glass-panel p-8 md:p-10 rounded-[32px] shadow-2xl shadow-primary/5 border border-primary/10">
            <button onClick={() => { setMode(null); setError(''); }} className="text-on-surface-variant hover:text-primary mb-4 flex items-center gap-1 font-body-sm transition-colors">
              <span className="material-symbols-outlined text-sm">arrow_back</span> Back
            </button>

            <h2 className="font-headline-md text-2xl text-on-surface mb-6">Plant Your Garden</h2>

            <form onSubmit={handleCreate} className="space-y-6">
              {error && (
                <div className="bg-error/10 border border-error/20 text-error rounded-2xl px-4 py-3 text-sm">{error}</div>
              )}

              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Anniversary Date *</label>
                <input
                  type="date"
                  required
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                />
              </div>

              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Special Time (Optional)</label>
                <input
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                  placeholder="e.g. the exact time you said yes"
                />
                <p className="text-on-surface-variant text-xs mt-1 italic">The exact moment it all began ✨</p>
              </div>

              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Special Phrase (Optional)</label>
                <input
                  type="text"
                  value={specialPhrase}
                  onChange={(e) => setSpecialPhrase(e.target.value)}
                  placeholder="e.g. Forever blooming together"
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full bg-primary text-on-primary py-4 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-all shadow-xl shadow-primary/10 disabled:opacity-50"
              >
                {isSubmitting ? 'Creating...' : 'Create Our Bloom 🌸'}
              </button>
            </form>
          </div>
        )}

        {/* Join Form */}
        {mode === 'join' && (
          <div className="glass-panel p-8 md:p-10 rounded-[32px] shadow-2xl shadow-primary/5 border border-primary/10">
            <button onClick={() => { setMode(null); setError(''); }} className="text-on-surface-variant hover:text-primary mb-4 flex items-center gap-1 font-body-sm transition-colors">
              <span className="material-symbols-outlined text-sm">arrow_back</span> Back
            </button>

            <h2 className="font-headline-md text-2xl text-on-surface mb-6">Join Your Partner's Garden</h2>

            <form onSubmit={handleJoin} className="space-y-6">
              {error && (
                <div className="bg-error/10 border border-error/20 text-error rounded-2xl px-4 py-3 text-sm">{error}</div>
              )}

              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Invite Code</label>
                <input
                  type="text"
                  required
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                  placeholder="BLOOM-XXXX"
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all text-center text-xl tracking-widest uppercase"
                />
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full bg-primary text-on-primary py-4 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-all shadow-xl shadow-primary/10 disabled:opacity-50"
              >
                {isSubmitting ? 'Joining...' : 'Join the Garden 🌿'}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
