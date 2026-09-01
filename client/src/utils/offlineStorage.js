/**
 * Lightweight localStorage-based cache for offline support.
 * Uses stale-while-revalidate pattern.
 */

const CACHE_PREFIX = 'bloom_cache_';

/**
 * Get cached data for a key
 * @returns {object|null} Cached data or null if expired/missing
 */
export const getCached = (key) => {
  try {
    const raw = localStorage.getItem(CACHE_PREFIX + key);
    if (!raw) return null;
    
    const { data, expiry } = JSON.parse(raw);
    // Return data even if expired (stale-while-revalidate)
    return { data, isStale: expiry ? Date.now() > expiry : false };
  } catch {
    return null;
  }
};

/**
 * Cache data with optional TTL
 * @param {string} key - Cache key
 * @param {*} data - Data to cache
 * @param {number} ttlMs - Time to live in milliseconds (default: 30 min)
 */
export const setCache = (key, data, ttlMs = 30 * 60 * 1000) => {
  try {
    const entry = {
      data,
      expiry: Date.now() + ttlMs,
      timestamp: Date.now(),
    };
    localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(entry));
  } catch (e) {
    // localStorage might be full — clear old entries
    clearOldCache();
    try {
      const entry = { data, expiry: Date.now() + ttlMs, timestamp: Date.now() };
      localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(entry));
    } catch {
      // Give up silently
    }
  }
};

/**
 * Remove a specific cache entry
 */
export const removeCache = (key) => {
  try {
    localStorage.removeItem(CACHE_PREFIX + key);
  } catch {}
};

/**
 * Clear all expired cache entries
 */
export const clearOldCache = () => {
  try {
    const keys = Object.keys(localStorage).filter(k => k.startsWith(CACHE_PREFIX));
    const now = Date.now();
    keys.forEach(key => {
      try {
        const { expiry } = JSON.parse(localStorage.getItem(key));
        if (expiry && now > expiry + 60 * 60 * 1000) { // Clear if expired by >1hr
          localStorage.removeItem(key);
        }
      } catch {
        localStorage.removeItem(key);
      }
    });
  } catch {}
};

/**
 * Generate a cache key from URL
 */
export const urlToCacheKey = (url) => {
  return url.replace(/[^a-zA-Z0-9]/g, '_').slice(0, 100);
};
