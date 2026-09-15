package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ViewerCanvas = Color(0xFF090B10)
internal val ViewerPanel = Color(0xFF191D25)
internal val ViewerMuted = Color(0xFF9EA8B8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaViewerLayout(title: String, onClose: () -> Unit, tapToHide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}, footer: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The media owns the entire window. Insets apply to controls only.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
        if (tapToHide) Box(Modifier.fillMaxSize().appClickable(feedback = false) { controlsVisible = !controlsVisible })
        AnimatedVisibility(controlsVisible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBarsIgnoringVisibility).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                actions()
                ViewerIcon(Icons.Default.Close, "关闭预览", onClose)
            }
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f))))
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBarsIgnoringVisibility).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 8.dp)) { footer() }
        }
    }
}

@Composable
internal fun ViewerIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape)
        .appClickable(rippleColor = Color.White, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color(0xFFE8ECF3), modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun ViewerAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 54.dp).clip(StudioStyle.control)
        .appClickable(enabled = enabled, rippleColor = Color.White, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        val color = Color.White.copy(alpha = if (enabled) 1f else .4f)
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
