package com.example.videoparser

import android.view.WindowManager
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

internal val SecondaryMenuAccent = Color(0xFF0A66FF)
private val SecondaryMenuDividerColor = Color(0xFFD7DAE0).copy(alpha = .68f)

// Cache a software-rendered soft shadow: Android elevation varies with the popup's
// window light source and can become almost invisible on some devices. Only the
// bitmap's straight edges extend during motion; corners and blur never scale.
private fun Modifier.menuReveal(progress: () -> Float, end: Boolean, up: Boolean) = drawWithCache {
    val gutter = 52.dp.toPx()
    val bitmap = android.graphics.Bitmap.createBitmap(
        (size.width + gutter * 2).toInt().coerceAtLeast(1),
        (size.height + gutter * 2).toInt().coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    val radius = minOf(20.dp.toPx(), size.width / 2f, size.height / 2f)
    for ((blur, offset, alpha) in listOf(Triple(18.dp.toPx(), 8.dp.toPx(), 38), Triple(4.dp.toPx(), 2.dp.toPx(), 24))) {
        paint.setShadowLayer(blur, 0f, offset, android.graphics.Color.argb(alpha, 25, 32, 46))
        canvas.drawRoundRect(gutter, gutter, gutter + size.width, gutter + size.height, radius, radius, paint)
    }
    paint.clearShadowLayer()
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    canvas.drawRoundRect(gutter, gutter, gutter + size.width, gutter + size.height, radius, radius, paint)
    val shadow = bitmap.asImageBitmap()
    val corner = kotlin.math.ceil(gutter + radius).toInt()
    val sourceX = intArrayOf(0, corner, bitmap.width - corner, bitmap.width)
    val sourceY = intArrayOf(0, corner, bitmap.height - corner, bitmap.height)
    val path = Path()
    onDrawWithContent {
        val b = menuRevealBounds(size.width, size.height, progress(), end, up, radius)
        // Nine slices preserve the shadow's corner radius and blur at both endpoints.
        val left = (b[0] - gutter).toInt()
        val top = (b[1] - gutter).toInt()
        val right = (b[2] + gutter).toInt()
        val bottom = (b[3] + gutter).toInt()
        val targetX = intArrayOf(left, left + corner, right - corner, right)
        val targetY = intArrayOf(top, top + corner, bottom - corner, bottom)
        for (row in 0..2) for (column in 0..2) {
            val targetWidth = targetX[column + 1] - targetX[column]
            val targetHeight = targetY[row + 1] - targetY[row]
            if (targetWidth > 0 && targetHeight > 0) drawImage(
                image = shadow,
                srcOffset = IntOffset(sourceX[column], sourceY[row]),
                srcSize = IntSize(sourceX[column + 1] - sourceX[column], sourceY[row + 1] - sourceY[row]),
                dstOffset = IntOffset(targetX[column], targetY[row]),
                dstSize = IntSize(targetWidth, targetHeight)
            )
        }
        path.reset()
        path.addRoundRect(RoundRect(b[0], b[1], b[2], b[3], CornerRadius(radius, radius)))
        drawPath(path, Color.White)
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

private fun menuFade(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
internal fun SecondaryMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp = 184.dp,
    alignToEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var mounted by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(false) }
    var opensUpward by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val motionEnabled = android.animation.ValueAnimator.areAnimatorsEnabled()
    val density = LocalDensity.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val isExpanded by rememberUpdatedState(expanded)
    // Retarget the same progress so rapid toggles continue from the current presentation.
    LaunchedEffect(expanded, motionEnabled) {
        if (expanded) mounted = true
        if (mounted) {
            // Popup mounting can take several frames. Start only after its first layout,
            // otherwise most of the entrance may finish before the window is visible.
            if (expanded && !positioned) {
                snapshotFlow { positioned }.first { it }
                withFrameNanos { }
            }
            if (motionEnabled) {
                progress.animateTo(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = if (expanded) tween(360, easing = CubicBezierEasing(.22f, .05f, .24f, 1f))
                        else tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
            } else {
                progress.snapTo(if (expanded) 1f else 0f)
            }
            if (!expanded) { mounted = false; positioned = false }
        }
    }
    if (mounted) {
        val shadowPadding = 52.dp
        val shadowPaddingPx = with(density) { shadowPadding.roundToPx() }
        val gapPx = with(density) { 6.dp.roundToPx() }
        val positionProvider = remember(alignToEnd, shadowPaddingPx, gapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    val surfaceWidth = (popupContentSize.width - shadowPaddingPx * 2).coerceAtLeast(0)
                    val surfaceHeight = (popupContentSize.height - shadowPaddingPx * 2).coerceAtLeast(0)
                    val desiredX = if (alignToEnd) anchorBounds.right - surfaceWidth else anchorBounds.left
                    val surfaceX = desiredX.coerceIn(0, (windowSize.width - surfaceWidth).coerceAtLeast(0))
                    val below = anchorBounds.bottom + gapPx
                    val placeAbove = below + surfaceHeight > windowSize.height
                    // Keep drawing in sync with placement, including the first upward-opening frame.
                    opensUpward = placeAbove
                    val surfaceY = if (!placeAbove) below else {
                        (anchorBounds.top - gapPx - surfaceHeight).coerceAtLeast(0)
                    }
                    return IntOffset(surfaceX - shadowPaddingPx, surfaceY - shadowPaddingPx)
                }
            }
        }
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { if (isExpanded) dismiss() },
            properties = PopupProperties(
                // A non-focusable window still intercepts touches. Release touches as soon as
                // closing starts, so the underlying trigger can reverse the spring immediately.
                flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    (if (expanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                dismissOnBackPress = expanded,
                dismissOnClickOutside = expanded
            )
        ) {
            val upward = opensUpward
            val menuShape = remember { androidx.compose.foundation.shape.RoundedCornerShape(20.dp) }
            Box(Modifier.pointerInput(Unit) {
                detectTapGestures { if (isExpanded) dismiss() }
            }) {
                Box(Modifier.onGloballyPositioned { positioned = true }.graphicsLayer {
                    // Composite once with the shadow gutter inside the layer. This avoids both
                    // clipped shadows and per-primitive alpha darkening the translucent surface.
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = menuFade(progress.value, 0f, .30f)
                }.padding(shadowPadding)) {
                    Box(
                        Modifier.width(width)
                            .testTag("secondary-menu")
                            // Blank space inside the menu must not act like its outer shadow gutter.
                            .pointerInput(Unit) { detectTapGestures { } }
                            .menuReveal({ progress.value }, alignToEnd, upward)
                            .graphicsLayer {
                                shape = menuShape
                                clip = true
                            }
                            .background(Color.White, menuShape)
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecondaryMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (selected) SecondaryMenuAccent else StudioStyle.ink
    val iconColor = if (selected) SecondaryMenuAccent else StudioStyle.muted
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        leadingIcon = { Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (selected) Icon(Icons.Default.Check, "当前选项", tint = SecondaryMenuAccent, modifier = Modifier.size(18.dp))
        },
        modifier = Modifier.heightIn(min = 46.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        onClick = onClick
    )
}

@Composable
internal fun SecondaryMenuDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .height(.7.dp).background(SecondaryMenuDividerColor)
    )
}
