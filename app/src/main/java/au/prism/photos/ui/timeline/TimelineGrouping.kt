package au.prism.photos.ui.timeline

import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.formatMonthHeader

/** Groups items (already newest first) into consecutive month buckets for sticky headers. */
fun groupByMonth(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> {
    val map = LinkedHashMap<String, MutableList<MediaItem>>()
    for (item in items) {
        val key = formatMonthHeader(item.takenAt)
        map.getOrPut(key) { mutableListOf() }.add(item)
    }
    return map.map { it.key to it.value }
}

data class FlatRow(val isHeader: Boolean, val month: String)

/** Flat row -> month label map, in the exact same order rows are emitted to the LazyGridState,
 * used by the fast scroller to translate a drag position into a scroll index and label. */
fun flattenRows(grouped: List<Pair<String, List<MediaItem>>>): List<FlatRow> = buildList {
    grouped.forEach { (month, items) ->
        add(FlatRow(true, month))
        repeat(items.size) { add(FlatRow(false, month)) }
    }
}
