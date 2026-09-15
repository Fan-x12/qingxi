package com.example.videoparser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

// 整段对话作为 Haze 的背景来源，仅在视口顶部和底部的控件重叠区域应用模糊。
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun ProgressiveHeaderBackdrop(
    state: HazeState,
    height: Dp,
    surface: Color,
    bottom: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fade = with(density) { 56.dp.toPx() }
    val progressiveStart = with(density) { (height - 56.dp).coerceAtLeast(0.dp).toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val mask = if (bottom) {
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .28f to Color.White.copy(alpha = .10f),
                        .56f to Color.White.copy(alpha = .42f),
                        .80f to Color.White.copy(alpha = .78f),
                        1f to Color.White,
                        startY = 0f,
                        endY = fade.coerceAtMost(size.height).coerceAtLeast(1f)
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color.White,
                        .20f to Color.White.copy(alpha = .78f),
                        .44f to Color.White.copy(alpha = .42f),
                        .72f to Color.White.copy(alpha = .10f),
                        1f to Color.Transparent,
                        startY = (size.height - fade).coerceAtLeast(0f),
                        endY = size.height.coerceAtLeast(1f)
                    )
                }
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
            .hazeChild(state) {
                backgroundColor = surface.copy(alpha = .82f)
                tints = listOf(HazeTint(Color.White.copy(alpha = .16f)))
                fallbackTint = HazeTint(surface.copy(alpha = .90f))
                blurRadius = 21.dp
                noiseFactor = .018f
                inputScale = HazeInputScale.Fixed(.55f)
                progressive = HazeProgressive.verticalGradient(
                    startY = if (bottom) 0f else progressiveStart,
                    endY = if (bottom) fade else Float.POSITIVE_INFINITY,
                    startIntensity = if (bottom) 0f else 1f,
                    endIntensity = if (bottom) 1f else 0f,
                    easing = LinearEasing,
                    preferPerformance = true
                )
            }
    )
}
