import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { APP_DOWNLOADS } from '../utils/appDownloads';
import OurBloomLogo from '../components/OurBloomLogo';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const data = await login(email, password);
      if (data.user.coupleId) {
        navigate('/');
      } else {
        navigate('/setup');
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F8FAFC] flex flex-col justify-between text-[#0A192F] relative selection:bg-blue-100 selection:text-blue-900">
      {/* Background Decor */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-96 bg-gradient-to-b from-blue-50/70 to-transparent pointer-events-none -z-10"></div>

      {/* Header */}
      <header className="px-6 py-5 max-w-6xl mx-auto w-full flex items-center justify-between">
        <Link to="/" className="flex items-center">
          <OurBloomLogo variant="primary" iconSize="w-6 h-7" textSize="text-lg" />
        </Link>
        <Link to="/" className="text-xs font-semibold text-blue-600 hover:text-blue-800 flex items-center gap-1">
          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
          Back to Home
        </Link>
      </header>

      {/* Main Login Card */}
      <main className="flex-1 flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-xl shadow-slate-200/50 p-8 md:p-10 space-y-6">
            {/* Header / Brand */}
            <div className="text-center space-y-1.5">
              <h1 className="text-2xl font-bold text-[#0F2744]">Partner Sign In</h1>
              <p className="text-xs text-slate-500">Access your private couple sanctuary and mutual vault.</p>
            </div>

            {error && (
              <div className="bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl p-3.5 flex items-start gap-2">
                <span className="material-symbols-outlined text-[16px] text-rose-500 mt-0.5">error</span>
                <span className="flex-1 font-medium">{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Email Address
                </label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="your@email.com"
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-3 text-sm text-[#0F2744] placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 uppercase tracking-wider text-[11px] mb-1.5">
                  Password
                </label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-3 text-sm text-[#0F2744] placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-[#2563EB] focus:bg-white transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-[#0F2744] text-white py-3.5 rounded-xl font-bold text-xs uppercase tracking-wider hover:bg-[#1B3B6F] transition-all shadow-md shadow-navy-900/10 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 mt-2"
              >
                {loading ? (
                  <>
                    <span className="material-symbols-outlined animate-spin text-[16px]">autorenew</span>
                    Signing In...
                  </>
                ) : (
                  'Sign In to Sanctuary'
                )}
              </button>
            </form>

            <div className="pt-4 border-t border-slate-100 text-center space-y-4">
              <p className="text-xs text-slate-600">
                Don't have an account yet?{' '}
                <Link to="/register" className="text-[#2563EB] font-bold hover:underline">
                  Create Sanctuary
                </Link>
              </p>

              {/* Native App Direct Download CTA */}
              <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-100 space-y-2">
                <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
                  Have an Android device?
                </span>
                <a
                  href={APP_DOWNLOADS.android}
                  download
                  className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-white text-slate-700 border border-slate-200 hover:bg-slate-100 rounded-lg text-xs font-semibold w-full transition-all"
                >
                  <span className="material-symbols-outlined text-[16px] text-blue-600">android</span>
                  Download Native Android App (.apk)
                </a>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="py-6 px-4 text-center text-xs text-slate-500 border-t border-slate-200/60 bg-white/50">
        <div className="max-w-md mx-auto space-y-2">
          <div className="flex flex-wrap justify-center gap-3">
            <Link to="/about" className="hover:text-slate-800">About</Link>
            <span>•</span>
            <Link to="/privacy" className="hover:text-slate-800">Privacy</Link>
            <span>•</span>
            <Link to="/terms" className="hover:text-slate-800">Terms</Link>
            <span>•</span>
            <Link to="/refund-policy" className="hover:text-slate-800">Refunds</Link>
            <span>•</span>
            <Link to="/contact" className="hover:text-slate-800">Contact</Link>
          </div>
          <p>© 2026 Our Bloom. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
