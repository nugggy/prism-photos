# Android code ownership and contracts

Package root: `au.prism.photos` under `app/src/main/java/au/prism/photos`.

The UI never talks to Retrofit or Plex directly. It uses the interfaces in
`domain/Contracts.kt` through `PrismApp.graph` (an `AppGraph`). Models are in
`domain/Models.kt`. Both files are frozen contracts. If a change is needed, add
things rather than renaming, and say so in your report.

## Ownership (do not edit files outside your area)

| Area | Owner | Packages and files |
|---|---|---|
| Data layer | data agent | `data/**` (including `data/AppGraph.kt`, replace the fakes), `app/src/test/**` for data tests |
| UI shell | shell agent | `MainActivity.kt`, `ui/theme/**`, `ui/nav/**`, `ui/signin/**`, `ui/servers/**`, `ui/home/**`, `ui/timeline/**`, `ui/albums/**`, `ui/favourites/**`, `ui/device/**`, `ui/locked/**`, `ui/search/**`, `ui/settings/**`, `ui/components/**`, `ui/update/**`, `res/values/strings.xml` |
| Viewer | viewer agent | `ui/viewer/**`, `ui/player/**`, `ui/editor/**`, `ui/lock/**`, `ui/share/**`, `util/**`, `res/values/strings_viewer.xml` |
| Overseer | overseer | Gradle files, manifest, icons, docs, workflows, README |

Stubs that the viewer agent replaces (keep the same signatures, the shell calls them):

- `ui/viewer/ViewerScreen.kt`: `ViewerScreen(source, startIndex, onClose, onEdit)`
- `ui/viewer/MediaActions.kt`: `MediaActions.download(context, items)`, `MediaActions.setWallpaper(context, item, lockScreen)`
- `ui/editor/EditorScreen.kt`: `EditorScreen(itemId, onDone, onCancel)`
- `ui/lock/LockGate.kt`: `LockGate(content)` and `LockSession.unlocked`
- `ui/share/ShareSheet.kt`: `ShareSheet(items, onDismiss)`

Navigation routes are in `ui/nav/Routes.kt` (shell owns it). `SourceCodec` encodes a
`ViewerSource` into the route.

## Build

Windows, run from Git Bash in the project root:

```
./gradlew.bat assembleDebug --no-daemon -q
./gradlew.bat testDebugUnitTest --no-daemon -q
```

Memory is limited. Build at milestones, not after every edit. The version catalog in
`gradle/libs.versions.toml` is pinned to versions that compile against SDK 36 with
AGP 8.13. Do not bump versions. Adding a library is allowed only if essential; note it.

## Style

Australian English in user facing text (favourite, colour, organise). No em dashes.
Material 3, edge to edge, dynamic colour when enabled, Plex gold `#E5A00D` as the
brand accent, Plex dark `#1F2326` background in dark mode.
