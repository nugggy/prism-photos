# Private folders in locked albums: design

Date: 15/10/2026. Status: approved in conversation, awaiting written spec review. Android only.

## Purpose

The user keeps intimate photos and videos in locked albums and in the Locked tab. They want to sort
them into folders so they are easier to browse. Success means they can create folders inside any
locked place, move items in and out quickly, and browse a folder on its own, without anything
about the folders becoming visible outside the app's lock.

## Decisions (made by the user)

- Folders exist only in the app, on the phone. Nothing is written to Plex or the NAS, so Plex,
  the web app and the NAS share see no change. Plex playlists and real NAS folders were rejected
  because they would be visible outside the lock.
- An item belongs to at most one folder within its place (filing, not labels).
- One level of folders. Folders cannot contain folders.
- Folders are available inside every locked album and on the Locked tab (for individually locked
  items).
- Folder data is excluded from Android cloud backup and device transfer, so folder names never
  leave the phone. Folders are lost if the phone is reset or replaced.
- The web app is unchanged.

Known limit, stated to the user: locking only hides items inside Plex Gallery. The files remain
visible in Plex's own apps and on the NAS share. Private folders do not change that.

## Model

```kotlin
@Serializable
data class PrivateFolder(
    val id: String,          // random UUID
    val home: String,        // a locked album's Plex id, or LOCKED_TAB_HOME
    val name: String,
    val itemIds: List<String>,
    val createdAt: Long,
)

const val LOCKED_TAB_HOME = "locked-tab"
```

A "place" is a home value. In one place, an item id appears in at most one folder. Items that are
in no folder of a place are that place's loose items.

The home for an album screen is the id of the album being shown, whenever that screen is opened
in gated (locked) mode. That covers a locked album and any Plex sub-album opened from inside it.

## Components

### `data/folders/PrivateFolders.kt` (new, pure Kotlin)

Pure functions over `List<PrivateFolder>`. No Android or coroutine dependencies, so they are
unit tested directly.

- `create(folders, home, name, now, id): List<PrivateFolder>`, rejecting a blank name or a name
  already used in that home (case-insensitive, trimmed). Rejection is a typed result, not an
  exception.
- `rename(folders, folderId, name)` with the same name rules.
- `delete(folders, folderId)`. The items become loose again. Photos are never deleted.
- `move(folders, home, itemIds, toFolderId)`. Removes the ids from every other folder in the same
  home, then appends any not already present to the target folder.
- `remove(folders, home, itemIds)`. Removes the ids from every folder in that home.
- `removeEverywhere(folders, itemIds)`. Removes the ids from every folder in every home.
- `folderOf(folders, home, itemId): PrivateFolder?`
- `filedIds(folders, home): Set<String>`, the union of item ids in that home's folders.
- `prune(folders, home, existingIds)`. Drops ids that are no longer in the place.

### `LocalStore` additions

```kotlin
val privateFolders: StateFlow<List<PrivateFolder>>
suspend fun updatePrivateFolders(transform: (List<PrivateFolder>) -> List<PrivateFolder>)
```

`LocalStoreImpl` keeps the folders in a separate DataStore file, `prism_private_folders`, under the
key `private_folders_json`. Android backup rules work per file, so the separate file is what lets
the folders be excluded. Loading follows the existing pattern: a synchronous first read in `init`,
and a corrupt or missing value gives an empty list. Every change is one atomic DataStore edit, so a
multi-item move is never half applied.

Fakes (`data/fake/Fakes.kt`) get an in-memory implementation.

### Backup exclusion

Add `datastore/prism_private_folders.preferences_pb` to the `cloud-backup` and `device-transfer`
exclusions in `res/xml/data_extraction_rules.xml`, and to `res/xml/backup_rules.xml`, next to the
existing session and upload settings entries.

### Lock interactions

- Unlocking an album keeps its folders but nothing shows them. Locking the album again brings them
  back.
- Unlocking an individually locked item also removes it from any Locked tab folder
  (`MediaSelectionOps.unlock` and `ViewerViewModel.unlock` call `PrivateFolders.remove` with
  `LOCKED_TAB_HOME`). Undo restores the lock, not the folder membership.
- Deleting an item from Plex removes it from every folder in every home. `MediaSelectionOps.delete`
  and the viewer's delete call a `PrivateFolders.removeEverywhere(folders, itemIds)` helper for the
  ids that were deleted successfully.

## Screens

### Folders row (new shared composable `ui/folders/FolderRow.kt`)

A horizontal row of folder cards for one home. Each card shows a cover (the folder's newest item
by `takenAt`), the name and an item count. The row ends with a New folder card. Long-pressing a card
offers Rename and Delete. The row is titled "Folders".

### Locked album (`AlbumDetailScreen`, gated mode only)

- The Folders row appears above the item grid, below the existing Plex sub-album row. The two keep
  separate titles.
- The item grid shows loose items only: album items whose ids are not in `filedIds` for this
  album's home.
- Selection mode gains Move to folder.
- In ungated mode the screen is unchanged.

### Locked tab (`LockedScreen`)

- The Folders row (home `LOCKED_TAB_HOME`) appears between the Locked albums chips and the Locked
  items grid.
- The grid shows loose locked items only.
- Selection mode gains Move to folder.

### Move to folder sheet (new `ui/folders/MoveToFolderSheet.kt`)

A bottom sheet that lists the home's folders, with the current folder (if any) disabled. A New
folder option asks for a name, creates the folder and moves the selection into it in one step.
After a move, a snackbar offers Undo, which restores the previous folder state for those items.

### Folder screen (new `ui/folders/PrivateFolderScreen.kt`)

- New route `privateFolder/{folderId}`, always wrapped in `Gated(true)`.
- The title is the folder name. The menu has Rename and Delete folder. Delete asks for
  confirmation and says the photos go back to the album or the Locked tab.
- The grid shows the folder's items, newest first.
- Selection mode offers share, download, favourite, Move to folder, Remove from folder, and delete
  from Plex (with the existing confirmation).
- If the folder no longer exists (for example it was deleted on another screen), the screen pops
  back.

### Viewer

- New `ViewerSource.PrivateFolder(folderId)`, encoded as `folder:ID` in `Routes`/`SourceCodec`, so
  swiping stays inside the folder. The viewer opens with `locked = true`.
- The viewer menu gains Move to folder when the source is a gated album, `PrivateFolder` or
  `Locked`, using the same sheet for the current item.

## Data flow

- Folder items are resolved by id with `MediaRepository.items(ids)`. That reads the timeline cache
  first and fetches missing ids from Plex. A small concurrency limit applies, matching how the
  Locked tab fills gaps.
- Ids that Plex no longer returns are skipped on display. They are pruned from the folder the next
  time the folders for that home are written.
- The loose items for an album are the album's contents minus `filedIds(home)`. They are worked out
  in the view model from `albumContents` and the `privateFolders` flow, so a move shows straight away.

## Error handling

- An empty or duplicate name is rejected in the dialog with an inline message. The dialog stays
  open.
- A failed item fetch on the folder screen shows the existing error state with Retry. A partial
  fetch shows what loaded.
- Corrupt folder data gives an empty list, never a crash, which matches locks today.

## Strings

All new text goes in string resources (`strings.xml` or a new `strings_folders.xml`), using plurals
for counts. Text is in Australian English.

## Testing

- `PrivateFoldersTest`: create, duplicate and blank names, rename, delete returning items to loose,
  move between folders (one folder per item per home), the same id in different homes being
  independent, remove, `filedIds`, and prune.
- `RoutesTest`: round trip of `ViewerSource.PrivateFolder` and the folder route.
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` must pass, with lint clean as CI
  requires.
- A debug APK for the user to try on the phone before any release.

## Out of scope

The web app, folders within folders, folders outside locked places, syncing folders between
devices, and per-folder sort options.
