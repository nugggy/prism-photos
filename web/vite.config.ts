import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  base: process.env.VITE_BASE || '/',
  server: {
    port: 1111,
    strictPort: true,
  },
  preview: {
    port: 1111,
    strictPort: true,
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.svg', 'logo.svg'],
      manifest: {
        name: 'Prism',
        short_name: 'Prism',
        description: 'A clean photo and video library client for Plex Media Server.',
        theme_color: '#1F2326',
        background_color: '#1F2326',
        display: 'standalone',
        icons: [
          { src: 'logo.svg', sizes: '192x192', type: 'image/svg+xml', purpose: 'any' },
          { src: 'logo.svg', sizes: '512x512', type: 'image/svg+xml', purpose: 'any' },
        ],
      },
      workbox: {
        // Cache the app shell only. Never cache Plex media responses.
        globPatterns: ['**/*.{js,css,html,svg,ico}'],
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
})
