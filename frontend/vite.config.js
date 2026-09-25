import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Les appels /api sont relayés vers le backend Spring Boot : le navigateur reste sur la même
// origine, sans CORS. Cible 127.0.0.1 plutôt que localhost : Node peut résoudre localhost
// en IPv6 (::1), adresse sur laquelle le backend n'écoute pas forcément.
const proxyApi = {
  '/api': 'http://127.0.0.1:8080',
};

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: proxyApi,
  },
  preview: {
    port: 4173,
    proxy: proxyApi,
  },
});
