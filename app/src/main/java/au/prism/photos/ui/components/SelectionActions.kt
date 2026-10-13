package au.prism.photos.ui.components

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.domain.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** While a grid is in multi-select mode, Back clears the selection instead of leaving the screen. */
@Composable
fun SelectionBackHandler(selection: SelectionState) {
    BackHandler(enabled = selection.active) { selection.clear() }
}

/**
 * The selection bar's reversible bulk actions (favourite, lock, unlock). Each clears the selection,
 * then shows a snackbar with Undo. [onChanged] runs after the action and again after an undo, for
 * screens that reload their list.
 */
class SelectionActions internal constructor(
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val resources: Resources,
    private val selection: SelectionState,
    private val onChanged: () -> Unit,
) {
    private val graph get() = PrismApp.graph

    fun toggleFavourite(items: List<MediaItem>) {
        val favouriting = MediaSelectionOps.willFavourite(items)
        scope.launch {
            val action = MediaSelectionOps.toggleFavourite(graph, items)
            selection.clear()
            onChanged()
            val message = resources.getQuantityString(
                if (favouriting) R.plurals.undo_favourited else R.plurals.undo_unfavourited, action.count, action.count,
            )
            MediaSelectionOps.offerUndo(snackbar, message, resources.getString(R.string.action_undo), action, onChanged)
        }
    }

    fun lock(ids: Collection<String>) {
        val snapshot = ids.toList()
        scope.launch {
            val action = MediaSelectionOps.lock(graph, snapshot)
            selection.clear()
            onChanged()
            val message = resources.getQuantityString(R.plurals.undo_locked, action.count, action.count)
            MediaSelectionOps.offerUndo(snackbar, message, resources.getString(R.string.action_undo), action, onChanged)
        }
    }

    fun unlock(ids: Collection<String>) {
        val snapshot = ids.toList()
        scope.launch {
            val action = MediaSelectionOps.unlock(graph, snapshot)
            selection.clear()
            onChanged()
            val message = resources.getQuantityString(R.plurals.undo_unlocked, action.count, action.count)
            MediaSelectionOps.offerUndo(snackbar, message, resources.getString(R.string.action_undo), action, onChanged)
        }
    }
}

@Composable
fun rememberSelectionActions(
    selection: SelectionState,
    snackbar: SnackbarHostState,
    onChanged: () -> Unit = {},
): SelectionActions {
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val latestOnChanged by rememberUpdatedState(onChanged)
    return remember(selection, snackbar, scope, resources) {
        SelectionActions(scope, snackbar, resources, selection) { latestOnChanged() }
    }
}
