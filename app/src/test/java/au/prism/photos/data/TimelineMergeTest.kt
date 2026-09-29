package au.prism.photos.data

import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineMergeTest {
    private fun item(id: String, takenAt: Long, kind: MediaKind = MediaKind.PHOTO) =
        MediaItem(id = id, kind = kind, title = id, takenAt = takenAt)

    @Test
    fun `merges photo and clip pages and sorts newest first`() {
        val photos = listOf(item("p1", 3_000), item("p2", 1_000))
        val clips = listOf(item("c1", 2_000, MediaKind.VIDEO))

        val merged = mergeTimelines(photos, clips)

        assertEquals(listOf("p1", "c1", "p2"), merged.map { it.id })
    }

    @Test
    fun `stable order for equal taken-at values keeps relative order`() {
        val a = listOf(item("a1", 5_000), item("a2", 5_000))
        val merged = mergeTimelines(a, emptyList())
        assertEquals(listOf("a1", "a2"), merged.map { it.id })
    }

    @Test
    fun `empty lists merge to empty`() {
        assertEquals(emptyList<MediaItem>(), mergeTimelines(emptyList(), emptyList()))
    }
}
