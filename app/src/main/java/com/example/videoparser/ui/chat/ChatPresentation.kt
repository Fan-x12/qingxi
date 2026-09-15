package com.example.videoparser

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun timelineSizeTransition() = tween<IntSize>(280, easing = FastOutSlowInEasing)

// Saved per keyed lazy item: scrolling back through history never replays arrival.
// New messages grow into place so older messages above them move in lockstep.
@Composable
internal fun MessageArrival(animate: Boolean, content: @Composable () -> Unit) {
    var arrived by rememberSaveable { mutableStateOf(!animate) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val visibility = remember { MutableTransitionState(arrived || !motion) }
    LaunchedEffect(visibility) {
        withFrameNanos { }
        visibility.targetState = true
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && visibility.currentState) arrived = true
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = if (motion) expandVertically(timelineSizeTransition(), expandFrom = Alignment.Top) +
            fadeIn(tween(220, easing = LinearOutSlowInEasing)) else EnterTransition.None,
        exit = ExitTransition.None
    ) { SelectionContainer { content() } }
}

// One slot per assistant response. Phase changes (waiting -> result) cross-fade while the slot's
// height eases without overshoot. Content has no independent slide or translation.
// States that map to the same key swap content in place and keep the composable's state.
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T : Any> ResponseEntrance(state: T, animate: Boolean, key: (T) -> Any, content: @Composable (T) -> Unit) {
    var arrived by rememberSaveable { mutableStateOf(!animate) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val transitionState = remember { MutableTransitionState<T?>(if (arrived || !motion) state else null) }
    // Open the presentation gate once per response, independently of network phase changes.
    // A fast result replaces waiting data during the delay rather than restarting the delay.
    var ready by remember { mutableStateOf(arrived || !motion) }
    LaunchedEffect(Unit) {
        if (!ready) {
            withFrameNanos { }
            delay(240)
            ready = true
        }
    }
    LaunchedEffect(ready, state) {
        if (ready) transitionState.targetState = state
    }
    LaunchedEffect(transitionState.isIdle, transitionState.currentState) {
        if (transitionState.isIdle && transitionState.currentState != null) arrived = true
    }
    val transition = rememberTransition(transitionState, label = "response")
    transition.AnimatedContent(
        transitionSpec = {
            if (!motion) EnterTransition.None togetherWith ExitTransition.None using null
            else {
                val enter = fadeIn(tween(240, easing = LinearOutSlowInEasing))
                val exit = fadeOut(tween(140, easing = FastOutLinearInEasing))
                enter togetherWith exit using SizeTransform { _, _ -> timelineSizeTransition() }
            }
        },
        contentAlignment = Alignment.TopCenter,
        contentKey = { it?.let(key) }
    ) { target -> if (target != null) SelectionContainer { content(target) } }
}


// One text response for both modes. The API supplies status, not token-streamed prose.
@Composable
internal fun StreamingResponse(title: String, text: String, colors: AppPalette, modifier: Modifier = Modifier) {
    var output by rememberSaveable { mutableStateOf("") }
    val motionEnabled = android.animation.ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(text, motionEnabled) {
        if (!motionEnabled) {
            output = text
            return@LaunchedEffect
        }
        if (!text.startsWith(output)) output = ""
        while (output.length < text.length) {
            // Reveal small chunks; completion immediately replaces this response.
            val end = text.offsetByCodePoints(output.length,
                minOf(2, text.codePointCount(output.length, text.length)))
            output = text.substring(0, end)
            delay(32)
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (motionEnabled) ShimmerTitle(title, colors)
        else Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        // Reserve paragraph height and expose the complete status once to screen readers.
        Box(Modifier.fillMaxWidth()) {
            Text(text, color = Color.Transparent, style = ChatTypography.body)
            Text(output, color = colors.secondaryText, style = ChatTypography.body,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
internal fun ShimmerTitle(title: String, colors: AppPalette) {
    if (!ValueAnimator.areAnimatorsEnabled()) {
        Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        return
    }
    val transition = rememberInfiniteTransition(label = "title-shimmer")
    val position by transition.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "title-highlight"
    )
    Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val x = size.width * position
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = .65f), Color.Transparent),
                    start = Offset(x - size.width * .4f, 0f), end = Offset(x + size.width * .4f, size.height)
                ), blendMode = BlendMode.SrcAtop)
            }
    )
}

