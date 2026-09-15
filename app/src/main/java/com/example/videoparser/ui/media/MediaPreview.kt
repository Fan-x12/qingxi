package com.example.videoparser

import android.view.LayoutInflater
import android.os.Build
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.videoparser.data.PlaybackCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.videoparser.domain.MediaItem
import kotlinx.coroutines.delay

@Composable
internal fun MediaPreview(video: MediaItem?, cover: String?, modifier: Modifier, aspectRatio: Float,
    onAspectRatio: (Float) -> Unit) {
    var started by rememberSaveable(video?.url) { mutableStateOf(false) }
    var startFullScreen by rememberSaveable(video?.url) { mutableStateOf(false) }
    // 外层附件负责圆角，视频与说明区域直接衔接，不额外添加内边框。
    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFF35383D)), contentAlignment = Alignment.Center) {
        // 保持附件画布稳定，横竖媒体在内部适配，不缩小整张消息卡片或裁切内容。
        val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
        val widthFromHeight = maxHeight * safeRatio
        val frameWidth = minOf(maxWidth, widthFromHeight)
        val frameHeight = frameWidth / safeRatio
        // 用低分辨率封面柔和填充视频留白，不裁切实际视频，并降低旧设备的解码开销。
        cover?.let {
            val context = LocalContext.current
            val ambientRequest = remember(context, it) {
                coil3.request.ImageRequest.Builder(context).data(it).size(48, 48).build()
            }
            AsyncImage(
                ambientRequest,
                contentDescription = null, contentScale = ContentScale.Crop,
                // 低分辨率封面仍需缩小绘制目标才能降低 GPU 模糊开销；放大后保持 28dp 的视觉模糊。
                modifier = Modifier.fillMaxSize().scale(1.12f)
                    .reducedResolutionLayer(divisor = 4).blur(7.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)))
        if (started && video != null) {
            PlaybackPreview(video.url, maxWidth, maxHeight, frameWidth, frameHeight, startFullScreen, onAspectRatio)
        } else {
            Box(Modifier.fillMaxWidth().height(frameHeight), contentAlignment = Alignment.Center) {
                // 封面进入视口时保持列表几何稳定，主动播放后再由播放器报告实际比例。
                cover?.let { AsyncImage(it, "视频封面", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()) }
                if (video != null) {
                    AppIconButton(onClick = { started = true }, modifier = Modifier.size(56.dp)
                        .background(Color.Black.copy(alpha = .35f), CircleShape), rippleColor = Color.White) {
                        Icon(Icons.Default.PlayArrow, "播放视频", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)))))
                    AppIconButton(onClick = { startFullScreen = true; started = true }, rippleColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                        Icon(Icons.Default.Fullscreen, "全屏播放", tint = Color.White)
                    }
                } else Text("暂无可播放地址", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackPreview(url: String, canvasWidth: Dp, canvasHeight: Dp, frameWidth: Dp, frameHeight: Dp, initiallyFullScreen: Boolean, onAspectRatio: (Float) -> Unit) {
    val reportAspectRatio by rememberUpdatedState(onAspectRatio)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fullScreen by rememberSaveable(url) { mutableStateOf(initiallyFullScreen) }
    var savedPosition by rememberSaveable(url) { mutableLongStateOf(0L) }
    var resumePlayback by rememberSaveable(url) { mutableStateOf(true) }
    val player = remember(url) {
        val factory = PlaybackCache.dataSource(context)
        ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(DefaultLoadControl.Builder()
                // 优先快速起播，再保留较充足的前向缓冲以适应移动网络波动。
                .setBufferDurationsMs(15_000, 50_000, 750, 1_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build())
            .build().apply {
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(PlayerMediaItem.fromUri(url)))
            seekTo(savedPosition)
            prepare()
            playWhenReady = resumePlayback
        }
    }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var buffering by remember(player) { mutableStateOf(true) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var position by remember(player) { mutableLongStateOf(savedPosition) }
    var ended by remember(player) { mutableStateOf(false) }
    var failed by remember(player) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    reportAspectRatio(videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    savedPosition = player.currentPosition.coerceAtLeast(0L)
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var playbackSpeed by rememberSaveable(url) { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(player, lifecycle, playing, scrubbing) {
        if (!playing && !scrubbing) return@LaunchedEffect
        while (playing || scrubbing) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                position = player.currentPosition.coerceAtLeast(0L)
                savedPosition = position
                duration = player.duration.coerceAtLeast(0L)
                resumePlayback = player.playWhenReady
            }
            delay(250)
        }
    }
    LaunchedEffect(player, playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }
    val controls: @Composable () -> Unit = {
        PlaybackControls(
            playing, buffering, ended, failed, position, duration, fullScreen,
            onPlay = {
                if (failed) { failed = false; player.prepare(); player.play() }
                else if (ended) { player.seekTo(0L); player.play() }
                else if (player.playWhenReady) player.pause()
                else player.play()
                resumePlayback = player.playWhenReady
            },
            onSeek = { player.seekTo(it); position = it; savedPosition = it },
            onFullScreen = { fullScreen = !fullScreen },
            speed = playbackSpeed, onSpeed = { playbackSpeed = if (playbackSpeed >= 2f) 1f else playbackSpeed + .25f },
            onSeekingChange = { scrubbing = it }
        )
    }
    // 始终只挂载一个 PlayerView；全屏切换宿主，不重建播放器或改变播放位置。
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, playing, buffering, failed, scrubbing) {
        if (controlsVisible && playing && !buffering && !failed && !scrubbing) {
            delay(3000)
            controlsVisible = false
        }
    }
    // 视频在 PlayerView 内适配，控制条使用整个附件宽度，避免竖视频两侧留白挤窄进度条。
    Box(Modifier.size(canvasWidth, canvasHeight).appClickable(feedback = false) { controlsVisible = !controlsVisible }) {
        if (!fullScreen) {
            VideoSurface(player, Modifier.size(frameWidth, frameHeight).align(Alignment.Center))
            androidx.compose.animation.AnimatedVisibility(visible = controlsVisible || !playing || buffering || failed,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) { controls() }
        }
    }
    if (fullScreen) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            ImmersivePlayerWindow()
            MediaViewerLayout("视频", onClose = { fullScreen = false }, tapToHide = true, footer = { controls() }) {
                VideoSurface(player, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun ImmersivePlayerWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousKeepScreenOn = view.keepScreenOn
        val previousStatusContrast = if (Build.VERSION.SDK_INT >= 29) window?.isStatusBarContrastEnforced else null
        val previousNavigationContrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            it.setBackgroundDrawableResource(android.R.color.transparent)
            @Suppress("DEPRECATION")
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            it.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= 28) {
                it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        fun hideBars() { controller?.hide(WindowInsetsCompat.Type.systemBars()) }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) hideBars() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        view.post { if (view.isAttachedToWindow) hideBars() }
        view.keepScreenOn = true
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            view.keepScreenOn = previousKeepScreenOn
            if (Build.VERSION.SDK_INT >= 29 && window != null) {
                previousStatusContrast?.let { window.isStatusBarContrastEnforced = it }
                previousNavigationContrast?.let { window.isNavigationBarContrastEnforced = it }
            }
            // 仅恢复弹窗窗口的系统栏，宿主 Activity 的外观保持不变。
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(player: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.video_surface, null, false) as PlayerView).apply {
                this.player = player
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black).testTag("video-frame")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(
    playing: Boolean, buffering: Boolean, ended: Boolean, failed: Boolean,
    position: Long, duration: Long, fullScreen: Boolean,
    onPlay: () -> Unit, onSeek: (Long) -> Unit, onFullScreen: () -> Unit,
    speed: Float, onSpeed: () -> Unit, onSeekingChange: (Boolean) -> Unit
) {
    if (fullScreen) {
        FullscreenTransport(playing, buffering, ended, failed, position, duration, speed, onPlay, onSeek, onSpeed)
        return
    }
    var seeking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { onSeekingChange(false) } }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val canSeek = duration > 0 && !failed
    Column(Modifier.fillMaxWidth().testTag("transport-controls").background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))).padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(onClick = onPlay, rippleColor = Color.White, modifier = Modifier.size(48.dp)) {
                if (buffering) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (failed || ended) Icons.Default.Refresh else if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (failed) "重试播放" else if (ended) "重新播放" else if (playing) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(23.dp))
            }
            PlaybackSeekBar(
                value = if (seeking) seekFraction else fraction,
                onValueChange = { seeking = true; onSeekingChange(true); seekFraction = it },
                onValueChangeFinished = {
                    if (canSeek) onSeek((seekFraction * duration).toLong())
                    seeking = false
                    onSeekingChange(false)
                },
                enabled = canSeek, dark = true,
                modifier = Modifier.weight(1f).height(40.dp)
                    .semantics { contentDescription = "播放进度" })
            AppIconButton(onClick = onFullScreen, rippleColor = Color.White, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Fullscreen, "全屏播放", tint = Color.White, modifier = Modifier.size(23.dp))
            }
        }
    }
}

internal fun previewAspectRatio(width: Int?, height: Int?): Float =
    if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else 16f / 9f

internal fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
