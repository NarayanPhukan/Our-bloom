import { Capacitor } from '@capacitor/core';

let Haptics = null;
let ImpactStyle = null;
let NotificationType = null;

// Lazy-load haptics to avoid importing on web
const loadHaptics = async () => {
  if (!Haptics && Capacitor.isNativePlatform()) {
    try {
      const mod = await import('@capacitor/haptics');
      Haptics = mod.Haptics;
      ImpactStyle = mod.ImpactStyle;
      NotificationType = mod.NotificationType;
    } catch (e) {
      console.log('Haptics not available');
    }
  }
};

// Pre-load on import if native
if (Capacitor.isNativePlatform()) {
  loadHaptics();
}

/**
 * Light tap feedback — for button presses, toggles
 */
export const tapFeedback = async () => {
  await loadHaptics();
  if (Haptics) {
    try {
      await Haptics.impact({ style: ImpactStyle.Light });
    } catch (_) {}
  }
};

/**
 * Medium impact — for significant actions like saving, deleting
 */
export const impactFeedback = async () => {
  await loadHaptics();
  if (Haptics) {
    try {
      await Haptics.impact({ style: ImpactStyle.Medium });
    } catch (_) {}
  }
};

/**
 * Success vibration — for completed uploads, saves
 */
export const successFeedback = async () => {
  await loadHaptics();
  if (Haptics) {
    try {
      await Haptics.notification({ type: NotificationType.Success });
    } catch (_) {}
  }
};

/**
 * Error vibration — for failed operations
 */
export const errorFeedback = async () => {
  await loadHaptics();
  if (Haptics) {
    try {
      await Haptics.notification({ type: NotificationType.Error });
    } catch (_) {}
  }
};
