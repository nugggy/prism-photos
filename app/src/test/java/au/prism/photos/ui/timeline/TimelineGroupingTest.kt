package au.prism.photos.ui.timeline

import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineGroupingTest {
    private fun item(id: String, month: String) =
        MediaItem(id = id, kind = MediaKind.PHOTO, title = month, takenAt = 0)

    private fun group(items: List<MediaItem>) = groupByKey(items) { it.title }

    @Test
    fun `month buckets carry the flat index of each item`() {
        val items = listOf(item("a", "Oct"), item("b", "Oct"), item("c", "Sep"), item("d", "Aug"), item("e", "Aug"))

        val groups = group(items)

        assertEquals(listOf("Oct", "Sep", "Aug"), groups.map { it.month })
        assertEquals(listOf(0, 2, 3), groups.map { it.startIndex })
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), groups.map { it.flatIndices })
        // Every cell maps back to the item at that flat index.
        groups.forEach { g -> g.items.forEachIndexed { i, it -> assertEquals(it, items[g.flatIndices[i]]) } }
    }

    @Test
    fun `out of order months still map to the right flat index`() {
        val items = listOf(item("a", "Oct"), item("b", "Sep"), item("c", "Oct"))

        val groups = group(items)

        assertEquals(listOf("a", "c"), groups[0].items.map { it.id })
        assertEquals(listOf(0, 2), groups[0].flatIndices)
        assertEquals(listOf(1), groups[1].flatIndices)
    }

    @Test
    fun `empty input gives no groups`() {
        assertEquals(emptyList<MonthGroup>(), group(emptyList()))
    }

    @Test
    fun `flat rows put a header before each month`() {
        val rows = flattenRows(group(listOf(item("a", "Oct"), item("b", "Oct"), item("c", "Sep"))))

        assertEquals(listOf(true, false, false, true, false), rows.map { it.isHeader })
        assertEquals(listOf("Oct", "Oct", "Oct", "Sep", "Sep"), rows.map { it.month })
    }

    @Test
    fun `thumbnail size only changes between two and three columns`() {
        assertEquals(800, thumbSizeForColumns(2))
        assertEquals(setOf(400), (3..6).map { thumbSizeForColumns(it) }.toSet())
    }
}
