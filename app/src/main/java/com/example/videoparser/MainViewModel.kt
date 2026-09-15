package com.example.videoparser

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoparser.data.download.MediaDownloader
import com.example.videoparser.data.history.HistoryEntry
import com.example.videoparser.data.history.HistoryRepository
import com.example.videoparser.data.provider.ProviderRegistry
import com.example.videoparser.domain.ParseAllProvidersException
import com.example.videoparser.domain.ParseCoordinator
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseState
import com.example.videoparser.domain.Platform
import com.example.videoparser.domain.PlatformDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 连接解析、历史与下载状态；生图任务由前台服务持有，避免随页面切换中断。 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    init { ImageGenerationSession.initialize(application) }
    internal val imageTurns = ImageGenerationSession.turns.asStateFlow()
    val imageGenerating = ImageGenerationSession.generating.asStateFlow()
    internal fun generateImage(prompt: String) { ImageGenerationSession.start(getApplication(), prompt) }
    fun cancelImage() { ImageGenerationSession.cancel(getApplication()) }

    private val _imageSaving = MutableStateFlow<String?>(null)
    internal val imageSaving = _imageSaving.asStateFlow()
    private val _imageSaveMessage = MutableStateFlow<Pair<String, String>?>(null)
    internal val imageSaveMessage = _imageSaveMessage.asStateFlow()

    internal fun saveGeneratedImage(source: String) {
        if (_imageSaving.value != null) return
        _imageSaving.value = source
        _imageSaveMessage.value = null
        viewModelScope.launch {
            try {
                ImageExport.save(getApplication(), source)
                _imageSaveMessage.value = source to "已保存到相册"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageSaveMessage.value = source to (e.message ?: "保存失败，请重试")
            } finally {
                _imageSaving.value = null
            }
        }
    }

    private val coordinator = ParseCoordinator(ProviderRegistry.default())
    private val settings = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _selectedProvider = MutableStateFlow(
        settings.getString("parse_provider", "")?.takeIf { it.isNotBlank() }
    )
    val selectedProvider: StateFlow<String?> = _selectedProvider.asStateFlow()
    private val downloader = MediaDownloader(application.contentResolver)
    private val historyRepository = HistoryRepository(application)
    private val _state = MutableStateFlow<ParseState>(ParseState.Idle)
    val state: StateFlow<ParseState> = _state.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()
    private val _downloadingUrl = MutableStateFlow<String?>(null)
    val downloadingUrl: StateFlow<String?> = _downloadingUrl.asStateFlow()
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()
    private val _history = MutableStateFlow(historyRepository.load())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    private val _activeUrl = MutableStateFlow<String?>(null)
    val activeUrl: StateFlow<String?> = _activeUrl.asStateFlow()
    private val _scrollRequest = MutableStateFlow(0)
    val scrollRequest: StateFlow<Int> = _scrollRequest.asStateFlow()
    private val _activeTurnId = MutableStateFlow<Long?>(null)
    val activeTurnId: StateFlow<Long?> = _activeTurnId.asStateFlow()
    /** 当前查看的历史记录标识与 [activeUrl] 分开保存，区分同一链接的历史预览与新请求。 */
    private val _openHistoryId = MutableStateFlow<Long?>(null)
    val openHistoryId: StateFlow<Long?> = _openHistoryId.asStateFlow()
    private var parseJob: Job? = null

    fun parse(raw: String, historyId: Long? = null) {
        if (_state.value is ParseState.Loading) cancel()
        parseJob?.cancel()
        val turnId = historyId ?: maxOf(System.currentTimeMillis(), (_history.value.maxOfOrNull { it.id } ?: 0L) + 1)
        _activeTurnId.value = turnId
        val openedFromHistory = historyId != null
        _openHistoryId.value = historyId
        if (!openedFromHistory) _scrollRequest.value += 1
        val url = PlatformDetector.clean(raw)
        _activeUrl.value = url.ifBlank { raw }
        if (!PlatformDetector.isHttpUrl(url)) {
            _state.value = ParseState.Error("请输入有效的 http/https 链接")
            _history.value = historyRepository.addFailure(url.ifBlank { raw }, Platform.UNKNOWN, "请输入有效的链接", turnId)
            return
        }
        val platform = PlatformDetector.detect(url)
        if (platform == Platform.UNKNOWN) {
            _state.value = ParseState.Error("暂不支持该链接，请使用抖音、快手或小红书公开链接")
            _history.value = historyRepository.addFailure(url, Platform.UNKNOWN, "暂不支持该平台", turnId)
            return
        }
        parseJob = viewModelScope.launch {
            _downloadMessage.value = null
            _state.value = ParseState.Loading(platform, 1, "准备接口")
            val result = coordinator.parse(ParseRequest(url, platform), _selectedProvider.value) { attempt, provider ->
                _state.value = ParseState.Loading(platform, attempt, provider.name)
            }
            result.fold(
                onSuccess = {
                    _state.value = ParseState.Success(it)
                    _history.value = historyRepository.add(url, it, turnId)
                },
                onFailure = { error ->
                    val details = (error as? ParseAllProvidersException)?.failures.orEmpty()
                    val selectedProvider = _selectedProvider.value
                    val message = when {
                        details.isEmpty() -> error.message ?: "解析失败"
                        selectedProvider != null -> "${ProviderRegistry.displayName(selectedProvider)} 解析失败"
                        else -> "${details.size} 个解析接口都没有返回有效视频"
                    }
                    _state.value = ParseState.Error(message, details)
                    val historyMessage = buildString {
                        append(message)
                        if (details.isNotEmpty()) {
                            append("：")
                            append(details.joinToString("；"))
                        }
                    }.take(500)
                    _history.value = historyRepository.addFailure(url, platform, historyMessage, turnId)
                }
            )
        }
    }

    fun setSelectedProvider(providerName: String?) {
        if (providerName != null && providerName !in setOf(ProviderRegistry.YRAIN_NAME, ProviderRegistry.BUGPK_NAME, ProviderRegistry.BUGPK_PLATFORM_NAME)) return
        _selectedProvider.value = providerName
        settings.edit().putString("parse_provider", providerName.orEmpty()).apply()
    }

    fun cancel() {
        val loading = _state.value as? ParseState.Loading
        val url = _activeUrl.value
        parseJob?.cancel()
        _state.value = ParseState.Idle
        if (loading != null && url != null) {
            _history.value = historyRepository.addCancelled(url, loading.platform, _activeTurnId.value ?: System.currentTimeMillis())
        }
    }

    fun newConversation() {
        if (_state.value is ParseState.Loading) cancel()
        parseJob?.cancel()
        _activeTurnId.value = null
        _openHistoryId.value = null
        _activeUrl.value = null
        _state.value = ParseState.Idle
        _downloadMessage.value = null
    }

    fun openHistory(entry: HistoryEntry) {
        if (_state.value is ParseState.Loading) cancel()
        parseJob?.cancel()
        val result = entry.toParseResult()
        if (result != null) {
            _activeTurnId.value = entry.id
            _openHistoryId.value = entry.id
            _activeUrl.value = entry.sourceUrl
            _state.value = ParseState.Success(result)
        } else {
            // 缓存链接过期时在原位置重新解析，不新增一轮底部消息。
            parse(entry.sourceUrl, historyId = entry.id)
        }
        _scrollRequest.value += 1
    }

    fun deleteHistory(id: Long) {
        if (_activeTurnId.value == id) {
            parseJob?.cancel()
            _activeTurnId.value = null
            _activeUrl.value = null
            _openHistoryId.value = null
            _state.value = ParseState.Idle
        }
        _history.value = historyRepository.delete(id)
    }

    fun clearHistory() {
        newConversation()
        _history.value = historyRepository.clear()
    }

    fun saveVideo(url: String, title: String?) {
        if (_downloadProgress.value != null) return
        viewModelScope.launch {
            _downloadingUrl.value = url
            _downloadProgress.value = 0
            _downloadMessage.value = null
            val fileName = (title ?: "video").replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]"), "_").take(40)
            downloader.saveVideo(url, fileName) { progress -> _downloadProgress.value = progress }
                .fold(
                    onSuccess = { _downloadMessage.value = "已保存到相册" },
                    onFailure = { _downloadMessage.value = it.message ?: "保存失败" }
                )
            _downloadProgress.value = null
            _downloadingUrl.value = null
        }
    }
}
