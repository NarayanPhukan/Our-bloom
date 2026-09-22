import { useState, useEffect, lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useParams, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { Capacitor } from '@capacitor/core';
import Header from './components/Header';
import Footer from './components/Footer';
import PetalEffect from './components/PetalEffect';
import ProtectedRoute from './components/ProtectedRoute';
import OfflineBanner from './components/OfflineBanner';
import UpdateBanner from './components/UpdateBanner';
import AppVersionIndicator from './components/AppVersionIndicator';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import SetupPage from './pages/SetupPage';
import { initializeNotifications } from './utils/notificationScheduler';

// Lazy-load heavy pages for better performance
const LandingPage = lazy(() => import('./pages/LandingPage'));
const JourneyPage = lazy(() => import('./pages/JourneyPage'));
const MemoriesPage = lazy(() => import('./pages/MemoriesPage'));
const LoveNotesPage = lazy(() => import('./pages/LoveNotesPage'));
const MapPage = lazy(() => import('./pages/MapPage'));
const PrivacyPolicy = lazy(() => import('./pages/legal/PrivacyPolicy'));
const TermsAndConditions = lazy(() => import('./pages/legal/TermsAndConditions'));
const RefundPolicy = lazy(() => import('./pages/legal/RefundPolicy'));
const ShippingPolicy = lazy(() => import('./pages/legal/ShippingPolicy'));
const ContactUs = lazy(() => import('./pages/legal/ContactUs'));
const AboutUs = lazy(() => import('./pages/legal/AboutUs'));

// Themed loading spinner for lazy-loaded routes
function PageLoader() {
  return (
    <div className="min-h-[60vh] flex items-center justify-center">
      <span className="material-symbols-outlined text-[48px] text-[#2563EB] animate-spin">filter_vintage</span>
    </div>
  );
}
function CoupleLayout() {
  return (
    <div className="bg-lily-pattern text-on-background min-h-screen flex flex-col relative overflow-x-hidden">
      <Header />
      <PetalEffect />
      <main className="pt-32 pb-20 flex-1">
        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route index element={<JourneyPage />} />
            <Route path="memories" element={<MemoriesPage />} />
            <Route path="love-notes" element={<LoveNotesPage />} />
            <Route path="map" element={<MapPage />} />
          </Routes>
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}

function AppRedirect() {
  const { user, couple, loading } = useAuth();
  
  useEffect(() => {
    if (user && couple) {
      initializeNotifications(user, couple);
    }
  }, [user, couple]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-lily-pattern">
        <span className="material-symbols-outlined text-[48px] text-primary animate-spin">filter_vintage</span>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" replace />;
  if (!couple) return <Navigate to="/setup" replace />;
  return <Navigate to={`/c/${couple.slug}`} replace />;
}

function RootRoute() {
  const isNative = Capacitor.isNativePlatform();
  if (isNative) {
    return <AppRedirect />;
  }
  return (
    <Suspense fallback={<PageLoader />}>
      <LandingPage />
    </Suspense>
  );
}

function BackButtonHandler() {
  const navigate = useNavigate();

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    let backListener;
    const setupBackButton = async () => {
      try {
        const { App: CapApp } = await import('@capacitor/app');
        backListener = await CapApp.addListener('backButton', ({ canGoBack }) => {
          if (canGoBack) {
            navigate(-1);
          } else {
            CapApp.exitApp();
          }
        });
      } catch (e) {
        console.log('Back button handler not available:', e.message);
      }
    };

    setupBackButton();
    return () => { if (backListener) backListener.remove(); };
  }, [navigate]);

  return null;
}

function App() {
  const [pendingUpdate, setPendingUpdate] = useState(null);

  useEffect(() => {
    // Listen for OTA update ready events from main.jsx
    const handleUpdateReady = (e) => {
      setPendingUpdate(e.detail);
    };
    window.addEventListener('ota-update-ready', handleUpdateReady);

    // Check if there's a pending update from a previous session
    try {
      const pending = localStorage.getItem('bloom_ota_pending');
      if (pending) {
        setPendingUpdate(JSON.parse(pending));
      }
    } catch {}

    return () => window.removeEventListener('ota-update-ready', handleUpdateReady);
  }, []);

  const handleApplyUpdate = async () => {
    if (!pendingUpdate) return;
    try {
      const { CapacitorUpdater } = await import('@capgo/capacitor-updater');
      localStorage.setItem('bloom_ota_version', pendingUpdate.version);
      localStorage.removeItem('bloom_ota_pending');
      await CapacitorUpdater.set({ id: pendingUpdate.id });
    } catch (err) {
      console.error('Failed to apply update:', err);
    }
  };

  return (
    <Router>
      <AuthProvider>
        <NotificationProvider>
          <OfflineBanner />
          <BackButtonHandler />
          <AppVersionIndicator />
          {pendingUpdate && (
            <UpdateBanner onUpdate={handleApplyUpdate} />
          )}
          <Routes>
            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Legal & Compliance routes */}
            <Route path="/privacy" element={<Suspense fallback={<PageLoader />}><PrivacyPolicy /></Suspense>} />
            <Route path="/privacy-policy" element={<Suspense fallback={<PageLoader />}><PrivacyPolicy /></Suspense>} />
            <Route path="/terms" element={<Suspense fallback={<PageLoader />}><TermsAndConditions /></Suspense>} />
            <Route path="/terms-and-conditions" element={<Suspense fallback={<PageLoader />}><TermsAndConditions /></Suspense>} />
            <Route path="/refund-policy" element={<Suspense fallback={<PageLoader />}><RefundPolicy /></Suspense>} />
            <Route path="/refunds" element={<Suspense fallback={<PageLoader />}><RefundPolicy /></Suspense>} />
            <Route path="/cancellation-refund" element={<Suspense fallback={<PageLoader />}><RefundPolicy /></Suspense>} />
            <Route path="/shipping-policy" element={<Suspense fallback={<PageLoader />}><ShippingPolicy /></Suspense>} />
            <Route path="/shipping-delivery" element={<Suspense fallback={<PageLoader />}><ShippingPolicy /></Suspense>} />
            <Route path="/contact" element={<Suspense fallback={<PageLoader />}><ContactUs /></Suspense>} />
            <Route path="/contact-us" element={<Suspense fallback={<PageLoader />}><ContactUs /></Suspense>} />
            <Route path="/about" element={<Suspense fallback={<PageLoader />}><AboutUs /></Suspense>} />
            <Route path="/about-us" element={<Suspense fallback={<PageLoader />}><AboutUs /></Suspense>} />

            {/* Setup (authenticated but no couple) */}
            <Route path="/setup" element={<SetupPage />} />

            {/* Couple routes (authenticated + has couple) */}
            <Route path="/c/:slug/*" element={
              <ProtectedRoute>
                <CoupleLayout />
              </ProtectedRoute>
            } />

            {/* Root & Fallback */}
            <Route path="/" element={<RootRoute />} />
            <Route path="*" element={<RootRoute />} />
          </Routes>
        </NotificationProvider>
      </AuthProvider>
    </Router>
  );
}

export default App;
