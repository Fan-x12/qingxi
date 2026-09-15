package com.example.videoparser

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 协调自动跟随与用户主动滚动，避免查看旧消息时被拉回底部。 */
internal class ChatBottomControl {
    var detached by mutableStateOf(false)
        private set
    var followResponse by mutableStateOf(true)
        private set
    var visible by mutableStateOf(false)
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                detached = true
                followResponse = false
            }
            return Offset.Zero
        }
    }
    fun reset(followResponse: Boolean = true) {
        detached = false
        visible = false
        this.followResponse = followResponse
    }
}

// The timelines use reverseLayout: index 0 is the newest message, sitting at the visual bottom.
@Composable
internal fun rememberChatBottomControl(list: LazyListState): ChatBottomControl {
    val density = LocalDensity.current
    val showDistance = with(density) { 180.dp.toPx() }
    val hideDistance = with(density) { 64.dp.toPx() }
    val control = remember(list) { ChatBottomControl() }
    LaunchedEffect(list, control, density) {
        snapshotFlow {
            val atBottom = !list.canScrollBackward
            val latestBelowViewport = list.firstVisibleItemIndex > 0
            val distance = if (atBottom) 0f else list.firstVisibleItemScrollOffset.toFloat()
            Triple(atBottom, list.isScrollInProgress,
                shouldShowChatBottom(control.detached, distance, latestBelowViewport,
                    showDistance, hideDistance, control.visible))
        }.collectLatest { (atBottom, scrolling, candidate) ->
            if (atBottom && !scrolling) control.reset(followResponse = control.followResponse)
            else control.visible = candidate
        }
    }
    return control
}

@Composable
internal fun BoxScope.ChatBottomButton(control: ChatBottomControl, list: LazyListState, clearance: Dp) {
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = control.visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = clearance + 8.dp),
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 }
    ) {
        Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).background(Color.White, CircleShape)
            .clip(CircleShape).appClickable {
                control.reset(followResponse = false)
                scope.launch { list.animateScrollToItem(0) }
            }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowDown, "回到底部", tint = StudioStyle.ink, modifier = Modifier.size(22.dp))
        }
    }
}

// A short response follows the bottom. Once it exceeds the usable viewport, keep its top
// visible instead of exposing only its trailing actions. User scrolling always wins.
// Only the response itself changing size re-anchors: the keyboard or composer resizing the
// viewport keeps the current offset, so content moves with the keyboard instead of
// fighting it frame by frame.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeepLatestResponseReadable(list: LazyListState, control: ChatBottomControl) {
    val density = LocalDensity.current
    // While the keyboard animates, the viewport height is transient; wait for its final size.
    val settled by rememberUpdatedState(
        WindowInsets.ime.getBottom(density) == WindowInsets.imeAnimationTarget.getBottom(density)
    )
    LaunchedEffect(list, control) {
        var anchoredKey: Any? = null
        var anchoredSize = -1
        snapshotFlow {
            // Stop observing per-frame layout changes once the user owns the scroll.
            if (!settled || !control.followResponse || control.detached || list.isScrollInProgress) {
                return@snapshotFlow null
            }
            val info = list.layoutInfo
            val latest = info.visibleItemsInfo.firstOrNull { it.index == 0 && it.key != "welcome" && it.key != "image-welcome" }
            val usableHeight = info.viewportEndOffset - info.viewportStartOffset -
                info.beforeContentPadding - info.afterContentPadding
            if (list.firstVisibleItemIndex != 0 || latest == null || usableHeight <= 0) null
            else Triple(latest.key, latest.size, usableHeight)
        }.collectLatest { measured ->
            val (key, size, usableHeight) = measured ?: return@collectLatest
            if (key == anchoredKey && size == anchoredSize) return@collectLatest
            anchoredKey = key
            anchoredSize = size
            val offset = latestResponseOffset(size, usableHeight)
            if (list.firstVisibleItemScrollOffset != offset) list.scrollToItem(0, offset)
        }
    }
}

internal fun latestResponseOffset(responseHeight: Int, viewportHeight: Int): Int =
    (responseHeight - viewportHeight.coerceAtLeast(0)).coerceAtLeast(0)
