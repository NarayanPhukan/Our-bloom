import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="w-full py-20 px-5 md:px-margin-desktop border-t border-primary/10">
      <div className="flex flex-col md:flex-row justify-between items-center w-full max-w-container-max mx-auto space-y-8 md:space-y-0">
        <div className="flex flex-col items-center md:items-start">
          <Link
            to="/"
            className="font-headline-md text-headline-md text-primary mb-2 hover:opacity-80 transition-opacity"
          >
            Our Bloom
          </Link>
          <p className="font-body-md text-body-md text-on-tertiary-container/80 italic">
            With love, forever &amp; always
          </p>
        </div>

        <div className="flex flex-col md:flex-row items-center space-y-4 md:space-y-0 md:space-x-12">
          <Link
            to="/"
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Our Story
          </Link>
          <Link
            to="/memories"
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Gallery
          </Link>
          <Link
            to="/love-notes"
            className="text-on-tertiary-container/80 hover:text-primary transition-colors duration-300 font-body-md"
          >
            Privacy
          </Link>
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
    </footer>
  );
}
