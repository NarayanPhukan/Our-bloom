import { useEffect, useRef } from 'react';

const ASPECT_MAP = {
  video: 'aspect-video',
  square: 'aspect-square',
  '4/5': 'aspect-[4/5]',
};

const COLOR_MAP = {
  primary: {
    bg: 'bg-primary-container',
    text: 'text-primary',
    border: 'border-background',
  },
  secondary: {
    bg: 'bg-secondary-container',
    text: 'text-secondary',
    border: 'border-background',
  },
  tertiary: {
    bg: 'bg-tertiary-container',
    text: 'text-tertiary',
    border: 'border-background',
  },
};

export default function MilestoneCard({ milestone, index, isLast, onWriteNote }) {
  const cardRef = useRef(null);
  const isEven = index % 2 === 0;
  const colors = COLOR_MAP[milestone.colorScheme] || COLOR_MAP.primary;
  const aspect = ASPECT_MAP[milestone.aspectRatio] || ASPECT_MAP.video;

  useEffect(() => {
    const el = cardRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('opacity-100', 'translate-y-0');
          el.classList.remove('opacity-0', 'translate-y-10');
        }
      },
      { threshold: 0.1 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div className="relative mb-32 md:flex md:justify-between items-center w-full group">
      {/* Card */}
      <div className={`md:w-[45%] mb-8 md:mb-0 ${!isEven ? 'order-2' : ''}`}>
        <div
          ref={cardRef}
          className="glass-card p-8 rounded-[24px] petal-shadow transition-all duration-500 hover:-translate-y-2 opacity-0 translate-y-10"
        >
          {/* Image */}
          {milestone.imageUrl && (
            <div className={`${aspect} rounded-[16px] overflow-hidden mb-6`}>
              <img
                className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                src={milestone.imageUrl}
                alt={milestone.title}
                loading="lazy"
              />
            </div>
          )}

          {/* Label */}
          <span className="font-label-sm text-label-sm text-secondary uppercase tracking-widest mb-2 block">
            {milestone.label}
          </span>

          {/* Title */}
          <h3 className="font-headline-md text-headline-md text-primary mb-3">
            {milestone.title}
          </h3>

          {/* Body */}
          <p className="font-body-md text-body-md text-on-surface-variant">
            {milestone.body}
          </p>

          {/* CTA on last card */}
          {isLast && (
            <button
              onClick={onWriteNote}
              className="mt-6 px-8 py-3 bg-primary text-on-primary rounded-full font-body-md hover:bg-secondary transition-all duration-300 transform hover:scale-105"
            >
              Write Our Next Note
            </button>
          )}
        </div>
      </div>

      {/* Timeline Dot */}
      <div className="absolute left-1/2 transform -translate-x-1/2 flex flex-col items-center z-10 hidden md:flex">
        <div
          className={`${isLast ? 'w-16 h-16 border-8 border-primary-container shadow-lg bg-primary' : `w-12 h-12 border-4 ${colors.border} ${colors.bg}`} rounded-full flex items-center justify-center ${isLast ? 'text-on-primary' : colors.text}`}
        >
          <span
            className={`material-symbols-outlined ${isLast ? 'scale-125' : ''}`}
            style={
              milestone.iconFill
                ? { fontVariationSettings: "'FILL' 1" }
                : undefined
            }
          >
            {milestone.icon}
          </span>
        </div>
      </div>

      {/* Spacer */}
      <div className="md:w-[45%]"></div>
    </div>
  );
}
