import { useRef, useState } from 'react';

export default function Polaroid({ src, alt, className = '' }) {
  const frameRef = useRef(null);
  const [style, setStyle] = useState({});

  const handleMouseMove = (e) => {
    if (!frameRef.current) return;
    const rect = frameRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;
    const rotateX = (y - centerY) / 20;
    const rotateY = (centerX - x) / 20;

    setStyle({
      transform: `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale(1.05)`,
      zIndex: 10,
    });
  };

  const handleMouseLeave = () => {
    setStyle({
      transform: '',
      zIndex: 1,
    });
  };

  return (
    <div
      ref={frameRef}
      className={`polaroid-frame rounded-lg overflow-hidden bg-white p-4 transition-transform duration-200 ease-out ${className}`}
      style={style}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      <img src={src} alt={alt} className="w-full h-full object-cover rounded-sm" loading="lazy" />
    </div>
  );
}
