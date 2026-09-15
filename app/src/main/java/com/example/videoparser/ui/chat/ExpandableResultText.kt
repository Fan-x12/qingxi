package com.example.videoparser

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
internal fun ExpandableResultText(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = androidx.compose.material3.LocalTextStyle.current.merge(ChatTypography.body)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measured = measurer.measure(AnnotatedString(text), style = textStyle,
            constraints = Constraints(maxWidth = with(density) { maxWidth.roundToPx() }))
        val overflowing = measured.lineCount > 3
        val fullHeight = with(density) { measured.size.height.toDp() }
        val collapsedHeight = with(density) {
            if (overflowing) measured.getLineBottom(2).toDp() else fullHeight
        }
        val height by animateDpAsState(if (expanded) fullHeight else collapsedHeight,
            spring(dampingRatio = 1f, stiffness = 900f), label = "result-text-height")
        val rotation by animateFloatAsState(if (expanded) 180f else 0f,
            spring(dampingRatio = 1f, stiffness = 900f), label = "result-text-chevron")
        Column {
            // 收起期间仍布局完整文字，仅改变外部可见范围。
            Box(Modifier.fillMaxWidth().height(height).clipToBounds().testTag("result-text-viewport")) {
                Text(text, style = textStyle, color = StudioStyle.ink,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true))
            }
            if (overflowing) Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.widthIn(min = 104.dp).heightIn(min = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .appClickable(rippleColor = StudioStyle.ink) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (expanded) "收起" else "展开全文", style = ChatTypography.detail, color = StudioStyle.muted)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = StudioStyle.muted,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation })
            }
            }
        }
    }
}
