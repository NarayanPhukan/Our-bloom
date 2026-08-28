import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children }) {
  const { user, couple, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-lily-pattern">
        <div className="text-center space-y-4">
          <span className="material-symbols-outlined text-[48px] text-primary animate-spin">
            filter_vintage
          </span>
          <p className="text-on-surface-variant font-body-md">Loading your garden...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!couple) {
    return <Navigate to="/setup" replace />;
  }

  return children;
}
