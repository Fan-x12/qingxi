package com.example.videoparser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// Bounded platform ripple; callers clip to the actual button or row shape.
// No scale or opacity animation is stacked on the press feedback.
// Compose clickable keeps press cancellation on scroll, keyboard activation and semantics.
@Composable
internal fun Modifier.appClickable(
    enabled: Boolean = true,
    feedback: Boolean = true,
    rippleColor: Color = Color(0xFF555B65),
    onClick: () -> Unit
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    return this.drawWithContent {
        drawContent()
        if (enabled && focused && feedback) {
            drawRect(Color(0xFF74849A),
                style = Stroke(2.dp.toPx()))
        }
    }.clickable(enabled = enabled, role = Role.Button, interactionSource = interactions,
        indication = if (feedback) ripple(bounded = true, color = rippleColor) else null, onClick = onClick)
}

@Composable
internal fun AppIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, rippleColor: Color = StudioStyle.muted, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).appClickable(enabled = enabled, rippleColor = rippleColor, onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}
