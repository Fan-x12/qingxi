package com.example.videoparser

// Coordinates use the caller's units. Grow from the trigger corner; never scale text.
internal fun menuRevealBounds(width: Float, height: Float, progress: Float, end: Boolean, up: Boolean, radius: Float = 20f): FloatArray {
    val p = progress.coerceIn(0f, 1f)
    // Start at 38% width. A slight lead over height makes the corner open into
    // a panel, while retaining visible horizontal travel through the middle.
    val widthProgress = p + .18f * p * (1f - p)
    val diameter = 2f * radius.coerceAtLeast(0f)
    val initialWidth = maxOf(width * .38f, diameter).coerceAtMost(width)
    val initialHeight = maxOf(height * .08f, diameter).coerceAtMost(height)
    val hiddenWidth = (width - initialWidth) * (1f - widthProgress)
    val hiddenHeight = (height - initialHeight) * (1f - p)
    return floatArrayOf(if (end) hiddenWidth else 0f, if (up) hiddenHeight else 0f,
        if (end) width else width - hiddenWidth, if (up) height else height - hiddenHeight)
}
