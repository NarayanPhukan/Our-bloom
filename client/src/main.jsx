import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { CapacitorUpdater } from '@capgo/capacitor-updater'
import { Capacitor } from '@capacitor/core'

if (Capacitor.isNativePlatform()) {
  CapacitorUpdater.notifyAppReady()

  const checkForUpdates = async () => {
    try {
      const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
      const response = await fetch(`${baseUrl}/updates/version.json`)
      const data = await response.json()
      
      const result = await CapacitorUpdater.download({
        url: baseUrl + '/updates/dist.zip',
        version: data.version,
      })
      
      if (result) {
        await CapacitorUpdater.set({ id: result.id })
      }
    } catch (err) {
      console.log('OTA Check failed (this is normal on first run or if no update available)', err)
    }
  }

  checkForUpdates()
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
