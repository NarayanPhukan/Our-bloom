import { Capacitor } from '@capacitor/core';

/**
 * Initialize all Capacitor plugins on app startup.
 * Safe to call on web — all calls are no-ops when not native.
 */
export const initCapacitorPlugins = async () => {
  if (!Capacitor.isNativePlatform()) return;

  try {
    // Status Bar
    const { StatusBar, Style } = await import('@capacitor/status-bar');
    await StatusBar.setBackgroundColor({ color: '#fbf9f8' });
    await StatusBar.setStyle({ style: Style.Light });
    await StatusBar.setOverlaysWebView({ overlay: false });
  } catch (e) {
    console.log('StatusBar plugin init skipped:', e.message);
  }

  try {
    // Keyboard
    const { Keyboard } = await import('@capacitor/keyboard');
    Keyboard.addListener('keyboardWillShow', () => {
      document.body.classList.add('keyboard-open');
    });
    Keyboard.addListener('keyboardWillHide', () => {
      document.body.classList.remove('keyboard-open');
    });
  } catch (e) {
    console.log('Keyboard plugin init skipped:', e.message);
  }
};

/**
 * Check if running as a native app. Uses a cached value
 * to avoid calling Capacitor bridge repeatedly.
 */
let _isNative = null;
export const isNativeApp = () => {
  if (_isNative === null) {
    _isNative = Capacitor.isNativePlatform();
  }
  return _isNative;
};
