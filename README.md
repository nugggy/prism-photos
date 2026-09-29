# Plex Gallery

A better photo library for Plex. Prism talks directly to your Plex Media Server
and gives you a proper gallery: a fast timeline, albums, favourites, search, a
real video player, an editor, locking, sharing and in-app updates.

Two clients share one design and one Plex API reference (`docs/plex-api.md`):

- `app/` Android app (Kotlin, Jetpack Compose, Media3). Built for a OnePlus 15 on Android 16, runs on Android 10 and up.
- `web/` Web app (Vite, React, TypeScript, PWA). Runs on port 1111 and deploys to GitHub Pages.

Repository: https://github.com/nugggy/prism-photos
Live web app: https://nugggy.github.io/prism-photos/

Plex Gallery (package name still "Prism" internally) is an independent project, not affiliated with or endorsed by Plex Inc.

## Features

- Sign in with Plex (PIN flow) or connect manually with a server URL and token.
- Remote access with a connection mode: Auto, Same network as server (LAN first), or Remote only, plus a manual LAN address.
- Timeline of every photo and video, newest first, month headers, pinch to change density, fast scroll, pull to refresh.
- Albums with covers, nested albums, rename, description, cover, accent colour, sort order.
- Favourites synced to Plex ratings. Search by title, tag, place and year.
- Viewer with pinch zoom, double tap, swipe, info sheet with camera EXIF, slideshow, set as wallpaper, open in Plex.
- Video player: play, pause, seek and scrub, skip 10 s, speed, mute, loop, rotate, picture in picture, direct play with transcode fallback.
- Editor: crop, rotate, flip, adjustments, filters. Saves a copy to the device. Plex originals are never changed.
- Lock photos, videos and albums behind fingerprint, face or device PIN (Android) or a passcode (web).
- Share to WhatsApp, Messages, Gmail, Instagram, Messenger, Telegram, or anything else, as real files.
- Multi select with share, download, favourite, lock and delete.
- Device tab showing the phone's own photos, and registration as a viewer, picker and share target so Prism can act as the default gallery.
- My albums: create albums and add photos and videos from the viewer or multi select. They are Plex photo playlists, so they also appear in Plex's own apps.
- Full photo editing suite on both clients: 20 adjustments, 18 filters with strength, crop with ratios and straighten, markup (pen, highlighter, shapes, text, stickers, blur brush), frames, date stamp and watermark, auto enhance, before and after, presets, copy and paste edits, JPEG, PNG or WebP export with size and quality, undo and redo.
- Video editor (Android): trim, mute, rotate, flip, speed, colour, resolution, frame capture, export with Media3 Transformer.
- Upload and automatic sync (Android): the phone's camera and screenshot folders back up to the Plex library folder over SMB or WebDAV (Plex has no upload API), then Plex scans. Deleting a synced item on the phone offers to remove it from Plex too.
- In-app updates from GitHub Releases (Android) and service worker updates (web).
- Material You dynamic colour, light, dark and AMOLED themes.

## Android: build and install

Requirements: JDK 17, Android SDK platform 36. Android Studio does all of this for you.

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press Run with your phone connected
(USB debugging on). The debug build installs as a separate app id (`au.prism.photos.debug`).

### Release signing and in-app updates

1. Run `bash scripts/make-keystore.sh` once. It creates `prism-release.jks` and
   `keystore.properties` (both git ignored) and prints the GitHub secrets to add.
2. Add the four secrets to the repository (Settings, Secrets and variables, Actions).
3. Push a tag: `git tag v1.0.0 && git push origin v1.0.0`. The Android release
   workflow builds a signed APK and attaches it to a GitHub Release.
4. The app checks `https://api.github.com/repos/<owner>/<repo>/releases/latest` on
   launch (at most every six hours) and offers to download and install newer versions.
   The repository slug defaults to `PRISM_UPDATE_REPO` in `gradle.properties` and can
   be changed in Settings. Android will ask once to allow Prism to install apps.

The first install must be the signed release APK from a GitHub Release (not the
debug build), so later updates are signed with the same key and install cleanly.

## Web app

```
cd web
npm install
npm run dev      # http://localhost:1111
npm run build
npm run preview  # http://localhost:1111
```

Docker: `docker build -t prism-web web && docker run -p 1111:1111 prism-web`.

GitHub Pages: the web deploy workflow publishes `web/` to Pages on every push to
`main` that touches `web/`. Enable Pages with source "GitHub Actions" in the
repository settings. Because Pages is served over https, browsers block plain
`http://` LAN addresses (mixed content). Prism prefers the `*.plex.direct` https
addresses Plex publishes, which resolve to your LAN IP as well, so LAN mode still
works. If you self host on http (Docker on port 1111), plain LAN addresses work too.

## Sync to Plex (Android)

Plex Media Server has no upload API, so Plex Gallery writes files straight into the library folder on the server and asks Plex to scan. In Settings, Sync to Plex, choose SMB (the NAS share that holds the library, with a NAS username and password) or WebDAV (if enabled on the NAS), tap Test connection, then turn on Auto sync and pick which device folders to back up. Wi-Fi only is on by default. "Upload to Plex" is also available from multi select on the Device tab.

## Remote access

Plex publishes local, remote and relay addresses for each server. Prism stores
all of them and picks one based on the connection mode in Settings. Choose
"Same network as server" at home for the fastest path and "Remote only" on mobile
data so the app never waits on an unreachable LAN address. "Auto" probes everything
and picks the best reachable one. Prism reconnects automatically when a request fails.

## Project layout

```
app/        Android app
web/        Web app
docs/       Design spec, Plex API reference, Android code contracts
scripts/    Keystore helper
.github/    CI, Android release, web deploy workflows
```
