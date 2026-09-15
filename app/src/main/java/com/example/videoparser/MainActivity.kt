package com.example.videoparser

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.filled.Fullscreen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.videoparser.data.history.HistoryEntry
import com.example.videoparser.data.history.HistoryStatus
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.ParseState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.videoparser.data.provider.ProviderRegistry.displayName as providerDisplayName
import kotlin.math.cos
import kotlin.math.sin

private val BubbleShape = RoundedCornerShape(16.dp)
private val InputShape = RoundedCornerShape(32.dp)
private val HeaderShape = RoundedCornerShape(24.dp)

// Static outer shadows stay on their own render layers. Press feedback never scales them.
private fun Modifier.cardShadow(shape: Shape): Modifier = this
    .shadow(28.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .11f))

private fun Modifier.floatingShadow(shape: Shape, elevation: androidx.compose.ui.unit.Dp = 28.dp): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = .20f), spotColor = Color.Black.copy(alpha = .16f))

/** 应用入口：接收系统分享，并连接主界面与页面状态。 */
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var imageOpenRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Match the initial light canvas before Compose has drawn its first frame.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK)
        )
        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent.getBooleanExtra("open_image_chat", false)) imageOpenRequest++
        setContent { ParserChatApp(viewModel, sharedText, imageOpenRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_image_chat", false)) imageOpenRequest++
    }
}

internal data class AppPalette(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentSoft: Color,
    val assistantBubble: Color,
    val error: Color,
    val errorSoft: Color
)

@Composable
internal fun palette() = remember {
    AppPalette(
        background = StudioStyle.canvas,
        surface = Color.White,
        surfaceMuted = StudioStyle.soft,
        text = StudioStyle.ink,
        secondaryText = StudioStyle.muted,
        accent = Color.Black,
        accentSoft = Color(0xFFF0F0F0),
        assistantBubble = Color.White,
        error = Color(0xFF555555),
        errorSoft = Color.White
    )
}

@Composable
private fun ColorFogBackdrop(colors: AppPalette, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "background-fog")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(42000, easing = LinearEasing), RepeatMode.Restart),
        label = "background-drift"
    )
    val motionEnabled = android.animation.ValueAnimator.areAnimatorsEnabled()
    Box(modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(.62f)
                .graphicsLayer {
                    val angle = if (motionEnabled) phase.value else 0f
                    scaleX = 1.20f
                    scaleY = 1.20f
                    translationX = sin(angle) * 34.dp.toPx()
                    translationY = cos(angle) * 24.dp.toPx()
                    rotationZ = sin(angle * .5f) * 1.4f
                }
                .reducedResolutionLayer()
                .blur(23.dp, BlurredEdgeTreatment.Unbounded)
                .drawWithCache {
                    val radius = size.maxDimension * .66f
                    val warm = Brush.radialGradient(
                        0f to Color(0xFFFF704D).copy(alpha = .28f),
                        .42f to Color(0xFFFFC04F).copy(alpha = .13f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .16f),
                        radius = radius
                    )
                    val cool = Brush.radialGradient(
                        0f to Color(0xFF547CFF).copy(alpha = .25f),
                        .46f to Color(0xFFA069E8).copy(alpha = .12f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .88f, size.height * .34f),
                        radius = radius * .90f
                    )
                    val rose = Brush.radialGradient(
                        0f to Color(0xFFFF6F9D).copy(alpha = .17f),
                        .48f to Color(0xFFFFA6B8).copy(alpha = .07f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .02f),
                        radius = radius * .72f
                    )
                    onDrawBehind {
                        drawRect(warm)
                        drawRect(cool)
                        drawRect(rose)
                    }
                }
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.60f)
                .graphicsLayer {
                    val angle = if (motionEnabled) phase.value else 0f
                    scaleX = 1.22f
                    scaleY = 1.22f
                    translationX = cos(angle) * 30.dp.toPx()
                    translationY = sin(angle) * 28.dp.toPx()
                    rotationZ = cos(angle * .5f) * -1.1f
                }
                .reducedResolutionLayer()
                .blur(25.dp, BlurredEdgeTreatment.Unbounded)
                .drawWithCache {
                    val radius = size.maxDimension * .68f
                    val fresh = Brush.radialGradient(
                        0f to Color(0xFF26C99A).copy(alpha = .23f),
                        .44f to Color(0xFF52D7DA).copy(alpha = .10f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .78f),
                        radius = radius
                    )
                    val violet = Brush.radialGradient(
                        0f to Color(0xFF8F6DE7).copy(alpha = .20f),
                        .48f to Color(0xFF668CFF).copy(alpha = .08f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .58f),
                        radius = radius * .92f
                    )
                    val coral = Brush.radialGradient(
                        0f to Color(0xFFFF8269).copy(alpha = .14f),
                        .50f to Color(0xFFFFC65D).copy(alpha = .06f),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(size.width * .56f, size.height * .96f),
                        radius = radius * .68f
                    )
                    onDrawBehind {
                        drawRect(fresh)
                        drawRect(violet)
                        drawRect(coral)
                    }
                }
        )
        content()
    }
}

@Composable
private fun ParserChatApp(viewModel: MainViewModel, sharedText: String?, imageOpenRequest: Int = 0) {
    PageSystemBars(StudioStyle.canvas)
    var input by rememberSaveable { mutableStateOf(sharedText.orEmpty()) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showImageSettings by rememberSaveable { mutableStateOf(false) }
    var showModes by rememberSaveable { mutableStateOf(false) }
    var imageMode by rememberSaveable { mutableStateOf(false) }
    var imageInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(imageOpenRequest) {
        if (imageOpenRequest > 0) {
            imageMode = true; showHistory = false; showSettings = false; showImageSettings = false; showModes = false
        }
    }
    val imageTurns by viewModel.imageTurns.collectAsStateWithLifecycle()
    val imageGenerating by viewModel.imageGenerating.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val activeUrl by viewModel.activeUrl.collectAsStateWithLifecycle()
    val scrollRequest by viewModel.scrollRequest.collectAsStateWithLifecycle()
    val activeTurnId by viewModel.activeTurnId.collectAsStateWithLifecycle()
    val openHistoryId by viewModel.openHistoryId.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadMessage by viewModel.downloadMessage.collectAsStateWithLifecycle()
    val colors = palette()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    fun generateImage(prompt: String) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.generateImage(prompt)
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var toast by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var composerSurfaceHeightPx by remember { mutableIntStateOf(0) }
    val topClearance = with(density) { if (headerHeightPx > 0) headerHeightPx.toDp() else 88.dp }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    // Follow the animated text viewport; the timeline must not start a second animation.
    val composerHeight = with(density) { if (composerSurfaceHeightPx > 0) composerSurfaceHeightPx.toDp() else ComposerCollapsedHeight }
    // The timeline is inset by the keyboard while the composer sits above max(navigation bar,
    // keyboard); only the part of the navigation bar not yet covered by the keyboard adds to the gap.
    val bottomClearance = composerHeight + 30.dp + (navigationBarHeight - imeHeight).coerceAtLeast(0.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val drawerWidth = minOf(336.dp, maxWidth * .84f)
    val drawerMotion = rememberDrawerMotion(showModes)
    val drawerVisible by remember { derivedStateOf { drawerMotion.dragging || drawerMotion.progress > .001f } }
    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = showModes || showImageSettings || showHistory || showSettings) {
        when {
            showModes -> showModes = false
            showImageSettings -> showImageSettings = false
            showHistory -> showHistory = false
            else -> showSettings = false
        }
    }

    LaunchedEffect(downloadMessage) {
        downloadMessage?.let { toast = it; delay(2400); toast = null }
    }

    val headerBackdrop = remember { HazeState() }
    Box(Modifier.fillMaxSize().drawerGestures(
        drawerMotion, with(density) { drawerWidth.toPx() }, with(density) { maxWidth.toPx() },
        enabled = !showSettings && !showImageSettings && !showHistory,
        onStart = ::dismissKeyboard, onSetOpen = { showModes = it }
    )) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = drawerWidth.toPx() * drawerMotion.progress
        }) {
        ColorFogBackdrop(colors, Modifier.haze(headerBackdrop)) {
            Box(Modifier.fillMaxSize().imePadding()) {
                if (imageMode) {
                    ImageChatTimeline(imageTurns, topClearance, bottomClearance, viewModel,
                        onConfigure = { dismissKeyboard(); showImageSettings = true }, onRetry = ::generateImage,
                        onDraft = { imageInput = it })
                } else ChatTimeline(history, activeUrl, state, scrollRequest, activeTurnId, openHistoryId, progress, viewModel, context, colors, Modifier.fillMaxSize(), topClearance, bottomClearance)
            }
        }
        ProgressiveHeaderBackdrop(headerBackdrop, topClearance + 8.dp, colors.background)

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            ProgressiveHeaderBackdrop(headerBackdrop, bottomClearance + 14.dp, colors.background,
                bottom = true, modifier = Modifier.align(Alignment.BottomCenter))
        }

        ChatHeader(
            colors = colors,
            title = if (imageMode) "对话生图" else "视频解析",
            onModes = { dismissKeyboard(); showModes = true },
            onSettings = { dismissKeyboard(); showSettings = true },
            onHistory = { dismissKeyboard(); showHistory = true },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { headerHeightPx = it.height }
        )

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Composer(
                value = if (imageMode) imageInput else input,
                placeholder = if (imageMode) "描述你想生成的画面…" else "发送一个分享链接…",
                isLoading = if (imageMode) imageGenerating else state is ParseState.Loading,
                colors = colors,
                onValueChange = { if (imageMode) imageInput = it else input = it },
                onPaste = { if (imageMode) imageInput = clipboardText(context) else input = clipboardText(context) },
                onCancel = { if (imageMode) viewModel.cancelImage() else viewModel.cancel() },
                onSurfaceHeightChanged = { composerSurfaceHeightPx = it },
                onSend = {
                    val sent = (if (imageMode) imageInput else input).trim()
                    if (sent.isNotBlank()) {
                        dismissKeyboard()
                        if (imageMode) { generateImage(sent); imageInput = "" }
                        else { viewModel.parse(sent); input = "" }
                    }
                }
            )
        }

        val retainedToast = remember { mutableStateOf("") }
        if (!toast.isNullOrBlank()) SideEffect { retainedToast.value = toast.orEmpty() }
        AnimatedVisibility(
            visible = toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 112.dp)
        ) {
            Text(retainedToast.value, color = colors.surface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(colors.text, RoundedCornerShape(18.dp)).padding(horizontal = 17.dp, vertical = 11.dp))
        }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(tween(140))
        ) {
            SettingsPage(
                onClose = { showSettings = false },
                onHistory = { showHistory = true },
                onImageSettings = { showImageSettings = true },
                selectedProvider = selectedProvider?.let(com.example.videoparser.data.provider.ProviderRegistry::displayName)
                    ?: "自动路由",
                onProviderSelected = { selected ->
                    val providerName = when (selected) {
                        "自动路由" -> null
                        "BugPk API" -> com.example.videoparser.data.provider.ProviderRegistry.BUGPK_NAME
                        "BugPk 聚合 API" -> com.example.videoparser.data.provider.ProviderRegistry.BUGPK_PLATFORM_NAME
                        else -> com.example.videoparser.data.provider.ProviderRegistry.YRAIN_NAME
                    }
                    viewModel.setSelectedProvider(providerName)
                }
            )
        }

        AnimatedVisibility(
            visible = showHistory,
            enter = slideInHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(tween(160))
        ) {
            HistoryScreen(
                history = history,
                colors = colors,
                onClose = { showHistory = false },
                onOpen = { viewModel.openHistory(it); imageMode = false; showHistory = false; showSettings = false },
                onDelete = viewModel::deleteHistory,
                onClear = viewModel::clearHistory
            )
        }
        AnimatedVisibility(visible = showImageSettings, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            ImageServiceSettings(onClose = { showImageSettings = false })
        }
        // Keep the drawer measured off-screen; opening should only move layers.
        Box(Modifier.fillMaxSize()) {
            if (showModes || drawerVisible) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = drawerMotion.progress }
                    .background(Color.Black.copy(alpha = .18f)).appClickable(feedback = false) { showModes = false })
            }
                ModeDrawer(
                    imageMode = imageMode,
                    onSelect = { imageMode = it; showModes = false },
                    onConfigure = { showModes = false; showImageSettings = true },
                    onSettings = { showModes = false; showSettings = true },
                    onClose = { showModes = false },
                    modifier = Modifier.width(drawerWidth)
                        .then(if (!showModes && !drawerVisible) Modifier.clearAndSetSemantics { } else Modifier)
                        .graphicsLayer {
                        translationX = -drawerWidth.toPx() * (1f - drawerMotion.progress)
                        alpha = if (drawerMotion.progress > 0f) 1f else 0f
                    }
                )
        }

}
}
}

@Composable
private fun ChatHeader(colors: AppPalette, title: String, onModes: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    val config = LocalConfiguration.current
    val compact = config.screenWidthDp > config.screenHeightDp
    Row(
        modifier.fillMaxWidth()
            .statusBarsPadding().height(if (compact) 62.dp else 64.dp)
            .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeaderAction(Icons.Default.Menu, "选择对话模式", colors, onModes)
        Box(Modifier.weight(1f)) {
            Box(
                Modifier.heightIn(min = 44.dp).floatingShadow(HeaderShape, 16.dp)
                    .background(Color.White, HeaderShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(title, color = colors.text, fontSize = 16.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HeaderAction(Icons.Default.Settings, "设置", colors, onSettings)
        HeaderAction(Icons.Default.History, "历史记录", colors, onHistory)
    }
}

private val TimelineSpacing = 22.dp

// What an assistant slot renders. Data-class equality keeps the response transition idle while
// only unrelated state (download progress, drafts) recomposes the timeline.
private sealed interface AssistantSlot {
    data class Live(val state: ParseState) : AssistantSlot
    data class Past(val entry: HistoryEntry) : AssistantSlot
    data object Stopped : AssistantSlot
}

// Live and persisted versions of the same outcome share a key, so finishing a parse and later
// starting another one never rebuilds the result card or its player state.
private fun AssistantSlot.phase(): String = when (this) {
    is AssistantSlot.Live -> when (state) {
        is ParseState.Loading -> "loading"
        is ParseState.Success -> "result"
        is ParseState.Error, ParseState.Idle -> "notice"
    }
    is AssistantSlot.Past -> when {
        entry.status != HistoryStatus.SUCCESS -> "notice"
        entry.toParseResult() != null -> "result"
        else -> "expired"
    }
    AssistantSlot.Stopped -> "notice"
}

@Composable
private fun ChatTimeline(history: List<HistoryEntry>, activeUrl: String?, state: ParseState, scrollRequest: Int, activeTurnId: Long?, openHistoryId: Long?, progress: Int?, viewModel: MainViewModel, context: Context, colors: AppPalette, modifier: Modifier, topClearance: Dp, bottomClearance: Dp) {
    // Newest turn first with reverseLayout: index 0 stays pinned to the bottom, so a response that
    // grows (waiting -> result, preview ratio, keyboard or composer height) extends upward instead
    // of sliding out under the composer. Cold starts and mode switches land at the bottom.
    val listState = rememberLazyListState()
    val bottomControl = rememberChatBottomControl(listState)
    KeepLatestResponseReadable(listState, bottomControl)
    // The same turn keeps its keys from loading through persistence and later requests.
    val turns = remember(history, activeTurnId, activeUrl) {
        history.asReversed().map { it.id to it.sourceUrl }.toMutableList().apply {
            if (activeTurnId != null && activeUrl != null && none { it.first == activeTurnId }) {
                add(activeTurnId to activeUrl)
            }
        }
    }
    // History already on screen is stable; only a newly submitted active turn should animate.
    // Keying the baseline to history prevents cold-start restoration from replaying every row.
    val initialTurnIds = remember(history) { history.map { it.id }.toSet() }
    val entriesById = remember(history) { history.associateBy { it.id } }
    var handledScrollRequest by rememberSaveable { mutableIntStateOf(scrollRequest) }
    if (scrollRequest != handledScrollRequest && openHistoryId == null) {
        // Applied in the same measure pass that adds the turn, so it is laid out at the bottom and
        // its entrance pushes older messages up instead of the list following a key afterwards.
        SideEffect { bottomControl.reset(); listState.requestScrollToItem(0) }
    }
    LaunchedEffect(scrollRequest) {
        if (scrollRequest == handledScrollRequest) return@LaunchedEffect
        handledScrollRequest = scrollRequest
        val historyId = openHistoryId ?: return@LaunchedEffect
        val position = turns.indexOfFirst { it.first == historyId }
        if (position >= 0) listState.revealFromTop((turns.size - 1 - position) * 2 + 1)
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(bottomControl.connection), state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(start = 20.dp, top = topClearance + 8.dp, end = 20.dp, bottom = bottomClearance + 12.dp),
            verticalArrangement = if (turns.isEmpty()) Arrangement.Top else Arrangement.Bottom
        ) {
            // Spacing lives inside each entrance so it grows with the message instead of appearing first.
            turns.asReversed().forEach { (id, url) ->
                item(key = "assistant-$id", contentType = "assistant") {
                    val slot = when {
                        id == activeTurnId && state !is ParseState.Idle -> AssistantSlot.Live(state)
                        else -> entriesById[id]?.let { AssistantSlot.Past(it) } ?: AssistantSlot.Stopped
                    }
                    ResponseEntrance(slot, animate = id !in initialTurnIds, key = AssistantSlot::phase) { current ->
                        Box(Modifier.padding(top = TimelineSpacing)) {
                            AssistantResponse(current, id, url, progress, viewModel, context, colors)
                        }
                    }
                }
                item(key = "user-$id", contentType = "user") {
                    MessageArrival(id !in initialTurnIds) {
                        Box(Modifier.padding(top = TimelineSpacing)) { UserLinkBubble(url) }
                    }
                }
            }
            item(key = "welcome", contentType = "welcome") {
                Box { WelcomeMessage(colors) }
            }
        }
        ChatBottomButton(bottomControl, listState, bottomClearance)
    }
}

// Reverse layout: offsets grow upward from the content start at the bottom. Put the opened turn's
// link at the top of the visible area with its result below, clamped at the newest message.
private suspend fun LazyListState.revealFromTop(index: Int) {
    scrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val visibleTop = info.viewportEndOffset - info.afterContentPadding
    scrollBy(-(visibleTop - item.size).toFloat().coerceAtLeast(0f))
}

@Composable
private fun AssistantResponse(slot: AssistantSlot, id: Long, url: String, progress: Int?, viewModel: MainViewModel, context: Context, colors: AppPalette) {
    when (slot) {
        is AssistantSlot.Live -> when (val current = slot.state) {
            ParseState.Idle -> StoppedBubble(colors)
            is ParseState.Loading -> ThinkingBubble(current, colors)
            is ParseState.Error -> ErrorBubble(current, { viewModel.parse(url, historyId = id) }, colors)
            is ParseState.Success -> ResultBubble(current.value, progress, viewModel, context, colors)
        }
        is AssistantSlot.Past -> PastConversationResult(slot.entry, progress, viewModel, context, colors)
        AssistantSlot.Stopped -> StoppedBubble(colors)
    }
}

@Composable
private fun PastConversationResult(entry: HistoryEntry, progress: Int?, viewModel: MainViewModel, context: Context, colors: AppPalette) {
    val result = entry.toParseResult()
    when {
        entry.status == HistoryStatus.SUCCESS && result != null -> ResultBubble(result, progress, viewModel, context, colors)
        entry.status == HistoryStatus.SUCCESS -> AssistantRow(colors) {
            Column(Modifier.fillMaxWidth().background(colors.surface, BubbleShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entry.coverUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(BubbleShape)) }
                Text(entry.title ?: "视频解析结果", color = colors.text, fontSize = 16.sp)
                Text("视频地址已过期，重新解析后即可播放和保存", color = colors.secondaryText, fontSize = 12.sp)
                InlineAction("重新解析", Icons.Default.Refresh, colors.accent, Color.White) { viewModel.parse(entry.sourceUrl, historyId = entry.id) }
            }
        }
        else -> ErrorBubble(
            ParseState.Error(entry.errorMessage ?: if (entry.status == HistoryStatus.CANCELLED) "已停止解析" else "解析失败"),
            { viewModel.parse(entry.sourceUrl, historyId = entry.id) }, colors,
            title = if (entry.status == HistoryStatus.CANCELLED) "已停止解析" else "这次没有解析成功"
        )
    }
}

@Composable
private fun StoppedBubble(colors: AppPalette) {
    AssistantRow(colors) {
        Text(
            "已停止解析。你可以继续发送新的链接。",
            color = colors.secondaryText,
            style = ChatTypography.body,
            modifier = Modifier.cardShadow(BubbleShape)
                .background(Color.White, BubbleShape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun WelcomeMessage(colors: AppPalette) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("粘贴链接，开始解析", color = colors.text, fontSize = 20.sp,
            lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("支持抖音公开链接。解析后可直接预览、保存视频或复制直链。",
            color = colors.secondaryText, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun UserLinkBubble(url: String) {
    Box(Modifier.fillMaxWidth().padding(start = 24.dp), contentAlignment = Alignment.CenterEnd) {
        UserMessageBubble(url, isLink = true, maxLines = 4)
    }
}

@Composable
internal fun ThinkingBubble(state: ParseState.Loading, colors: AppPalette) {
    val status = "已识别${state.platform.label}链接，正在获取视频信息…" +
        if (state.attempt > 1) "\n正在进行第 ${state.attempt} 次连接。" else ""
    ChatWaitingStatus("正在解析", colors, Modifier.testTag("parsing-bubble").semantics { stateDescription = status })
}

@Composable
private fun ErrorBubble(error: ParseState.Error, retry: () -> Unit, colors: AppPalette, title: String = "这次没有解析成功") {
    AssistantRow(colors) {
        Column(Modifier.cardShadow(BubbleShape).background(Color.White, BubbleShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color(0xFFAA4545), style = ChatTypography.title)
            Text(error.message, color = colors.text, style = ChatTypography.body)
            if (error.details.isNotEmpty()) {
                Column(Modifier.background(colors.surface.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("接口状态", color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    error.details.forEach { detail ->
                        Text(detail, color = colors.secondaryText, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            InlineAction("再试一次", Icons.Default.Refresh, colors.error, Color.White, retry)
        }
    }
}

@Composable
internal fun ResultBubble(result: ParseResult, progress: Int?, viewModel: MainViewModel, context: Context, colors: AppPalette) {
    if (result.items.isEmpty()) return
    val pager = androidx.compose.foundation.pager.rememberPagerState { result.items.size }
    val selected = result.items[pager.currentPage.coerceIn(result.items.indices)]
    val video = selected.takeIf { it.type != MediaType.IMAGE_SET }
    var openedImage by rememberSaveable { mutableStateOf<String?>(null) }
    openedImage?.let { ImageViewer(it, viewModel) { openedImage = null } }
    val ratios = rememberSaveable(saver = androidx.compose.runtime.saveable.mapSaver(
        save = { it.toMap() },
        restore = { values -> androidx.compose.runtime.mutableStateMapOf<String, Float>().apply {
            values.forEach { (key, value) -> put(key, value as Float) }
        } }
    )) { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    val downloadingUrl by viewModel.downloadingUrl.collectAsStateWithLifecycle()
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val url = pendingUrl; pendingUrl = null
        if (granted && url != null) viewModel.saveVideo(url, result.title)
    }
    val configuration = LocalConfiguration.current
    val mediaLabel = when (selected.type) {
        MediaType.VIDEO -> "视频"
        MediaType.IMAGE_SET -> "图文"
        MediaType.LIVE_PHOTO -> "实况"
    }
    val visibleSummary = listOfNotNull(
        result.platform.label.takeIf { it.isNotBlank() },
        mediaLabel,
        result.durationSeconds?.takeIf { it > 0 }?.let { playbackTime((it * 1000).toLong()) }
    ).distinct().joinToString(" · ")
    val workDetails = listOfNotNull(
        result.title?.takeIf { it.isNotBlank() }?.let { "标题：$it" },
        result.authorName?.takeIf { it.isNotBlank() }?.let { "作者：$it" },
        result.platform.label.takeIf { it.isNotBlank() }?.let { "平台：$it" },
        "类型：$mediaLabel",
        result.durationSeconds?.takeIf { it > 0 }?.let { "时长：${playbackTime((it * 1000).toLong())}" },
        result.sizeLabel?.takeIf { it.isNotBlank() }?.let { "大小：$it" },
        result.quality?.takeIf { it.isNotBlank() }?.let { "清晰度：$it" },
        result.publishTime?.takeIf { it.isNotBlank() }?.let { "发布时间：$it" },
        result.providerName?.takeIf { it.isNotBlank() }?.let { "解析服务：${providerDisplayName(it)}" }
    ).joinToString("\n")

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isImage = selected.type == MediaType.IMAGE_SET
        val attachmentWidth = minOf(maxWidth, 480.dp)
        val previewHeightLimit = if (configuration.screenWidthDp > configuration.screenHeightDp) {
            (configuration.screenHeightDp.dp * .55f).coerceIn(200.dp, 284.dp)
        } else {
            (configuration.screenHeightDp.dp * .70f).coerceIn(320.dp, 640.dp)
        }
        // Prefer decoded image dimensions; missing metadata must not force portrait images
        // into a landscape canvas. Videos retain stable playback geometry.
        val layoutRatio = if (isImage) {
            ratios[selected.url] ?: if ((selected.width ?: 0) > 0 && (selected.height ?: 0) > 0) {
                previewAspectRatio(selected.width, selected.height)
            } else 3f / 4f
        } else ratios[selected.url] ?: previewAspectRatio(selected.width, selected.height)
        val targetPreviewHeight = ((attachmentWidth - 16.dp) / layoutRatio.coerceAtLeast(.1f))
            .coerceAtMost(previewHeightLimit)
        val previewHeight by animateDpAsState(targetPreviewHeight,
            animationSpec = tween(180, easing = FastOutSlowInEasing), label = "attachment-height")
        Column(
            Modifier.width(attachmentWidth).resultSurface(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    result.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                        var avatarFailed by remember(result.authorAvatarUrl) { mutableStateOf(false) }
                        Box(Modifier.size(32.dp).smoothClip(CircleShape).background(StudioStyle.soft), contentAlignment = Alignment.Center) {
                            if (result.authorAvatarUrl != null && !avatarFailed) {
                                AsyncImage(imageModel(result.authorAvatarUrl), "$author 的头像", contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(), onError = { avatarFailed = true })
                            } else Icon(Icons.Default.Person, null, tint = StudioStyle.muted, modifier = Modifier.size(17.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(result.authorName?.takeIf { it.isNotBlank() } ?: result.platform.label,
                            color = StudioStyle.ink, fontSize = 13.sp, lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(visibleSummary, color = StudioStyle.muted, style = ChatTypography.detail,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            result.providerName?.takeIf { it.isNotBlank() }?.let { ProviderBadge(it) }
                        }
                    }
                    var menuOpen by remember(selected.url) { mutableStateOf(false) }
                    Box {
                        AppIconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreHoriz, "更多媒体操作", tint = StudioStyle.muted, modifier = Modifier.size(20.dp))
                        }
                        SecondaryMenuPopup(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            width = 184.dp,
                            alignToEnd = true
                        ) {
                            SecondaryMenuItem(
                                label = "复制媒体链接",
                                icon = Icons.Default.ContentCopy,
                                onClick = { copyText(context, selected.url); menuOpen = false }
                            )
                            SecondaryMenuDivider()
                            val still = if (selected.type == MediaType.IMAGE_SET) selected.url
                                else selected.previewUrl?.takeIf { selected.type == MediaType.LIVE_PHOTO }
                            if (still != null) {
                                SecondaryMenuItem(
                                    label = if (selected.type == MediaType.LIVE_PHOTO) "查看实况照片" else "查看原图",
                                    icon = Icons.Default.Image,
                                    onClick = { openedImage = still; menuOpen = false }
                                )
                                SecondaryMenuDivider()
                            }
                            result.audio?.let { audio ->
                                SecondaryMenuItem(
                                    label = "复制音频链接",
                                    icon = Icons.Default.MusicNote,
                                    onClick = { copyText(context, audio.url); menuOpen = false }
                                )
                                SecondaryMenuDivider()
                            }
                            SecondaryMenuItem(
                                label = "复制作品信息",
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    copyText(context, workDetails); menuOpen = false
                                }
                            )
                        }
                    }
                }
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isImage) StudioStyle.soft else Color(0xFF151619))) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    key = { result.items[it].url }
                ) { index ->
                    val item = result.items[index]
                    if (item.type == MediaType.IMAGE_SET) {
                        var failed by remember(item.url) { mutableStateOf(false) }
                        var retry by remember(item.url) { mutableIntStateOf(0) }
                        Box(Modifier.fillMaxSize().background(StudioStyle.soft).appClickable { openedImage = item.url }, contentAlignment = Alignment.Center) {
                            androidx.compose.runtime.key(item.url, retry) {
                                AsyncImage(
                                    imageModel(item.url),
                                    "查看第 ${index + 1} 张图片",
                                    // Fill the preview during resizing and for capped long images;
                                    // the viewer still fits the complete original without cropping.
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                    onSuccess = { state ->
                                        failed = false
                                        val image = state.result.image
                                        if (image.width > 0 && image.height > 0) ratios[item.url] = image.width.toFloat() / image.height
                                    },
                                    onError = { failed = true }
                                )
                            }
                            if (failed) ChatAction("重新加载", Icons.Default.Refresh) { failed = false; retry++ }
                        }
                    } else if (index == pager.currentPage) {
                        MediaPreview(
                            item,
                            item.previewUrl ?: result.coverUrl,
                            Modifier.fillMaxSize(),
                            ratios[item.url] ?: previewAspectRatio(item.width, item.height),
                            onAspectRatio = { if (it.isFinite() && it > 0f) ratios[item.url] = it }
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF151619)), contentAlignment = Alignment.Center) {
                            AsyncImage(item.previewUrl ?: result.coverUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = .58f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(if (selected.type == MediaType.IMAGE_SET) Icons.Default.Image else Icons.Default.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(mediaLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (result.items.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${result.items.size}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                            .background(Color.Black.copy(alpha = .58f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            if (result.items.size > 1) {
                MediaThumbnailStrip(pager) { index ->
                    val item = result.items[index]
                    val thumbnail = if (item.type == MediaType.IMAGE_SET) item.url else item.previewUrl ?: result.coverUrl
                    if (thumbnail != null) AsyncImage(imageModel(thumbnail), null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(if (item.type == MediaType.IMAGE_SET) Icons.Default.Image else Icons.Default.PlayArrow,
                        null, tint = StudioStyle.muted, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(
                    start = 16.dp,
                    top = if (result.items.size > 1) 0.dp else 16.dp,
                    end = 16.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                result.title?.takeIf { it.isNotBlank() }?.let { title ->
                    ExpandableResultText(title)
                }

                val saveVideo: () -> Unit = {
                    if (video != null && progress == null) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            pendingUrl = video.url
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else viewModel.saveVideo(video.url, result.title)
                    }
                }
                result.audio?.let { AudioAttachment(it, Modifier.fillMaxWidth()) }
                if (selected.type == MediaType.LIVE_PHOTO) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChatSaveAction(
                            if (downloadingUrl == selected.url && progress != null) "$progress%" else "保存视频",
                            Icons.Default.Download,
                            Modifier.weight(1f),
                            enabled = progress == null,
                            busy = downloadingUrl == selected.url && progress != null,
                            onClick = saveVideo
                        )
                        selected.previewUrl?.let {
                            ImageSaveAction(it, viewModel, Modifier.weight(1f), idleLabel = "保存照片")
                        }
                    }
                } else if (isImage) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChatSaveAction("查看原图", Icons.Default.Fullscreen,
                            Modifier.weight(1f), grouped = true, onClick = { openedImage = selected.url })
                        ImageSaveAction(selected.url, viewModel, Modifier.weight(1f))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(listOfNotNull(result.quality?.takeIf { it.isNotBlank() },
                            result.sizeLabel?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "视频文件" },
                            style = ChatTypography.detail, color = StudioStyle.muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        when (selected.type) {
                            MediaType.VIDEO -> ChatSaveAction(
                                if (downloadingUrl == selected.url && progress != null) "$progress%" else "保存视频",
                                Icons.Default.Download,
                                Modifier.width(104.dp),
                                enabled = progress == null,
                                busy = downloadingUrl == selected.url && progress != null,
                                onClick = saveVideo
                            )
                            MediaType.IMAGE_SET -> ImageSaveAction(selected.url, viewModel,
                                Modifier.width(104.dp))
                            MediaType.LIVE_PHOTO -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderBadge(providerName: String) {
    Row(
        Modifier.height(22.dp).background(StudioStyle.soft, RoundedCornerShape(50.dp))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).background(StudioStyle.ink, CircleShape))
        Text(providerDisplayName(providerName), color = StudioStyle.muted, fontSize = 9.sp,
            lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}


private val ComposerSurfacePadding = 7.dp
private val ComposerFieldTopInset = 11.dp
private val ComposerFieldBottomInset = 12.dp
private val ComposerCollapsedHeight = 58.dp

@Composable
internal fun Composer(value: String, placeholder: String = "发送一个分享链接…", isLoading: Boolean, colors: AppPalette, onValueChange: (String) -> Unit, onPaste: () -> Unit, onCancel: () -> Unit, onSurfaceHeightChanged: (Int) -> Unit, onSend: () -> Unit) {
    val inputInteraction = remember { MutableInteractionSource() }
    val inputStyle = TextStyle(color = colors.text, fontSize = 15.sp, lineHeight = 21.sp)
    Box(
        Modifier.fillMaxWidth()
            .navigationBarsPadding().imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = ComposerCollapsedHeight)
                .testTag("composer-surface")
                .onSizeChanged { onSurfaceHeightChanged(it.height) }
                .floatingShadow(InputShape, 24.dp)
                .clip(InputShape)
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = ComposerSurfacePadding)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = 54.dp, end = 54.dp, top = ComposerFieldTopInset, bottom = ComposerFieldBottomInset)
                    .testTag("composer-text-viewport")
                    // Clip new lines inside the animated viewport. Its measured height also
                    // drives the surface, while top alignment keeps existing lines continuous.
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = 1f, stiffness = 1400f),
                        alignment = Alignment.TopStart
                    ),
                contentAlignment = Alignment.TopStart
            ) {
                BasicTextField(
                    value = value, onValueChange = onValueChange, enabled = !isLoading,
                    // Keep the field's natural layout independent of the animated viewport.
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = inputStyle,
                    cursorBrush = SolidColor(colors.accent), maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank() && !isLoading) onSend() }),
                    interactionSource = inputInteraction,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(placeholder, style = inputStyle.copy(color = colors.secondaryText),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    }
                )
            }
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 1.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF5F5F6), CircleShape).appClickable(enabled = !isLoading, onClick = onPaste), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ContentPaste, "粘贴链接", tint = colors.text, modifier = Modifier.size(21.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp), contentAlignment = Alignment.Center) {
                SendButton(value.isNotBlank(), isLoading, colors, if (isLoading) onCancel else onSend)
            }
        }
    }
}

@Composable
private fun SendButton(canSend: Boolean, isLoading: Boolean, colors: AppPalette, onClick: () -> Unit) {
    val enabled = canSend || isLoading
    val buttonSurface = if (enabled) Color(0xFF171719) else Color(0xFFF1F1F3)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(buttonSurface, CircleShape)
            .appClickable(enabled = enabled, rippleColor = Color.White, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(isLoading, transitionSpec = { fadeIn(tween(120)).togetherWith(fadeOut(tween(100))) }, label = "send-stop") { loading ->
            val icon = if (loading) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send
            Icon(icon, if (loading) "停止" else "发送", tint = if (enabled) Color.White else colors.secondaryText, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun AssistantRow(colors: AppPalette, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(end = 18.dp)) { content() }
}

@Composable
internal fun HeaderAction(icon: ImageVector, description: String, colors: AppPalette, onClick: () -> Unit) {
    // Keep the shadow outside the clipping layer. Compose expands the inner
    // clickable's touch target into this 48dp slot without expanding its ripple.
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).floatingShadow(CircleShape, 14.dp)
            .clip(CircleShape).background(Color.White)
            .appClickable(rippleColor = colors.text, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = colors.text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InlineAction(text: String, icon: ImageVector, background: Color, content: Color, onClick: () -> Unit) {
    Row(Modifier.clip(StudioStyle.control).background(background, StudioStyle.control).appClickable(rippleColor = content, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(15.dp)); Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


private fun clipboardText(context: Context): String =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("video-url", value))
}



