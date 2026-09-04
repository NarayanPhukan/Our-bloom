import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  base: mode === 'electron' ? './' : '/',
  plugins: [react()],
  build: {
    target: 'esnext'
  },
  esbuild: {
    drop: ['console', 'debugger']
  }
}))
