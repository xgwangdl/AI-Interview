import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': process.env.API_SERVER_URL || 'http://localhost:8088',
      '/login': process.env.API_SERVER_URL || 'http://localhost:8088',
      '/auth': process.env.API_SERVER_URL || 'http://localhost:8088',
    },
  },
});
