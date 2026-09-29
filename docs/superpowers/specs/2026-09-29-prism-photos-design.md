# Prism: a better photo library for Plex (Android)

Date: 29/09/2026
Status: built under stated assumptions (user was not available for questions)

## Intent

The user keeps photos and videos in a Plex photo library and finds the Plex Photos
experience poor. They want a clean, professional Android app (OnePlus 15, Android 16)
with everything a modern photo library app offers, including a real video player with
play, pause, seek, scrubbing and the rest. Plex stays the source of truth for the media.

## Assumptions made

- The app talks to an existing Plex Media Server over the Plex HTTP API. It does not
  replace Plex or copy the library elsewhere.
- Sign in uses the standard Plex PIN flow (plex.tv). A manual server URL plus token
  fallback is included for servers not linked to a Plex account.
- Favourites map to a Plex user rating of 10 so they sync back to Plex.
- Deleting from the app deletes from Plex (only if the server allows it). It always confirms first.
- No Chromecast, no map view (both need third party keys or receivers). Location text is shown.
- App name is Prism. Package is `au.prism.photos`. Logo is a Plex style gold chevron
  inside a camera aperture ring on Plex dark grey. It is inspired by Plex, not a copy.

## Approach chosen

Native Android, Kotlin, Jetpack Compose, Material 3 with dynamic colour. Media3
ExoPlayer for video. Coil for images. Retrofit + OkHttp + kotlinx.serialization for
the Plex API. DataStore for settings and session. A JSON disk cache of the last
loaded library so cold start is instant. Manual dependency wiring through one
AppGraph object (no Hilt, fewer build moving parts).

Alternatives considered: React Native or Flutter (neither SDK installed, and neither
gives as good a video player or Material You integration), or a web app wrapped in
Capacitor (poor gallery performance on large libraries).

## Features

Sign in: Plex PIN flow with link opening in the browser, manual URL and token,
server discovery from plex.tv resources with best connection selection (local
first, then remote, then relay), library picker for multiple photo libraries.

Photos tab: timeline grid of every photo and video across the selected library,
newest first, grouped by month with sticky headers, pinch to change grid density
(2 to 6 columns), fast scroll, video badges with duration, favourite badges,
pull to refresh, long press multi-select with share, download, favourite, delete.

Albums tab: Plex photo albums (folders) with cover art and item counts, nested
albums, album grid.

Favourites tab: everything rated 10 in Plex.

Search: Plex library search by title, tag, place, camera model.

Viewer: swipe between items, pinch and double tap zoom, immersive mode on tap,
info sheet with EXIF (camera make and model, lens, aperture, exposure, ISO,
dimensions, file size, path, taken date and location), share, download to device
Downloads, favourite toggle, delete, set as wallpaper, slideshow with interval.

Video player: play, pause, seek bar with drag scrubbing and time preview,
double tap sides to skip 10 seconds, playback speed 0.5x to 2x, mute, loop,
rotate to landscape fullscreen, picture in picture, direct play by default with
a transcode (HLS) fallback toggle, remembers position while swiping.

Settings: theme (system, light, dark, AMOLED black), dynamic colour toggle,
default grid density, thumbnail quality, video transcode preference, cache size
and clear, sign out, about.

## Architecture

```
au.prism.photos
  PrismApp            Application, AppGraph (manual DI)
  MainActivity        single activity, NavHost, PiP hooks
  data/plex           Retrofit APIs (plex.tv and server), DTOs, URL builders, headers
  data/               SessionStore (DataStore), ConnectionChooser, MediaRepository, DiskCache
  domain/             MediaItem, Album, Library models
  ui/theme            Plex inspired palette, dynamic colour, typography
  ui/nav              routes and NavHost
  ui/signin, servers, timeline, albums, favourites, search, viewer, player, settings
  util/               Downloads (MediaStore), Share (FileProvider), Format (AU dates)
```

Data flow: Screen -> ViewModel (StateFlow) -> MediaRepository -> PlexServerApi.
The repository keeps the timeline in memory, persists it to disk as JSON, and
exposes flows the screens collect. Favourite and delete are optimistic with rollback.

Error handling: every network call returns a Result. Screens show an inline error
with retry. A lost connection re-runs the connection chooser once before failing.

Testing: JVM unit tests for URL builders, DTO parsing of real Plex JSON shapes,
connection ranking and timeline grouping. Build verified with assembleDebug.

## Out of scope for this pass

Chromecast, map view, on-device face grouping, uploads to Plex, editing.

## Additions requested mid build

### In-app updates from GitHub

The app is hosted on GitHub. Updates ship as GitHub Releases with a signed APK
attached. The app checks the latest release on launch (at most once every six
hours) and from Settings. If the release tag is newer than the installed
version, it shows the release notes and an Update button. The APK downloads to
the app cache with a progress bar, then the Android package installer opens
through a FileProvider. This needs the REQUEST_INSTALL_PACKAGES permission and
the user allowing "install unknown apps" for Prism once.

The repository slug lives in gradle.properties (PRISM_UPDATE_REPO) and can be
overridden in Settings. A GitHub Actions workflow builds and signs the release
APK on every `v*` tag using a keystore stored in repository secrets. Every
release must be signed with the same key or Android will refuse the update.

### Remote access with a LAN toggle

Plex publishes several connections per server: local LAN addresses, a remote
address (port forwarded or plex.direct), and a relay. The app stores all of
them. Settings has a connection mode:

- Auto: test each connection, prefer local, then remote, then relay.
- Same network as server (LAN): try local addresses first, fall back to remote.
- Remote only: skip local addresses (useful on mobile data so it never waits on
  an unreachable LAN address).

A manual LAN address override (for example http://192.168.1.20:32400) is
available for servers with unusual network setups. The active connection and
its type are shown in Settings and the app re-runs the chooser when the network
changes or a request fails.

### Lock, edit and customise

Locked items: any photo, video or album can be locked. Locked items disappear
from the timeline, albums and search and live in a Locked tab that opens only
after fingerprint, face or device PIN (BiometricPrompt). Locks are stored on
the device (Plex has no lock concept), so they are per phone.

Editor: rotate, flip, crop with free and preset ratios, adjustments
(brightness, contrast, saturation, warmth), and filter presets. The result is
saved as a new JPEG in Pictures/Prism on the device, because the Plex API has
no upload path for photo libraries. The original in Plex is never changed.

Customise: rename a photo, video or album, edit its description, add or remove
tags (all written back to Plex), set an album cover and an album accent colour
(stored on the device), and choose sort order per album.

### Sharing

Long press or the viewer share button opens a share row with direct targets:
WhatsApp, Messages, Gmail, Instagram, Facebook Messenger, Telegram, and a
More button for the full Android share sheet. Files are downloaded from Plex
to a private cache first and shared through a FileProvider, so the recipient
gets the actual photo or video, not a link that needs Plex access. Multi-select
sharing sends several files at once.

### Default gallery for the device

Android has no single default gallery setting, but Prism registers as a viewer
for images and videos (ACTION_VIEW and the camera REVIEW action), as a picker
(ACTION_PICK and GET_CONTENT) so other apps offering choose from gallery list it,
and as a share target. A Device tab shows the phone photos and videos from
MediaStore so Prism works as a complete gallery. Settings has a Set as default
gallery entry that opens the system Open by default page for Prism.
