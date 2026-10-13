package au.prism.photos.ui.viewer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.prism.photos.R
import au.prism.photos.ui.components.KeepScreenOn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.ViewerSource
import au.prism.photos.ui.albums.AddToAlbumSheet
import au.prism.photos.ui.player.VideoPlayer
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full screen photo and video viewer. Pages through the items resolved from [source], starting
 * at [startIndex]. Handles zoom, playback, info, share, favourite, lock, delete and slideshow.
 */
@Composable
fun ViewerScreen(
    source: ViewerSource,
    startIndex: Int,
    onClose: () -> Unit,
    onEdit: (itemId: String) -> Unit,
    onEditVideo: ((itemId: String) -> Unit)? = null,
) {
    val vm: ViewerViewModel = viewModel(factory = ViewerViewModel.Factory(source))
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by PrismApp.graph.settings.settings.collectAsStateWithLifecycle()
    val session by PrismApp.graph.session.session.collectAsStateWithLifecycle()
    val lockedIds by PrismApp.graph.local.lockedItemIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Several paths can ask to close (back, drag down, the list emptying after a delete). Only the
    // first one pops, so a double call can never pop the screen underneath as well.
    val latestOnClose by rememberUpdatedState(onClose)
    var closed by remember { mutableStateOf(false) }
    val close: () -> Unit = remember {
        {
            if (!closed) {
                closed = true
                latestOnClose()
            }
        }
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.clearError()
    }

    if (items.isEmpty()) {
        when {
            loadError != null -> ViewerLoadError(message = loadError!!, onRetry = { vm.retry() }, onClose = close)
            loading -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PlexGold)
            }
            else -> LaunchedEffect(Unit) { close() }
        }
        return
    }

    val clampedStart = startIndex.coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = clampedStart) { items.size }
    var swipeEnabled by remember { mutableStateOf(true) }
    var immersive by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var slideshowProgress by remember { mutableFloatStateOf(0f) }

    // Each dialog and sheet captures the item it was opened for, so a slideshow advance or a list
    // update while it is open cannot redirect the action to a different photo.
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var shareItem by remember { mutableStateOf<MediaItem?>(null) }
    var renameItem by remember { mutableStateOf<MediaItem?>(null) }
    var deleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var syncedDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var syncedDeleteKey by remember { mutableStateOf<String?>(null) }
    var wallpaperItem by remember { mutableStateOf<MediaItem?>(null) }
    var addToAlbumItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    val dialogOpen = infoItem != null || shareItem != null || renameItem != null || deleteItem != null ||
        syncedDeleteItem != null || wallpaperItem != null || addToAlbumItem != null

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val currentIndex = pagerState.currentPage.coerceIn(0, items.size - 1)
    val currentItem = items[currentIndex]
    val isLocked = currentItem.id in lockedIds

    /** Latest copy of a captured item (its summary or tags may have changed), or the capture if it has gone. */
    fun fresh(item: MediaItem): MediaItem = items.firstOrNull { it.id == item.id } ?: item

    // System bars follow immersive mode; always restore them when the viewer closes.
    DisposableEffect(immersive, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) WindowInsetsControllerCompat(w, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Slideshow: auto advance every settings.slideshowIntervalSec, keep the screen on. Paused while
    // any dialog or sheet is open.
    LaunchedEffect(slideshow, dialogOpen, pagerState.currentPage, items.size, settings.slideshowIntervalSec) {
        if (slideshow && !dialogOpen) {
            slideshowProgress = 0f
            val intervalMs = (settings.slideshowIntervalSec.coerceAtLeast(1)) * 1000L
            val stepMs = 50L
            var elapsed = 0L
            while (elapsed < intervalMs) {
                delay(stepMs)
                elapsed += stepMs
                slideshowProgress = (elapsed.toFloat() / intervalMs).coerceIn(0f, 1f)
            }
            if (pagerState.currentPage < items.size - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                slideshow = false
            }
        }
    }
    KeepScreenOn(enabled = slideshow)

    // A successful delete removes the item from vm.items; when that empties the list the empty
    // branch above closes the viewer (once), so the delete paths never call close themselves.
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val item = pendingDeleteItem
        pendingDeleteItem = null
        if (result.resultCode == Activity.RESULT_OK && item != null) {
            scope.launch {
                vm.delete(item)
                    .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't delete") }
            }
        }
    }

    fun performDelete(item: MediaItem) {
        scope.launch {
            vm.delete(item)
                .onFailure { e ->
                    val recoverable = e as? RecoverableSecurityException
                    if (recoverable != null) {
                        pendingDeleteItem = item
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build(),
                        )
                    } else {
                        snackbarHostState.showSnackbar(e.message ?: "Couldn't delete")
                    }
                }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = !immersive, enter = fadeIn(), exit = fadeOut()) {
                ViewerTopBar(
                    item = currentItem,
                    isLocked = isLocked,
                    onBack = close,
                    onInfo = { infoItem = currentItem },
                    onRename = { renameItem = currentItem },
                    onEditDescription = { infoItem = currentItem },
                    onTags = { infoItem = currentItem },
                    onSetAlbumCover = {
                        vm.setAlbumCover(currentItem)
                        scope.launch { snackbarHostState.showSnackbar("Set as album cover") }
                    },
                    onAddToAlbum = { addToAlbumItem = currentItem },
                    onWallpaper = { wallpaperItem = currentItem },
                    onLockToggle = {
                        if (isLocked) vm.unlock(currentItem) else vm.lock(currentItem)
                    },
                    onSlideshow = { slideshow = !slideshow },
                    onOpenInPlex = if (!currentItem.isLocal && session.server != null) {
                        {
                            val serverId = session.server?.clientIdentifier
                            val url = "https://app.plex.tv/desktop/#!/server/$serverId/details?key=/library/metadata/${currentItem.id}"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    } else null,
                    onDelete = {
                        // Device items that were synced to Plex get the "delete from Plex as well?" prompt.
                        val target = currentItem
                        scope.launch {
                            val key = if (target.isLocal) vm.syncedPlexRatingKey(target) else null
                            if (key != null) {
                                syncedDeleteKey = key
                                syncedDeleteItem = target
                            } else {
                                deleteItem = target
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !immersive && !slideshow, enter = fadeIn(), exit = fadeOut()) {
                ViewerBottomBar(
                    item = currentItem,
                    onShare = { shareItem = currentItem },
                    onFavourite = { vm.toggleFavourite(currentItem) },
                    onEdit = {
                        val idOrUri = if (currentItem.isLocal) currentItem.localUri ?: currentItem.id else currentItem.id
                        if (currentItem.isVideo) (onEditVideo ?: onEdit)(idOrUri) else onEdit(idOrUri)
                    },
                    onDownload = {
                        scope.launch {
                            MediaActions.download(context, listOf(currentItem))
                                .onSuccess { count -> snackbarHostState.showSnackbar(if (count > 0) "Saved to Downloads" else "Couldn't save") }
                                .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't save") }
                        }
                    },
                    onInfo = { infoItem = currentItem },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = swipeEnabled && !dragging,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragOffsetY
                        val progress = (abs(dragOffsetY) / 800f).coerceIn(0f, 1f)
                        alpha = 1f - progress * 0.6f
                    }
                    .pointerInput(swipeEnabled) {
                        if (swipeEnabled) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    if (abs(dragOffsetY) > 260f) close() else dragOffsetY = 0f
                                },
                                onDragCancel = { dragging = false; dragOffsetY = 0f },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount
                                },
                            )
                        }
                    },
            ) { page ->
                val pageItem = items[page]
                if (pageItem.isVideo) {
                    VideoPlayer(
                        item = pageItem,
                        isActive = page == pagerState.currentPage && !slideshow,
                        initialPositionMs = vm.positionFor(pageItem.id),
                        onPositionChange = { vm.setPosition(pageItem.id, it) },
                        onTap = { immersive = !immersive },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableImage(
                        model = if (pageItem.isLocal) pageItem.localUri else PrismApp.graph.media.originalUrl(pageItem),
                        placeholderModel = if (!pageItem.isLocal) PrismApp.graph.media.thumbUrl(pageItem, 1600) else null,
                        contentDescription = pageItem.title,
                        onTap = { immersive = !immersive },
                        onSwipeEnabled = { swipeEnabled = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (slideshow) {
                LinearProgressIndicator(
                    progress = { slideshowProgress },
                    color = PlexGold,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }

    infoItem?.let { target ->
        val item = fresh(target)
        InfoSheet(
            item = item,
            onDismiss = { infoItem = null },
            onSummaryChange = { vm.setSummary(fresh(target), it) },
            onAddTag = { vm.addTag(fresh(target), it) },
            onRemoveTag = { vm.removeTag(fresh(target), it) },
        )
    }
    shareItem?.let { target ->
        ShareSheet(items = listOf(target), onDismiss = { shareItem = null })
    }
    renameItem?.let { target ->
        RenameDialog(
            current = target.title,
            onDismiss = { renameItem = null },
            onConfirm = { vm.rename(fresh(target), it); renameItem = null },
        )
    }
    deleteItem?.let { target ->
        DeleteConfirmDialog(
            onDismiss = { deleteItem = null },
            onConfirm = { deleteItem = null; performDelete(target) },
        )
    }
    syncedDeleteItem?.let { target ->
        SyncedDeleteConfirmDialog(
            onDismiss = { syncedDeleteItem = null },
            onDeleteFromBoth = {
                syncedDeleteItem = null
                val key = syncedDeleteKey
                if (key == null) {
                    performDelete(target)
                } else {
                    scope.launch {
                        vm.deleteFromBoth(target, key)
                            .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't delete from Plex") }
                    }
                }
            },
            onPhoneOnly = { syncedDeleteItem = null; performDelete(target) },
        )
    }
    wallpaperItem?.let { target ->
        WallpaperSheet(
            onDismiss = { wallpaperItem = null },
            onPick = { home, lock ->
                wallpaperItem = null
                scope.launch {
                    MediaActions.setWallpaperFlags(context, target, home, lock)
                        .onSuccess { snackbarHostState.showSnackbar("Wallpaper set") }
                        .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't set wallpaper") }
                }
            },
        )
    }
    addToAlbumItem?.let { target ->
        AddToAlbumSheet(
            itemIds = listOf(target.id),
            onDismiss = { addToAlbumItem = null },
            onAdded = { title ->
                addToAlbumItem = null
                scope.launch { snackbarHostState.showSnackbar("Added to $title") }
            },
        )
    }

}

/** Shown when the viewer's items could not be loaded at all (rather than closing straight away). */
@Composable
private fun ViewerLoadError(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(stringResource(R.string.viewer_load_failed), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.action_close), color = Color.White) }
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PlexGold)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}
