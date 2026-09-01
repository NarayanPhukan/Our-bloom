import { useState, useRef, useCallback } from 'react';
import { tapFeedback, impactFeedback } from '../utils/useHaptic';

const THRESHOLD = 80;
const MAX_PULL = 120;

export default function PullToRefresh({ onRefresh, children }) {
  const [pulling, setPulling] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const startYRef = useRef(0);
  const containerRef = useRef(null);

  const handleTouchStart = useCallback((e) => {
    // Only activate when at top of scroll
    if (window.scrollY > 5) return;
    startYRef.current = e.touches[0].clientY;
    setPulling(true);
  }, []);

  const handleTouchMove = useCallback((e) => {
    if (!pulling || refreshing) return;
    const delta = e.touches[0].clientY - startYRef.current;
    if (delta > 0) {
      // Diminishing returns effect for natural feel
      const distance = Math.min(delta * 0.5, MAX_PULL);
      setPullDistance(distance);
      if (distance >= THRESHOLD) {
        tapFeedback();
      }
    }
  }, [pulling, refreshing]);

  const handleTouchEnd = useCallback(async () => {
    if (!pulling) return;
    
    if (pullDistance >= THRESHOLD && onRefresh) {
      setRefreshing(true);
      impactFeedback();
      try {
        await onRefresh();
      } catch (e) {
        console.error('Refresh failed:', e);
      }
      setRefreshing(false);
    }
    
    setPulling(false);
    setPullDistance(0);
  }, [pulling, pullDistance, onRefresh]);

  const progress = Math.min(pullDistance / THRESHOLD, 1);
  const rotation = pullDistance * 3;

  return (
    <div
      ref={containerRef}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      {/* Pull indicator */}
      <div 
        className="flex justify-center items-center overflow-hidden transition-all duration-200 ease-out"
        style={{ 
          height: refreshing ? 60 : pullDistance,
          opacity: progress,
        }}
      >
        <div 
          className="flex flex-col items-center gap-1"
          style={{ transform: `rotate(${rotation}deg)` }}
        >
          <span 
            className={`material-symbols-outlined text-primary text-3xl ${refreshing ? 'animate-spin' : ''}`}
            style={{ fontVariationSettings: refreshing ? "'FILL' 1" : "'FILL' 0" }}
          >
            filter_vintage
          </span>
          {!refreshing && pullDistance > 20 && (
            <span className="text-[10px] text-primary/60 font-label-sm uppercase tracking-widest">
              {pullDistance >= THRESHOLD ? 'Release to refresh' : 'Pull down'}
            </span>
          )}
          {refreshing && (
            <span className="text-[10px] text-primary/60 font-label-sm uppercase tracking-widest">
              Refreshing...
            </span>
          )}
        </div>
      </div>
      
      {/* Content */}
      <div 
        style={{ 
          transform: pulling && pullDistance > 0 ? `translateY(${Math.min(pullDistance * 0.3, 20)}px)` : 'none',
          transition: pulling ? 'none' : 'transform 0.3s ease-out',
        }}
      >
        {children}
      </div>
    </div>
  );
}
