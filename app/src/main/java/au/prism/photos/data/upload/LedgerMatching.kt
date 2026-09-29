package au.prism.photos.data.upload

import au.prism.photos.domain.MediaItem

/**
 * Pure matching logic for [SyncLedger], kept free of Android/DataStore dependencies so it can be
 * unit tested on the plain JVM (see app/src/test/java/au/prism/photos/data/upload).
 */
object LedgerMatching {
    /** True if [item] (matched by content URI, or by name+size as a fallback) is already synced. */
    fun isUploaded(entries: List<LedgerEntry>, item: MediaItem): Boolean {
        val uri = item.localUri
        if (uri != null && entries.any { it.contentUri == uri }) return true
        return entries.any { it.displayName == item.title && it.size > 0 && it.size == item.fileSize }
    }

    fun entryFor(entries: List<LedgerEntry>, item: MediaItem): LedgerEntry? {
        val uri = item.localUri
        return entries.firstOrNull { uri != null && it.contentUri == uri }
            ?: entries.firstOrNull { it.displayName == item.title && it.size > 0 && it.size == item.fileSize }
    }

    /**
     * The Plex timeline item [entry] became, matched by comparing the uploaded file's name and
     * size against [MediaItem.filePath]/[MediaItem.fileSize]. See docs/plex-api.md.
     */
    fun matchPlexItem(entry: LedgerEntry, timeline: List<MediaItem>): MediaItem? = timeline.firstOrNull { plexItem ->
        val name = plexItem.filePath?.substringAfterLast('/')
        name != null && name == entry.remotePath.substringAfterLast('/') &&
            (entry.size <= 0 || plexItem.fileSize == entry.size)
    }
}
