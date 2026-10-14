# Private folders implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user create folders inside locked albums and on the Locked tab of the Android app, move items into and out of them, and browse each folder on its own, with folders stored only on the phone and excluded from backup.

**Architecture:** A `PrivateFolder` model lives in `domain/Models.kt`. All folder rules are pure functions in `data/folders/PrivateFolders.kt`, which are unit tested. `LocalStore` stores the list in its own DataStore file, which is excluded from backup. UI pieces in `ui/folders/` (folder row, dialogs, move sheet, folder screen) are wired into the locked album screen, the Locked tab and the viewer.

**Tech stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.06.01, material-icons-extended 1.7.8), AndroidX DataStore Preferences, kotlinx.serialization, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-15-private-folders-design.md`

## Deviations from the spec (decided while planning)

- The viewer source is named `ViewerSource.Folder(folderId)` rather than `ViewerSource.PrivateFolder`, to avoid clashing with the `PrivateFolder` model class. It's encoded as `folder:ID`.
- `PrivateFolders.move(folders, itemIds, toFolderId)` takes the home from the target folder, so it has no `home` parameter.
- Removing deleted items from folders happens once, in `MediaRepositoryImpl.delete`, instead of in `MediaSelectionOps` and the viewer. Every Plex delete passes through there.
- Removing unlocked items from Locked tab folders happens in `LocalStore.setItemLocked`/`setItemsLocked`, instead of in `MediaSelectionOps` and the viewer. That's the one path every unlock takes.
- Pruning runs only on the locked album screen after its contents load successfully. It prunes against the album's items plus individually locked ids, because the repository leaves individually locked items out of album contents. The Locked tab is never pruned, because its list isn't authoritative while the timeline is loading.
- Folder items are resolved with the existing `MediaRepository.items(ids)`. That reads the timeline cache first and fetches the rest one at a time. No new concurrency code is added.

## Global constraints

- Android only. The web app (`web/`) is not touched.
- Nothing is written to Plex or the NAS for folders. No new network calls.
- Folder data is kept in DataStore file `prism_private_folders`, key `private_folders_json`. That file is excluded from `cloud-backup` and `device-transfer` (in `data_extraction_rules.xml`) and from `backup_rules.xml`.
- One folder per item within a home. One level of folders. Homes are a locked album's Plex id or `LOCKED_TAB_HOME = "locked-tab"`.
- Deleting a folder never deletes photos.
- All user-visible text goes in string resources (new file `app/src/main/res/values/strings_folders.xml`), in Australian English, with plurals for counts.
- No dependency or version changes. Library versions are pinned to compile against SDK 36 with AGP 8.13.
- Keep Gradle memory low: pass `"-Dorg.gradle.jvmargs=-Xmx2g"`. Run Gradle from PowerShell as `.\gradlew.bat ...`.
- Work on branch `private-folders`, not `main`. End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review focus

1. Moving items that are already in the target folder must not duplicate them, and must not reorder that folder (Task 1 test `move does not duplicate items already in the target`).
2. The same photo filed in a locked album folder and in a Locked tab folder are independent memberships. Moving it in one home must not touch the other (Task 1 test `homes are independent`).
3. Undo after the original folder was deleted must leave the item loose. It must not crash or recreate the folder (Task 1 test `restore treats a deleted folder as loose`).
4. Folder names are compared trimmed and case-insensitively, and renaming a folder to its own name in a different case is allowed (Task 1 tests on `nameProblem` and `rename`).
5. A folder id inside a route or viewer source must round trip unchanged (Task 4 tests in `RoutesTest`).

---

## File map

Create:
- `app/src/main/java/au/prism/photos/data/folders/PrivateFolders.kt`: pure folder rules
- `app/src/test/java/au/prism/photos/data/folders/PrivateFoldersTest.kt`
- `app/src/main/res/values/strings_folders.xml`
- `app/src/main/java/au/prism/photos/ui/folders/FolderOps.kt`: suspend operations with undo
- `app/src/main/java/au/prism/photos/ui/folders/FolderDialogs.kt`: name and delete dialogs
- `app/src/main/java/au/prism/photos/ui/folders/FolderRow.kt`: row of folder cards, with its own dialogs
- `app/src/main/java/au/prism/photos/ui/folders/MoveToFolderSheet.kt`
- `app/src/main/java/au/prism/photos/ui/folders/PrivateFolderViewModel.kt`
- `app/src/main/java/au/prism/photos/ui/folders/PrivateFolderScreen.kt`

Modify:
- `app/src/main/java/au/prism/photos/domain/Models.kt`: `PrivateFolder`, `LOCKED_TAB_HOME`, `ViewerSource.Folder`
- `app/src/main/java/au/prism/photos/domain/Contracts.kt`: `LocalStore` additions
- `app/src/main/java/au/prism/photos/data/LocalStoreImpl.kt`
- `app/src/main/java/au/prism/photos/data/fake/Fakes.kt`: `FakeLocalStore`
- `app/src/main/java/au/prism/photos/data/MediaRepositoryImpl.kt`: `delete`
- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/java/au/prism/photos/ui/nav/Routes.kt`, `ui/nav/PrismNavHost.kt`
- `app/src/test/java/au/prism/photos/ui/nav/RoutesTest.kt`
- `app/src/main/java/au/prism/photos/ui/viewer/ViewerViewModel.kt`, `ViewerScreen.kt`, `ViewerBars.kt`
- `app/src/main/java/au/prism/photos/ui/components/SelectionBar.kt`
- `app/src/main/java/au/prism/photos/ui/albums/AlbumDetailViewModel.kt`, `AlbumDetailScreen.kt`
- `app/src/main/java/au/prism/photos/ui/locked/LockedScreen.kt`, `ui/home/HomeScreen.kt`

---

### Task 0: Branch

- [ ] **Step 1: Create the branch**

```powershell
git checkout -b private-folders
```

Expected: `Switched to a new branch 'private-folders'`.

---

### Task 1: Folder model and rules

**Files:**
- Modify: `app/src/main/java/au/prism/photos/domain/Models.kt` (append after `ViewerSource`)
- Create: `app/src/main/java/au/prism/photos/data/folders/PrivateFolders.kt`
- Test: `app/src/test/java/au/prism/photos/data/folders/PrivateFoldersTest.kt`

**Interfaces:**
- Produces: `PrivateFolder(id, home, name, itemIds, createdAt)`, `LOCKED_TAB_HOME`, `FolderNameProblem { BLANK, DUPLICATE }`, and `object PrivateFolders` with `inHome`, `nameProblem`, `create`, `rename`, `delete`, `move`, `remove`, `removeEverywhere`, `folderOf`, `filedIds`, `prune`, `membership`, `restore` (signatures below).

- [ ] **Step 1: Add the model to `domain/Models.kt`** (append at the end of the file)

```kotlin
/**
 * A folder the user made inside a locked place to sort its items. Exists only on this phone and is
 * kept out of backups; see data/folders/PrivateFolders.kt for the rules.
 */
@Serializable
data class PrivateFolder(
    val id: String,
    /** A locked album's Plex id, or [LOCKED_TAB_HOME]. */
    val home: String,
    val name: String,
    val itemIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/** [PrivateFolder.home] for folders on the Locked tab. */
const val LOCKED_TAB_HOME = "locked-tab"
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package au.prism.photos.data.folders

import au.prism.photos.domain.PrivateFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateFoldersTest {
    private fun folder(id: String, home: String = "A", name: String = id, items: List<String> = emptyList()) =
        PrivateFolder(id = id, home = home, name = name, itemIds = items, createdAt = 0L)

    @Test
    fun `create adds an empty folder with a trimmed name`() {
        val result = PrivateFolders.create(emptyList(), "A", "  Beach  ", id = "f1", now = 5L)
        assertEquals(listOf(PrivateFolder("f1", "A", "Beach", emptyList(), 5L)), result)
    }

    @Test
    fun `nameProblem rejects blank and duplicate names in the same home only`() {
        val folders = listOf(folder("f1", name = "Beach"))
        assertEquals(FolderNameProblem.BLANK, PrivateFolders.nameProblem(folders, "A", "   "))
        assertEquals(FolderNameProblem.DUPLICATE, PrivateFolders.nameProblem(folders, "A", " beach "))
        assertNull(PrivateFolders.nameProblem(folders, "B", "Beach"))
        assertNull(PrivateFolders.nameProblem(folders, "A", "Beach", excludingId = "f1"))
    }

    @Test
    fun `create leaves the list unchanged for a bad name`() {
        val folders = listOf(folder("f1", name = "Beach"))
        assertSame(folders, PrivateFolders.create(folders, "A", "BEACH", "f2", 0L))
        assertSame(folders, PrivateFolders.create(folders, "A", "", "f2", 0L))
    }

    @Test
    fun `rename allows a case change of its own name and rejects a clash`() {
        val folders = listOf(folder("f1", name = "Beach"), folder("f2", name = "Night"))
        assertEquals("BEACH", PrivateFolders.rename(folders, "f1", "BEACH").first { it.id == "f1" }.name)
        assertSame(folders, PrivateFolders.rename(folders, "f1", "night"))
        assertSame(folders, PrivateFolders.rename(folders, "missing", "X"))
    }

    @Test
    fun `delete removes only the folder`() {
        val folders = listOf(folder("f1", items = listOf("1")), folder("f2", items = listOf("2")))
        assertEquals(listOf("f2"), PrivateFolders.delete(folders, "f1").map { it.id })
    }

    @Test
    fun `move files items into the target and out of other folders in the same home`() {
        val folders = listOf(
            folder("f1", items = listOf("1", "2")),
            folder("f2", items = listOf("3")),
            folder("g1", home = "B", items = listOf("1")),
        )
        val result = PrivateFolders.move(folders, listOf("1", "4"), "f2")
        assertEquals(listOf("2"), result.first { it.id == "f1" }.itemIds)
        assertEquals(listOf("3", "1", "4"), result.first { it.id == "f2" }.itemIds)
        assertEquals(listOf("1"), result.first { it.id == "g1" }.itemIds)
    }

    @Test
    fun `move does not duplicate items already in the target`() {
        val folders = listOf(folder("f1", items = listOf("1", "2")))
        assertEquals(listOf("1", "2", "3"), PrivateFolders.move(folders, listOf("2", "3", "3"), "f1").single().itemIds)
    }

    @Test
    fun `move to an unknown folder changes nothing`() {
        val folders = listOf(folder("f1", items = listOf("1")))
        assertSame(folders, PrivateFolders.move(folders, listOf("1"), "missing"))
    }

    @Test
    fun `homes are independent`() {
        val folders = listOf(folder("f1", home = "A", items = listOf("1")), folder("g1", home = "B"))
        val result = PrivateFolders.move(folders, listOf("1"), "g1")
        assertEquals(listOf("1"), result.first { it.id == "f1" }.itemIds)
        assertEquals(listOf("1"), result.first { it.id == "g1" }.itemIds)
    }

    @Test
    fun `remove takes items out of one home only`() {
        val folders = listOf(folder("f1", items = listOf("1", "2")), folder("g1", home = "B", items = listOf("1")))
        val result = PrivateFolders.remove(folders, "A", listOf("1"))
        assertEquals(listOf("2"), result.first { it.id == "f1" }.itemIds)
        assertEquals(listOf("1"), result.first { it.id == "g1" }.itemIds)
    }

    @Test
    fun `removeEverywhere takes items out of every home`() {
        val folders = listOf(folder("f1", items = listOf("1", "2")), folder("g1", home = "B", items = listOf("1")))
        val result = PrivateFolders.removeEverywhere(folders, listOf("1"))
        assertEquals(listOf(listOf("2"), emptyList<String>()), result.map { it.itemIds })
    }

    @Test
    fun `filedIds and folderOf look within one home`() {
        val folders = listOf(folder("f1", items = listOf("1")), folder("f2", items = listOf("2")), folder("g1", home = "B", items = listOf("3")))
        assertEquals(setOf("1", "2"), PrivateFolders.filedIds(folders, "A"))
        assertEquals("f2", PrivateFolders.folderOf(folders, "A", "2")?.id)
        assertNull(PrivateFolders.folderOf(folders, "A", "3"))
    }

    @Test
    fun `prune drops missing ids in one home and keeps order`() {
        val folders = listOf(folder("f1", items = listOf("3", "1", "2")), folder("g1", home = "B", items = listOf("9")))
        val result = PrivateFolders.prune(folders, "A", setOf("1", "3"))
        assertEquals(listOf("3", "1"), result.first { it.id == "f1" }.itemIds)
        assertEquals(listOf("9"), result.first { it.id == "g1" }.itemIds)
    }

    @Test
    fun `membership and restore undo a move`() {
        val before = listOf(folder("f1", items = listOf("1")), folder("f2"))
        val membership = PrivateFolders.membership(before, "A", listOf("1", "2"))
        assertEquals(mapOf("1" to "f1", "2" to null), membership)
        val moved = PrivateFolders.move(before, listOf("1", "2"), "f2")
        val restored = PrivateFolders.restore(moved, "A", membership)
        assertEquals(listOf("1"), restored.first { it.id == "f1" }.itemIds)
        assertEquals(emptyList<String>(), restored.first { it.id == "f2" }.itemIds)
    }

    @Test
    fun `restore treats a deleted folder as loose`() {
        val membership = mapOf("1" to "gone")
        val folders = listOf(folder("f2", items = listOf("1")))
        val restored = PrivateFolders.restore(folders, "A", membership)
        assertEquals(listOf(folder("f2")), restored)
    }

    @Test
    fun `inHome lists one home sorted by name`() {
        val folders = listOf(folder("f1", name = "zed"), folder("f2", name = "Alpha"), folder("g1", home = "B", name = "B"))
        assertEquals(listOf("f2", "f1"), PrivateFolders.inHome(folders, "A").map { it.id })
        assertTrue(PrivateFolders.inHome(folders, "C").isEmpty())
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "au.prism.photos.data.folders.PrivateFoldersTest" "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: compilation fails with `Unresolved reference 'PrivateFolders'`.

- [ ] **Step 4: Write `data/folders/PrivateFolders.kt`**

```kotlin
package au.prism.photos.data.folders

import au.prism.photos.domain.PrivateFolder

/** Why a proposed folder name cannot be used. */
enum class FolderNameProblem { BLANK, DUPLICATE }

/**
 * Pure rules for private folders. Within one home an item id is in at most one folder, and every
 * operation here keeps that true. Nothing here deletes photos: deleting a folder or removing items
 * only makes them loose again.
 */
object PrivateFolders {
    /** The folders of [home], sorted by name. */
    fun inHome(folders: List<PrivateFolder>, home: String): List<PrivateFolder> =
        folders.filter { it.home == home }.sortedBy { it.name.lowercase() }

    /** Null when [name] can be used in [home]. [excludingId] is the folder being renamed. */
    fun nameProblem(folders: List<PrivateFolder>, home: String, name: String, excludingId: String? = null): FolderNameProblem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return FolderNameProblem.BLANK
        val clash = folders.any { it.home == home && it.id != excludingId && it.name.trim().equals(trimmed, ignoreCase = true) }
        return if (clash) FolderNameProblem.DUPLICATE else null
    }

    /** Adds an empty folder, or returns [folders] unchanged if the name has a [nameProblem]. */
    fun create(folders: List<PrivateFolder>, home: String, name: String, id: String, now: Long): List<PrivateFolder> {
        if (nameProblem(folders, home, name) != null) return folders
        return folders + PrivateFolder(id = id, home = home, name = name.trim(), itemIds = emptyList(), createdAt = now)
    }

    fun rename(folders: List<PrivateFolder>, folderId: String, name: String): List<PrivateFolder> {
        val folder = folders.firstOrNull { it.id == folderId } ?: return folders
        if (nameProblem(folders, folder.home, name, excludingId = folderId) != null) return folders
        return folders.map { if (it.id == folderId) it.copy(name = name.trim()) else it }
    }

    /** Removes the folder; its items become loose. */
    fun delete(folders: List<PrivateFolder>, folderId: String): List<PrivateFolder> = folders.filterNot { it.id == folderId }

    /** Files [itemIds] into [toFolderId], taking them out of every other folder in the same home. */
    fun move(folders: List<PrivateFolder>, itemIds: Collection<String>, toFolderId: String): List<PrivateFolder> {
        val target = folders.firstOrNull { it.id == toFolderId } ?: return folders
        val ids = itemIds.distinct()
        val moving = ids.toSet()
        return folders.map { f ->
            when {
                f.id == toFolderId -> f.copy(itemIds = f.itemIds + ids.filterNot { it in f.itemIds })
                f.home == target.home -> f.copy(itemIds = f.itemIds.filterNot { it in moving })
                else -> f
            }
        }
    }

    /** Makes [itemIds] loose in [home]. */
    fun remove(folders: List<PrivateFolder>, home: String, itemIds: Collection<String>): List<PrivateFolder> {
        val ids = itemIds.toSet()
        return folders.map { if (it.home == home) it.copy(itemIds = it.itemIds.filterNot { id -> id in ids }) else it }
    }

    /** Takes [itemIds] out of every folder in every home (used when they are deleted from Plex). */
    fun removeEverywhere(folders: List<PrivateFolder>, itemIds: Collection<String>): List<PrivateFolder> {
        val ids = itemIds.toSet()
        return folders.map { it.copy(itemIds = it.itemIds.filterNot { id -> id in ids }) }
    }

    fun folderOf(folders: List<PrivateFolder>, home: String, itemId: String): PrivateFolder? =
        folders.firstOrNull { it.home == home && itemId in it.itemIds }

    /** Every item id filed in a folder of [home]. */
    fun filedIds(folders: List<PrivateFolder>, home: String): Set<String> =
        folders.filter { it.home == home }.flatMapTo(HashSet()) { it.itemIds }

    /** Drops ids not in [existingIds] from the folders of [home], keeping their order. */
    fun prune(folders: List<PrivateFolder>, home: String, existingIds: Set<String>): List<PrivateFolder> =
        folders.map { if (it.home == home) it.copy(itemIds = it.itemIds.filter { id -> id in existingIds }) else it }

    /** The folder id (or null for loose) of each of [itemIds] in [home], for undo with [restore]. */
    fun membership(folders: List<PrivateFolder>, home: String, itemIds: Collection<String>): Map<String, String?> =
        itemIds.associateWith { folderOf(folders, home, it)?.id }

    /** Puts each item back where [membership] says. A folder deleted since then counts as loose. */
    fun restore(folders: List<PrivateFolder>, home: String, membership: Map<String, String?>): List<PrivateFolder> {
        var out = remove(folders, home, membership.keys)
        membership.entries.groupBy({ it.value }, { it.key }).forEach { (folderId, ids) ->
            if (folderId != null) out = move(out, ids, folderId)
        }
        return out
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "au.prism.photos.data.folders.PrivateFoldersTest" "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`, with all 16 tests passing.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/au/prism/photos/domain/Models.kt app/src/main/java/au/prism/photos/data/folders/PrivateFolders.kt app/src/test/java/au/prism/photos/data/folders/PrivateFoldersTest.kt
git commit -m @'
Private folders: model and pure folder rules with tests

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 2: Storage, backup exclusion, unlock and delete hooks

**Files:**
- Modify: `app/src/main/java/au/prism/photos/domain/Contracts.kt:37-49` (`LocalStore`)
- Modify: `app/src/main/java/au/prism/photos/data/LocalStoreImpl.kt`
- Modify: `app/src/main/java/au/prism/photos/data/fake/Fakes.kt:101-118` (`FakeLocalStore`)
- Modify: `app/src/main/java/au/prism/photos/data/MediaRepositoryImpl.kt` (`override suspend fun delete`)
- Modify: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:**
- Consumes: `PrivateFolder`, `LOCKED_TAB_HOME`, `PrivateFolders.remove`, `PrivateFolders.removeEverywhere`, `PrivateFolders.filedIds` (Task 1).
- Produces: `LocalStore.privateFolders: StateFlow<List<PrivateFolder>>` and `LocalStore.updatePrivateFolders(transform: (List<PrivateFolder>) -> List<PrivateFolder>)`.

The rules these hooks rely on are already covered by Task 1's tests. This task is wiring and is verified by compiling and running the full suite.

- [ ] **Step 1: Extend `LocalStore` in `domain/Contracts.kt`** (add inside the interface, after `setAlbumSort`)

```kotlin
    /** Folders inside locked places (see data/folders/PrivateFolders.kt). Stored apart and kept out of backups. */
    val privateFolders: StateFlow<List<PrivateFolder>>
    /** Applies [transform] to the folder list and saves the result in one write. */
    suspend fun updatePrivateFolders(transform: (List<PrivateFolder>) -> List<PrivateFolder>)
```

`Contracts.kt` is in package `au.prism.photos.domain`, the same as `PrivateFolder`, so it needs no import.

- [ ] **Step 2: Implement in `LocalStoreImpl.kt`**

Add these imports:

```kotlin
import au.prism.photos.data.folders.PrivateFolders
import au.prism.photos.domain.LOCKED_TAB_HOME
import au.prism.photos.domain.PrivateFolder
```

Below `private val Context.localDataStore ...`, add a second store and key:

```kotlin
/** Its own file so backup rules can exclude it: folder names never leave the phone. */
private val Context.privateFolderStore by preferencesDataStore(name = "prism_private_folders")
private val PRIVATE_FOLDERS_KEY = stringPreferencesKey("private_folders_json")
```

In the class, add the field next to the other `MutableStateFlow` fields:

```kotlin
    private val folders: MutableStateFlow<List<PrivateFolder>>
```

At the end of `init`, add:

```kotlin
        val folderPrefs = runBlocking { runCatching { app.privateFolderStore.data.first() }.getOrNull() }
        folders = MutableStateFlow(folderPrefs?.get(PRIVATE_FOLDERS_KEY)?.let { decodeOrNull<List<PrivateFolder>>(it) } ?: emptyList())
```

After `override val albumSort ...`, add:

```kotlin
    override val privateFolders: StateFlow<List<PrivateFolder>> = folders.asStateFlow()

    override suspend fun updatePrivateFolders(transform: (List<PrivateFolder>) -> List<PrivateFolder>) {
        folders.update(transform)
        val text = json.encodeToString(folders.value)
        runCatching { app.privateFolderStore.edit { it[PRIVATE_FOLDERS_KEY] = text } }
    }

    /** An item that is no longer locked leaves the Locked tab's folders. */
    private suspend fun leaveLockedTabFolders(ids: Collection<String>) {
        val filed = PrivateFolders.filedIds(folders.value, LOCKED_TAB_HOME)
        if (ids.none { it in filed }) return
        updatePrivateFolders { PrivateFolders.remove(it, LOCKED_TAB_HOME, ids) }
    }
```

Change `setItemLocked` and `setItemsLocked` to:

```kotlin
    override suspend fun setItemLocked(id: String, locked: Boolean) {
        this.locked.update { if (locked) it + id else it - id }
        persist(LOCKED_ITEMS_KEY, this.locked.value)
        if (!locked) leaveLockedTabFolders(listOf(id))
    }

    override suspend fun setItemsLocked(ids: Collection<String>, locked: Boolean) {
        this.locked.update { if (locked) it + ids else it - ids.toSet() }
        persist(LOCKED_ITEMS_KEY, this.locked.value)
        if (!locked) leaveLockedTabFolders(ids)
    }
```

- [ ] **Step 3: Implement in `FakeLocalStore` (`data/fake/Fakes.kt`)**

Add these imports: `au.prism.photos.data.folders.PrivateFolders`, `au.prism.photos.domain.LOCKED_TAB_HOME`, `au.prism.photos.domain.PrivateFolder`. Then replace the two lock setters and add the folder members:

```kotlin
    private val folders = MutableStateFlow<List<PrivateFolder>>(emptyList())
    override val privateFolders: StateFlow<List<PrivateFolder>> = folders.asStateFlow()
    override suspend fun updatePrivateFolders(transform: (List<PrivateFolder>) -> List<PrivateFolder>) = folders.update(transform)
    override suspend fun setItemLocked(id: String, locked: Boolean) {
        this.locked.update { if (locked) it + id else it - id }
        if (!locked) folders.update { PrivateFolders.remove(it, LOCKED_TAB_HOME, listOf(id)) }
    }
    override suspend fun setItemsLocked(ids: Collection<String>, locked: Boolean) {
        this.locked.update { if (locked) it + ids else it - ids.toSet() }
        if (!locked) folders.update { PrivateFolders.remove(it, LOCKED_TAB_HOME, ids) }
    }
```

- [ ] **Step 4: Remove deleted items from folders in `MediaRepositoryImpl.delete`**

Add the import `au.prism.photos.data.folders.PrivateFolders`. Then, in `override suspend fun delete(id: String)`, directly after `allItems.update { list -> list.filterNot { it.id == id } }`, add:

```kotlin
        if (local.privateFolders.value.any { id in it.itemIds }) {
            local.updatePrivateFolders { PrivateFolders.removeEverywhere(it, listOf(id)) }
        }
```

- [ ] **Step 5: Exclude the folder file from backups**

In `app/src/main/res/xml/backup_rules.xml`, inside `<full-backup-content>`, after the upload settings line, add:

```xml
    <exclude domain="file" path="datastore/prism_private_folders.preferences_pb" />
```

Also add a sentence to that file's comment: `Private folder names (datastore/prism_private_folders) are excluded so they never leave the phone.`

In `app/src/main/res/xml/data_extraction_rules.xml`, add the same `<exclude ... prism_private_folders.preferences_pb" />` line inside both `<cloud-backup>` and `<device-transfer>`, after the upload settings line.

- [ ] **Step 6: Compile and run all unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/au/prism/photos/domain/Contracts.kt app/src/main/java/au/prism/photos/data/LocalStoreImpl.kt app/src/main/java/au/prism/photos/data/fake/Fakes.kt app/src/main/java/au/prism/photos/data/MediaRepositoryImpl.kt app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git commit -m @'
Private folders: on-device storage excluded from backup, unlock and delete clean-up

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 3: Shared folder UI pieces

**Files:**
- Create: `app/src/main/res/values/strings_folders.xml`
- Create: `app/src/main/java/au/prism/photos/ui/folders/FolderOps.kt`
- Create: `app/src/main/java/au/prism/photos/ui/folders/FolderDialogs.kt`
- Create: `app/src/main/java/au/prism/photos/ui/folders/FolderRow.kt`
- Create: `app/src/main/java/au/prism/photos/ui/folders/MoveToFolderSheet.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/components/SelectionBar.kt`

**Interfaces:**
- Consumes: `LocalStore.privateFolders`, `LocalStore.updatePrivateFolders` (Task 2); `PrivateFolders.*` (Task 1); `Undoable`, `MediaSelectionOps.offerUndo` (existing, `ui/components/MediaSelectionOps.kt`).
- Produces:
  - `FolderOps.create(graph: AppGraph, home: String, name: String): String` (returns the new folder id)
  - `FolderOps.rename(graph: AppGraph, folderId: String, name: String)`
  - `FolderOps.delete(graph: AppGraph, folderId: String)`
  - `FolderOps.moveAndOfferUndo(graph: AppGraph, snackbar: SnackbarHostState, resources: Resources, home: String, itemIds: Collection<String>, folderId: String)`
  - `FolderOps.removeAndOfferUndo(graph: AppGraph, snackbar: SnackbarHostState, resources: Resources, home: String, itemIds: Collection<String>)`
  - `@Composable FolderNameDialog(title: String, confirmLabel: String, home: String, initial: String = "", excludingId: String? = null, onConfirm: (String) -> Unit, onDismiss: () -> Unit)`
  - `@Composable DeleteFolderDialog(home: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable FolderRow(home: String, folders: List<PrivateFolder>, coverFor: (PrivateFolder) -> MediaItem?, onOpen: (PrivateFolder) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable MoveToFolderSheet(home: String, currentFolderId: String?, onMove: (folderId: String) -> Unit, onDismiss: () -> Unit)`
  - `SelectionTopBar(..., onMoveToFolder: (() -> Unit)? = null, onRemoveFromFolder: (() -> Unit)? = null, ...)`

These are Compose and Android resource pieces that have no unit-test harness in this project. The logic they call is tested in Task 1. Verification is compiling and running Android lint.

- [ ] **Step 1: Strings (`strings_folders.xml`)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="folders_title">Folders</string>
    <string name="folders_new">New folder</string>
    <string name="folders_create">Create</string>
    <string name="folders_rename_title">Rename folder</string>
    <string name="folders_name_label">Folder name</string>
    <string name="folders_name_blank">Enter a name for the folder</string>
    <string name="folders_name_duplicate">There is already a folder with that name here</string>
    <string name="folders_delete_title">Delete this folder?</string>
    <string name="folders_delete_body_album">The folder is removed and its photos and videos go back to the album. Nothing is deleted from Plex.</string>
    <string name="folders_delete_body_locked">The folder is removed and its photos and videos go back to the Locked tab. Nothing is deleted from Plex.</string>
    <string name="folders_move_to">Move to folder</string>
    <string name="folders_remove_from">Remove from folder</string>
    <string name="folders_empty_title">This folder is empty</string>
    <string name="folders_empty_body">Select photos or videos, then choose Move to folder.</string>
    <string name="folders_load_failed">Could not load this folder</string>
    <plurals name="undo_moved_to_folder">
        <item quantity="one">Moved %1$d item to %2$s</item>
        <item quantity="other">Moved %1$d items to %2$s</item>
    </plurals>
    <plurals name="undo_removed_from_folder">
        <item quantity="one">Removed %d item from the folder</item>
        <item quantity="other">Removed %d items from the folder</item>
    </plurals>
</resources>
```

- [ ] **Step 2: `ui/folders/FolderOps.kt`**

```kotlin
package au.prism.photos.ui.folders

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import au.prism.photos.R
import au.prism.photos.data.AppGraph
import au.prism.photos.data.folders.PrivateFolders
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.Undoable
import java.util.UUID

/** Folder changes as single local writes, with Undo for moves. Nothing here touches Plex. */
object FolderOps {
    /** Creates an empty folder in [home] and returns its id. The caller has checked the name. */
    suspend fun create(graph: AppGraph, home: String, name: String): String {
        val id = UUID.randomUUID().toString()
        graph.local.updatePrivateFolders { PrivateFolders.create(it, home, name, id, System.currentTimeMillis()) }
        return id
    }

    suspend fun rename(graph: AppGraph, folderId: String, name: String) =
        graph.local.updatePrivateFolders { PrivateFolders.rename(it, folderId, name) }

    suspend fun delete(graph: AppGraph, folderId: String) =
        graph.local.updatePrivateFolders { PrivateFolders.delete(it, folderId) }

    private suspend fun move(graph: AppGraph, home: String, itemIds: Collection<String>, folderId: String): Undoable {
        val before = PrivateFolders.membership(graph.local.privateFolders.value, home, itemIds)
        graph.local.updatePrivateFolders { PrivateFolders.move(it, itemIds, folderId) }
        return Undoable(itemIds.size) { graph.local.updatePrivateFolders { PrivateFolders.restore(it, home, before) } }
    }

    private suspend fun remove(graph: AppGraph, home: String, itemIds: Collection<String>): Undoable {
        val before = PrivateFolders.membership(graph.local.privateFolders.value, home, itemIds)
        graph.local.updatePrivateFolders { PrivateFolders.remove(it, home, itemIds) }
        return Undoable(itemIds.size) { graph.local.updatePrivateFolders { PrivateFolders.restore(it, home, before) } }
    }

    suspend fun moveAndOfferUndo(
        graph: AppGraph,
        snackbar: SnackbarHostState,
        resources: Resources,
        home: String,
        itemIds: Collection<String>,
        folderId: String,
    ) {
        val action = move(graph, home, itemIds, folderId)
        val name = graph.local.privateFolders.value.firstOrNull { it.id == folderId }?.name.orEmpty()
        val message = resources.getQuantityString(R.plurals.undo_moved_to_folder, action.count, action.count, name)
        MediaSelectionOps.offerUndo(snackbar, message, resources.getString(R.string.action_undo), action)
    }

    suspend fun removeAndOfferUndo(
        graph: AppGraph,
        snackbar: SnackbarHostState,
        resources: Resources,
        home: String,
        itemIds: Collection<String>,
    ) {
        val action = remove(graph, home, itemIds)
        val message = resources.getQuantityString(R.plurals.undo_removed_from_folder, action.count, action.count)
        MediaSelectionOps.offerUndo(snackbar, message, resources.getString(R.string.action_undo), action)
    }
}
```

- [ ] **Step 3: `ui/folders/FolderDialogs.kt`**

```kotlin
package au.prism.photos.ui.folders

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.data.folders.FolderNameProblem
import au.prism.photos.data.folders.PrivateFolders
import au.prism.photos.domain.LOCKED_TAB_HOME

/**
 * Asks for a folder name in [home]. A clash shows straight away. A blank name shows once Create or
 * Save has been tapped. [onConfirm] gets the trimmed name.
 */
@Composable
fun FolderNameDialog(
    title: String,
    confirmLabel: String,
    home: String,
    initial: String = "",
    excludingId: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val folders by PrismApp.graph.local.privateFolders.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(initial) }
    var attempted by remember { mutableStateOf(false) }
    val problem = PrivateFolders.nameProblem(folders, home, text, excludingId)
    val shown = problem.takeIf { attempted || it == FolderNameProblem.DUPLICATE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folders_name_label)) },
                isError = shown != null,
                supportingText = shown?.let { p ->
                    { Text(stringResource(if (p == FolderNameProblem.BLANK) R.string.folders_name_blank else R.string.folders_name_duplicate)) }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (problem == null) onConfirm(text.trim())
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Confirms deleting a folder and says where its items go. */
@Composable
fun DeleteFolderDialog(home: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folders_delete_title)) },
        text = {
            Text(stringResource(if (home == LOCKED_TAB_HOME) R.string.folders_delete_body_locked else R.string.folders_delete_body_album))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
```

- [ ] **Step 4: `ui/folders/FolderRow.kt`**

```kotlin
package au.prism.photos.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.PrivateFolder
import au.prism.photos.ui.theme.PlexGold
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val CardSize = 120.dp

/**
 * The "Folders" row for one locked place: a card per folder plus a New folder card. Tapping a card
 * opens it, long-pressing offers Rename and Delete. The row runs its own name and delete dialogs.
 */
@Composable
fun FolderRow(
    home: String,
    folders: List<PrivateFolder>,
    coverFor: (PrivateFolder) -> MediaItem?,
    onOpen: (PrivateFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = PrismApp.graph
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PrivateFolder?>(null) }
    var deleting by remember { mutableStateOf<PrivateFolder?>(null) }

    Column(modifier) {
        Text(
            stringResource(R.string.folders_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(folders, key = { it.id }) { folder ->
                FolderCard(
                    folder = folder,
                    cover = coverFor(folder),
                    onOpen = { onOpen(folder) },
                    onRename = { renaming = folder },
                    onDelete = { deleting = folder },
                )
            }
            item(key = "new-folder") { NewFolderCard(onClick = { creating = true }) }
        }
    }

    if (creating) {
        FolderNameDialog(
            title = stringResource(R.string.folders_new),
            confirmLabel = stringResource(R.string.folders_create),
            home = home,
            onConfirm = { name -> creating = false; scope.launch { FolderOps.create(graph, home, name) } },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folders_rename_title),
            confirmLabel = stringResource(R.string.action_save),
            home = home,
            initial = folder.name,
            excludingId = folder.id,
            onConfirm = { name -> renaming = null; scope.launch { FolderOps.rename(graph, folder.id, name) } },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { folder ->
        DeleteFolderDialog(
            home = home,
            onConfirm = { deleting = null; scope.launch { FolderOps.delete(graph, folder.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(folder: PrivateFolder, cover: MediaItem?, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.width(CardSize).combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })) {
            Box(
                Modifier.size(CardSize).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = PrismApp.graph.media.thumbUrl(cover, 300),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = PlexGold, modifier = Modifier.size(40.dp))
                }
            }
            Text(folder.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(
                pluralStringResource(R.plurals.common_item_count, folder.itemIds.size, folder.itemIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    Column(Modifier.width(CardSize).combinedClickable(onClick = onClick)) {
        Box(
            Modifier.size(CardSize).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
        Text(stringResource(R.string.folders_new), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}
```

- [ ] **Step 5: `ui/folders/MoveToFolderSheet.kt`**

```kotlin
package au.prism.photos.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.data.folders.PrivateFolders
import kotlinx.coroutines.launch

/**
 * Lists the folders of [home] to move into, plus New folder (which creates one and moves into it).
 * [currentFolderId] is shown but cannot be picked. [onMove] gets the chosen folder id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(home: String, currentFolderId: String?, onMove: (folderId: String) -> Unit, onDismiss: () -> Unit) {
    val graph = PrismApp.graph
    val scope = rememberCoroutineScope()
    val all by graph.local.privateFolders.collectAsStateWithLifecycle()
    val folders = remember(all, home) { PrivateFolders.inHome(all, home) }
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.folders_move_to),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(folders, key = { it.id }) { folder ->
                val current = folder.id == currentFolderId
                ListItem(
                    headlineContent = { Text(folder.name) },
                    supportingContent = { Text(pluralStringResource(R.plurals.common_item_count, folder.itemIds.size, folder.itemIds.size)) },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.alpha(if (current) 0.4f else 1f).clickable(enabled = !current) { onMove(folder.id) },
                )
            }
            item(key = "new-folder") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.folders_new)) },
                    leadingContent = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable { naming = true },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (naming) {
        FolderNameDialog(
            title = stringResource(R.string.folders_new),
            confirmLabel = stringResource(R.string.folders_create),
            home = home,
            onConfirm = { name ->
                naming = false
                scope.launch { onMove(FolderOps.create(graph, home, name)) }
            },
            onDismiss = { naming = false },
        )
    }
}
```

- [ ] **Step 6: Add folder actions to `SelectionTopBar` (`ui/components/SelectionBar.kt`)**

Add these imports:

```kotlin
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOff
```

Add two parameters after `onRemoveFromAlbum`:

```kotlin
    onMoveToFolder: (() -> Unit)? = null,
    onRemoveFromFolder: (() -> Unit)? = null,
```

Add the buttons directly after the `onRemoveFromAlbum` block:

```kotlin
            if (onMoveToFolder != null) {
                IconButton(onClick = onMoveToFolder) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.folders_move_to)) }
            }
            if (onRemoveFromFolder != null) {
                IconButton(onClick = onRemoveFromFolder) { Icon(Icons.Filled.FolderOff, contentDescription = stringResource(R.string.folders_remove_from)) }
            }
```

- [ ] **Step 7: Compile and lint**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:lintDebug "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`, with no new lint errors. Unused-resource warnings for strings that later tasks use are acceptable at this point.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/res/values/strings_folders.xml app/src/main/java/au/prism/photos/ui/folders app/src/main/java/au/prism/photos/ui/components/SelectionBar.kt
git commit -m @'
Private folders: shared row, dialogs, move sheet and selection actions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 4: Viewer source, routes and folder-aware viewer lists

**Files:**
- Modify: `app/src/main/java/au/prism/photos/domain/Models.kt` (`ViewerSource`)
- Modify: `app/src/main/java/au/prism/photos/ui/nav/Routes.kt`
- Test: `app/src/test/java/au/prism/photos/ui/nav/RoutesTest.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/viewer/ViewerViewModel.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt` (viewer gate only)

**Interfaces:**
- Consumes: `LocalStore.privateFolders` (Task 2); `PrivateFolders.filedIds`, `LOCKED_TAB_HOME` (Task 1).
- Produces: `ViewerSource.Folder(folderId: String)`; `Routes.FOLDER = "folder/{folderId}"`; `Routes.folder(folderId: String): String`; `ViewerViewModel.folderHome: String?` (public, used in Task 8).

- [ ] **Step 1: Write the failing route tests** (append inside `RoutesTest`, and add the import `au.prism.photos.domain.ViewerSource`)

```kotlin
    @Test
    fun `folder viewer source round trips`() {
        val source = ViewerSource.Folder("3f2b8c1e-5d6a-4b7c-9e8f-0a1b2c3d4e5f")
        assertEquals(source, SourceCodec.decode(SourceCodec.encode(source)))
    }

    @Test
    fun `folder route keeps the id in one path segment`() {
        val route = Routes.folder("a/b c")
        val arg = route.removePrefix("folder/")
        assertFalse(arg.contains('/'))
        assertEquals("a/b c", Routes.decodeArg(arg))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "au.prism.photos.ui.nav.RoutesTest" "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: compilation fails with `Unresolved reference 'Folder'`.

- [ ] **Step 3: Add the source and routes**

In `ViewerSource` (`domain/Models.kt`), after `MyAlbum`, add:

```kotlin
    /** A private folder inside a locked place (always behind the lock). */
    data class Folder(val folderId: String) : ViewerSource()
```

In `Routes`, add `const val FOLDER = "folder/{folderId}"` after `MY_ALBUM`. After `fun myAlbum`, add:

```kotlin
    /** A private folder; always opened behind the LockGate. */
    fun folder(folderId: String) = "folder/${encodeArg(folderId)}"
```

In `SourceCodec.encode`, add the branch `is au.prism.photos.domain.ViewerSource.Folder -> "folder:${source.folderId}"`. In `decode`, before the `else` branch, add `s.startsWith("folder:") -> au.prism.photos.domain.ViewerSource.Folder(s.removePrefix("folder:"))`. Also update the format comment above `SourceCodec` to include `folder:ID`.

- [ ] **Step 4: Run the route tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "au.prism.photos.ui.nav.RoutesTest" "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: all `RoutesTest` tests pass. Kotlin will not compile `ViewerViewModel` until Step 5 adds its `when` branch, so run Step 5 first if the build stops there.

- [ ] **Step 5: Make `ViewerViewModel` folder-aware**

Add these imports:

```kotlin
import au.prism.photos.data.folders.PrivateFolders
import au.prism.photos.domain.LOCKED_TAB_HOME
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
```

Replace the `items` declaration (lines 38-49) with the following. `folderHome` must be declared before `items`.

```kotlin
    /**
     * The locked place whose folders apply here: the Locked tab, a locked album opened behind the
     * lock, or the home of the folder being viewed. Null everywhere else.
     */
    val folderHome: String? = when (source) {
        ViewerSource.Locked -> LOCKED_TAB_HOME
        is ViewerSource.Album -> if (showLocked) source.albumId else null
        is ViewerSource.Folder -> graph.local.privateFolders.value.firstOrNull { it.id == source.folderId }?.home
        else -> null
    }

    /**
     * The pager's items. Outside the lock (anything but the Locked view or a locked album), items
     * locked while the viewer is open, from here or elsewhere, drop out straight away. In a locked
     * album or the Locked tab, items filed into a folder show only inside that folder.
     */
    val items: StateFlow<List<MediaItem>> =
        combine(_items, graph.local.lockedItemIds, graph.local.privateFolders) { list, locked, folders ->
            var out = list
            if (!showLocked && source != ViewerSource.Locked && locked.isNotEmpty()) out = out.filterNot { it.id in locked }
            val home = folderHome
            if (home != null && source !is ViewerSource.Folder) {
                val filed = PrivateFolders.filedIds(folders, home)
                if (filed.isNotEmpty()) out = out.filterNot { it.id in filed }
            }
            out
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

Add these fields after `private var settleJob: Job? = null`:

```kotlin
    /** Items already resolved for a [ViewerSource.Folder], by id, so a change to the folder does not refetch them. */
    private val folderCache = mutableMapOf<String, MediaItem>()
    private var folderJob: Job? = null
```

In `init`, add the branch `is ViewerSource.Folder -> followFolder(source.folderId)` before `else`.

Add this function after `follow(...)`:

```kotlin
    /** Follows one private folder: its items in folder order resolved by id, newest first. */
    private fun followFolder(folderId: String) {
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            graph.local.privateFolders
                .map { folders -> folders.firstOrNull { it.id == folderId }?.itemIds }
                .distinctUntilChanged()
                .collect { ids ->
                    if (ids == null) {
                        _items.value = emptyList()
                        _loading.value = false
                        return@collect
                    }
                    val missing = ids.filter { it !in folderCache }
                    if (missing.isNotEmpty()) graph.media.items(missing).forEach { folderCache[it.id] = it }
                    val resolved = ids.mapNotNull { folderCache[it] }.sortedByDescending { it.takenAt }
                    _loadError.value = if (resolved.isEmpty() && ids.isNotEmpty()) PrismApp.instance.getString(R.string.viewer_load_failed) else null
                    _items.value = resolved
                    _loading.value = false
                }
        }
    }
```

In `retry()`, add the branch `is ViewerSource.Folder -> followFolder(source.folderId)` before `else`. In `load()`, change the first branch to `ViewerSource.Timeline, ViewerSource.Locked, is ViewerSource.Folder -> emptyList()`.

Replace `updateItem` with:

```kotlin
    private fun updateItem(id: String, transform: (MediaItem) -> MediaItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
        folderCache[id]?.let { folderCache[id] = transform(it) }
    }
```

- [ ] **Step 6: Gate the folder viewer in `PrismNavHost.kt`**

In the `Routes.VIEWER` destination's `gated` expression, add `source is ViewerSource.Folder ||` before `source == ViewerSource.Locked ||`.

- [ ] **Step 7: Compile and run all unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`. If another exhaustive `when` over `ViewerSource` fails to compile, add a `ViewerSource.Folder` branch that matches what that `when` does for `ViewerSource.Album`, then re-run.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/au/prism/photos/domain/Models.kt app/src/main/java/au/prism/photos/ui/nav/Routes.kt app/src/test/java/au/prism/photos/ui/nav/RoutesTest.kt app/src/main/java/au/prism/photos/ui/viewer/ViewerViewModel.kt app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt
git commit -m @'
Private folders: folder viewer source and route, filed items leave the album and Locked viewers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 5: Folder screen

**Files:**
- Create: `app/src/main/java/au/prism/photos/ui/folders/PrivateFolderViewModel.kt`
- Create: `app/src/main/java/au/prism/photos/ui/folders/PrivateFolderScreen.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt` (new destination)

**Interfaces:**
- Consumes: `FolderOps`, `FolderNameDialog`, `DeleteFolderDialog`, `MoveToFolderSheet`, `SelectionTopBar(onMoveToFolder, onRemoveFromFolder)` (Task 3); `Routes.FOLDER`, `Routes.folder`, `ViewerSource.Folder` (Task 4).
- Produces: `@Composable PrivateFolderScreen(folderId: String, onBack: () -> Unit, onOpenViewer: (index: Int) -> Unit)`.

- [ ] **Step 1: `PrivateFolderViewModel.kt`**

```kotlin
package au.prism.photos.ui.folders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.PrivateFolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One private folder: follows the stored folder and resolves its item ids, newest first. */
class PrivateFolderViewModel(private val graph: AppGraph, private val folderId: String) : ViewModel() {
    var folder by mutableStateOf<PrivateFolder?>(graph.local.privateFolders.value.firstOrNull { it.id == folderId })
        private set
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    /** True when the folder has ids but none of them could be loaded. */
    var failed by mutableStateOf(false)
        private set

    private val cache = mutableMapOf<String, MediaItem>()
    private var job: Job? = null

    init { load() }

    /** Starts following the folder again, refetching every item (after Retry or a favourite change). */
    fun reload() {
        cache.clear()
        load()
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            graph.local.privateFolders
                .map { list -> list.firstOrNull { it.id == folderId } }
                .distinctUntilChanged()
                .collect { f ->
                    folder = f
                    if (f == null) {
                        items = emptyList()
                        loading = false
                        return@collect
                    }
                    val missing = f.itemIds.filter { it !in cache }
                    if (missing.isNotEmpty()) {
                        if (items.isEmpty()) loading = true
                        graph.media.items(missing).forEach { cache[it.id] = it }
                    }
                    items = f.itemIds.mapNotNull { cache[it] }.sortedByDescending { it.takenAt }
                    failed = items.isEmpty() && f.itemIds.isNotEmpty()
                    loading = false
                }
        }
    }
}
```

- [ ] **Step 2: `PrivateFolderScreen.kt`**

```kotlin
package au.prism.photos.ui.folders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.ConfirmDeleteDialog
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectableMediaGrid
import au.prism.photos.ui.components.SelectionBackHandler
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.components.downloadResultMessage
import au.prism.photos.ui.components.rememberSelectionActions
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.launch

/** One private folder's items. Always shown behind the LockGate (see PrismNavHost). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateFolderScreen(folderId: String, onBack: () -> Unit, onOpenViewer: (index: Int) -> Unit) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val vm: PrivateFolderViewModel = viewModel(factory = viewModelFactory { initializer { PrivateFolderViewModel(graph, folderId) } })
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectionActions = rememberSelectionActions(selection, snackbarHostState, onChanged = { vm.reload() })
    SelectionBackHandler(selection)
    val gridState = rememberLazyGridState()

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<List<String>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    // The folder was deleted (here or on another screen): leave, once.
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(vm.folder, vm.loading) {
        if (!vm.loading && vm.folder == null && !left) {
            left = true
            onBack()
        }
    }
    val folder = vm.folder ?: return
    val home = folder.home

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = vm.items.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = selectedItems.isNotEmpty() && selectedItems.all { it.favourite },
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(vm.items.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(downloadResultMessage(context.resources, result))
                        }
                    },
                    onToggleFavourite = { selectionActions.toggleFavourite(selectedItems) },
                    onMoveToFolder = { moving = selection.selected.toList() },
                    onRemoveFromFolder = {
                        val ids = selection.selected.toList()
                        selection.clear()
                        scope.launch { FolderOps.removeAndOfferUndo(graph, snackbarHostState, context.resources, home, ids) }
                    },
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text(folder.name) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.albums_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; deleting = true })
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loading && vm.items.isEmpty() -> LoadingState()
                vm.failed -> ErrorState(stringResource(R.string.folders_load_failed), onRetry = { vm.reload() })
                vm.items.isEmpty() -> EmptyState(stringResource(R.string.folders_empty_title), stringResource(R.string.folders_empty_body))
                else -> SelectableMediaGrid(
                    items = vm.items,
                    columns = 3,
                    thumbUrl = { graph.media.thumbUrl(it, 400) },
                    selection = selection,
                    onOpenViewer = onOpenViewer,
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (renaming) {
        FolderNameDialog(
            title = stringResource(R.string.folders_rename_title),
            confirmLabel = stringResource(R.string.action_save),
            home = home,
            initial = folder.name,
            excludingId = folder.id,
            onConfirm = { name -> renaming = false; scope.launch { FolderOps.rename(graph, folder.id, name) } },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        DeleteFolderDialog(
            home = home,
            onConfirm = { deleting = false; scope.launch { FolderOps.delete(graph, folder.id) } },
            onDismiss = { deleting = false },
        )
    }
    moving?.let { ids ->
        MoveToFolderSheet(
            home = home,
            currentFolderId = folder.id,
            onMove = { target ->
                moving = null
                selection.clear()
                scope.launch { FolderOps.moveAndOfferUndo(graph, snackbarHostState, context.resources, home, ids, target) }
            },
            onDismiss = { moving = null },
        )
    }
    if (showDeleteConfirm) {
        val selectedItems = vm.items.filter { it.id in selection.selected }
        ConfirmDeleteDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val errors = MediaSelectionOps.delete(graph, selectedItems)
                    selection.clear()
                    if (errors.isNotEmpty()) snackbarHostState.showSnackbar(errors.first())
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }
}
```

`SelectableMediaGrid`, `EmptyState`, `ErrorState`, `LoadingState`, `ConfirmDeleteDialog`, `ShareSheet` and `MediaActions.download` are called exactly as `FavouritesScreen.kt` calls them. Successful Plex deletes leave the folder through Task 2's repository hook, so the list updates itself.

- [ ] **Step 3: Add the destination in `PrismNavHost.kt`**

Add the import `au.prism.photos.ui.folders.PrivateFolderScreen`. After the `Routes.MY_ALBUM` composable, add:

```kotlin
            composable(
                Routes.FOLDER,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) { entry ->
                val folderId = Routes.decodeArg(entry.arguments?.getString("folderId").orEmpty())
                Gated(true) {
                    PrivateFolderScreen(
                        folderId = folderId,
                        onBack = { navController.popBackStack() },
                        onOpenViewer = { index ->
                            navController.navigate(Routes.viewer(SourceCodec.encode(ViewerSource.Folder(folderId)), index, locked = true))
                        },
                    )
                }
            }
```

- [ ] **Step 4: Compile and run all unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/au/prism/photos/ui/folders app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt
git commit -m @'
Private folders: folder screen behind the lock

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 6: Folders in locked albums

**Files:**
- Modify: `app/src/main/java/au/prism/photos/ui/albums/AlbumDetailViewModel.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/albums/AlbumDetailScreen.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt` (`Routes.ALBUM` destination)

**Interfaces:**
- Consumes: `FolderRow`, `MoveToFolderSheet`, `FolderOps.moveAndOfferUndo`, `SelectionTopBar(onMoveToFolder)` (Task 3); `Routes.folder` (Task 4); `PrivateFolders.inHome/filedIds/prune` (Task 1).
- Produces: `AlbumDetailViewModel.folders: List<PrivateFolder>`, `AlbumDetailViewModel.coverFor(folder: PrivateFolder): MediaItem?`; `AlbumDetailScreen(..., onOpenFolder: (folderId: String) -> Unit = {})`.

- [ ] **Step 1: `AlbumDetailViewModel.kt`**

Add these imports: `au.prism.photos.data.folders.PrivateFolders`, `au.prism.photos.domain.PrivateFolder`.

Add this field after `lockedAlbumIds`:

```kotlin
    private var folderList by mutableStateOf(if (showLocked) graph.local.privateFolders.value else emptyList())
```

Replace the `items` property and add `folders` and `coverFor`:

```kotlin
    /** This album's private folders. Only behind the lock. */
    val folders: List<PrivateFolder> by derivedStateOf {
        if (showLocked) PrivateFolders.inHome(folderList, albumId) else emptyList()
    }
    /** Behind the lock, items filed in a folder show only inside it, so the grid holds loose items. */
    val items: List<MediaItem> by derivedStateOf {
        if (showLocked) {
            val filed = PrivateFolders.filedIds(folderList, albumId)
            allItems.filterNot { it.id in filed }
        } else {
            allItems.filter { it.id !in lockedItemIds }
        }
    }

    /** The newest item of [folder], for its card. */
    fun coverFor(folder: PrivateFolder): MediaItem? {
        val ids = folder.itemIds.toSet()
        return allItems.filter { it.id in ids }.maxByOrNull { it.takenAt }
    }
```

In `init`, before `load()`, add:

```kotlin
        if (showLocked) viewModelScope.launch { graph.local.privateFolders.collect { folderList = it } }
```

In `load()`, replace the `.onSuccess { ... }` lambda with:

```kotlin
                .onSuccess {
                    allSubAlbums = it.albums
                    allItems = it.items
                    loading = false
                    if (showLocked) {
                        // Album contents leave out individually locked items, so keep those ids as well.
                        val existing = it.items.mapTo(HashSet()) { item -> item.id } + graph.local.lockedItemIds.value
                        graph.local.updatePrivateFolders { folders -> PrivateFolders.prune(folders, albumId, existing) }
                    }
                }
```

- [ ] **Step 2: `AlbumDetailScreen.kt`**

Add these imports: `au.prism.photos.ui.folders.FolderOps`, `au.prism.photos.ui.folders.FolderRow`, `au.prism.photos.ui.folders.MoveToFolderSheet`.

Add the parameter `onOpenFolder: (folderId: String) -> Unit = {},` after `showLocked`. Add the state `var moving by remember { mutableStateOf<List<String>?>(null) }` next to the other dialog state.

In `SelectionTopBar(...)`, add `onMoveToFolder = if (showLocked) { { moving = selection.selected.toList() } } else null,` after `onAddToAlbum`.

Change the empty-state condition to:

```kotlin
                !showLocked && vm.subAlbums.isEmpty() && sortedItems.isEmpty() -> EmptyState(stringResource(R.string.albums_detail_empty))
```

Inside the `LazyVerticalGrid`, directly after the `if (vm.subAlbums.isNotEmpty()) { ... }` block, add:

```kotlin
                    if (showLocked) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            FolderRow(
                                home = albumId,
                                folders = vm.folders,
                                coverFor = vm::coverFor,
                                onOpen = { onOpenFolder(it.id) },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
```

After the `showAddToAlbum` block at the end of the composable, add:

```kotlin
    moving?.let { ids ->
        MoveToFolderSheet(
            home = albumId,
            currentFolderId = null,
            onMove = { target ->
                moving = null
                selection.clear()
                scope.launch { FolderOps.moveAndOfferUndo(graph, snackbarHostState, context.resources, albumId, ids, target) }
            },
            onDismiss = { moving = null },
        )
    }
```

- [ ] **Step 3: Pass `onOpenFolder` in `PrismNavHost.kt`**

In the `Routes.ALBUM` destination's `AlbumDetailScreen(...)` call, add:

```kotlin
                        onOpenFolder = { id -> navController.navigate(Routes.folder(id)) },
```

- [ ] **Step 4: Compile and run all unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/au/prism/photos/ui/albums/AlbumDetailViewModel.kt app/src/main/java/au/prism/photos/ui/albums/AlbumDetailScreen.kt app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt
git commit -m @'
Private folders: folders row and Move to folder in locked albums

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 7: Folders on the Locked tab

**Files:**
- Modify: `app/src/main/java/au/prism/photos/ui/locked/LockedScreen.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt` (`Routes.HOME` destination)

**Interfaces:**
- Consumes: `FolderRow`, `MoveToFolderSheet`, `FolderOps.moveAndOfferUndo` (Task 3); `Routes.folder` (Task 4); `PrivateFolders.inHome/filedIds`, `LOCKED_TAB_HOME` (Task 1).
- Produces: `LockedScreen(..., onOpenFolder: (folderId: String) -> Unit)`; `HomeScreen(..., onOpenFolder: (folderId: String) -> Unit)`.

- [ ] **Step 1: `LockedScreen.kt`**

Add these imports:

```kotlin
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import au.prism.photos.data.folders.PrivateFolders
import au.prism.photos.domain.LOCKED_TAB_HOME
import au.prism.photos.ui.folders.FolderOps
import au.prism.photos.ui.folders.FolderRow
import au.prism.photos.ui.folders.MoveToFolderSheet
```

Add the parameter `onOpenFolder: (folderId: String) -> Unit,` after `onOpenAlbum`.

After `val lockedAlbumIds by ...`, add:

```kotlin
        val allFolders by graph.local.privateFolders.collectAsStateWithLifecycle()
        val folders = remember(allFolders) { PrivateFolders.inHome(allFolders, LOCKED_TAB_HOME) }
        // Items filed in a Locked tab folder show only inside it.
        val looseItems = remember(items, allFolders) {
            val filed = PrivateFolders.filedIds(allFolders, LOCKED_TAB_HOME)
            items.filterNot { it.id in filed }
        }
        var moving by remember { mutableStateOf<List<String>?>(null) }
```

In the rest of the composable, replace `items` with `looseItems` everywhere a selection or the grid uses it. That covers the `selectedItems` in the top bar, `selection.selectAll(...)`, `SelectableMediaGrid(items = ...)` and the `selectedItems` in the delete dialog. Keep `items` only in the empty check below.

In the selection `TopAppBar` actions, after the Unlock button, add:

```kotlin
                            IconButton(onClick = { moving = selection.selected.toList() }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.folders_move_to))
                            }
```

Replace the body's `if (items.isEmpty() && lockedAlbums.isEmpty()) { ... } else { Column { ... } }` block with:

```kotlin
                if (items.isEmpty() && lockedAlbums.isEmpty() && folders.isEmpty()) {
                    EmptyState(stringResource(R.string.locked_empty_title), stringResource(R.string.locked_empty_body))
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (lockedAlbums.isNotEmpty()) {
                            Text(stringResource(R.string.locked_albums), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(lockedAlbums, key = { it.id }) { album ->
                                    LockedAlbumChip(album, onClick = { onOpenAlbum(album.id) }, onUnlock = { scope.launch { graph.local.setAlbumLocked(album.id, false) } })
                                }
                            }
                        }
                        if (items.isNotEmpty() || folders.isNotEmpty()) {
                            val byId = items.associateBy { it.id }
                            FolderRow(
                                home = LOCKED_TAB_HOME,
                                folders = folders,
                                coverFor = { folder -> folder.itemIds.mapNotNull { byId[it] }.maxByOrNull { it.takenAt } },
                                onOpen = { onOpenFolder(it.id) },
                            )
                        }
                        if (looseItems.isNotEmpty()) {
                            Text(stringResource(R.string.locked_items), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            SelectableMediaGrid(
                                items = looseItems,
                                columns = 3,
                                thumbUrl = { graph.media.thumbUrl(it, 400) },
                                selection = selection,
                                onOpenViewer = onOpenViewer,
                                gridState = gridState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
```

Before the closing brace of `LockGate { ... }`, after the `showAddToAlbum` block, add:

```kotlin
        moving?.let { ids ->
            MoveToFolderSheet(
                home = LOCKED_TAB_HOME,
                currentFolderId = null,
                onMove = { target ->
                    moving = null
                    selection.clear()
                    scope.launch { FolderOps.moveAndOfferUndo(graph, snackbarHostState, context.resources, LOCKED_TAB_HOME, ids, target) }
                },
                onDismiss = { moving = null },
            )
        }
```

- [ ] **Step 2: `HomeScreen.kt`**

Add the parameter `onOpenFolder: (folderId: String) -> Unit,` to `HomeScreen` after `onOpenMyAlbum`. Change the Locked tab line to:

```kotlin
                HomeTab.LOCKED -> LockedScreen(gridState = lockedState, onOpenViewer = { index -> onOpenViewer(ViewerSource.Locked, index) }, onOpenAlbum = onOpenAlbum, onOpenFolder = onOpenFolder)
```

- [ ] **Step 3: `PrismNavHost.kt`**

In the `Routes.HOME` destination's `HomeScreen(...)` call, add:

```kotlin
                    onOpenFolder = { folderId -> navController.navigate(Routes.folder(folderId)) },
```

- [ ] **Step 4: Compile and run all unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/au/prism/photos/ui/locked/LockedScreen.kt app/src/main/java/au/prism/photos/ui/home/HomeScreen.kt app/src/main/java/au/prism/photos/ui/nav/PrismNavHost.kt
git commit -m @'
Private folders: folders row and Move to folder on the Locked tab

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 8: Move to folder in the viewer

**Files:**
- Modify: `app/src/main/java/au/prism/photos/ui/viewer/ViewerBars.kt` (`ViewerTopBar`)
- Modify: `app/src/main/java/au/prism/photos/ui/viewer/ViewerScreen.kt`

**Interfaces:**
- Consumes: `ViewerViewModel.folderHome` (Task 4); `MoveToFolderSheet`, `FolderOps.moveAndOfferUndo` (Task 3).
- Produces: `ViewerTopBar(..., onMoveToFolder: (() -> Unit)?, ...)`.

- [ ] **Step 1: `ViewerBars.kt`**

Add the import `androidx.compose.material.icons.automirrored.filled.DriveFileMove`. Add the parameter `onMoveToFolder: (() -> Unit)?,` after `onAddToAlbum`. After the Add to album `DropdownMenuItem`, add:

```kotlin
                if (onMoveToFolder != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folders_move_to)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                        onClick = { menuOpen = false; onMoveToFolder() },
                    )
                }
```

- [ ] **Step 2: `ViewerScreen.kt`**

Add these imports: `au.prism.photos.domain.ViewerSource` (if it isn't already imported), `au.prism.photos.ui.folders.FolderOps`, `au.prism.photos.ui.folders.MoveToFolderSheet`.

Next to `addToAlbumItem`, add `var moveItem by remember { mutableStateOf<MediaItem?>(null) }`, and add `|| moveItem != null` to `dialogOpen`.

In the `ViewerTopBar(...)` call, after `onAddToAlbum = ...`, add:

```kotlin
                    onMoveToFolder = if (vm.folderHome != null && !currentItem.isLocal) { { moveItem = currentItem } } else null,
```

Next to where the add-to-album sheet for `addToAlbumItem` is shown, add:

```kotlin
    moveItem?.let { item ->
        val home = vm.folderHome
        if (home != null) {
            MoveToFolderSheet(
                home = home,
                currentFolderId = (source as? ViewerSource.Folder)?.folderId,
                onMove = { target ->
                    moveItem = null
                    scope.launch { FolderOps.moveAndOfferUndo(PrismApp.graph, snackbarHostState, context.resources, home, listOf(item.id), target) }
                },
                onDismiss = { moveItem = null },
            )
        }
    }
```

In a locked album or the Locked tab, the moved item leaves the pager straight away, just as a lock does. In a folder, it leaves that folder's pager.

- [ ] **Step 3: Compile, run all unit tests and lint**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`, with no lint errors (CI fails on lint errors).

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/au/prism/photos/ui/viewer/ViewerBars.kt app/src/main/java/au/prism/photos/ui/viewer/ViewerScreen.kt
git commit -m @'
Private folders: Move to folder from the viewer in locked places

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 9: Debug build for the user to try

- [ ] **Step 1: Full verification**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug "-Dorg.gradle.jvmargs=-Xmx2g"`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists with a new timestamp.

- [ ] **Step 2: Hand over the debug APK**

Give the user the path to `app-debug.apk` and this checklist to try on the phone:
1. Open a locked album and create a folder.
2. Select some photos, choose Move to folder and pick the folder. They leave the album grid, and Undo brings them back.
3. Open the folder and swipe through in the viewer. Only the folder's items appear.
4. Use Move to folder from the viewer menu in the album.
5. Do the same on the Locked tab.
6. Unlock an item that's in a Locked tab folder. It leaves the folder.
7. Rename and delete a folder. Its photos go back and nothing is deleted from Plex.
8. Lock the phone app (background it past the timeout), reopen a folder from recents, and confirm the passcode is asked for.

Do not merge to `main` or release until the user has tried it. Merging and releasing (the usual signed 1.2.6 build) are separate requests.
