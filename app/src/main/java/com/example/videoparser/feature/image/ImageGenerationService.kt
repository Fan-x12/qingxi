package com.example.videoparser

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// 生图状态由进程持有，切换页面或重建 ViewModel 时继续保留。
/** 保存进程内共享的生图状态，供页面与前台服务共同使用。 */
internal object ImageGenerationSession {
    private var initialized = false
    val turns = MutableStateFlow<List<ImageTurn>>(emptyList())
    val generating = MutableStateFlow(false)

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        turns.value = ImageChatStore(context).load()
        initialized = true
    }

    fun start(context: Context, prompt: String) {
        initialize(context)
        if (generating.value || prompt.isBlank()) return
        val turn = ImageTurn(java.util.UUID.randomUUID().toString(), prompt.trim())
        turns.value = (turns.value + turn).takeLast(50)
        generating.value = true
        try {
            ContextCompat.startForegroundService(context, Intent(context, ImageGenerationService::class.java)
                .putExtra("turn_id", turn.id))
        } catch (_: Exception) {
            turns.value = turns.value.map { if (it.id == turn.id) it.copy(status = "error", message = "无法启动后台生成服务，请回到应用后重试") else it }
            generating.value = false
        }
    }

    fun cancel(context: Context) {
        if (generating.value) context.startService(Intent(context, ImageGenerationService::class.java).setAction("cancel"))
    }
}

/** 通过前台服务执行生图任务，并提供进度通知与取消入口。 */
class ImageGenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ImageGenerationSession.initialize(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("image-generation", "图片生成", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .putExtra("open_image_chat", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ImageGenerationService::class.java).setAction("cancel"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "image-generation")
            .setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("正在生成图片")
            .setContentText("可切换其他应用，完成后返回查看")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(0, 0, true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止生成", stop).build()
        ServiceCompat.startForeground(this, 4102, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            job?.cancel()
            if (job == null) { ImageGenerationSession.generating.value = false; stopSelf() }
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val id = intent?.getStringExtra("turn_id")
        val turn = ImageGenerationSession.turns.value.firstOrNull { it.id == id && it.status == "generating" }
        if (turn == null) { ImageGenerationSession.generating.value = false; stopSelf(); return START_NOT_STICKY }
        job = scope.launch {
            var result = turn
            val store = ImageChatStore(applicationContext)
            try {
                withContext(Dispatchers.IO) { store.save(ImageGenerationSession.turns.value) }
                val (config, key) = withContext(Dispatchers.IO) {
                    val settings = ImageServiceStore(applicationContext)
                    settings.load() to settings.apiKey()
                }
                require(config.endpoint.isNotBlank() && config.model.isNotBlank()) { "请先配置图片接口与生图模型" }
                val images = ImageServiceClient().generate(applicationContext, config, key, turn.prompt)
                result = turn.copy(status = "success", images = images)
            } catch (e: CancellationException) {
                result = turn.copy(status = "cancelled", message = "已停止生成")
            } catch (e: Exception) {
                result = turn.copy(status = "error", message = e.message ?: "生成失败，请重试")
            } finally {
                ImageGenerationSession.turns.value = ImageGenerationSession.turns.value.map { if (it.id == turn.id) result else it }
                withContext(NonCancellable + Dispatchers.IO) { runCatching { store.save(ImageGenerationSession.turns.value) } }
                ServiceCompat.stopForeground(this@ImageGenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                ImageGenerationSession.generating.value = false
            }
        }
        // 进程终止后不自动重发生成请求，避免已计费的任务重复扣费。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
