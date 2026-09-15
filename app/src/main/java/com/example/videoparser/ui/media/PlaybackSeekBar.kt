package com.example.videoparser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Keep Slider's seeking/keyboard/accessibility behavior; draw one continuous track.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSeekBar(value: Float, onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit, enabled: Boolean, dark: Boolean,
    modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val emphasis by animateFloatAsState(if (enabled && (dragged || pressed || focused)) 1f else 0f,
        tween(120), label = "seek-emphasis")
    val active = (if (dark) Color.White else StudioStyle.ink).copy(alpha = if (enabled) 1f else .3f)
    val inactive = if (dark) Color.White.copy(alpha = if (enabled) .24f else .1f)
        else StudioStyle.ink.copy(alpha = if (enabled) .14f else .06f)
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished, enabled = enabled, interactionSource = interaction,
        modifier = modifier.height(48.dp),
        thumb = {
            // A fixed slot prevents track length and thumb position from shifting on press.
            Canvas(Modifier.size(24.dp)) {
                if (emphasis > 0f) drawCircle(active.copy(alpha = .13f * emphasis), radius = 12.dp.toPx())
                drawCircle(active, radius = (4.5f + 2f * emphasis).dp.toPx())
            }
        },
        track = { slider ->
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val thickness = (2.5f + 1.5f * emphasis).dp.toPx()
                val start = Offset(0f, size.height / 2)
                val end = Offset(size.width, size.height / 2)
                drawLine(inactive, start, end, thickness, StrokeCap.Round)
                val progress = slider.value.coerceIn(0f, 1f)
                if (progress > 0f) {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    drawLine(active, if (rtl) end else start,
                        Offset(size.width * if (rtl) 1f - progress else progress, size.height / 2),
                        thickness, StrokeCap.Round)
                }
            }
        })
}
