package au.prism.photos.data.upload

import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCandidatesTest {

    private fun deviceItem(n: Int, takenAt: Long = 1_700_000_000_000L, title: String = "IMG_$n.jpg", size: Long = 1000L + n, uri: String? = "content://media/external/images/media/$n") =
        MediaItem(id = "local:$n", kind = MediaKind.PHOTO, title = title, takenAt = takenAt, fileSize = size, localUri = uri)

    private fun entry(uri: String, uploadedName: String, size: Long, originalName: String? = null) =
        LedgerEntry(contentUri = uri, displayName = uploadedName, size = size, takenAt = 0, remotePath = "Uploads/$uploadedName", originalName = originalName)

    @Test
    fun `items with old EXIF dates are still candidates`() {
        // The old worker dropped anything taken before the last run; the ledger is now the only filter.
        val old = deviceItem(1, takenAt = 946_684_800_000L) // 1 January 2000
        val result = LedgerMatching.selectSyncCandidates(listOf(old), LedgerIndex.EMPTY)
        assertEquals(listOf(old), result)
    }

    @Test
    fun `items already in the ledger are skipped and failed ones are retried`() {
        val done = deviceItem(1)
        val failedLastTime = deviceItem(2)
        val index = LedgerIndex(listOf(entry(done.localUri!!, done.title, done.fileSize)))
        assertEquals(listOf(failedLastTime), LedgerMatching.selectSyncCandidates(listOf(done, failedLastTime), index))
    }

    @Test
    fun `duplicates across albums are uploaded once and items without a uri are dropped`() {
        val a = deviceItem(1)
        val sameInOtherBucket = a.copy(id = "local:1-other")
        val noUri = deviceItem(3, uri = null)
        val result = LedgerMatching.selectSyncCandidates(listOf(a, sameInOtherBucket, noUri), LedgerIndex.EMPTY)
        assertEquals(listOf(a), result)
    }

    @Test
    fun `scales to thousands of items`() {
        val items = (1..5000).map { deviceItem(it) }
        val index = LedgerIndex(items.take(4000).map { entry(it.localUri!!, it.title, it.fileSize) })
        val started = System.nanoTime()
        val result = LedgerMatching.selectSyncCandidates(items, index)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertEquals(1000, result.size)
        assertTrue("took $ms ms", ms < 2000)
    }

    @Test
    fun `renamed upload still matches by original name and size`() {
        // IMG_1.jpg clashed with a different file and went up as IMG_1_1.jpg; the same photo seen
        // through a different content uri must still count as synced.
        val item = deviceItem(1)
        val index = LedgerIndex(listOf(entry("content://elsewhere/1", "IMG_1_1.jpg", item.fileSize, originalName = "IMG_1.jpg")))
        assertTrue(index.isUploaded(item.copy(localUri = "content://other/99")))
        assertEquals("IMG_1_1.jpg", index.entryFor(item.copy(localUri = "content://other/99"))?.displayName)
    }

    @Test
    fun `renamed upload does not match a different size`() {
        val item = deviceItem(1)
        val index = LedgerIndex(listOf(entry("content://elsewhere/1", "IMG_1_1.jpg", item.fileSize + 1, originalName = "IMG_1.jpg")))
        assertFalse(index.isUploaded(item.copy(localUri = "content://other/99")))
    }

    @Test
    fun `zero size never matches by name`() {
        val index = LedgerIndex(listOf(entry("content://a/1", "IMG_1.jpg", 0)))
        assertNull(index.entryFor("content://b/2", "IMG_1.jpg", 0))
    }

    @Test
    fun `uri plus name lookup used by manual uploads`() {
        val index = LedgerIndex(listOf(entry("content://a/1", "IMG_1_1.jpg", 500, originalName = "IMG_1.jpg")))
        assertTrue(index.isUploaded("content://a/1", "anything.jpg", 1))
        assertTrue(index.isUploaded("content://b/2", "IMG_1.jpg", 500))
        assertFalse(index.isUploaded("content://b/2", "IMG_2.jpg", 500))
    }
}
