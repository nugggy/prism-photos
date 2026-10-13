package au.prism.photos.ui.timeline

import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.formatMonthHeader

/**
 * One month bucket. [flatIndices] holds, for each of [items], its position in the flat list that
 * was grouped (the list the viewer pages through), worked out once while grouping so the grid
 * never has to search for it.
 */
data class MonthGroup(val month: String, val items: List<MediaItem>, val flatIndices: List<Int>) {
    /** Flat index of the first item; the items follow on contiguously when the input was sorted. */
    val startIndex: Int get() = flatIndices.firstOrNull() ?: 0
}

/** Groups items (already newest first) into consecutive month buckets for sticky headers. */
fun groupByMonth(items: List<MediaItem>): List<MonthGroup> =
    groupByKey(items) { formatMonthHeader(it.takenAt) }

/**
 * [groupByMonth] with the key function passed in, so the grouping and offsets can be unit tested
 * without the locale dependent month formatting. Items sharing a key are gathered into the bucket
 * where that key first appeared (as before), and each keeps its true flat index even if the input
 * was not perfectly sorted.
 */
internal fun groupByKey(items: List<MediaItem>, key: (MediaItem) -> String): List<MonthGroup> {
    val map = LinkedHashMap<String, Pair<MutableList<MediaItem>, MutableList<Int>>>()
    items.forEachIndexed { index, item ->
        val bucket = map.getOrPut(key(item)) { mutableListOf<MediaItem>() to mutableListOf() }
        bucket.first.add(item)
        bucket.second.add(index)
    }
    return map.map { (month, bucket) -> MonthGroup(month, bucket.first, bucket.second) }
}

data class FlatRow(val isHeader: Boolean, val month: String)

/** Flat row -> month label map, in the exact same order rows are emitted to the LazyGridState,
 * used by the fast scroller to translate a drag position into a scroll index and label. */
fun flattenRows(grouped: List<MonthGroup>): List<FlatRow> = buildList {
    grouped.forEach { group ->
        add(FlatRow(true, group.month))
        repeat(group.items.size) { add(FlatRow(false, group.month)) }
    }
}

/**
 * Thumbnail size requested for a timeline cell. Quantised to two sizes so pinch zooming between
 * column counts reuses cached thumbnails instead of fetching a new size for every count. 400
 * matches the other grids (albums, search, locked), so those share the cache as well.
 */
fun thumbSizeForColumns(columns: Int): Int = if (columns <= 2) 800 else 400
