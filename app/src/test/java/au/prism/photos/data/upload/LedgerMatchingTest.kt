package au.prism.photos.data.upload

import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMatchingTest {

    private fun deviceItem(
        id: String = "local:1",
        title: String = "IMG_0001.jpg",
        size: Long = 1000L,
        localUri: String? = "content://media/external/images/media/1",
    ) = MediaItem(id = id, kind = MediaKind.PHOTO, title = title, takenAt = 1_700_000_000_000L, fileSize = size, localUri = localUri)

    private fun ledgerEntry(
        contentUri: String = "content://media/external/images/media/1",
        displayName: String = "IMG_0001.jpg",
        size: Long = 1000L,
        remotePath: String = "Uploads/Pixel/2026/03/IMG_0001.jpg",
        plexRatingKey: String? = null,
    ) = LedgerEntry(contentUri = contentUri, displayName = displayName, size = size, takenAt = 1_700_000_000_000L, remotePath = remotePath, plexRatingKey = plexRatingKey)

    @Test
    fun `isUploaded matches by content uri`() {
        val entries = listOf(ledgerEntry())
        assertTrue(LedgerMatching.isUploaded(entries, deviceItem()))
    }

    @Test
    fun `isUploaded is false for a different item`() {
        val entries = listOf(ledgerEntry())
        val other = deviceItem(id = "local:2", title = "IMG_0002.jpg", size = 2000L, localUri = "content://media/external/images/media/2")
        assertFalse(LedgerMatching.isUploaded(entries, other))
    }

    @Test
    fun `isUploaded falls back to name and size when the uri differs`() {
        // Same file re-resolved through a different content:// URI (e.g. picked via GET_CONTENT
        // instead of the MediaStore row), still matched by name + size.
        val entries = listOf(ledgerEntry())
        val item = deviceItem(localUri = "content://com.android.providers.media.documents/document/image%3A1")
        assertTrue(LedgerMatching.isUploaded(entries, item))
    }

    @Test
    fun `isUploaded does not match same name with a different size`() {
        val entries = listOf(ledgerEntry(size = 1000L))
        val item = deviceItem(localUri = "content://other/1", size = 2000L)
        assertFalse(LedgerMatching.isUploaded(entries, item))
    }

    @Test
    fun `entryFor returns the matching entry`() {
        val entry = ledgerEntry()
        val found = LedgerMatching.entryFor(listOf(entry), deviceItem())
        assertEquals(entry, found)
    }

    @Test
    fun `entryFor returns null when nothing matches`() {
        val found = LedgerMatching.entryFor(listOf(ledgerEntry()), deviceItem(id = "local:9", localUri = "content://media/external/images/media/9", title = "other.jpg", size = 5L))
        assertNull(found)
    }

    @Test
    fun `matchPlexItem matches by file name and size`() {
        val entry = ledgerEntry(remotePath = "Uploads/Pixel/2026/03/IMG_0001.jpg", size = 1000L)
        val plexItem = MediaItem(
            id = "42",
            kind = MediaKind.PHOTO,
            title = "IMG_0001",
            takenAt = 1_700_000_000_000L,
            filePath = "/share/PLEX Library/Photos/Uploads/Pixel/2026/03/IMG_0001.jpg",
            fileSize = 1000L,
        )
        val match = LedgerMatching.matchPlexItem(entry, listOf(plexItem))
        assertEquals("42", match?.id)
    }

    @Test
    fun `matchPlexItem ignores a same name file with a different size`() {
        val entry = ledgerEntry(remotePath = "Uploads/IMG_0001.jpg", size = 1000L)
        val plexItem = MediaItem(
            id = "42",
            kind = MediaKind.PHOTO,
            title = "IMG_0001",
            takenAt = 1_700_000_000_000L,
            filePath = "/share/Photos/IMG_0001.jpg",
            fileSize = 999L,
        )
        assertNull(LedgerMatching.matchPlexItem(entry, listOf(plexItem)))
    }

    @Test
    fun `matchPlexItem returns null when no file path is present`() {
        val entry = ledgerEntry()
        val plexItem = MediaItem(id = "42", kind = MediaKind.PHOTO, title = "IMG_0001", takenAt = 1_700_000_000_000L)
        assertNull(LedgerMatching.matchPlexItem(entry, listOf(plexItem)))
    }
}
