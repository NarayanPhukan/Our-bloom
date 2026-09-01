import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    target: 'esnext',
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    },
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          leaflet: ['leaflet', 'react-leaflet', 'leaflet-geosearch'],
          editor: ['react-quill-new'],
          capacitor: ['@capacitor/core', '@capacitor/app', '@capacitor/haptics', '@capacitor/keyboard', '@capacitor/network', '@capacitor/status-bar']
        }
      }
    }
  }
})
