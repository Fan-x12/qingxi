package com.example.videoparser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File

internal fun imageModel(source: String): Any = if (File(source).isAbsolute) File(source) else source

@Composable
internal fun ImageSaveAction(source: String, viewModel: MainViewModel, modifier: Modifier = Modifier, dark: Boolean = false,
    compact: Boolean = false, compactSize: androidx.compose.ui.unit.Dp = 48.dp, grouped: Boolean = false,
    idleLabel: String = if (dark) "保存原图" else "保存图片") {
    val context = LocalContext.current
    val saving by viewModel.imageSaving.collectAsStateWithLifecycle()
    val message by viewModel.imageSaveMessage.collectAsStateWithLifecycle()
    var denied by rememberSaveable(source) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) viewModel.saveGeneratedImage(source)
    }
    val text = if (denied) "需要存储权限才能保存到相册"
        else message?.takeIf { it.first == source }?.second
    var showFeedback by remember(source) { mutableStateOf(false) }
    LaunchedEffect(text, saving) {
        showFeedback = text != null && saving != source
        if (showFeedback) {
            kotlinx.coroutines.delay(if (text == "已保存到相册") 2400 else 4500)
            showFeedback = false
        }
    }
    val phase = when {
        saving == source -> ImageSavePhase.Saving
        showFeedback && text == "已保存到相册" -> ImageSavePhase.Saved
        showFeedback && text != null -> ImageSavePhase.Failed
        else -> ImageSavePhase.Idle
    }
    ImageSaveControl(phase, enabled = saving == null, compact = compact, dark = dark,
        grouped = grouped, feedback = text?.takeIf { showFeedback }, idleLabel = idleLabel,
        modifier = modifier.then(if (compact) Modifier.size(compactSize) else Modifier)) {
        denied = false
        showFeedback = false
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else viewModel.saveGeneratedImage(source)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageViewer(source: String, viewModel: MainViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersivePlayerWindow()
        var scale by remember(source) { mutableFloatStateOf(1f) }
        var offset by remember(source) { mutableStateOf(Offset.Zero) }
        var viewport by remember { mutableStateOf(IntSize.Zero) }
        var ratio by remember(source) { mutableFloatStateOf(1f) }
        var loaded by remember(source) { mutableStateOf(false) }
        var failed by remember(source) { mutableStateOf(false) }
        var retry by remember(source) { mutableIntStateOf(0) }
        fun clamp(value: Offset, zoom: Float): Offset {
            val fittedWidth = minOf(viewport.width.toFloat(), viewport.height * ratio)
            val fittedHeight = if (ratio > 0) fittedWidth / ratio else 0f
            val x = ((fittedWidth * zoom - viewport.width) / 2).coerceAtLeast(0f)
            val y = ((fittedHeight * zoom - viewport.height) / 2).coerceAtLeast(0f)
            return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
        }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = clamp(offset + pan, scale)
        }
        MediaViewerLayout("图片", onClose, actions = {
            ViewerIcon(Icons.Default.Refresh, "还原图片") { scale = 1f; offset = Offset.Zero }
        }, footer = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${(scale * 100).toInt()}%", color = Color.White, fontSize = 16.sp)
                    Text("双指缩放 · 双击还原", color = ViewerMuted, fontSize = 12.sp)
                }
                ImageSaveAction(source, viewModel, Modifier.widthIn(max = 180.dp), dark = true)
            }
        }) {
            Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged {
                viewport = it; offset = clamp(offset, scale)
            }.transformable(transform, enabled = loaded)
                .pointerInput(source, loaded) { detectTapGestures(onDoubleTap = {
                    if (loaded) { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }
                }) }, contentAlignment = Alignment.Center) {
                key(source, retry) {
                    AsyncImage(imageModel(source), "生成图片，可双指缩放", contentScale = ContentScale.Fit,
                        onSuccess = { loaded = true; failed = false
                            val image = it.result.image
                            if (image.height > 0) ratio = image.width.toFloat() / image.height
                        }, onError = { failed = true; loaded = false },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        })
                }
                if (failed) StudioAction("图片加载失败，点击重试") { failed = false; retry++ }
                else if (!loaded) Text("正在加载图片…", color = Color.LightGray, fontSize = 13.sp)
            }

        }
    }
}
