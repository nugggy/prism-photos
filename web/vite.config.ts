import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// Content Security Policy for the built app. Plex servers can be anywhere (plex.tv,
// *.plex.direct, or a LAN address over plain http), so images, media and API calls are allowed
// to any http/https origin; scripts, objects and the base URL are locked down. 'unsafe-inline'
// styles are needed for React style props. blob: covers editor exports, hls.js workers and MSE.
// Keep in step with nginx.conf, which sends the same policy as a header plus frame-ancestors
// (frame-ancestors is ignored in a meta tag).
export const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: http: https:",
  "media-src 'self' blob: http: https:",
  "connect-src 'self' http: https:",
  "worker-src 'self' blob:",
  "manifest-src 'self'",
  "font-src 'self' data:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ')

/**
 * Adds the CSP meta tag to the production build only. GitHub Pages cannot send headers, so the
 * meta tag is the only way to apply a policy there. The dev server is left alone because Vite
 * and React Refresh inject inline scripts in development.
 */
function cspMetaTag(): Plugin {
  return {
    name: 'plex-gallery-csp-meta',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: CONTENT_SECURITY_POLICY }, injectTo: 'head-prepend' },
    ],
  }
}

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
    cspMetaTag(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.svg', 'logo.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png'],
      manifest: {
        name: 'Plex Gallery',
        short_name: 'Plex Gallery',
        description: 'A clean photo and video library client for Plex Media Server.',
        theme_color: '#1F2326',
        background_color: '#1F2326',
        display: 'standalone',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: 'logo.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
        ],
      },
      workbox: {
        // Cache the app shell only. Never cache Plex media responses.
        globPatterns: ['**/*.{js,css,html,svg,ico,png}'],
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
})
