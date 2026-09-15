package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

@Composable
internal fun MediaThumbnailStrip(pager: PagerState, modifier: Modifier = Modifier,
    thumbnail: @Composable BoxScope.(Int) -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    val stride = with(LocalDensity.current) { 60.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 直接跟随分页位置，避免额外动画滞后；手动滚动缩略图条后，直到主分页变化才恢复跟随。
        LaunchedEffect(pager, stride, maxWidth) {
            snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
                val bounded = position.coerceIn(0f, (pager.pageCount - 1).coerceAtLeast(0).toFloat())
                val index = floor(bounded).toInt()
                list.scrollToItem(index, ((bounded - index) * stride).toInt())
            }
        }
        LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("media-filmstrip"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pager.pageCount, key = { it }) { index ->
                Box(Modifier.size(52.dp).testTag("media-thumbnail-$index")
                    .semantics {
                        selected = index == pager.currentPage
                        contentDescription = "查看第 ${index + 1} 项媒体"
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .appClickable {
                        navigation?.cancel()
                        navigation = scope.launch { pager.animateScrollToPage(index) }
                    }.padding(3.dp)
                    .graphicsLayer {
                        val distance = abs(index - pager.currentPage - pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        alpha = 1f - distance * .35f
                        scaleX = 1f - distance * .10f
                        scaleY = scaleX
                    }
                    .border(1.5.dp, if (index == pager.currentPage) StudioStyle.ink else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(2.dp).clip(RoundedCornerShape(8.dp)).background(StudioStyle.soft),
                    contentAlignment = Alignment.Center) { thumbnail(index) }
            }
        }
    }
}
