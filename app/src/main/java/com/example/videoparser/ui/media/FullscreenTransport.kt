package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullscreenTransport(playing: Boolean, buffering: Boolean, ended: Boolean, failed: Boolean,
    position: Long, duration: Long, speed: Float, onPlay: () -> Unit, onSeek: (Long) -> Unit, onSpeed: () -> Unit) {
    var seeking by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val enabled = duration > 0 && !failed
    val displayPosition = if (seeking) (fraction * duration).toLong() else position
    val playButton: @Composable () -> Unit = {
        Box(Modifier.size(48.dp).clip(CircleShape)
            .appClickable(rippleColor = Color.White, onClick = onPlay).semantics {
                contentDescription = if (buffering) "缓冲中，点击暂停或继续" else if (failed) "重试播放" else if (ended) "重新播放" else if (playing) "暂停" else "播放"
            }, contentAlignment = Alignment.Center) {
            if (buffering) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(if (failed || ended) Icons.Default.Refresh else if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
    val timeline: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(playbackTime(displayPosition), color = Color.White, fontSize = 12.sp)
            PlaybackSeekBar(value = if (seeking) fraction else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { seeking = true; fraction = it },
                onValueChangeFinished = { if (enabled) onSeek((fraction * duration).toLong()); seeking = false },
                enabled = enabled, dark = true,
                modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "全屏播放进度" })
            Text(playbackTime(duration), color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
        }
    }
    val speedButton: @Composable () -> Unit = {
        Text("${speed}×", color = Color(0xFFD6DEEB), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(StudioStyle.control)
                .appClickable(rippleColor = Color.White, onClick = onSpeed)
                .semantics { contentDescription = "播放速度 $speed 倍，点击切换" }.padding(horizontal = 13.dp, vertical = 16.dp))
    }
    val rewind: @Composable () -> Unit = {
        AppIconButton({ onSeek((displayPosition - 10_000L).coerceAtLeast(0)) }, rippleColor = Color.White, enabled = enabled) {
            Icon(Icons.Default.Replay10, "后退 10 秒", tint = Color.White.copy(alpha = if (enabled) .85f else .3f))
        }
    }
    val forward: @Composable () -> Unit = {
        AppIconButton({ onSeek((displayPosition + 10_000L).coerceAtMost(duration)) }, rippleColor = Color.White, enabled = enabled) {
            Icon(Icons.Default.Forward10, "前进 10 秒", tint = Color.White.copy(alpha = if (enabled) .85f else .3f))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        timeline()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            playButton()
            rewind()
            forward()
            Spacer(Modifier.weight(1f))
            speedButton()
        }
    }
}
