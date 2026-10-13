package au.prism.photos.ui.search

import au.prism.photos.ui.components.downloadResultMessage
import au.prism.photos.ui.components.rememberSelectionActions
import au.prism.photos.ui.components.SelectionBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import au.prism.photos.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.albums.AddToAlbumSheet
import au.prism.photos.ui.components.ConfirmDeleteDialog
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectableMediaGrid
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.viewer.MediaActions
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenViewer: (query: String, index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectionActions = rememberSelectionActions(selection, snackbarHostState)
    SelectionBackHandler(selection)

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    val recents = remember { mutableStateListOf<String>() }
    val searchFailedFallback = stringResource(R.string.search_failed)

    val timeline by graph.media.timeline.collectAsStateWithLifecycle()
    val tagChips = remember(timeline) {
        timeline.flatMap { it.tags }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(10).map { it.key }
    }
    val yearChips = remember(timeline) {
        timeline.map { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).year }
            .distinct().sortedDescending().take(10).map { it.toString() }
    }

    LaunchedEffect(query, retryNonce) {
        searchError = null
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(400)
        graph.media.search(query)
            .onSuccess { results = it }
            .onFailure { results = emptyList(); searchError = it.message ?: searchFailedFallback }
        searching = false
    }

    fun runSearch(q: String) {
        query = q
        if (q.isNotBlank()) {
            recents.remove(q)
            recents.add(0, q)
            while (recents.size > 8) recents.removeAt(recents.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = results.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = selectedItems.isNotEmpty() && selectedItems.all { it.favourite },
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(results.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(downloadResultMessage(context.resources, result))
                        }
                    },
                    onToggleFavourite = { selectionActions.toggleFavourite(selectedItems) },
                    onLock = { selectionActions.lock(selection.selected) },
                    onAddToAlbum = { showAddToAlbum = true },
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.common_clear)) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                query.isBlank() -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    if (recents.isNotEmpty()) {
                        Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                            recents.forEach { r ->
                                AssistChip(onClick = { runSearch(r) }, label = { Text(r) })
                            }
                        }
                    }
                    if (tagChips.isNotEmpty()) {
                        Text(stringResource(R.string.search_tags), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                            tagChips.forEach { t -> SuggestionChip(onClick = { runSearch(t) }, label = { Text(t) }) }
                        }
                    }
                    if (yearChips.isNotEmpty()) {
                        Text(stringResource(R.string.search_years), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            yearChips.forEach { y -> SuggestionChip(onClick = { runSearch(y) }, label = { Text(y) }) }
                        }
                    }
                    if (recents.isEmpty() && tagChips.isEmpty() && yearChips.isEmpty()) {
                        EmptyState(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
                    }
                }
                searching && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                searchError != null -> ErrorState(searchError!!, onRetry = { retryNonce++ })
                results.isEmpty() -> EmptyState(stringResource(R.string.search_no_results, query))
                else -> SelectableMediaGrid(
                    items = results,
                    columns = 3,
                    thumbUrl = { graph.media.thumbUrl(it, 400) },
                    selection = selection,
                    onOpenViewer = { index -> onOpenViewer(query, index) },
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val selectedItems = results.filter { it.id in selection.selected }
        ConfirmDeleteDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val errors = MediaSelectionOps.delete(graph, selectedItems)
                    selection.clear()
                    if (errors.isNotEmpty()) snackbarHostState.showSnackbar(errors.first())
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }

    if (showAddToAlbum) {
        AddToAlbumSheet(
            itemIds = selection.selected.toList(),
            onDismiss = { showAddToAlbum = false },
            onAdded = { title ->
                showAddToAlbum = false
                selection.clear()
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.selection_added_to_album, title)) }
            },
        )
    }
}
