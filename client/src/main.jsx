import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { Capacitor } from '@capacitor/core'
import { initCapacitorPlugins } from './utils/capacitorPlugins'

// Initialize native plugins
initCapacitorPlugins();

// OTA Update System — downloads in background, applies on next launch
if (Capacitor.isNativePlatform()) {
  const { CapacitorUpdater } = await import('@capgo/capacitor-updater');
  
  CapacitorUpdater.notifyAppReady();

  const checkForUpdates = async () => {
    try {
      const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
      const response = await fetch(`${baseUrl}/updates/version.json`);
      const data = await response.json();
      
      // Check if we already have this version downloaded
      const currentVersion = localStorage.getItem('bloom_ota_version');
      if (currentVersion === data.version) {
        console.log('OTA: Already on latest version', data.version);
        return;
      }

      console.log('OTA: Downloading update', data.version);
      const result = await CapacitorUpdater.download({
        url: baseUrl + '/updates/dist.zip',
        version: data.version,
      });
      
      if (result) {
        // Store the update bundle ID — don't apply immediately
        localStorage.setItem('bloom_ota_pending', JSON.stringify({ id: result.id, version: data.version }));
        // Dispatch event so App.jsx can show the UpdateBanner
        window.dispatchEvent(new CustomEvent('ota-update-ready', { detail: { id: result.id, version: data.version } }));
      }
    } catch (err) {
      console.log('OTA Check failed (normal on first run or if no update):', err.message);
    }
  };

  // Delay the check slightly to not block app startup
  setTimeout(checkForUpdates, 5000);
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
