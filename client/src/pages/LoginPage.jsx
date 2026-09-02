import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

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
        // User has a couple — redirect will happen via App routing
        navigate('/');
      } else {
        navigate('/setup');
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-lily-pattern px-5 relative overflow-hidden">
      {/* Decorative background elements */}
      <div className="absolute top-20 left-10 opacity-10 pointer-events-none">
        <span className="material-symbols-outlined text-[180px] text-primary rotate-12">local_florist</span>
      </div>
      <div className="absolute bottom-20 right-10 opacity-10 pointer-events-none">
        <span className="material-symbols-outlined text-[140px] text-secondary -rotate-12">favorite</span>
      </div>

      <div className="w-full max-w-md relative z-10">
        {/* Logo */}
        <div className="text-center mb-10">
          <h1 className="font-display-lg text-display-lg-mobile md:text-display-lg text-primary">Our Bloom</h1>
          <p className="text-on-surface-variant font-body-md mt-2 italic">Welcome back to your garden</p>
        </div>

        {/* Login Card */}
        <div className="glass-panel p-8 md:p-10 rounded-[32px] shadow-2xl shadow-primary/5 border border-primary/10">
          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="bg-error/10 border border-error/20 text-error rounded-2xl px-4 py-3 text-sm font-medium">
                {error}
              </div>
            )}

            <div>
              <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="your@email.com"
                className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-all"
              />
            </div>

            <div>
              <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Password</label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary text-on-primary py-4 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-all duration-300 shadow-xl shadow-primary/10 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="material-symbols-outlined animate-spin text-lg">autorenew</span>
                  Entering garden...
                </span>
              ) : (
                'Enter Your Garden'
              )}
            </button>
          </form>

          <div className="mt-8 text-center">
            <div className="w-full h-[1px] bg-outline-variant/30 mb-6"></div>
            <p className="text-on-surface-variant font-body-md">
              Don't have an account?{' '}
              <Link to="/register" className="text-primary font-semibold hover:text-secondary transition-colors">
                Plant Your Seed
              </Link>
            </p>
            
            <div className="mt-8 pt-6 border-t border-outline-variant/20">
              <p className="text-label-sm uppercase tracking-widest text-on-surface-variant mb-4">Experience it Natively</p>
              <a 
                href="/OurBloom.apk" 
                download
                className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-secondary/10 text-secondary hover:bg-secondary/20 hover:scale-105 transition-all rounded-full font-label-sm uppercase tracking-wider w-full md:w-auto"
              >
                <span className="material-symbols-outlined text-[18px]">android</span>
                Download Android App
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
