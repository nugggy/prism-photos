package au.prism.photos.ui.videoeditor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import au.prism.photos.BuildConfig
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.Format
import kotlinx.coroutines.delay

/**
 * Video editor for the Plex clip or device video [itemId] (device items pass their content://
 * URI as the id): trim, mute, rotate, flip, speed and colour adjustments through Media3
 * Transformer. Exports to Movies/Plex Gallery and calls [onDone] with the saved content URI,
 * or [onCancel].
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    itemId: String,
    onDone: (savedUri: String) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: VideoEditorViewModel = viewModel(factory = VideoEditorViewModel.Factory(itemId))
    val sourcePhase by vm.sourcePhase.collectAsStateWithLifecycle()
    val editState by vm.editState.collectAsStateWithLifecycle()
    val filmstrip by vm.filmstrip.collectAsStateWithLifecycle()
    val exportPhase by vm.exportPhase.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(VideoEditorTab.TRIM) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var seekRequest by remember { mutableStateOf<Long?>(null) }

    fun requestClose() {
        if (vm.hasChanges) showDiscardConfirm = true else onCancel()
    }

    BackHandler { requestClose() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // Keep the screen on while exporting so a long export isn't interrupted by sleep.
    DisposableEffect(exportPhase is ExportPhase.Exporting) {
        view.keepScreenOn = exportPhase is ExportPhase.Exporting
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { VideoEditorTopBar(onClose = { requestClose() }) },
        bottomBar = { VideoEditorTabBar(selected = selectedTab, onSelect = { selectedTab = it }) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            when (val phase = sourcePhase) {
                is SourcePhase.Preparing -> LoadingState("Preparing…")
                is SourcePhase.Downloading -> LoadingState("Downloading clip… ${(phase.fraction * 100).toInt()}%", phase.fraction)
                is SourcePhase.Error -> ErrorState(phase.message, onCancel)
                is SourcePhase.Ready -> {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            EditorPreviewPlayer(
                                sourceUri = phase.uri,
                                state = editState,
                                trimStartMs = editState.trimStartMs,
                                trimEndMs = editState.trimEndMs.coerceAtLeast(editState.trimStartMs + 200),
                                onPosition = { positionMs = it },
                                onPlayingChange = { isPlaying = it },
                                loopPreview = editState.loopPreview,
                                seekRequestMs = seekRequest,
                                onSeekHandled = { seekRequest = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (exportPhase is ExportPhase.Exporting) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = PlexGold)
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth().background(Color(0xFF0B0C0D))) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(Format.duration(positionMs), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                                LoopToggle(loop = editState.loopPreview, onLoopChange = { vm.setLoopPreview(it) })
                            }
                            FilmstripRangeSlider(
                                frames = filmstrip,
                                durationMs = phase.durationMs,
                                trimStartMs = editState.trimStartMs,
                                trimEndMs = editState.trimEndMs,
                                positionMs = positionMs,
                                onTrimChange = { start, end -> vm.setTrim(start, end) },
                                onScrub = { ms -> positionMs = ms; seekRequest = ms },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            )

                            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                when (selectedTab) {
                                    VideoEditorTab.TRIM -> TrimPanel(
                                        trimStartMs = editState.trimStartMs,
                                        trimEndMs = editState.trimEndMs,
                                        durationMs = phase.durationMs,
                                        onCaptureFrame = { vm.captureFrame(positionMs) },
                                    )
                                    VideoEditorTab.AUDIO -> AudioPanel(muted = editState.muted, onMutedChange = { vm.setMuted(it) })
                                    VideoEditorTab.TRANSFORM -> TransformPanel(
                                        state = editState,
                                        onRotate = { vm.rotate90() },
                                        onFlipHorizontal = { vm.flipHorizontal() },
                                        onFlipVertical = { vm.flipVertical() },
                                        onResolution = { vm.setResolution(it) },
                                    )
                                    VideoEditorTab.SPEED -> SpeedPanel(speed = editState.speed, onSpeedChange = { vm.setSpeed(it) })
                                    VideoEditorTab.COLOUR -> ColourPanel(
                                        state = editState,
                                        onBrightness = { vm.setBrightness(it) },
                                        onContrast = { vm.setContrast(it) },
                                        onSaturation = { vm.setSaturation(it) },
                                        onHue = { vm.setHue(it) },
                                        onWarmth = { vm.setWarmth(it) },
                                        onReset = { vm.resetColour() },
                                    )
                                    VideoEditorTab.EXPORT -> ExportPanel(
                                        exportPhase = exportPhase,
                                        onExport = { vm.export() },
                                        onCancelExport = { vm.cancelExport() },
                                        onShare = { file -> shareExportedFile(context, file) },
                                        onDone = { uri -> onDone(uri.toString()) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onCancel() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoEditorTopBar(onClose: () -> Unit) {
    TopAppBar(
        title = { Text("Edit video") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White),
    )
}

@Composable
private fun LoadingState(label: String, fraction: Float? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = PlexGold)
        Text(label, color = Color.White, modifier = Modifier.padding(top = 16.dp))
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                color = PlexGold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) { Text("Close", color = PlexGold) }
    }
}

@Composable
private fun ExportPanel(
    exportPhase: ExportPhase,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShare: (java.io.File) -> Unit,
    onDone: (Uri) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (exportPhase) {
            is ExportPhase.Idle -> {
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export to Plex Gallery") }
            }
            is ExportPhase.Exporting -> {
                Text("Exporting… ${(exportPhase.fraction * 100).toInt()}%", color = Color.White)
                LinearProgressIndicator(
                    progress = { exportPhase.fraction },
                    color = PlexGold,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                TextButton(onClick = onCancelExport) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
            }
            is ExportPhase.Done -> {
                Text("Saved to Movies/Plex Gallery", color = PlexGold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onShare(exportPhase.shareFile) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White)
                        Text(" Share", color = Color.White)
                    }
                    Button(
                        onClick = { onDone(exportPhase.uri) },
                        colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black),
                        modifier = Modifier.weight(1f),
                    ) { Text("Done") }
                }
            }
            is ExportPhase.Failed -> {
                Text(exportPhase.message, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = PlexGold, contentColor = Color.Black),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Retry") }
            }
        }
    }
}

private fun shareExportedFile(context: android.content.Context, file: java.io.File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}

/**
 * Small self-contained ExoPlayer preview: shows the source clip with the current rotate, flip,
 * colour and resolution edits applied live through [ExoPlayer.setVideoEffects], clamped and
 * looped (or paused) at the current trim range.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EditorPreviewPlayer(
    sourceUri: Uri,
    state: VideoEditState,
    trimStartMs: Long,
    trimEndMs: Long,
    onPosition: (Long) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    loopPreview: Boolean,
    seekRequestMs: Long?,
    onSeekHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    val player = remember(sourceUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sourceUri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlayingChange(playing)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Apply the current transform and colour edits as live preview effects.
    LaunchedEffect(player, state.rotationDegrees, state.flipHorizontal, state.flipVertical, state.brightness, state.contrast, state.saturation, state.hue, state.warmth, state.resolution) {
        val effects = mutableListOf<Effect>()
        if (state.rotationDegrees != 0 || state.flipHorizontal || state.flipVertical) {
            val sx = if (state.flipHorizontal) -1f else 1f
            val sy = if (state.flipVertical) -1f else 1f
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(sx, sy)
                .setRotationDegrees(state.rotationDegrees.toFloat())
                .build()
        }
        if (state.brightness != 0f) effects += Brightness(state.brightness)
        if (state.contrast != 0f) effects += Contrast(state.contrast)
        if (state.saturation != 0f || state.hue != 0f) {
            effects += HslAdjustment.Builder().adjustHue(state.hue).adjustSaturation(state.saturation).build()
        }
        if (state.warmth != 0f) {
            effects += RgbAdjustment.Builder()
                .setRedScale((1f + state.warmth * 0.3f).coerceAtLeast(0f))
                .setBlueScale((1f - state.warmth * 0.3f).coerceAtLeast(0f))
                .build()
        }
        when (state.resolution) {
            VideoResolution.R1080 -> effects += Presentation.createForHeight(1080)
            VideoResolution.R720 -> effects += Presentation.createForHeight(720)
            VideoResolution.KEEP -> {}
        }
        runCatching { player.setVideoEffects(effects) }
    }

    LaunchedEffect(player, state.speed) {
        player.playbackParameters = PlaybackParameters(state.speed)
    }
    LaunchedEffect(player, state.muted) {
        player.volume = if (state.muted) 0f else 1f
    }

    // Honour external scrub requests from the filmstrip (tap-to-seek and drag handles).
    LaunchedEffect(player, seekRequestMs) {
        val target = seekRequestMs
        if (target != null) {
            player.seekTo(target)
            onSeekHandled()
        }
    }

    // Keep playback inside the trim range, looping or pausing at the end, and report position.
    LaunchedEffect(player, trimStartMs, trimEndMs, loopPreview) {
        if (player.currentPosition < trimStartMs || player.currentPosition > trimEndMs) {
            player.seekTo(trimStartMs)
        }
        while (true) {
            if (player.isPlaying) {
                val pos = player.currentPosition
                if (pos >= trimEndMs) {
                    if (loopPreview) {
                        player.seekTo(trimStartMs)
                    } else {
                        player.pause()
                        player.seekTo(trimEndMs)
                    }
                }
                onPosition(player.currentPosition.coerceAtLeast(0))
            }
            delay(150)
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = { if (player.isPlaying) player.pause() else { if (player.currentPosition >= trimEndMs) player.seekTo(trimStartMs); player.play() } },
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
