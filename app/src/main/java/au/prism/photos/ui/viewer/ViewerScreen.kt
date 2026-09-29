package au.prism.photos.ui.viewer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    val settings by PrismApp.graph.settings.settings.collectAsStateWithLifecycle()
    val session by PrismApp.graph.session.session.collectAsStateWithLifecycle()
    val lockedIds by PrismApp.graph.local.lockedItemIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (loading && items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PlexGold)
        }
        return
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val clampedStart = startIndex.coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = clampedStart) { items.size }
    var swipeEnabled by remember { mutableStateOf(true) }
    var immersive by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var slideshowProgress by remember { mutableFloatStateOf(0f) }

    var showInfo by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val currentIndex = pagerState.currentPage.coerceIn(0, items.size - 1)
    val currentItem = items[currentIndex]
    val isLocked = currentItem.id in lockedIds

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

    // Slideshow: auto advance every settings.slideshowIntervalSec, keep the screen on.
    LaunchedEffect(slideshow, pagerState.currentPage, items.size, settings.slideshowIntervalSec) {
        if (slideshow) {
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
    DisposableEffect(slideshow) {
        if (slideshow) view.keepScreenOn = true
        onDispose { if (slideshow) view.keepScreenOn = false }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val item = pendingDeleteItem
        pendingDeleteItem = null
        if (result.resultCode == Activity.RESULT_OK && item != null) {
            scope.launch {
                vm.delete(item)
                    .onSuccess { if (items.size <= 1) onClose() }
                    .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't delete") }
            }
        }
    }

    fun performDelete(item: MediaItem) {
        scope.launch {
            vm.delete(item)
                .onSuccess { if (items.size <= 1) onClose() }
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
                    onBack = onClose,
                    onInfo = { showInfo = true },
                    onRename = { showRename = true },
                    onEditDescription = { showInfo = true },
                    onTags = { showInfo = true },
                    onSetAlbumCover = {
                        vm.setAlbumCover(currentItem)
                        scope.launch { snackbarHostState.showSnackbar("Set as album cover") }
                    },
                    onAddToAlbum = { showAddToAlbum = true },
                    onWallpaper = { showWallpaperSheet = true },
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
                    onDelete = { showDeleteConfirm = true },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !immersive && !slideshow, enter = fadeIn(), exit = fadeOut()) {
                ViewerBottomBar(
                    item = currentItem,
                    onShare = { showShare = true },
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
                    onInfo = { showInfo = true },
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
                                    if (abs(dragOffsetY) > 260f) onClose() else dragOffsetY = 0f
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

    if (showInfo) {
        InfoSheet(
            item = currentItem,
            onDismiss = { showInfo = false },
            onSummaryChange = { vm.setSummary(currentItem, it) },
            onAddTag = { vm.addTag(currentItem, it) },
            onRemoveTag = { vm.removeTag(currentItem, it) },
        )
    }
    if (showShare) {
        ShareSheet(items = listOf(currentItem), onDismiss = { showShare = false })
    }
    if (showRename) {
        RenameDialog(
            current = currentItem.title,
            onDismiss = { showRename = false },
            onConfirm = { vm.rename(currentItem, it); showRename = false },
        )
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { showDeleteConfirm = false; performDelete(currentItem) },
        )
    }
    if (showWallpaperSheet) {
        WallpaperSheet(
            onDismiss = { showWallpaperSheet = false },
            onPick = { home, lock ->
                showWallpaperSheet = false
                scope.launch {
                    MediaActions.setWallpaperFlags(context, currentItem, home, lock)
                        .onSuccess { snackbarHostState.showSnackbar("Wallpaper set") }
                        .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Couldn't set wallpaper") }
                }
            },
        )
    }
    if (showAddToAlbum) {
        AddToAlbumSheet(
            itemIds = listOf(currentItem.id),
            onDismiss = { showAddToAlbum = false },
            onAdded = { title ->
                showAddToAlbum = false
                scope.launch { snackbarHostState.showSnackbar("Added to $title") }
            },
        )
    }

}
