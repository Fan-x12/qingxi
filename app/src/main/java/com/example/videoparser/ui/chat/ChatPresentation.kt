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

// 按消息标识保存入场状态，查看历史时不重播；新消息展开时同步推动旧消息。
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

// 每个响应使用独立容器；阶段切换时淡入淡出，高度平滑变化而不回弹，同标识内容保留组件状态。
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T : Any> ResponseEntrance(state: T, animate: Boolean, key: (T) -> Any, content: @Composable (T) -> Unit) {
    var arrived by rememberSaveable { mutableStateOf(!animate) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val transitionState = remember { MutableTransitionState<T?>(if (arrived || !motion) state else null) }
    // 每个响应只启动一次展示延迟；快速返回的结果直接替换等待内容，不重新计时。
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


// 两种模式共用状态文字展示，接口返回状态而非逐字流式文本。
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
            // 分段显示等待文字，任务完成后立即替换为结果。
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
        // 预留段落高度，并向屏幕阅读器一次提供完整状态。
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

