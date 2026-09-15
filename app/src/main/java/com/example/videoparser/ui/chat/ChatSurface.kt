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

// Opaque, borderless surfaces avoid expensive diffuse shadows over the animated canvas.
internal fun Modifier.resultSurface(): Modifier =
    clip(RoundedCornerShape(22.dp)).background(StudioStyle.response)

@Composable
internal fun UserMessageBubble(text: String, isLink: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Box(Modifier.widthIn(max = 480.dp)
        .background(StudioStyle.userBubble, StudioStyle.bubbleShape)
        .padding(horizontal = 14.dp, vertical = 11.dp)) {
        // Preserve the source text for selection/copy. Simple wrapping does not rebalance
        // short messages into heading-like lines or add hyphens to URL tokens.
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

// Compact visual surface within a full-height touch target, shared by media save actions.
@Composable
internal fun ChatSaveAction(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, grouped: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50.dp)
    val ink = if ((enabled || busy) && !grouped) Color.White else if (enabled || busy) StudioStyle.ink else StudioStyle.muted
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        // The clickable's automatic minimum touch target reaches into the outer padding;
        // its indication is clipped to the actual painted button, not that padding.
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

// Only for soft decorative layers: the caller scales its blur radius by the same divisor.
// Layout and interaction bounds stay unchanged; sharp media must remain outside this layer.
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

// A static, diffuse shadow separates opaque messages from the moving background.
internal fun Modifier.chatShadow(shape: Shape = StudioStyle.group): Modifier = shadow(
    elevation = 24.dp, shape = shape, clip = false,
    ambientColor = Color(0xFF566274).copy(alpha = .09f),
    spotColor = Color(0xFF566274).copy(alpha = .06f)
)

// Force rounded media and cards onto one composited layer so their clipped edge stays smooth.
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
