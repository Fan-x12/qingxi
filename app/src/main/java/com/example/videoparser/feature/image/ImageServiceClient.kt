package com.example.videoparser

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 封装兼容图片服务的请求与响应处理，界面不直接访问网络。 */
internal class ImageServiceClient {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS).callTimeout(360, TimeUnit.SECONDS)
        .followRedirects(false).build()

    private fun endpoint(base: String, path: String): HttpUrl {
        val url = base.trim().toHttpUrlOrNull() ?: error("接口地址无效")
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "请使用 HTTPS 接口地址" }
        val root = url.encodedPath.trimEnd('/').removeSuffix("/images/generations").removeSuffix("/models")
        require(!root.endsWith("/chat/completions") && !root.endsWith("/responses")) {
            "当前生图使用 Images 协议，请填写接口基础地址或 /images/generations 地址，而不是对话接口地址"
        }
        val prefix = if (root.isEmpty()) "/v1" else root
        return url.newBuilder().encodedPath("$prefix/$path").query(null).fragment(null).build()
    }
    private suspend fun request(config: ImageServiceConfig, key: String, path: String, body: JsonObject? = null): JsonObject {
        val request = Request.Builder().url(endpoint(config.endpoint, path)).header("Accept", "application/json")
            .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key")
                if (body != null) post(body.toString().toRequestBody("application/json".toMediaType())) }.build()
        val raw = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            if (path == "models") call.timeout().timeout(30, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException(when (e) {
                        is java.net.UnknownHostException -> "无法解析服务域名，请检查地址和网络"
                        is javax.net.ssl.SSLException -> "服务的 HTTPS 连接失败，请检查证书或网络"
                        is java.io.InterruptedIOException -> "生成等待超时，服务可能仍在处理；请先检查服务记录，再决定是否重试"
                        else -> "无法连接服务，请检查网络、代理或服务地址"
                    }))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) {
                                val detail = serviceError(it.peekBody(16L * 1024).string(), key)
                                val reason = when (it.code) {
                                    400, 422 -> "请求参数或模型不被支持"
                                    401, 403 -> "认证失败或没有模型权限"
                                    404 -> "接口路径或模型不存在"
                                    429 -> "请求限流或额度不足"
                                    in 300..399 -> "接口发生重定向，请填写服务的最终 HTTPS 地址"
                                    in 500..599 -> "生图服务暂时异常"
                                    else -> "请求失败"
                                }
                                throw IOException("$reason（HTTP ${it.code}）" + if (detail != null) "\n$detail" else "")
                            }
                            val body = it.body ?: throw IOException("服务返回为空")
                            val limit = if (path == "models") 2L * 1024 * 1024 else 48L * 1024 * 1024
                            if (body.contentLength() > limit) throw IOException("服务响应过大")
                            val source = body.source()
                            if (source.request(limit + 1)) throw IOException("服务响应过大")
                            val text = source.readUtf8()
                            if (continuation.isActive) continuation.resume(text)
                        }
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
        return withContext(Dispatchers.Default) {
            val response = try { Json.parseToJsonElement(raw).jsonObject }
                catch (_: Exception) { error("服务返回的不是 Images JSON 响应，请确认地址与协议；当前不支持 SSE 或异步任务接口") }
            if (response["error"] != null && response["error"] !is JsonNull)
                error(serviceError(raw, key) ?: "服务返回生成错误")
            response
        }
    }
    private fun serviceError(raw: String, key: String): String? {
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val error = obj["error"]
        val message = when (error) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> (obj["message"] as? JsonPrimitive)?.contentOrNull
        } ?: return null
        return message.let { if (key.isNotBlank()) it.replace(key, "[已隐藏密钥]") else it }
            .replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [已隐藏]")
            .replace(Regex("sk-[A-Za-z0-9_-]+"), "[已隐藏密钥]")
            .replace(Regex("[\\p{Cntrl}&&[^\\n]]"), " ").take(360)
    }

    suspend fun models(config: ImageServiceConfig, key: String): List<String> {
        val data = request(config, key, "models")["data"] as? JsonArray ?: error("接口未返回模型列表")
        return data.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }.distinct().sorted()
    }
    suspend fun generate(context: Context, config: ImageServiceConfig, key: String, prompt: String): List<String> {
        require(config.model.isNotBlank()) { "请先在设置中配置生图模型" }
        val response = request(config, key, "images/generations", buildJsonObject {
            put("model", config.model); put("prompt", prompt); put("n", 1)
        })
        return withContext(Dispatchers.IO) {
            val data = response["data"] as? JsonArray ?: error(
                if (response["task_id"] != null || response["id"] != null) "服务返回任务标识而非图片，可能是异步接口；请使用兼容 Images 的同步接口"
                else "响应缺少 data 图片列表，请确认所选模型支持 /images/generations")
            data.take(4).mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                ensureActive()
                val imageUrl = (obj["url"] as? JsonPrimitive)?.contentOrNull
                val encoded = (obj["b64_json"] as? JsonPrimitive)?.contentOrNull
                    ?: imageUrl?.takeIf { it.startsWith("data:image/") && it.contains(";base64,") }
                if (!encoded.isNullOrBlank()) {
                    val bytes = Base64.decode(encoded.substringAfter(";base64,", encoded), Base64.DEFAULT)
                    require(bytes.size <= 30 * 1024 * 1024) { "图片超过 30MB" }
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "返回内容不是有效图片" }
                    val dir = File(context.filesDir, "generated-images").apply { mkdirs() }
                    File(dir, "${java.util.UUID.randomUUID()}.${android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(options.outMimeType ?: "image/png") ?: "png"}").apply { writeBytes(bytes) }.absolutePath
                } else imageUrl?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
            }.also { require(it.isNotEmpty()) { "服务未返回可显示的图片" } }
        }
    }
}
