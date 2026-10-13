package au.prism.photos.data.upload

import au.prism.photos.domain.MediaItem

/**
 * Hash indexes over the ledger so a sync of thousands of device items is O(N + M) rather than
 * comparing every item with every entry. Immutable; [SyncLedger] builds a new one on each change.
 */
class LedgerIndex(val entries: List<LedgerEntry>) {
    private val byUri: Map<String, LedgerEntry> = entries.associateBy { it.contentUri }

    /**
     * (file name, size) to entry. Both the name the file was uploaded under (possibly renamed to
     * IMG_0001_1.jpg on a clash) and the device's original name are indexed, so the name+size
     * fallback still matches after a rename.
     */
    private val byNameSize: Map<Pair<String, Long>, LedgerEntry> = HashMap<Pair<String, Long>, LedgerEntry>().also { map ->
        for (entry in entries) {
            if (entry.size <= 0) continue
            map[entry.displayName to entry.size] = entry
            entry.originalName?.takeIf { it.isNotBlank() }?.let { map[it to entry.size] = entry }
        }
    }

    fun entryFor(contentUri: String?, displayName: String, size: Long): LedgerEntry? {
        if (contentUri != null) byUri[contentUri]?.let { return it }
        if (size <= 0) return null
        return byNameSize[displayName to size]
    }

    fun isUploaded(contentUri: String?, displayName: String, size: Long): Boolean = entryFor(contentUri, displayName, size) != null

    fun entryFor(item: MediaItem): LedgerEntry? = entryFor(item.localUri, item.title, item.fileSize)

    fun isUploaded(item: MediaItem): Boolean = entryFor(item) != null

    companion object {
        val EMPTY = LedgerIndex(emptyList())
    }
}

/**
 * Pure matching logic for [SyncLedger], kept free of Android/DataStore dependencies so it can be
 * unit tested on the plain JVM (see app/src/test/java/au/prism/photos/data/upload).
 */
object LedgerMatching {
    /** True if [item] (matched by content URI, or by name+size as a fallback) is already synced. */
    fun isUploaded(entries: List<LedgerEntry>, item: MediaItem): Boolean = LedgerIndex(entries).isUploaded(item)

    fun entryFor(entries: List<LedgerEntry>, item: MediaItem): LedgerEntry? = LedgerIndex(entries).entryFor(item)

    /**
     * Device items from the selected albums that still need uploading: one per content URI, items
     * without a URI dropped, and anything the ledger already has removed. There is deliberately no
     * date cutoff: an item that failed last time, or whose EXIF date is older than the last run,
     * must still be picked up, and the ledger alone prevents duplicates.
     */
    fun selectSyncCandidates(deviceItems: List<MediaItem>, index: LedgerIndex): List<MediaItem> =
        deviceItems
            .filter { it.localUri != null }
            .distinctBy { it.localUri }
            .filterNot { index.isUploaded(it) }

    /** Timeline items keyed by the file name on disk, for [matchPlexItem]. */
    fun indexTimeline(timeline: List<MediaItem>): Map<String, List<MediaItem>> =
        timeline.filter { it.filePath != null }.groupBy { it.filePath!!.substringAfterLast('/') }

    /**
     * The Plex timeline item [entry] became, matched by comparing the uploaded file's name and
     * size against [MediaItem.filePath]/[MediaItem.fileSize]. See docs/plex-api.md.
     */
    fun matchPlexItem(entry: LedgerEntry, timeline: List<MediaItem>): MediaItem? = matchPlexItem(entry, indexTimeline(timeline))

    fun matchPlexItem(entry: LedgerEntry, timelineByName: Map<String, List<MediaItem>>): MediaItem? =
        timelineByName[entry.remotePath.substringAfterLast('/')]?.firstOrNull { plexItem ->
            entry.size <= 0 || plexItem.fileSize == entry.size
        }
}
