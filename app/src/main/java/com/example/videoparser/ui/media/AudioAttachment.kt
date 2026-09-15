package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.videoparser.data.PlaybackCache
import com.example.videoparser.domain.AudioTrack
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun AudioAttachment(track: AudioTrack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(track.url) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(track.url) { mutableStateOf(false) }
    var buffering by remember(track.url) { mutableStateOf(false) }
    var failed by remember(track.url) { mutableStateOf(false) }
    var ended by remember(track.url) { mutableStateOf(false) }
    var duration by remember(track.url) { mutableLongStateOf(0L) }
    var position by remember(track.url) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val activePlayer = player
        if (activePlayer == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                duration = activePlayer.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                buffering = false
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
            activePlayer.release()
        }
    }
    DisposableEffect(lifecycle, player) {
        val activePlayer = player
        if (activePlayer == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) activePlayer.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            position = activePlayer.currentPosition.coerceAtLeast(0L)
            duration = activePlayer.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    Row(
        modifier.heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIconButton(
            onClick = {
                val activePlayer = player
                when {
                    activePlayer == null -> {
                        buffering = true
                        failed = false
                        player = ExoPlayer.Builder(context.applicationContext).build().apply {
                            val source = ProgressiveMediaSource.Factory(PlaybackCache.dataSource(context))
                                .createMediaSource(PlayerMediaItem.fromUri(track.url))
                            setMediaSource(source)
                            prepare()
                            play()
                        }
                    }
                    failed -> { failed = false; buffering = true; activePlayer.prepare(); activePlayer.play() }
                    ended -> { activePlayer.seekTo(0L); activePlayer.play() }
                    playing -> activePlayer.pause()
                    else -> activePlayer.play()
                }
            },
            modifier = Modifier.size(34.dp).background(if (playing) StudioStyle.ink else StudioStyle.soft, CircleShape),
            rippleColor = if (playing) Color.White else StudioStyle.ink
        ) {
            if (buffering && !failed) CircularProgressIndicator(Modifier.size(13.dp),
                color = if (playing) Color.White else StudioStyle.ink, strokeWidth = 2.dp)
            else Icon(if (failed) Icons.Default.Refresh else if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (failed) "重试音频" else if (playing) "暂停音频" else "播放音频",
                tint = if (playing) Color.White else StudioStyle.ink, modifier = Modifier.size(17.dp))
        }
        Text("原声", color = StudioStyle.ink, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        val time = if (duration > 0) "${playbackTime(position)} / ${playbackTime(duration)}" else playbackTime(position)
        Text(time, color = StudioStyle.muted, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 1)
    }
}
