import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In dev, proxy /api to the FinCopilot backend so the SPA and API share an origin (as in production,
// where nginx proxies /api). SSE responses must not be buffered.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
