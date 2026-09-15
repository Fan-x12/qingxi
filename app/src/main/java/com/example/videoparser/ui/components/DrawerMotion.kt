package com.example.videoparser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

/** 统一保存拖拽进度与动画状态，让抽屉手势和过渡衔接。 */
internal class DrawerMotion(open: Boolean) {
    val animation = Animatable(if (open) 1f else 0f, visibilityThreshold = .001f)
    var dragging by mutableStateOf(false)
    var dragProgress by mutableFloatStateOf(0f)
    var releaseVelocity: Float? = null
    val progress: Float get() = if (dragging) dragProgress else animation.value
}

@Composable
internal fun rememberDrawerMotion(open: Boolean): DrawerMotion {
    val motion = remember { DrawerMotion(open) }
    LaunchedEffect(open, motion.dragging) {
        if (!motion.dragging) {
            // Preserve velocity when a button/back action reverses an animation.
            val velocity = motion.releaseVelocity ?: motion.animation.velocity
            motion.releaseVelocity = null
            motion.animation.animateTo(if (open) 1f else 0f,
                spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = .001f), initialVelocity = velocity)
        }
    }
    return motion
}

@Composable
internal fun Modifier.drawerGestures(
    motion: DrawerMotion, widthPx: Float, edgePx: Float, enabled: Boolean,
    onStart: () -> Unit, onSetOpen: (Boolean) -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    val start by rememberUpdatedState(onStart)
    val setOpen by rememberUpdatedState(onSetOpen)
    return pointerInput(motion, widthPx, edgePx, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The opening zone is supplied by the page (full width). Child
            // horizontal controls may consume the gesture before this parent.
            if (motion.progress < .001f && down.position.x > edgePx) return@awaitEachGesture
            val tracker = VelocityTracker()
            val startedOpen = motion.progress >= .5f
            tracker.addPosition(down.uptimeMillis, down.position)
            var overSlop = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                if (motion.progress > .001f || over > 0f) {
                    overSlop = over
                    change.consume()
                }
            } ?: return@awaitEachGesture
            motion.dragProgress = motion.progress
            tracker.addPosition(drag.uptimeMillis, drag.position)
            motion.dragging = true
            start()
            motion.dragProgress = (motion.dragProgress + overSlop / widthPx).coerceIn(0f, 1f)
            val completed = horizontalDrag(drag.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                motion.dragProgress = (motion.dragProgress + change.positionChange().x / widthPx).coerceIn(0f, 1f)
                change.consume()
            }
            val velocity = if (completed) tracker.calculateVelocity().x / widthPx else 0f
            val target = when {
                velocity > 1f -> true
                velocity < -1f -> false
                // A short deliberate drag opens; the reverse gesture closes at
                // the matching distance, without requiring a half-screen pull.
                else -> motion.dragProgress >= (if (startedOpen) .7f else .3f)
            }
            val releasePosition = motion.dragProgress
            // Transfer the exact release position before the next rendering frame.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                motion.animation.snapTo(releasePosition)
                motion.releaseVelocity = velocity.coerceIn(-4f, 4f)
                setOpen(target)
                motion.dragging = false
            }
        }
    }
}
