package com.example.videoparser

// 坐标沿用调用方单位，从触发角展开面板，不缩放文字。
internal fun menuRevealBounds(width: Float, height: Float, progress: Float, end: Boolean, up: Boolean, radius: Float = 20f): FloatArray {
    val p = progress.coerceIn(0f, 1f)
    // 宽度从 38% 开始并略领先高度，让面板沿横向持续展开。
    val widthProgress = p + .18f * p * (1f - p)
    val diameter = 2f * radius.coerceAtLeast(0f)
    val initialWidth = maxOf(width * .38f, diameter).coerceAtMost(width)
    val initialHeight = maxOf(height * .08f, diameter).coerceAtMost(height)
    val hiddenWidth = (width - initialWidth) * (1f - widthProgress)
    val hiddenHeight = (height - initialHeight) * (1f - p)
    return floatArrayOf(if (end) hiddenWidth else 0f, if (up) hiddenHeight else 0f,
        if (end) width else width - hiddenWidth, if (up) height else height - hiddenHeight)
}
