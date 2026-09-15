package com.example.videoparser.data.provider

import com.example.videoparser.BuildConfig
import com.example.videoparser.domain.MediaItem
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.AudioTrack
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** BugPk 抖音无水印解析接口（GET /api/dyjx）。 */
class IfphpDouyinProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val apiKey: String = BuildConfig.BUGPK_API_KEY
) : ParseProvider {
    override val name: String = "BugPk 抖音解析"
    override val supportedPlatforms: Set<Platform> = setOf(Platform.DOUYIN)
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.VIDEO, MediaType.IMAGE_SET, MediaType.LIVE_PHOTO)

    override suspend fun parse(request: ParseRequest): Result<ParseResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(request.platform == Platform.DOUYIN) { "该接口仅支持抖音链接" }
            require(apiKey.isNotBlank()) { "未配置 BugPk API Key，请先设置 BUGPK_API_KEY" }
            val url = endpoint.newBuilder()
                .addQueryParameter("key", apiKey)
                .addQueryParameter("url", request.sourceUrl)
                .build()
            val httpRequest = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { "HTTP ${response.code}" }
                require(raw.isNotBlank()) { "接口返回为空" }
                val root = Json.parseToJsonElement(raw)
                val code = findString(root, setOf("code"))
                if (code != null && code !in SUCCESS_CODES) {
                    val message = findString(root, setOf("message", "msg", "error")) ?: "接口返回错误"
                    throw IllegalStateException("$message（code=$code）")
                }
                mapResponse(root, request.sourceUrl)
            }
        }
    }

    internal fun mapResponse(
        root: JsonElement,
        sourceUrl: String,
        platform: Platform = Platform.DOUYIN
    ): ParseResult {
        val envelope = root as? kotlinx.serialization.json.JsonObject ?: error("接口返回格式不正确")
        val data = envelope["data"] as? kotlinx.serialization.json.JsonObject ?: envelope
        fun text(obj: kotlinx.serialization.json.JsonObject, key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }
                ?.content?.takeIf { it.isNotBlank() }
        fun url(value: JsonElement?): String? = (value as? JsonPrimitive)
            ?.content?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        fun firstUrl(obj: kotlinx.serialization.json.JsonObject, vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> url(obj[key]) }
        fun sameMedia(left: String?, right: String?): Boolean = left != null && right != null &&
            (left == right || left.substringBefore('?') == right.substringBefore('?'))

        val cover = firstUrl(data, "cover", "cover_url", "coverUrl", "poster", "thumbnail")
        val images = (data["images"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull(::url).distinct()
        val rawLive = listOf("live_photo", "live_photos", "livePhoto", "livephoto")
            .firstNotNullOfOrNull { data[it] }
        val liveEntries: List<JsonElement> = when (rawLive) {
            is kotlinx.serialization.json.JsonArray -> rawLive
            is kotlinx.serialization.json.JsonObject, is JsonPrimitive -> listOf(rawLive)
            else -> emptyList()
        }
        val live = liveEntries.mapIndexedNotNull { index, entry ->
            if (entry is JsonPrimitive) {
                return@mapIndexedNotNull url(entry)?.let { video ->
                    MediaItem(MediaType.LIVE_PHOTO, video, previewUrl = images.getOrNull(index) ?: cover)
                }
            }
            val pair = entry as? kotlinx.serialization.json.JsonObject ?: return@mapIndexedNotNull null
            val video = firstUrl(pair, "video", "video_url", "videoUrl", "url", "play_url", "playUrl", "download_url")
                ?: return@mapIndexedNotNull null
            val preview = firstUrl(
                pair, "image", "image_url", "imageUrl", "cover", "cover_url", "coverUrl",
                "preview", "preview_url", "poster", "thumbnail"
            ) ?: images.getOrNull(index) ?: cover
            MediaItem(
                MediaType.LIVE_PHOTO,
                video,
                previewUrl = preview,
                width = text(pair, "width")?.toIntOrNull(),
                height = text(pair, "height")?.toIntOrNull()
            )
        }
        val primaryVideo = listOf("url", "video_url", "videoUrl", "download_url", "play_url", "playUrl", "playurl", "video")
            .firstNotNullOfOrNull { url(data[it]) }
            ?.takeUnless { it == sourceUrl }
        val responseType = text(data, "type")?.lowercase()
        val items = when (responseType) {
            "live", "live_photo", "livephoto", "motion_photo" -> buildList {
                if (live.isNotEmpty()) addAll(live)
                else if (primaryVideo != null) add(MediaItem(
                    MediaType.LIVE_PHOTO,
                    primaryVideo,
                    previewUrl = images.firstOrNull() ?: cover,
                    width = text(data, "width")?.toIntOrNull(),
                    height = text(data, "height")?.toIntOrNull()
                ))
                images.filter { image -> none { item -> sameMedia(item.previewUrl, image) } }
                    .forEach { image -> add(MediaItem(MediaType.IMAGE_SET, image, previewUrl = image)) }
            }
            "image" -> buildList {
                val usedLiveUrls = mutableSetOf<String>()
                images.forEachIndexed { index, image ->
                    val motion = live.firstOrNull { it.url !in usedLiveUrls && sameMedia(it.previewUrl, image) }
                        ?: live.getOrNull(index)?.takeIf { it.url !in usedLiveUrls }
                    if (motion != null) {
                        add(motion)
                        usedLiveUrls += motion.url
                    } else add(MediaItem(MediaType.IMAGE_SET, image, previewUrl = image))
                }
                live.filter { it.url !in usedLiveUrls }.forEach(::add)
            }
            else -> {
                // Only explicit video fields are eligible; music, avatars and share links are not media fallbacks.
                if (primaryVideo != null) listOf(MediaItem(MediaType.VIDEO, primaryVideo,
                    previewUrl = cover, width = text(data, "width")?.toIntOrNull(), height = text(data, "height")?.toIntOrNull()))
                else if (live.isNotEmpty()) live + images.filter { image -> live.none { sameMedia(it.previewUrl, image) } }
                    .map { MediaItem(MediaType.IMAGE_SET, it, previewUrl = it) }
                else images.map { MediaItem(MediaType.IMAGE_SET, it, previewUrl = it) }
            }
        }
        require(items.isNotEmpty()) { "接口未返回可用的图片或视频地址" }
        val author = data["author"] as? kotlinx.serialization.json.JsonObject
        val music = data["music"] as? kotlinx.serialization.json.JsonObject
        val audio = music?.let { audioData ->
            url(audioData["url"])?.let { audioUrl ->
                AudioTrack(
                    title = text(audioData, "title"),
                    author = text(audioData, "author"),
                    url = audioUrl,
                    coverUrl = url(audioData["cover"])
                )
            }
        }
        return ParseResult(
            platform = platform,
            title = text(data, "title") ?: text(data, "desc"),
            coverUrl = cover ?: items.firstOrNull()?.previewUrl,
            items = items,
            authorName = author?.let { text(it, "name") ?: text(it, "nickname") } ?: text(data, "author_name"),
            authorAvatarUrl = author?.let { url(it["avatar"]) ?: url(it["avatar_url"]) },
            audio = audio,
            durationSeconds = text(data, "duration")?.toDoubleOrNull(),
            sizeLabel = text(data, "size_label"),
            publishTime = text(data, "publish_time"),
            quality = text(data, "quality")
        )
    }

    private fun findString(element: JsonElement, keys: Set<String>): String? =
        (element as? kotlinx.serialization.json.JsonObject)?.entries?.firstOrNull { (key, value) ->
            key in keys && value is JsonPrimitive && value !is kotlinx.serialization.json.JsonNull
        }?.value?.jsonPrimitive?.content

    private companion object {
        val endpoint = "https://api-new.ifphp.com/api/dyjx".toHttpUrl()
        val SUCCESS_CODES = setOf("200", "0", "success")
    }
}
