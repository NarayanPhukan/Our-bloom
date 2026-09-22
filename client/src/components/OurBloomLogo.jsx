import React from 'react';

/**
 * Official OurBloom Brand Identity Logo Component
 * Implements the brand guidelines spec sheet:
 * - 01 Primary Logo (Two-color horizontal lockup)
 * - 02 Symbol (Two-tone arch mark: #0A192F left, #2563EB right)
 * - 03 Wordmark ("Our" medium, "Bloom" bold)
 * - 04 Color Variants (Primary, Navy, Blue, Mono)
 * - 05 Reverse (On dark ground #0A192F: White left, Sky Blue right)
 * - 06 App Icon & Squircle badge
 */
export function OurBloomIcon({
  variant = 'primary', // 'primary' | 'reverse' | 'navy' | 'blue' | 'mono-white' | 'mono-dark'
  className = 'w-6 h-7',
  strokeWidth = 4.5,
}) {
  let leftColor = '#0A192F';
  let rightColor = '#2563EB';

  if (variant === 'reverse') {
    leftColor = '#FFFFFF';
    rightColor = '#38BDF8';
  } else if (variant === 'navy' || variant === 'mono-dark') {
    leftColor = '#0A192F';
    rightColor = '#0A192F';
  } else if (variant === 'mono-white') {
    leftColor = '#FFFFFF';
    rightColor = '#FFFFFF';
  } else if (variant === 'blue') {
    leftColor = '#2563EB';
    rightColor = '#2563EB';
  }

  return (
    <svg
      viewBox="0 0 24 28"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 ${className}`}
      aria-label="OurBloom Logo Symbol"
    >
      {/* Left Stroke: Navy / Partner 1 (Individual Path 1) */}
      <path
        d="M 7.2 24.2 C 5.4 18 5.6 11.5 11.8 4.2"
        stroke={leftColor}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
      />
      {/* Right Stroke: Blue / Partner 2 & Shared Apex (Individual Path 2 -> Shared Space) */}
      <path
        d="M 11.8 4.2 C 12.8 3.8 18.4 11.5 16.8 24.2"
        stroke={rightColor}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
      />
    </svg>
  );
}

/**
 * App Icon Squircle Container as specified in 06 - APP ICON
 */
export function OurBloomAppIcon({
  size = 'w-10 h-10',
  iconSize = 'w-5 h-6',
  className = '',
}) {
  return (
    <div className={`rounded-2xl bg-[#0A192F] flex items-center justify-center shadow-md ring-1 ring-white/10 shrink-0 ${size} ${className}`}>
      <OurBloomIcon variant="reverse" className={iconSize} />
    </div>
  );
}

export default function OurBloomLogo({
  variant = 'primary', // 'primary' | 'reverse' | 'navy' | 'blue' | 'mono-white' | 'mono-dark'
  showWordmark = true,
  iconSize = 'w-6 h-7',
  textSize = 'text-xl',
  className = '',
}) {
  const isDark = variant === 'reverse' || variant === 'mono-white';
  const textColor = isDark ? 'text-white' : variant === 'blue' ? 'text-blue-600' : 'text-[#0A192F]';

  return (
    <div className={`inline-flex items-center gap-2.5 select-none ${className}`}>
      <OurBloomIcon variant={variant} className={iconSize} />
      {showWordmark && (
        <span className={`tracking-tight leading-none ${textSize} ${textColor}`}>
          <span className="font-medium">Our</span>
          <span className="font-bold">Bloom</span>
        </span>
      )}
    </div>
  );
}
