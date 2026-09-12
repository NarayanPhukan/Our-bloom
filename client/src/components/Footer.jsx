import { Link, useParams } from 'react-router-dom';
import { APP_DOWNLOADS } from '../utils/appDownloads';

export default function Footer() {
  const { slug } = useParams();

  return (
    <footer className="w-full py-20 px-5 md:px-margin-desktop border-t border-primary/10">
      <div className="flex flex-col md:flex-row justify-between items-center w-full max-w-container-max mx-auto space-y-8 md:space-y-0">
        <div className="flex flex-col items-center md:items-start">
          <Link
            to={`/c/${slug}`}
            className="font-headline-md text-headline-md text-primary mb-2 hover:opacity-80 transition-opacity"
          >
            Our Bloom
          </Link>
          <p className="font-body-md text-body-md text-on-tertiary-container/80 italic">
            With love, forever &amp; always
          </p>
        </div>

        <div className="flex flex-col md:flex-row items-center space-y-4 md:space-y-0 md:space-x-8">
          <Link
            to={`/c/${slug}`}
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Timeline
          </Link>
          <Link
            to={`/c/${slug}#map`}
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Map
          </Link>
          <Link
            to={`/c/${slug}#gallery`}
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Gallery
          </Link>
          <Link
            to={`/c/${slug}/love-notes`}
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Love Notes
          </Link>
          <div className="flex items-center gap-4 pl-2 border-l border-outline-variant/30">
            <a
              href={APP_DOWNLOADS.android}
              download
              className="flex items-center gap-1.5 text-secondary hover:text-primary transition-colors duration-300 font-body-md font-semibold"
              title="Download Android App (.apk)"
            >
              <span className="material-symbols-outlined text-[18px]">android</span>
              Android
            </a>
            <a
              href={APP_DOWNLOADS.windows}
              download
              className="flex items-center gap-1.5 text-primary hover:text-secondary transition-colors duration-300 font-body-md font-semibold"
              title="Download Windows App (.exe)"
            >
              <span className="material-symbols-outlined text-[18px]">desktop_windows</span>
              Windows
            </a>
          </div>
        </div>

        <div className="flex space-x-6">
          <span className="material-symbols-outlined text-secondary cursor-pointer hover:scale-110 transition-transform">
            auto_awesome
          </span>
          <span className="material-symbols-outlined text-secondary cursor-pointer hover:scale-110 transition-transform">
            favorite_border
          </span>
        </div>
      </div>

      <div className="flex flex-wrap justify-center items-center gap-3 text-xs text-on-tertiary-container/60 pt-6 mt-6 border-t border-primary/10 w-full max-w-container-max mx-auto">
        <a href="https://our-bloom.onrender.com/about" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">About</a>
        <span>•</span>
        <a href="https://our-bloom.onrender.com/privacy" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">Privacy Policy</a>
        <span>•</span>
        <a href="https://our-bloom.onrender.com/terms" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">Terms &amp; Conditions</a>
        <span>•</span>
        <a href="https://our-bloom.onrender.com/refund-policy" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">Refund &amp; Cancellation</a>
        <span>•</span>
        <a href="https://our-bloom.onrender.com/shipping-policy" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">Delivery Policy</a>
        <span>•</span>
        <a href="https://our-bloom.onrender.com/contact" target="_blank" rel="noreferrer" className="hover:text-primary transition-colors">Contact Support</a>
      </div>
    </footer>
  );
}
