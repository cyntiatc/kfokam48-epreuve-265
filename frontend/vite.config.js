import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En développement, les appels /api sont relayés vers le backend Spring Boot :
// le navigateur reste sur la même origine, sans configuration CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
