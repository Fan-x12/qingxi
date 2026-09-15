package com.example.videoparser.data.provider

import com.example.videoparser.BuildConfig
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** BugPk 文档 2 对应的抖音解析接口（GET /api/dyjx），作为夜雨接口的备用节点。 */
class BugPkAggregateProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val apiKey: String = BuildConfig.BUGPK_API_KEY
) : ParseProvider {
    override val name = "BugPk 聚合解析"
    override val supportedPlatforms = setOf(Platform.DOUYIN)
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE_SET, MediaType.LIVE_PHOTO)

    // 仅复用响应映射，不通过该对象发起旧接口请求。
    private val responseMapper = IfphpDouyinProvider(client, apiKey)

    override suspend fun parse(request: ParseRequest): Result<ParseResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(request.platform in supportedPlatforms) { "该接口仅支持抖音链接" }
            require(apiKey.isNotBlank()) { "未配置 BugPk API Key" }
            val httpRequest = Request.Builder()
                .url(requestUrl(request.sourceUrl))
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(raw.isNotBlank()) { "接口返回为空" }
                val root = JSON.parseToJsonElement(raw)
                validateEnvelope(root as? JsonObject ?: error("接口返回格式不正确"))
                require(response.isSuccessful) { "HTTP ${response.code}" }
                responseMapper.mapResponse(root, request.sourceUrl, request.platform)
            }
        }
    }

    internal fun requestUrl(sourceUrl: String) = endpoint.newBuilder()
        .addQueryParameter("key", apiKey)
        .addQueryParameter("url", sourceUrl)
        .build()

    private fun validateEnvelope(root: JsonObject) {
        fun text(key: String) = (root[key] as? JsonPrimitive)?.content
        val code = text("code")?.lowercase()
        val success = listOf("succ", "success", "status").firstNotNullOfOrNull(::text)?.toBooleanStrictOrNull()
        val message = listOf("msg", "message", "error").firstNotNullOfOrNull(::text) ?: "BugPk 接口返回错误"
        if (code != null && code !in SUCCESS_CODES) error("$message（code=$code）")
        if (success == false) error(message)
    }

    private companion object {
        val endpoint = "https://api-new.ifphp.com/api/dyjx".toHttpUrl()
        val JSON = Json { ignoreUnknownKeys = true }
        val SUCCESS_CODES = setOf("0", "200", "success")
    }
}
