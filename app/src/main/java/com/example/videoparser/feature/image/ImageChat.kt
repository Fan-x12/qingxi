package com.example.videoparser

import androidx.compose.foundation.layout.heightIn

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class ImageTurn(
    val id: String,
    val prompt: String,
    val images: List<String> = emptyList(),
    val status: String = "generating",
    val message: String = ""
)

internal class ImageChatStore(context: Context) {
    private val file = File(context.filesDir, "image-chat.json")

    fun load(): List<ImageTurn> = runCatching {
        Json.decodeFromString<List<ImageTurn>>(file.readText()).map {
            if (it.status == "generating") it.copy(status = "cancelled", message = "上次生成已中断") else it
        }
    }.getOrDefault(emptyList())

    fun save(turns: List<ImageTurn>) {
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(Json.encodeToString(turns).toByteArray())
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}

@Composable
internal fun ImageChatTimeline(
    turns: List<ImageTurn>,
    top: Dp,
    bottom: Dp,
    viewModel: MainViewModel,
    onConfigure: () -> Unit,
    onRetry: (String) -> Unit,
    onDraft: (String) -> Unit
) {
    var selectedImage by rememberSaveable { mutableStateOf<String?>(null) }
    selectedImage?.let { ImageViewer(it, viewModel) { selectedImage = null } }

    val initialIds = remember { turns.map { it.id }.toSet() }
    // Newest turn first with reverseLayout: the latest response stays pinned to the bottom while it
    // grows from waiting dots into images, and while the keyboard or composer changes height.
    val listState = rememberLazyListState()
    val bottomControl = rememberChatBottomControl(listState)
    KeepLatestResponseReadable(listState, bottomControl)
    val latestId = turns.lastOrNull()?.id
    var anchoredId by rememberSaveable { mutableStateOf(latestId) }
    if (latestId != anchoredId) {
        // Sending a prompt lands the new turn at the bottom in the same measure pass; later status
        // changes only grow the pinned item, so a reader further up is never pulled down.
        SideEffect { anchoredId = latestId; bottomControl.reset(); listState.requestScrollToItem(0) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(bottomControl.connection),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 8.dp, bottom = bottom + 12.dp),
            verticalArrangement = if (turns.isEmpty()) Arrangement.Top else Arrangement.Bottom
        ) {
            items(turns.asReversed(), key = { it.id }) { turn ->
                val fresh = turn.id !in initialIds
                Column {
                    MessageArrival(fresh) {
                        Box(Modifier.fillMaxWidth().padding(top = 22.dp, start = 24.dp), contentAlignment = Alignment.CenterEnd) {
                            UserMessageBubble(turn.prompt)
                        }
                    }
                    ResponseEntrance(turn, animate = fresh, key = { it.status }) { current ->
                        Box(Modifier.padding(top = 12.dp)) {
                            when (current.status) {
                                "generating" -> ChatWaitingStatus("正在创建图片", palette())
                                "success" -> ImageResultCard(current, viewModel, onOpen = { selectedImage = it })
                                else -> ImageGenerationNotice(current, onRetry = { onRetry(current.prompt) }, onConfigure = onConfigure)
                            }
                        }
                    }
                }
            }
            item(key = "image-welcome") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("把想象变成画面", color = StudioStyle.ink, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("描述主体、场景、光线和风格，生成结果会直接出现在对话中。", color = StudioStyle.muted, fontSize = 13.sp, lineHeight = 20.sp)
                    ImageInterfaceAction(onConfigure)
                    if (turns.isEmpty()) {
                        Text("从一个想法开始", color = StudioStyle.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 13.dp))
                        Column(Modifier.fillMaxWidth().clip(StudioStyle.group).background(StudioStyle.response)) {
                            listOf(
                                "电影感照片" to "雨后的城市街道，暖色橱窗与湿润路面的倒影，电影感摄影，柔和自然光。",
                                "活动海报" to "设计一张春日咖啡市集海报，奶油白背景，手绘咖啡杯与花朵，简洁排版。",
                                "可爱角色" to "一只戴着蓝色围巾的小狐狸，温柔治愈的绘本风格，柔软笔触，干净背景。"
                            ).forEach { (title, prompt) ->
                                Row(Modifier.fillMaxWidth().appClickable { onDraft(prompt) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(title, color = StudioStyle.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.AutoAwesome, null, tint = StudioStyle.muted, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                        Text("点击示例填入输入框，修改后再发送", color = StudioStyle.muted, fontSize = 11.sp)
                    }
                }
            }
        }
        ChatBottomButton(bottomControl, listState, bottom)
    }
}

@Composable
private fun ImageInterfaceAction(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(Color.White.copy(alpha = .88f), shape)
            .border(1.dp, Color(0xFFE2E5E9), shape)
            .appClickable(rippleColor = StudioStyle.muted, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Tune, null, tint = StudioStyle.ink, modifier = Modifier.size(16.dp))
        Text("图片接口", color = StudioStyle.ink, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun ImageGenerationNotice(turn: ImageTurn, onRetry: () -> Unit, onConfigure: () -> Unit) {
    val cancelled = turn.status == "cancelled"
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().chatShadow(StudioStyle.group).clip(StudioStyle.group).background(StudioStyle.response).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(38.dp).background(Color(0xFFF0F1F2), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, null, tint = StudioStyle.ink, modifier = Modifier.size(19.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (cancelled) "生成已停止" else "这次没有生成图片", color = StudioStyle.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("结果不会丢失，你可以从当前描述重新开始。", color = StudioStyle.muted, fontSize = 11.sp)
            }
        }
        Text(turn.message.ifBlank { if (cancelled) "描述已保留，可以随时重新生成。" else "请稍后重试，或检查图片接口配置。" }, color = StudioStyle.muted, fontSize = 13.sp, lineHeight = 21.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatAction("重新生成", Icons.Default.Refresh, Modifier.weight(1f), primary = true, onClick = onRetry)
            if (!cancelled) ChatAction("接口设置", Icons.Default.Settings, Modifier.weight(1f), onClick = onConfigure)
        }
    }
}

@Composable
private fun ImageResultCard(turn: ImageTurn, viewModel: MainViewModel, onOpen: (String) -> Unit) {
    val ratios = rememberSaveable(turn.id, saver = androidx.compose.runtime.saveable.mapSaver(
        save = { it.toMap() },
        restore = { values -> mutableStateMapOf<String, Float>().apply {
            values.forEach { (key, value) -> put(key, value as Float) }
        } }
    )) { mutableStateMapOf<String, Float>() }
    val count = turn.images.size
    if (count == 0) return
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = StudioStyle.ink, modifier = Modifier.size(17.dp))
            Text("生成完成", style = ChatTypography.title, color = StudioStyle.ink, modifier = Modifier.weight(1f))
            Text("$count 张作品", style = ChatTypography.detail, color = StudioStyle.muted)
        }
        if (count == 1) {
            val source = turn.images.first()
            // The tile starts square and settles on the decoded ratio; the pinned list grows upward with it.
            val ratio by animateFloatAsState(ratios[source] ?: 1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), label = "image-ratio")
            ImageResultTile(source, 0, count, Modifier.fillMaxWidth(), viewModel,
                { onOpen(source) }, { ratios[source] = it }, ratio = ratio)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                turn.images.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { columnIndex, source ->
                            val index = rowIndex * 2 + columnIndex
                            ImageResultTile(source, index, count, Modifier.weight(1f), viewModel,
                                { onOpen(source) }, { ratios[source] = it }, compact = true, ratio = .8f)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageResultTile(
    source: String,
    index: Int,
    count: Int,
    modifier: Modifier,
    viewModel: MainViewModel,
    onOpen: () -> Unit,
    onRatio: (Float) -> Unit,
    compact: Boolean = false,
    ratio: Float = 1f
) {
    var loaded by rememberSaveable(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    var retry by remember(source) { mutableIntStateOf(0) }
    val opacity by animateFloatAsState(if (loaded) 1f else 0f, tween(260), label = "image-reveal")
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.resultSurface().padding(7.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).smoothClip(shape).background(Color(0xFFF0F1F3)).appClickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            key(source, retry) {
                AsyncImage(imageModel(source), "查看第 ${index + 1} 张生成图片", contentScale = if (compact) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity },
                    onSuccess = { state ->
                        loaded = true
                        failed = false
                        val image = state.result.image
                        if (image.width > 0 && image.height > 0) onRatio(image.width.toFloat() / image.height.toFloat())
                    }, onError = { failed = true; loaded = false })
            }
            if (!loaded && !failed) Box(Modifier.size(if (compact) 30.dp else 38.dp).background(Color.White.copy(alpha = .88f), CircleShape), contentAlignment = Alignment.Center) { Text("…", color = StudioStyle.muted, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold) }
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("图片暂时无法显示", color = StudioStyle.muted, fontSize = 11.sp)
                    ChatAction("重新加载", Icons.Default.Refresh) { failed = false; retry++ }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 7.dp, end = 3.dp, top = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (compact) "作品 ${(index + 1).toString().padStart(2, '0')}" else "生成作品",
                    style = if (compact) ChatTypography.detail else ChatTypography.title, color = StudioStyle.ink)
                if (!compact) Text("点击查看原图", style = ChatTypography.detail, color = StudioStyle.muted)
            }
            if (loaded) ImageSaveAction(source, viewModel,
                if (compact) Modifier.size(48.dp)
                else Modifier.width(104.dp),
                compact = compact, compactSize = 48.dp)
        }
    }
}
