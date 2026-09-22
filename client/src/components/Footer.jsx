import { Link, useParams } from 'react-router-dom';
import { APP_DOWNLOADS } from '../utils/appDownloads';

export default function Footer() {
  const { slug } = useParams();

  return (
    <footer className="w-full py-14 px-5 md:px-margin-desktop border-t border-slate-200 bg-white text-slate-600 text-xs">
      <div className="flex flex-col md:flex-row justify-between items-center w-full max-w-container-max mx-auto space-y-6 md:space-y-0">
        <div className="flex flex-col items-center md:items-start space-y-1">
          <Link
            to={slug ? `/c/${slug}` : '/'}
            className="font-bold text-lg text-[#0F2744] hover:text-[#2563EB] transition-colors"
          >
            Our Bloom
          </Link>
          <p className="text-xs text-slate-500">
            Private Platform for Couples • With love, forever &amp; always
          </p>
        </div>

        <div className="flex flex-col md:flex-row items-center space-y-3 md:space-y-0 md:space-x-6 text-xs font-medium">
          {slug && (
            <>
              <Link to={`/c/${slug}`} className="hover:text-[#2563EB] transition-colors">
                Timeline
              </Link>
              <Link to={`/c/${slug}#map`} className="hover:text-[#2563EB] transition-colors">
                Map
              </Link>
              <Link to={`/c/${slug}#gallery`} className="hover:text-[#2563EB] transition-colors">
                Gallery
              </Link>
              <Link to={`/c/${slug}/love-notes`} className="hover:text-[#2563EB] transition-colors">
                Love Notes
              </Link>
            </>
          )}

          <div className="flex items-center gap-3 pl-2 md:border-l md:border-slate-200">
            <a
              href={APP_DOWNLOADS.android}
              download
              className="flex items-center gap-1 text-[#2563EB] hover:text-blue-800 transition-colors font-semibold"
              title="Download Android App (.apk)"
            >
              <span className="material-symbols-outlined text-[16px]">android</span>
              <span>Android (.apk)</span>
            </a>
            <a
              href={APP_DOWNLOADS.windows}
              download
              className="flex items-center gap-1 text-slate-600 hover:text-slate-900 transition-colors font-semibold"
              title="Download Windows App (.exe)"
            >
              <span className="material-symbols-outlined text-[16px]">desktop_windows</span>
              <span>Windows (.exe)</span>
            </a>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap justify-center items-center gap-3 text-xs text-slate-500 pt-6 mt-6 border-t border-slate-100 w-full max-w-container-max mx-auto font-medium">
        <Link to="/" className="hover:text-blue-600 transition-colors">Home</Link>
        <span>•</span>
        <Link to="/about" className="hover:text-blue-600 transition-colors">About Us</Link>
        <span>•</span>
        <Link to="/privacy" className="hover:text-blue-600 transition-colors">Privacy Policy</Link>
        <span>•</span>
        <Link to="/terms" className="hover:text-blue-600 transition-colors">Terms &amp; Conditions</Link>
        <span>•</span>
        <Link to="/refund-policy" className="hover:text-blue-600 transition-colors">Refund &amp; Cancellation</Link>
        <span>•</span>
        <Link to="/shipping-policy" className="hover:text-blue-600 transition-colors">Digital Delivery</Link>
        <span>•</span>
        <Link to="/contact" className="hover:text-blue-600 transition-colors">Contact Support</Link>
      </div>

      <div className="text-center text-[11px] text-slate-400 mt-4">
        © 2026 Our Bloom. All rights reserved. Registered Entity: Our Bloom, India.
      </div>
    </footer>
  );
}
