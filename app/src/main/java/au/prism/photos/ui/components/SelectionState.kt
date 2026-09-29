package au.prism.photos.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import au.prism.photos.domain.MediaItem

/** Tracks the set of selected item ids for a grid's multi-select mode. */
class SelectionState {
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    val active: Boolean get() = selected.isNotEmpty()

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun start(id: String) {
        selected = selected + id
    }

    fun clear() {
        selected = emptySet()
    }

    fun selectAll(ids: Collection<String>) {
        selected = selected + ids
    }
}

/**
 * When set (non null), grids are in "pick" mode for an external ACTION_PICK / GET_CONTENT
 * request: a tap calls this instead of opening the viewer or toggling selection.
 */
val LocalPickHandler = staticCompositionLocalOf<((MediaItem) -> Unit)?> { null }
