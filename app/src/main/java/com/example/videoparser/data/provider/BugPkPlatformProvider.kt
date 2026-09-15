package com.example.videoparser.data.provider

import com.example.videoparser.BuildConfig
import com.example.videoparser.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 文档 5：GET /api/svparse。与 /api/dyjx 独立，复用已验证的统一媒体结构。 */
class BugPkPlatformProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build(),
    private val apiKey: String = BuildConfig.BUGPK_API_KEY
) : ParseProvider {
    override val name = ProviderRegistry.BUGPK_PLATFORM_NAME
    override val supportedPlatforms = setOf(Platform.DOUYIN, Platform.KUAISHOU, Platform.XIAOHONGSHU)
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE_SET, MediaType.LIVE_PHOTO)
    // 多平台响应与旧格式共享字段映射，平台信息由当前请求传入。
    private val mapper = IfphpDouyinProvider(client, apiKey)

    override suspend fun parse(request: ParseRequest): Result<ParseResult> = withContext(Dispatchers.IO) {
        try {
            require(request.platform in supportedPlatforms) { "暂不支持该平台" }
            require(apiKey.isNotBlank()) { "未配置 BugPk API Key" }
            val httpRequest = Request.Builder().url(requestUrl(request.sourceUrl))
                .header("Accept", "application/json").build()
            client.newCall(httpRequest).execute().use { response ->
                require(response.isSuccessful) { "BugPk 聚合请求失败（HTTP ${response.code}）" }
                Result.success(mapResponse(Json.parseToJsonElement(response.body?.string().orEmpty()), request))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun requestUrl(sourceUrl: String) = "https://api-new.ifphp.com/api/svparse".toHttpUrl()
        .newBuilder().addQueryParameter("key", apiKey).addQueryParameter("url", sourceUrl).build()

    internal fun mapResponse(root: JsonElement, request: ParseRequest): ParseResult {
        val envelope = root as? JsonObject ?: error("接口返回格式不正确")
        val code = (envelope["code"] as? JsonPrimitive)?.content
        require(code == "200") {
            (envelope["msg"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: "BugPk 聚合接口返回错误"
        }
        require(envelope["data"] is JsonObject) { "接口未返回媒体数据" }
        // /svparse duration is already seconds (real sample: 80.01), unlike extra.duration_ms.
        // Only data.url is the primary video; video_backup and music must not become extra items.
        return mapper.mapResponse(root, request.sourceUrl, request.platform).copy(providerName = name)
    }
}
