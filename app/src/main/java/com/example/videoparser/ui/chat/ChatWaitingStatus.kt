package com.example.videoparser

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
internal fun ChatWaitingStatus(title: String, colors: AppPalette, modifier: Modifier = Modifier) {
    val motion = ValueAnimator.areAnimatorsEnabled()
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(motion) {
        elapsedMillis = 0L
        if (!motion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { elapsedMillis = (it - start) / 1_000_000L }
        }
    }
    val spectrum = remember(colors.secondaryText) { DotSpectrum(colors.secondaryText) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Column(
        modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerTitle(title, colors)
        Spacer(
            Modifier.widthIn(max = 336.dp).fillMaxWidth(.96f).aspectRatio(1.25f).drawWithCache {
                // 仅依赖尺寸的数据缓存一次，时间参数只在绘制阶段读取。
                val lattice = DotLattice(size.width, size.height, 2.5.dp.toPx())
                onDrawBehind {
                    val seconds = if (motion) elapsedMillis / 1000f else DotLattice.STILL_SECONDS
                    drawIntoCanvas { lattice.draw(it.nativeCanvas, paint, spectrum, seconds, settled = !motion) }
                }
            }
        )
    }
}

private val ParsingSpectrum = listOf(
    Color(0xFFEF6A78), // coral
    Color(0xFFF2A447), // amber
    Color(0xFF58B982), // mint
    Color(0xFF35AFC1), // cyan
    Color(0xFF557DE1), // blue
    Color(0xFF9A68D5), // violet
    Color(0xFFEF6A78)
)

private val TAU = (Math.PI * 2).toFloat()
private val HALF_TAU = Math.PI.toFloat()

private fun smoothstep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

internal fun parsingSpectrumColor(position: Float): Color {
    val normalized = ((position % 1f) + 1f) % 1f
    val scaled = normalized * (ParsingSpectrum.size - 1)
    val index = scaled.toInt().coerceAtMost(ParsingSpectrum.lastIndex - 1)
    return lerp(ParsingSpectrum[index], ParsingSpectrum[index + 1], scaled - index)
}

// 预先计算 Oklab 感知色彩混合，逐帧绘制时只进行简单的 sRGB 插值。
private class DotSpectrum(quietBase: Color) {
    val size = 96
    private val active = FloatArray(size * 3)
    private val quiet = FloatArray(size * 3)

    init {
        for (i in 0 until size) {
            val tone = parsingSpectrumColor(i / size.toFloat())
            val muted = lerp(quietBase, tone, .32f)
            active[i * 3] = tone.red; active[i * 3 + 1] = tone.green; active[i * 3 + 2] = tone.blue
            quiet[i * 3] = muted.red; quiet[i * 3 + 1] = muted.green; quiet[i * 3 + 2] = muted.blue
        }
    }

    fun argb(index: Int, mix: Float, alpha: Float): Int {
        val base = index * 3
        val r = quiet[base] + (active[base] - quiet[base]) * mix
        val g = quiet[base + 1] + (active[base + 1] - quiet[base + 1]) * mix
        val b = quiet[base + 2] + (active[base + 2] - quiet[base + 2]) * mix
        return (channel(alpha) shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun channel(value: Float) = (value * 255f + .5f).toInt().coerceIn(0, 255)
}

private class ValueNoise(private val columns: Int, private val rows: Int, random: Random) {
    private val values = FloatArray(columns * rows) { random.nextFloat() }

    fun at(x: Float, y: Float): Float {
        val u = x * (columns - 1)
        val v = y * (rows - 1)
        val column = min(u.toInt(), columns - 2)
        val row = min(v.toInt(), rows - 2)
        val fu = smoothstep(u - column)
        val fv = smoothstep(v - row)
        val top = values[row * columns + column]
        val topRight = values[row * columns + column + 1]
        val bottom = values[(row + 1) * columns + column]
        val bottomRight = values[(row + 1) * columns + column + 1]
        val upper = top + (topRight - top) * fu
        val lower = bottom + (bottomRight - bottom) * fu
        return upper + (lower - upper) * fv
    }
}

private class DotLattice(width: Float, height: Float, radiusCap: Float) {
    private val centerX: FloatArray
    private val centerY: FloatArray
    private val nx: FloatArray
    private val ny: FloatArray
    private val vignette: FloatArray
    private val hue: FloatArray
    private val cloudA: FloatArray
    private val cloudB: FloatArray
    private val delay: FloatArray
    private val count: Int
    private val maxRadius: Float

    init {
        val columns = 29
        val rows = 23
        val xGap = width / (columns + 1)
        val yGap = height / (rows + 1)
        maxRadius = min(radiusCap, min(xGap, yGap) * .3f)
        val noiseA = ValueNoise(6, 5, Random(11))
        val noiseB = ValueNoise(5, 4, Random(29))
        val capacity = columns * rows
        centerX = FloatArray(capacity); centerY = FloatArray(capacity)
        nx = FloatArray(capacity); ny = FloatArray(capacity)
        vignette = FloatArray(capacity); hue = FloatArray(capacity)
        cloudA = FloatArray(capacity); cloudB = FloatArray(capacity); delay = FloatArray(capacity)
        var n = 0
        for (row in 0 until rows) for (column in 0 until columns) {
            val x = column.toFloat() / (columns - 1)
            val y = row.toFloat() / (rows - 1)
            // 省略角落点形成圆角轮廓，其余点保持正圆。
            val cornerX = (abs(x - .5f) - .39f).coerceAtLeast(0f)
            val cornerY = (abs(y - .5f) - .39f).coerceAtLeast(0f)
            if (cornerX * cornerX + cornerY * cornerY > .13f * .13f) continue
            val distance = sqrt((x - .5f) * (x - .5f) + (y - .5f) * (y - .5f))
            val shade = noiseA.at(x, y)
            centerX[n] = (column + 1) * xGap
            centerY[n] = (row + 1) * yGap
            nx[n] = x; ny[n] = y
            vignette[n] = .5f + .5f * smoothstep((.67f - distance) / .16f)
            hue[n] = x * .52f + y * .28f
            cloudA[n] = shade * TAU
            cloudB[n] = noiseB.at(x, y) * TAU
            // 点阵从中心沿不规则边界向外展开，不形成同心圆。
            delay[n] = BLOOM_SPREAD * (.55f * distance / .7071f + .45f * shade)
            n++
        }
        count = n
    }

    fun draw(canvas: android.graphics.Canvas, paint: Paint, spectrum: DotSpectrum, seconds: Float, settled: Boolean) {
        val phase = seconds * TAU / 6.4f
        val hueShift = seconds / 14f
        val driftA = seconds * TAU / 4.6f
        val driftB = seconds * TAU / 7.3f
        val blooming = !settled && seconds < BLOOM_SPREAD + DOT_BLOOM
        for (i in 0 until count) {
            val appear = if (blooming) smoothstep((seconds - delay[i]) / DOT_BLOOM) else 1f
            if (appear <= 0f) continue
            val x = nx[i]
            val y = ny[i]
            // 弧形光带以 6.4 秒周期斜向移动，首尾相位与速度一致；两层缓慢变化的噪声阴影保持背景流动。
            val bend = .28f * sin(y * TAU - phase)
            val wave = .5f + .5f * cos(x * TAU - y * HALF_TAU + bend - phase)
            val ribbon = wave * wave * wave
            val undertone = .5f + .5f * sin(y * TAU + phase)
            val cloud = .5f + .25f * sin(driftA + cloudA[i]) + .25f * sin(driftB + cloudB[i])
            val intensity = (.06f + .74f * ribbon + .08f * undertone + .12f * cloud).coerceIn(0f, 1f)
            val alpha = (.19f + intensity * .66f) * vignette[i] * appear
            val radius = maxRadius * (.60f + intensity * .30f) * (.35f + .65f * appear)
            val position = hue[i] + hueShift
            val index = ((position - floor(position)) * spectrum.size).toInt().coerceIn(0, spectrum.size - 1)
            paint.color = spectrum.argb(index, .28f + .72f * intensity, alpha)
            canvas.drawCircle(centerX[i], centerY[i], radius, paint)
        }
    }

    companion object {
        const val STILL_SECONDS = 1.6f
        private const val BLOOM_SPREAD = .28f
        private const val DOT_BLOOM = .36f
    }
}
