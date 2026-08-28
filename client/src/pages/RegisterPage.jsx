import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await register(email, password, name);
      navigate('/setup');
    } catch (err) {
      setError(err.response?.data?.error || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-lily-pattern px-5 relative overflow-hidden">
      {/* Decorative background elements */}
      <div className="absolute top-20 right-10 opacity-10 pointer-events-none">
        <span className="material-symbols-outlined text-[160px] text-secondary rotate-12">eco</span>
      </div>
      <div className="absolute bottom-20 left-10 opacity-10 pointer-events-none">
        <span className="material-symbols-outlined text-[120px] text-primary -rotate-6">spa</span>
      </div>

      <div className="w-full max-w-md relative z-10">
        {/* Logo */}
        <div className="text-center mb-10">
          <h1 className="font-display-lg text-display-lg-mobile md:text-display-lg text-primary">Our Bloom</h1>
          <p className="text-on-surface-variant font-body-md mt-2 italic">Start growing your love story</p>
        </div>

        {/* Register Card */}
        <div className="glass-panel p-8 md:p-10 rounded-[32px] shadow-2xl shadow-primary/5 border border-primary/10">
          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="bg-error/10 border border-error/20 text-error rounded-2xl px-4 py-3 text-sm font-medium">
                {error}
              </div>
            )}

            <div>
              <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Your Name</label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="What should we call you?"
                className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-all"
              />
            </div>

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
                minLength={4}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Choose a password"
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
                  Planting your seed...
                </span>
              ) : (
                'Create Account'
              )}
            </button>
          </form>

          <div className="mt-8 text-center">
            <div className="w-full h-[1px] bg-outline-variant/30 mb-6"></div>
            <p className="text-on-surface-variant font-body-md">
              Already have an account?{' '}
              <Link to="/login" className="text-primary font-semibold hover:text-secondary transition-colors">
                Enter Your Garden
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
