package au.prism.photos.ui.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Rational
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.PipState
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.Format
import au.prism.photos.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(UnstableApi::class)
private fun buildMediaSource(context: android.content.Context, url: String): MediaSource {
    val uri = Uri.parse(url)
    val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    return if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
        HlsMediaSource.Factory(httpFactory).createMediaSource(Media3Item.fromUri(uri))
    } else {
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Media3Item.fromUri(uri))
    }
}

/** Owns the ExoPlayer instance for one video page: source selection, transcode retry, release. */
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: android.content.Context,
    private val item: MediaItem,
    startPositionMs: Long,
    initialTranscode: Boolean,
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        repeatMode = Player.REPEAT_MODE_OFF
    }

    var transcode: Boolean = initialTranscode && !item.isLocal
        private set
    var onSwitchedToTranscode: (() -> Unit)? = null
    private var hasAutoRetried = false

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!item.isLocal && !transcode && !hasAutoRetried) {
                    hasAutoRetried = true
                    val position = exoPlayer.currentPosition
                    transcode = true
                    reload(position)
                    exoPlayer.playWhenReady = true
                    onSwitchedToTranscode?.invoke()
                }
            }
        })
        reload(startPositionMs)
    }

    private fun sourceUrl(): String =
        item.localUri ?: PrismApp.graph.media.videoUrl(item, transcode)

    private fun reload(startPositionMs: Long) {
        val source = buildMediaSource(context, sourceUrl())
        exoPlayer.setMediaSource(source, startPositionMs.coerceAtLeast(0))
        exoPlayer.prepare()
    }

    /** Restarts playback with the given transcode preference, at the same position. */
    fun setTranscode(value: Boolean) {
        if (item.isLocal || value == transcode) return
        val position = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
        if (transcode && !value) {
            PrismApp.graph.scope.launch { PrismApp.graph.media.stopTranscode() }
        }
        transcode = value
        reload(position)
        exoPlayer.playWhenReady = wasPlaying
    }

    fun release() {
        if (transcode && !item.isLocal) {
            PrismApp.graph.scope.launch { PrismApp.graph.media.stopTranscode() }
        }
        exoPlayer.release()
    }
}

/**
 * A single video page: Media3 [PlayerView] plus custom transport controls. One [ExoPlayer] is
 * created per composition and released on dispose.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    item: MediaItem,
    isActive: Boolean,
    initialPositionMs: Long,
    onPositionChange: (Long) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    val settings by PrismApp.graph.settings.settings.collectAsStateWithLifecycle()

    val controller = remember(item.id) {
        PlayerController(context, item, initialPositionMs, settings.preferTranscode)
    }

    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var isBuffering by remember(item.id) { mutableStateOf(true) }
    var positionMs by remember(item.id) { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember(item.id) { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var fullscreen by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var skipLabel by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    controller.onSwitchedToTranscode = { toastMessage = "Switched to transcoding" }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                PipState.videoPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) durationMs = controller.exoPlayer.duration.coerceAtLeast(0)
            }
        }
        controller.exoPlayer.addListener(listener)
        onDispose {
            controller.exoPlayer.removeListener(listener)
            onPositionChange(controller.exoPlayer.currentPosition.coerceAtLeast(0))
            PipState.videoPlaying = false
            controller.release()
        }
    }

    // Play or pause based on whether this page is the active one.
    LaunchedEffect(isActive) {
        controller.exoPlayer.playWhenReady = isActive
        if (!isActive) onPositionChange(controller.exoPlayer.currentPosition.coerceAtLeast(0))
    }

    // Poll position while playing and not being scrubbed.
    LaunchedEffect(isPlaying, dragging) {
        while (isPlaying && !dragging) {
            positionMs = controller.exoPlayer.currentPosition.coerceAtLeast(0)
            onPositionChange(positionMs)
            delay(250)
        }
    }

    // Auto hide controls after 3 seconds of inactivity while playing.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose {}
    }

    fun requestSkip(forwardMs: Long) {
        val target = (controller.exoPlayer.currentPosition + forwardMs)
            .coerceIn(0, controller.exoPlayer.duration.coerceAtLeast(0))
        controller.exoPlayer.seekTo(target)
        positionMs = target
        skipLabel = if (forwardMs > 0) "+10s" else "-10s"
    }

    LaunchedEffect(skipLabel) {
        if (skipLabel != null) {
            delay(600)
            skipLabel = null
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = controller.exoPlayer
                }
            },
            update = { it.player = controller.exoPlayer },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            onTap()
                        },
                        onDoubleTap = { offset ->
                            controlsVisible = true
                            if (offset.x < size.width / 2) requestSkip(-10_000) else requestSkip(10_000)
                        },
                    )
                },
        )

        if (isBuffering) {
            CircularProgressIndicator(color = PlexGold)
        }

        skipLabel?.let { label ->
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(label, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !PipState.inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = {
                        if (controller.exoPlayer.isPlaying) controller.exoPlayer.pause() else controller.exoPlayer.play()
                        controlsVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.duration(if (dragging) dragValue.toLong() else positionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = (if (dragging) dragValue else positionMs.toFloat())
                                .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            onValueChange = { value ->
                                dragging = true
                                dragValue = value
                                if (durationMs in 1..(10 * 60 * 1000)) {
                                    controller.exoPlayer.seekTo(value.toLong())
                                }
                            },
                            onValueChangeFinished = {
                                controller.exoPlayer.seekTo(dragValue.toLong())
                                positionMs = dragValue.toLong()
                                dragging = false
                            },
                            colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                        Text(Format.duration(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row {
                            IconButton(onClick = {
                                muted = !muted
                                controller.exoPlayer.volume = if (muted) 0f else 1f
                            }) {
                                Icon(
                                    imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = {
                                looping = !looping
                                controller.exoPlayer.repeatMode =
                                    if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            }) {
                                Icon(
                                    Icons.Filled.Loop,
                                    contentDescription = "Loop",
                                    tint = if (looping) PlexGold else Color.White,
                                )
                            }
                            Box {
                                IconButton(onClick = { showSpeedMenu = true }) {
                                    Icon(Icons.Filled.Speed, contentDescription = "Playback speed", tint = Color.White)
                                }
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    speedOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text("${option}x") },
                                            onClick = {
                                                speed = option
                                                controller.exoPlayer.playbackParameters = PlaybackParameters(option)
                                                showSpeedMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Row {
                            TranscodeToggle(
                                transcode = controller.transcode,
                                enabled = !item.isLocal,
                                onToggle = { controller.setTranscode(it) },
                            )
                            IconButton(onClick = {
                                val videoSize = controller.exoPlayer.videoSize
                                if (activity != null && videoSize.width > 0 && videoSize.height > 0) {
                                    val ratio = (videoSize.width.toFloat() / videoSize.height)
                                        .coerceIn(1f / 2.39f, 2.39f)
                                    val aspect = Rational((ratio * 1000).toInt(), 1000)
                                    PipState.aspect = aspect
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder().setAspectRatio(aspect).build(),
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                            }
                            IconButton(onClick = {
                                if (activity != null) {
                                    fullscreen = !fullscreen
                                    activity.requestedOrientation = if (fullscreen) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscodeToggle(transcode: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    if (!enabled) return
    IconButton(onClick = { onToggle(!transcode) }) {
        Text(
            text = if (transcode) "HLS" else "Direct",
            color = if (transcode) PlexGold else Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
