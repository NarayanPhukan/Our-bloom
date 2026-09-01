import { useEffect, useCallback } from 'react';
import { isNativeApp } from '../utils/capacitorPlugins';

export default function PetalEffect() {
  const createPetal = useCallback(() => {
    // Respect reduced motion
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    // Cap concurrent petals to avoid jank on low-end devices
    const currentPetals = document.querySelectorAll('.petal').length;
    if (currentPetals >= (isNativeApp() ? 8 : 15)) return;

    const petal = document.createElement('div');
    petal.classList.add('petal');

    const size = Math.random() * 15 + 10;
    petal.style.width = `${size}px`;
    petal.style.height = `${size}px`;
    petal.style.left = `${Math.random() * 100}vw`;
    petal.style.top = `-20px`;
    petal.style.opacity = (Math.random() * 0.5 + 0.2).toString();
    petal.style.willChange = 'transform'; // Hardware acceleration hint

    const duration = Math.random() * 10 + 10;
    const drift = (Math.random() - 0.5) * 200;

    document.body.appendChild(petal);

    const animation = petal.animate(
      [
        { transform: `translateY(0) rotate(0deg) translateX(0)`, opacity: petal.style.opacity },
        { transform: `translateY(110vh) rotate(720deg) translateX(${drift}px)`, opacity: 0 },
      ],
      {
        duration: duration * 1000,
        easing: 'linear',
      }
    );

    animation.onfinish = () => petal.remove();
  }, []);

  useEffect(() => {
    // Slower generation on native to save battery/resources
    const intervalTime = isNativeApp() ? 3000 : 1500;
    const interval = setInterval(createPetal, intervalTime);
    return () => clearInterval(interval);
  }, [createPetal]);

  return null;
}
