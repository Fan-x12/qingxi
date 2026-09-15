package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ChatTypography {
    val body = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
    val title = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val detail = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
}

// 使用不透明、无边框的消息背景，减少动态画布上大范围阴影的开销。
internal fun Modifier.resultSurface(): Modifier =
    clip(RoundedCornerShape(22.dp)).background(StudioStyle.response)

@Composable
internal fun UserMessageBubble(text: String, isLink: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Box(Modifier.widthIn(max = 480.dp)
        .background(StudioStyle.userBubble, StudioStyle.bubbleShape)
        .padding(horizontal = 14.dp, vertical = 11.dp)) {
        // 保留原文供选择与复制，正常换行，不人为重排短消息或给链接插入连字符。
        Text(text, style = ChatTypography.body.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = if (isLink) 14.sp else 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        ), color = StudioStyle.userInk, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

// 媒体保存操作共用紧凑按钮外观，同时保留足够的点击高度。
@Composable
internal fun ChatSaveAction(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, grouped: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50.dp)
    val ink = if ((enabled || busy) && !grouped) Color.White else if (enabled || busy) StudioStyle.ink else StudioStyle.muted
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        // 最小点击区域可延伸到外侧留白，反馈效果只绘制在按钮实际范围内。
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(shape)
            .background(if (grouped) Color.Transparent else if (enabled || busy) StudioStyle.ink else StudioStyle.soft, shape)
            .appClickable(enabled = enabled, rippleColor = ink, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = ink, strokeWidth = 2.dp)
            else Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
            Text(label, color = ink, fontSize = 12.sp, lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

// 仅用于柔和装饰层，模糊半径按相同比例缩小；布局与交互范围不变，清晰媒体应放在此层外。
internal fun Modifier.reducedResolutionLayer(divisor: Int = 2): Modifier = layout { measurable, constraints ->
    check(divisor > 0)
    val placeable = measurable.measure(constraints.copy(
        minWidth = constraints.minWidth / divisor, maxWidth = constraints.maxWidth / divisor,
        minHeight = constraints.minHeight / divisor, maxHeight = constraints.maxHeight / divisor
    ))
    val width = constraints.constrainWidth(placeable.width * divisor)
    val height = constraints.constrainHeight(placeable.height * divisor)
    layout(width, height) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = width.toFloat() / placeable.width.coerceAtLeast(1)
            scaleY = height.toFloat() / placeable.height.coerceAtLeast(1)
        }
    }
}

// 静态柔和阴影用于区分不透明消息与动态背景。
internal fun Modifier.chatShadow(shape: Shape = StudioStyle.group): Modifier = shadow(
    elevation = 24.dp, shape = shape, clip = false,
    ambientColor = Color(0xFF566274).copy(alpha = .09f),
    spotColor = Color(0xFF566274).copy(alpha = .06f)
)

// 将圆角媒体和卡片合成到同一层，保持裁切边缘平滑。
internal fun Modifier.smoothClip(shape: Shape): Modifier = graphicsLayer {
    this.shape = shape
    clip = true
}

@Composable
internal fun ChatAction(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    val primaryActive = primary && enabled
    val ink = when {
        primaryActive -> Color.White
        enabled -> StudioStyle.ink
        else -> StudioStyle.muted.copy(alpha = .55f)
    }
    val surface = when {
        primaryActive -> StudioStyle.ink
        enabled -> Color.White
        else -> Color(0xFFF5F6F7)
    }
    Row(modifier.heightIn(min = 46.dp).clip(shape)
        .background(surface)
        .then(if (!primaryActive && enabled) Modifier.border(1.dp, Color(0xFFE2E5E9), shape) else Modifier)
        .appClickable(enabled = enabled, rippleColor = if (primaryActive) Color.White else ink, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = ink, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}
