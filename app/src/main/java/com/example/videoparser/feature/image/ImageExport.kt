package com.example.videoparser

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 将本地或远程生成图片保存到相册，限制下载大小并响应取消。 */
internal object ImageExport {
    private const val LIMIT = 30L * 1024 * 1024
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS).followSslRedirects(false).build()

    private suspend fun remoteBytes(source: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(source).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("图片下载失败，请重试"))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it.isSuccessful) { "图片下载失败：HTTP ${it.code}" }
                        val body = it.body ?: error("图片内容为空")
                        require(body.contentLength() <= LIMIT) { "图片超过 30MB" }
                        val stream = body.source()
                        require(!stream.request(LIMIT + 1)) { "图片超过 30MB" }
                        stream.readByteArray()
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun save(context: Context, source: String) = withContext(Dispatchers.IO) {
        val bytes = if (source.startsWith("https://")) remoteBytes(source) else {
            val file = File(source).canonicalFile
            val directory = File(context.filesDir, "generated-images").canonicalFile
            require(file.parentFile == directory && file.isFile) { "本地图片不存在" }
            require(file.length() <= LIMIT) { "图片超过 30MB" }
            file.readBytes()
        }
        ensureActive()
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
        require(info.outWidth > 0 && info.outHeight > 0) { "下载内容不是有效图片" }
        val mime = info.outMimeType ?: error("无法识别图片格式")
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: error("不支持此图片格式")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "创作_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/短视频解析")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入相册")
            ensureActive()
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
