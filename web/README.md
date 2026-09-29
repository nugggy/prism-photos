# Prism (web)

A clean, professional photo and video library client for Plex Media Server, built with
Vite, React 19 and TypeScript. This is the web counterpart to the Prism Android app; see
`docs/superpowers/specs/2026-09-29-prism-photos-design.md` and `docs/plex-api.md` at the
repository root for the product spec and Plex API reference both apps implement against.

Prism is not affiliated with Plex Inc.

## Requirements

- Node 24+
- npm

## Run locally

```bash
npm install
npm run dev
```

The dev server always runs on **http://localhost:1111** (`server.port` is fixed in
`vite.config.ts`).

## Test

```bash
npm test
```

Runs the Vitest unit test suite (Plex URL builders, connection ranking, DTO mapping,
timeline merge/sort, passcode hashing).

## Build

```bash
npm run build
```

Type-checks with `tsc -b` and produces a production build in `dist/`.

## Preview a production build

```bash
npm run preview
```

Also serves on http://localhost:1111.

## Docker

```bash
docker build -t prism-web .
docker run -p 1111:1111 prism-web
```

Serves the production build with nginx on port 1111, with SPA fallback to `index.html`
(the app uses a hash router, so this is mostly a safety net for direct hits on the root).

## Deployment (GitHub Pages)

`.github/workflows/web-deploy.yml` builds and deploys `web/` to GitHub Pages on every push
to `main` that touches `web/**`. It sets `VITE_BASE=/<repository-name>/` so asset URLs
resolve correctly under a project Pages subpath, and uses a hash router
(`createHashRouter`) so client-side routes work without server-side rewrites.

To deploy elsewhere, set `VITE_BASE` to your desired base path (default `/`) and run
`npm run build`.

## Connecting to Plex

Sign in with a Plex account (PIN flow) or connect manually with a server URL and access
token. If Prism is served over https, plain `http://` server addresses cannot be loaded
from the page due to the browser's mixed content policy; use the server's `*.plex.direct`
https address (shown in the server picker) or serve Prism itself over http on your LAN.

## Notes

- All Plex media caching stays in the browser cache and IndexedDB (via `idb-keyval`) for
  the timeline/album lists; the PWA service worker only caches the app shell, never Plex
  media responses.
- Locks, passcode, album covers/accents/sort order and theme/density preferences are
  stored in `localStorage` and are per-browser, per-device (Plex has no lock concept).
