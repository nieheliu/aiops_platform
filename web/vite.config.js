import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sys-users': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sys-roles': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ops-tickets': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ops-ticket-logs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ai-diagnoses': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ai-models': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ops-alerts': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ops-knowledge': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/dashboard/summary': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ticket-knowledge': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/knowledge-articles': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
