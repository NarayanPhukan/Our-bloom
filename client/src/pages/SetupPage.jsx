import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { createCouple, joinCouple } from '../api';
import OurBloomLogo from '../components/OurBloomLogo';

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
      <div className="min-h-screen flex items-center justify-center bg-[#F8FAFC]">
        <span className="material-symbols-outlined text-[48px] text-[#2563EB] animate-spin">filter_vintage</span>
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
      setError(err.response?.data?.error || 'Failed to initialize couple profile');
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
      setError(err.response?.data?.error || 'Failed to link with partner invite code');
    } finally {
      setIsSubmitting(false);
    }
  };

  // After creating — show the invite code
  if (inviteCodeResult) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#F8FAFC] px-5 py-12">
        <div className="w-full max-w-md text-center">
          <div className="bg-white p-8 md:p-10 rounded-2xl shadow-xl border border-slate-200 space-y-6">
            <div className="w-16 h-16 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center mx-auto border border-blue-200">
              <span className="material-symbols-outlined text-3xl">celebration</span>
            </div>

            <div>
              <h2 className="text-2xl font-bold text-[#0F2744] mb-2">Your Sanctuary is Created!</h2>
              <p className="text-xs text-slate-500">
                Share this private authorization code with your partner to synchronize accounts:
              </p>
            </div>

            <div className="bg-slate-50 rounded-xl p-5 border border-slate-200">
              <p className="font-mono font-bold text-2xl text-[#2563EB] tracking-widest select-all">{inviteCodeResult}</p>
            </div>

            <p className="text-xs text-slate-400 italic">
              Your partner will register their account, then paste this invite code to connect with you.
            </p>

            <button
              className="w-full bg-[#0F2744] text-white py-3.5 rounded-xl font-bold text-xs uppercase tracking-wider hover:bg-[#1B3B6F] transition-all shadow-md shadow-navy-900/10"
              onClick={() => window.location.href = '/'}
            >
              Enter Sanctuary Dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#F8FAFC] px-5 py-12 relative overflow-hidden">
      <div className="w-full max-w-lg relative z-10">
        {/* Brand Header */}
        <div className="text-center mb-8 space-y-2">
          <div className="flex items-center justify-center">
            <OurBloomLogo variant="primary" iconSize="w-7 h-8" textSize="text-xl" />
          </div>
          <h1 className="text-2xl font-bold text-[#0F2744]">Welcome, {user?.name}!</h1>
          <p className="text-xs text-slate-500">
            Let's link your relationship profile to begin your timeline &amp; vault.
          </p>
        </div>

        {/* Mode Selection */}
        {!mode && (
          <div className="space-y-4">
            <button
              onClick={() => setMode('create')}
              className="w-full bg-white p-6 rounded-2xl border border-slate-200 hover:border-blue-400 hover:shadow-md transition-all group cursor-pointer text-left shadow-sm"
            >
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-200 group-hover:bg-blue-600 group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-2xl">add_circle</span>
                </div>
                <div>
                  <h3 className="font-bold text-base text-[#0F2744] mb-1">Create a New Couple Profile</h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Set up your anniversary date and generate an invite code for your partner.
                  </p>
                </div>
              </div>
            </button>

            <button
              onClick={() => setMode('join')}
              className="w-full bg-white p-6 rounded-2xl border border-slate-200 hover:border-blue-400 hover:shadow-md transition-all group cursor-pointer text-left shadow-sm"
            >
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-slate-50 text-slate-700 flex items-center justify-center shrink-0 border border-slate-200 group-hover:bg-[#0F2744] group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined text-2xl">link</span>
                </div>
                <div>
                  <h3 className="font-bold text-base text-[#0F2744] mb-1">Join Your Partner</h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Partner already created your profile? Enter their invite code to sync.
                  </p>
                </div>
              </div>
            </button>

            <div className="text-center mt-6">
              <button onClick={logout} className="text-xs font-semibold text-slate-500 hover:text-slate-800 transition-colors">
                ← Sign out
              </button>
            </div>
          </div>
        )}

        {/* Create Form */}
        {mode === 'create' && (
          <div className="bg-white p-8 md:p-10 rounded-2xl shadow-xl border border-slate-200 space-y-6">
            <button 
              onClick={() => { setMode(null); setError(''); }} 
              className="text-xs font-semibold text-slate-500 hover:text-slate-800 flex items-center gap-1 transition-colors"
            >
              <span className="material-symbols-outlined text-sm">arrow_back</span> Back
            </button>

            <div>
              <h2 className="text-xl font-bold text-[#0F2744]">Initialize Couple Profile</h2>
              <p className="text-xs text-slate-500">Record your anniversary date and special relationship motto.</p>
            </div>

            <form onSubmit={handleCreate} className="space-y-4 text-xs">
              {error && (
                <div className="bg-rose-50 border border-rose-200 text-rose-700 rounded-xl p-3">{error}</div>
              )}

              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Anniversary Date *
                </label>
                <input
                  type="date"
                  required
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-3 text-sm text-[#0F2744] focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Special Time (Optional)
                </label>
                <input
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-3 text-sm text-[#0F2744] focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
                <p className="text-slate-400 text-[11px] mt-1">The exact moment your story began ✨</p>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Special Motto / Phrase (Optional)
                </label>
                <input
                  type="text"
                  value={specialPhrase}
                  onChange={(e) => setSpecialPhrase(e.target.value)}
                  placeholder="e.g. Forever &amp; Always"
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-3 text-sm text-[#0F2744] focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full bg-[#0F2744] text-white py-3.5 rounded-xl font-bold text-xs uppercase tracking-wider hover:bg-[#1B3B6F] transition-all shadow-md shadow-navy-900/10 disabled:opacity-50 mt-2"
              >
                {isSubmitting ? 'Creating Profile...' : 'Create Couple Profile'}
              </button>
            </form>
          </div>
        )}

        {/* Join Form */}
        {mode === 'join' && (
          <div className="bg-white p-8 md:p-10 rounded-2xl shadow-xl border border-slate-200 space-y-6">
            <button 
              onClick={() => { setMode(null); setError(''); }} 
              className="text-xs font-semibold text-slate-500 hover:text-slate-800 flex items-center gap-1 transition-colors"
            >
              <span className="material-symbols-outlined text-sm">arrow_back</span> Back
            </button>

            <div>
              <h2 className="text-xl font-bold text-[#0F2744]">Join Your Partner</h2>
              <p className="text-xs text-slate-500">Enter the authorization code sent by your partner.</p>
            </div>

            <form onSubmit={handleJoin} className="space-y-4 text-xs">
              {error && (
                <div className="bg-rose-50 border border-rose-200 text-rose-700 rounded-xl p-3">{error}</div>
              )}

              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Partner Invite Code
                </label>
                <input
                  type="text"
                  required
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                  placeholder="BLOOM-XXXX"
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-base font-mono tracking-widest text-center text-[#0F2744] uppercase focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full bg-[#0F2744] text-white py-3.5 rounded-xl font-bold text-xs uppercase tracking-wider hover:bg-[#1B3B6F] transition-all shadow-md shadow-navy-900/10 disabled:opacity-50 mt-2"
              >
                {isSubmitting ? 'Linking Accounts...' : 'Connect to Sanctuary'}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
