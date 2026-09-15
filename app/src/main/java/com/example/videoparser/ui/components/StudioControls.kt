package com.example.videoparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StudioAction(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(StudioStyle.control)
        .background(if (primary) StudioStyle.ink.copy(alpha = if (enabled) 1f else .35f) else StudioStyle.soft, StudioStyle.control)
        .appClickable(enabled = enabled, rippleColor = if (primary) Color.White else StudioStyle.muted, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = (if (primary) Color.White else StudioStyle.ink).copy(alpha = if (enabled) 1f else .4f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StudioSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
        .padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = StudioStyle.muted, modifier = Modifier.size(20.dp))
        BasicTextField(value, onValueChange, singleLine = true,
            textStyle = TextStyle(color = StudioStyle.ink, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(StudioStyle.ink),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 13.dp),
            decorationBox = { inner -> Box {
                if (value.isEmpty()) Text(placeholder, color = StudioStyle.muted, fontSize = 14.sp, lineHeight = 20.sp)
                inner()
            } })
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (value.isNotEmpty()) AppIconButton({ onValueChange("") }, Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Close, "清除搜索", tint = StudioStyle.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun StudioConfirmDialog(title: String, message: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, dismissLabel: String = "取消") {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp),
        containerColor = Color.White, tonalElevation = 0.dp,
        title = { Text(title, color = StudioStyle.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, color = StudioStyle.muted, fontSize = 14.sp, lineHeight = 22.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color(0xFFBD3939)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = StudioStyle.ink) } })
}
