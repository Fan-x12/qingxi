package com.example.videoparser

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

internal enum class ImageSavePhase { Idle, Saving, Saved, Failed }

@Composable
internal fun ImageSaveControl(phase: ImageSavePhase, enabled: Boolean, compact: Boolean,
    dark: Boolean, grouped: Boolean, feedback: String?, modifier: Modifier = Modifier,
    idleLabel: String = if (dark) "保存原图" else "保存图片", onClick: () -> Unit) {
    val label = when (phase) {
        ImageSavePhase.Idle -> idleLabel
        ImageSavePhase.Saving -> "保存中"
        ImageSavePhase.Saved -> "已保存"
        ImageSavePhase.Failed -> "重试"
    }
    val ink = if (dark || (!compact && !grouped)) Color.White else StudioStyle.ink
    val surface = if (compact || grouped) Color.Transparent else if (dark) Color(0xFF303237) else StudioStyle.ink
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 36.dp).clip(RoundedCornerShape(50.dp))
            .background(surface)
            .appClickable(enabled = enabled, rippleColor = ink, onClick = onClick)
            .semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center) {
            Crossfade(phase, modifier = Modifier.fillMaxWidth(), animationSpec = tween(150), label = "image-save-state") { state ->
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .padding(horizontal = if (compact) 12.dp else 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (state == ImageSavePhase.Saving) CircularProgressIndicator(Modifier.size(16.dp), color = ink, strokeWidth = 2.dp)
                    else Icon(when (state) {
                        ImageSavePhase.Saved -> Icons.Default.Check
                        ImageSavePhase.Failed -> Icons.Default.Refresh
                        else -> Icons.Default.Download
                    }, null, tint = ink, modifier = Modifier.size(16.dp))
                    if (!compact) Text(when (state) {
                        ImageSavePhase.Idle -> idleLabel
                        ImageSavePhase.Saving -> "保存中"
                        ImageSavePhase.Saved -> "已保存"
                        ImageSavePhase.Failed -> "重试"
                    }, fontSize = 12.sp, lineHeight = 18.sp, color = ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
        // 浮层不改变卡片测量高度，也不推动时间线。
        var retained by remember { mutableStateOf<String?>(null) }
        val opacity = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(feedback) {
            val message = feedback?.takeIf { it.isNotBlank() }
            if (message != null) {
                retained = message
                opacity.animateTo(1f, tween(150))
            } else {
                opacity.animateTo(0f, tween(150))
                retained = null
            }
        }
        retained?.let { message ->
            Popup(alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(LocalDensity.current) { (-54).dp.roundToPx() })) {
                Text(message, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 260.dp)
                        .graphicsLayer {
                            alpha = opacity.value
                            scaleX = .96f + .04f * opacity.value
                            scaleY = scaleX
                        }
                        .testTag("image-save-feedback")
                        .background(Color(0xFF303237), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
