package com.example.videoparser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 集中定义页面与共享控件使用的颜色、圆角等样式。 */
internal object StudioStyle {
    val canvas = Color(0xFFF5F6F8)
    val ink = Color(0xFF202124)
    val muted = Color(0xFF747A84)
    val soft = Color(0xFFF0F2F5)
    val hairline = Color(0xFFE7E9ED)
    val accentSoft = Color(0xFFEAF2FF)
    val accent = Color(0xFF246BCE)
    val userBubble = Color(0xFF343539)
    val userInk = Color(0xFFF5F5F6)
    val response = Color.White
    val bubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)
    val group = RoundedCornerShape(20.dp)
    val control = RoundedCornerShape(13.dp)
    val pageSurface = RoundedCornerShape(22.dp)
}

// Shared insets and width limit keep every utility page usable on landscape/tablets.
@Composable
internal fun StudioPage(title: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit = {},
    navigationSurface: Color = StudioStyle.canvas,
    content: @Composable ColumnScope.() -> Unit) {
    PageSystemBars(StudioStyle.canvas, navigationSurface)
    Box(Modifier.fillMaxSize().background(StudioStyle.canvas),
        contentAlignment = Alignment.TopCenter) {
        // Paint behind the gesture/three-button bar before applying content insets.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(navigationSurface))
        Column(Modifier.safeDrawingPadding().imePadding().widthIn(max = 720.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(onClose, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = StudioStyle.ink, modifier = Modifier.size(20.dp))
                }
                Text(title, color = StudioStyle.ink, fontSize = 17.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 9.dp))
                actions()
            }
            content()
        }
    }
}

@Composable
internal fun StudioSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = StudioStyle.muted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.fillMaxWidth()
            .shadow(10.dp, StudioStyle.group, clip = false,
                ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .055f))
            .clip(StudioStyle.group).background(Color.White), content = content)
    }
}

@Composable
internal fun StudioDivider(startInset: Dp = 62.dp) {
    Box(Modifier.fillMaxWidth().padding(start = startInset, end = 16.dp).height(.7.dp).background(StudioStyle.hairline))
}

// Secondary explanation under a section card; wraps naturally and never competes with rows.
@Composable
internal fun StudioFootnote(text: String) {
    Text(text, color = StudioStyle.muted, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 6.dp))
}


@Composable
internal fun StudioNavigation(icon: ImageVector, title: String, detail: String, selected: Boolean = false,
    selectable: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val iconInk = if (selected) Color.White else StudioStyle.ink
    Row(Modifier.fillMaxWidth()
        // Settings entries use the whole rectangular row. Only inset selection tiles
        // have their own rounded boundary; the section clips its outer corners.
        .then(if (selectable) Modifier.padding(horizontal = 5.dp, vertical = 4.dp).clip(StudioStyle.control) else Modifier)
        .background(if (selected) Color(0xFFF0F1F3) else Color.Transparent, StudioStyle.control)
        .semantics { if (selectable) this.selected = selected }
        .appClickable(rippleColor = StudioStyle.muted, onClick = onClick)
        .padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(StudioStyle.control).background(if (selected) StudioStyle.ink else StudioStyle.soft),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconInk, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = StudioStyle.ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = StudioStyle.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        when {
            trailing != null -> trailing()
            else -> Icon(if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = if (selected) StudioStyle.ink else StudioStyle.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ModeDrawer(imageMode: Boolean, onSelect: (Boolean) -> Unit, onConfigure: () -> Unit,
    onSettings: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 336.dp).fillMaxWidth(.84f).fillMaxHeight()
        .shadow(12.dp, RectangleShape, clip = false,
            ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .12f))
        .background(Color(0xFFFBFBFC))
        .pointerInput(Unit) { detectTapGestures { /* Keep blank drawer space modal. */ } }
        .safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "软件图标",
                modifier = Modifier.size(36.dp)
            )
            Text("清析", color = StudioStyle.ink, fontSize = 18.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            AppIconButton(onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, "关闭侧滑栏", tint = StudioStyle.muted, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            DrawerSectionLabel("模式")
            DrawerItem(Icons.Default.PlayArrow, "视频解析", selected = !imageMode) { onSelect(false) }
            DrawerItem(Icons.Default.Image, "对话生图", selected = imageMode) { onSelect(true) }

            Spacer(Modifier.height(24.dp))

            DrawerSectionLabel("设置")
            DrawerItem(Icons.Default.Tune, "图片生成设置", showChevron = true, onClick = onConfigure)
            DrawerItem(Icons.Default.Settings, "应用设置", showChevron = true, onClick = onSettings)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("清析", color = StudioStyle.ink, fontSize = 12.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold)
            Text("UI 交互学习项目", color = StudioStyle.muted, fontSize = 10.sp, lineHeight = 15.sp)
            Text("第三方接口  夜雨 API · BugPk API", color = StudioStyle.muted, fontSize = 10.sp, lineHeight = 15.sp)
            Text("代码许可 Apache 2.0", color = StudioStyle.muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun DrawerSectionLabel(title: String) {
    Text(title, fontSize = 11.sp, lineHeight = 16.sp, color = StudioStyle.muted,
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, bottom = 7.dp))
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    selected: Boolean = false,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    val surface by animateColorAsState(
        if (selected) Color(0xFFEDEEF1) else Color.Transparent,
        label = "drawer-item-surface"
    )
    Row(Modifier.fillMaxWidth().height(56.dp)
        .background(surface)
        .semantics { if (!showChevron) this.selected = selected }
        .appClickable(rippleColor = StudioStyle.muted, onClick = onClick)
        .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) StudioStyle.ink else StudioStyle.muted, modifier = Modifier.size(20.dp))
        Text(title, color = StudioStyle.ink, fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 13.dp))
        when {
            selected -> Icon(Icons.Default.Check, "当前模式", tint = StudioStyle.ink, modifier = Modifier.size(18.dp))
            showChevron -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = StudioStyle.muted.copy(alpha = .72f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun SettingsPage(
    onClose: () -> Unit,
    onHistory: () -> Unit,
    onImageSettings: () -> Unit,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    StudioPage("应用设置", onClose) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StudioSection("接口") {
                ParseProviderRow(selectedProvider, onProviderSelected)
                StudioDivider()
                StudioNavigation(Icons.Default.Image, "生图接口", "添加服务、选择模型与切换配置") { onImageSettings() }
            }
            Column(Modifier.fillMaxWidth().clip(StudioStyle.group).background(Color.White).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.VolunteerActivism, null, tint = StudioStyle.accent, modifier = Modifier.size(21.dp))
                    Text("第三方接口说明", color = StudioStyle.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("夜雨 API · BugPk API", color = StudioStyle.ink, fontSize = 13.sp)
                Text("视频解析由第三方服务提供，收费、配额、可用性与数据处理规则以服务商条款为准。自动路由会在某个接口异常时尝试其他接口。",
                    color = StudioStyle.muted, fontSize = 12.sp, lineHeight = 19.sp)
                Text("代码采用 Apache 2.0 许可，允许合规商业使用。接口使用与媒体内容授权需另行遵守服务商条款、平台规则及版权要求。",
                    color = StudioStyle.muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
            StudioSection("记录") {
                StudioNavigation(Icons.Default.History, "解析历史", "查看已保存的视频与记录") { onHistory() }
            }
            StudioSection("关于") {
                StudioNavigation(Icons.Default.Info, "关于清析", "版本 ${BuildConfig.VERSION_NAME}") { }
            }
            StudioFootnote("清析 ${BuildConfig.VERSION_NAME} · 学习交流 · 代码许可 Apache License 2.0")
        }
    }
}

private val ParseProviderOptions = listOf(
    "自动路由" to Icons.Default.AutoAwesome,
    "夜雨 API" to Icons.Default.CloudQueue,
    "BugPk API" to Icons.Default.CloudQueue,
    "BugPk 聚合 API" to Icons.Default.Public
)

@Composable
private fun ParseProviderRow(selectedProvider: String, onProviderSelected: (String) -> Unit) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val indicatorRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = .2f),
        label = "provider-indicator-rotation"
    )
    Box(Modifier.fillMaxWidth()) {
        StudioNavigation(Icons.Default.Tune, "解析接口", "$selectedProvider · 支持抖音、快手、小红书",
            trailing = {
                Icon(
                    Icons.Default.ExpandMore,
                    if (menuExpanded) "收起接口菜单" else "展开接口菜单",
                    tint = StudioStyle.muted,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = indicatorRotation }
                )
            }) { menuExpanded = !menuExpanded }
        SecondaryMenuPopup(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            width = 184.dp,
            alignToEnd = true
        ) {
            ParseProviderOptions.forEachIndexed { index, (label, icon) ->
                if (index > 0) SecondaryMenuDivider()
                SecondaryMenuItem(
                    label = label,
                    icon = icon,
                    selected = selectedProvider == label,
                    onClick = { onProviderSelected(label); menuExpanded = false }
                )
            }
        }
    }
}
