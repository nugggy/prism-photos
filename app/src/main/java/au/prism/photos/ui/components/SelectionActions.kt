package au.prism.photos.ui.components

import android.content.Context
import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.compose.material3.SnackbarDuration
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
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
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

/** Snackbar text after downloading a selection: "Downloaded 3 items" (or [success] wording), or "Download failed". */
fun downloadResultMessage(resources: Resources, result: Result<Int>, @PluralsRes success: Int = R.plurals.selection_downloaded): String {
    val count = result.getOrNull() ?: return resources.getString(R.string.selection_download_failed)
    return resources.getQuantityString(success, count, count)
}

/**
 * Downloads [items] with a "Downloading…" snackbar up until it finishes, so the button never looks
 * dead on a slow remote connection, then shows [downloadResultMessage].
 */
suspend fun downloadWithProgress(
    context: Context,
    snackbarHostState: SnackbarHostState,
    items: List<MediaItem>,
    @PluralsRes success: Int = R.plurals.selection_downloaded,
) = coroutineScope {
    val resources = context.resources
    val progress = launch {
        snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.selection_downloading, items.size, items.size), duration = SnackbarDuration.Indefinite)
    }
    val result = MediaActions.download(context, items)
    progress.cancel()
    snackbarHostState.showSnackbar(downloadResultMessage(resources, result, success))
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
