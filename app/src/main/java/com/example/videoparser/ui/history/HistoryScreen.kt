package com.example.videoparser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.videoparser.data.history.HistoryEntry
import com.example.videoparser.data.history.HistoryStatus
import com.example.videoparser.domain.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryScreen(history: List<HistoryEntry>, colors: AppPalette, onClose: () -> Unit,
    onOpen: (HistoryEntry) -> Unit, onDelete: (Long) -> Unit, onClear: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pending by remember { mutableStateOf(setOf<Long>()) }
    var clearAll by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val filtered = remember(history, query) {
        val term = query.trim()
        history.filter { entry -> listOfNotNull(entry.title, entry.authorName, entry.sourceUrl, entry.platform.label)
            .any { it.contains(term, ignoreCase = true) } }.sortedByDescending { it.createdAt }
    }
    val groups = remember(filtered) {
        val date = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
        filtered.groupBy { date.format(Date(it.createdAt)) }
    }
    LaunchedEffect(history) {
        selected = selected.intersect(history.map { it.id }.toSet())
        if (history.isEmpty()) selecting = false
    }
    fun finishSelection() { selecting = false; selected = emptySet() }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    BackHandler(selecting && pending.isEmpty() && !clearAll) { finishSelection() }
    StudioPage(if (selecting) "已选 ${selected.size} 项" else "解析历史",
        navigationSurface = if (selecting) Color.White else StudioStyle.canvas,
        onClose = { if (selecting) finishSelection() else onClose() }, actions = {
            if (history.isNotEmpty()) {
                TextButton(onClick = { if (selecting) finishSelection() else selecting = true }) {
                    Text(if (selecting) "完成" else "选择", color = StudioStyle.ink)
                }
                if (!selecting) Box {
                    AppIconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreHoriz, "历史记录选项", tint = StudioStyle.ink, modifier = Modifier.size(20.dp))
                    }
                    SecondaryMenuPopup(menuOpen, { menuOpen = false }, alignToEnd = true) {
                        SecondaryMenuItem("清空历史", Icons.Outlined.DeleteOutline) { menuOpen = false; clearAll = true }
                    }
                }
            }
        }) {
        StudioSearchField(query, onValueChange = { query = it }, placeholder = "搜索标题、作者或链接",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        Text(if (query.isBlank()) "共 ${history.size} 条记录" else "找到 ${filtered.size} 条记录",
            color = StudioStyle.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        if (filtered.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (history.isEmpty()) Icons.Default.History else Icons.Default.SearchOff, null,
                    tint = StudioStyle.muted, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(14.dp))
                Text(if (history.isEmpty()) "暂无解析记录" else "没有匹配的记录", color = StudioStyle.ink, fontSize = 16.sp)
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            groups.forEach { (date, entries) ->
                item(key = "date-$date", contentType = "date") {
                    Text(date, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = StudioStyle.muted,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp, start = 2.dp))
                }
                items(entries, key = { it.id }, contentType = { "history" }) { entry ->
                    HistoryEntryRow(entry, selecting, entry.id in selected, colors,
                        onClick = { if (selecting) toggle(entry.id) else onOpen(entry) },
                        onDelete = { pending = setOf(entry.id) }, modifier = Modifier.animateItem())
                }
            }
        }
        if (selecting) Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            val visibleIds = filtered.map { it.id }.toSet()
            val allSelected = visibleIds.isNotEmpty() && selected.containsAll(visibleIds)
            TextButton(enabled = visibleIds.isNotEmpty(), onClick = {
                selected = if (allSelected) selected - visibleIds else selected + visibleIds
            }) { Text(if (allSelected) "取消全选" else "全选当前列表", color = StudioStyle.ink) }
            TextButton(enabled = selected.isNotEmpty(), onClick = { pending = selected },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除 (${selected.size})")
            }
        }
    }
    if (pending.isNotEmpty() || clearAll) StudioConfirmDialog(
        title = if (clearAll) "清空解析历史？" else "删除 ${pending.size} 条记录？",
        message = "记录删除后无法恢复，已保存到相册的文件不会被删除。",
        confirmLabel = if (clearAll) "清空" else "删除",
        onConfirm = {
            if (clearAll) onClear() else pending.forEach(onDelete)
            pending = emptySet(); clearAll = false; finishSelection()
        },
        onDismiss = { pending = emptySet(); clearAll = false }
    )
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntry, selecting: Boolean, selected: Boolean, colors: AppPalette,
    onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val image = entry.mediaItems.firstOrNull()?.type == MediaType.IMAGE_SET
    val title = entry.title?.takeIf { it.isNotBlank() } ?: when (entry.status) {
        HistoryStatus.SUCCESS -> "${entry.platform.label}${if (image) "图文" else "视频"}"
        HistoryStatus.ERROR -> "解析失败"
        HistoryStatus.CANCELLED -> "已停止解析"
    }
    val time = remember(entry.createdAt) { SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(entry.createdAt)) }
    Row(modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(8.dp))
        .background(if (selected) Color(0xFFE9ECEF) else Color.White)
        .then(if (selecting) Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = "选择记录：$title" } else Modifier.appClickable(onClick = onClick))
        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(StudioStyle.soft), contentAlignment = Alignment.Center) {
            Icon(if (image) Icons.Default.Image else Icons.Default.PlayArrow, null, tint = StudioStyle.muted)
            entry.coverUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 20.sp, color = StudioStyle.ink,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(entry.authorName?.takeIf { it.isNotBlank() }, entry.platform.label, time).joinToString(" · "),
                fontSize = 11.sp, color = StudioStyle.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when (entry.status) { HistoryStatus.SUCCESS -> if (image) "图文 · ${entry.mediaItems.size} 张" else "视频 · 已完成"
                HistoryStatus.ERROR -> "解析失败"; HistoryStatus.CANCELLED -> "已停止" },
                fontSize = 11.sp, color = if (entry.status == HistoryStatus.ERROR) colors.error else StudioStyle.muted)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (selecting) Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null, tint = if (selected) StudioStyle.ink else Color(0xFFBDC2C9), modifier = Modifier.size(22.dp))
            else AppIconButton(onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除记录：$title",
                tint = StudioStyle.muted, modifier = Modifier.size(19.dp)) }
        }
    }
}
