package com.example.videoparser.data.provider

import com.example.videoparser.BuildConfig
import com.example.videoparser.domain.AudioTrack
import com.example.videoparser.domain.MediaItem
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform
import com.example.videoparser.domain.withSecureMediaUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 夜雨聚合解析接口。真实返回统一为 video/image，媒体字段会随平台和内容类型变化。 */
class YrainAggregateProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val apiKey: String = BuildConfig.YRAIN_API_KEY
) : ParseProvider {
    override val name = "夜雨聚合解析"
    override val supportedPlatforms = setOf(Platform.DOUYIN, Platform.KUAISHOU, Platform.XIAOHONGSHU)
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE_SET, MediaType.LIVE_PHOTO)

    override suspend fun parse(request: ParseRequest): Result<ParseResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(request.platform in supportedPlatforms) { "该接口支持抖音、快手和小红书" }
            require(apiKey.isNotBlank()) { "未配置夜雨聚合接口 Key" }
            val httpRequest = Request.Builder()
                .url(requestUrl(request.sourceUrl))
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(raw.isNotBlank()) { "接口返回为空" }
                val root = json.parseToJsonElement(raw)
                validateEnvelope(root)
                require(response.isSuccessful) { "HTTP ${response.code}" }
                mapResponse(root, request.platform, request.sourceUrl)
            }
        }
    }

    internal fun mapResponse(root: JsonElement, platform: Platform, sourceUrl: String = ""): ParseResult {
        val envelope = root as? JsonObject ?: error("接口返回格式不正确")
        val data = envelope["data"] as? JsonObject ?: error("接口未返回可用的数据")
        val type = contentType(text(data, "type", "media_type", "mediaType"))
        val declaredCover = firstUrl(data, *COVER_KEYS)
        val images = values(data, IMAGE_KEYS)
            .mapNotNull(::asImageUrl)
            .filterNot { sameMedia(it, sourceUrl) }
            .distinctBy(::mediaIdentity)
        val primaryVideo = firstUrl(data, *VIDEO_KEYS)?.takeUnless { sameMedia(it, sourceUrl) }
        val fallbackVideo = values(data, BACKUP_VIDEO_KEYS)
            .mapNotNull(::asUrl)
            .firstOrNull { !sameMedia(it, sourceUrl) && !sameMedia(it, primaryVideo) }
        val live = livePhotoItems(data, images, declaredCover)
        val video = primaryVideo ?: fallbackVideo
        val preview = declaredCover ?: images.firstOrNull()
        val items = when (type) {
            ContentType.VIDEO -> videoItems(video, preview, data)
                .ifEmpty { inferItems(video, images, live, declaredCover, data) }
            ContentType.IMAGE -> if (images.isNotEmpty() || live.isNotEmpty()) {
                imageAndLiveItems(images, live, declaredCover)
            } else {
                videoItems(video, preview, data).ifEmpty {
                    imageAndLiveItems(emptyList(), emptyList(), declaredCover)
                }
            }
            ContentType.LIVE_PHOTO -> liveResultItems(live, video, images, declaredCover, data)
            ContentType.UNKNOWN -> inferItems(video, images, live, declaredCover, data)
        }
        require(items.isNotEmpty()) { "接口未返回可用的图片或视频地址" }

        val author = data["author"] as? JsonObject
        val music = (data["music"] as? JsonObject) ?: (data["audio"] as? JsonObject)
        val audioUrl = music?.let { firstUrl(it, *AUDIO_URL_KEYS) }
            ?: text(data, "audio", "audio_url", "audioUrl")?.takeIf(::isUrl)
        val audio = audioUrl?.let {
            AudioTrack(
                title = music?.let { value -> text(value, "title", "name") },
                author = music?.let { value -> text(value, "author", "artist", "singer") },
                url = it,
                coverUrl = music?.let { value -> firstUrl(value, *AUDIO_COVER_KEYS) }
            )
        }
        return ParseResult(
            platform = platform,
            title = text(data, "title", "desc", "description", "text"),
            coverUrl = declaredCover ?: items.firstNotNullOfOrNull { it.previewUrl }
                ?: items.firstOrNull { it.type == MediaType.IMAGE_SET }?.url,
            items = items.distinctBy { "${it.type}:${mediaIdentity(it.url)}" },
            authorName = author?.let { text(it, "name", "nickname", "username") }
                ?: text(data, "author_name", "authorName", "nickname", "username", "author"),
            authorAvatarUrl = author?.let { firstUrl(it, *AUTHOR_AVATAR_KEYS) }
                ?: firstUrl(data, "author_avatar", "authorAvatar", "avatar", "avatar_url"),
            audio = audio,
            durationSeconds = durationSeconds(data),
            sizeLabel = text(data, "size_label", "sizeLabel", "size"),
            publishTime = text(data, "publish_time", "publishTime", "create_time", "createTime"),
            quality = text(data, "quality", "definition", "resolution")
        ).withSecureMediaUrls()
    }

    internal fun requestUrl(sourceUrl: String) = endpoint.newBuilder()
        .addQueryParameter("key", apiKey)
        .addQueryParameter("url", sourceUrl)
        .build()

    internal fun validateEnvelope(root: JsonElement) {
        val envelope = root as? JsonObject ?: error("接口返回格式不正确")
        val code = text(envelope, "code")?.lowercase()
        val success = text(envelope, "succ", "success", "status")?.let {
            when (it.lowercase()) {
                "true", "1", "ok", "success" -> true
                "false", "0", "error", "failed" -> false
                else -> null
            }
        }
        val message = text(envelope, "msg", "message", "error") ?: "聚合接口返回错误"
        if (code != null && code !in SUCCESS_CODES) throw IllegalStateException("$message（code=$code）")
        if (success == false) throw IllegalStateException(message)
        if (envelope["data"] == null || envelope["data"] is JsonNull) throw IllegalStateException(message)
    }

    private fun videoItems(video: String?, preview: String?, data: JsonObject): List<MediaItem> {
        if (video == null) return emptyList()
        val size = dimensions(data)
        return listOf(MediaItem(MediaType.VIDEO, video, preview, width = size.first, height = size.second))
    }

    private fun imageAndLiveItems(
        images: List<String>,
        live: List<MediaItem>,
        cover: String?
    ): List<MediaItem> = buildList {
        val usedMotion = mutableSetOf<String>()
        images.forEachIndexed { index, image ->
            val motion = live.firstOrNull {
                mediaIdentity(it.url) !in usedMotion && sameMedia(it.previewUrl, image)
            } ?: live.getOrNull(index)?.takeIf { mediaIdentity(it.url) !in usedMotion }
            if (motion == null) {
                add(MediaItem(MediaType.IMAGE_SET, image, image))
            } else {
                add(motion.copy(previewUrl = motion.previewUrl ?: image))
                usedMotion += mediaIdentity(motion.url)
            }
        }
        live.filter { mediaIdentity(it.url) !in usedMotion }.forEach(::add)
        if (isEmpty() && cover != null) add(MediaItem(MediaType.IMAGE_SET, cover, cover))
    }

    private fun liveResultItems(
        live: List<MediaItem>,
        video: String?,
        images: List<String>,
        cover: String?,
        data: JsonObject
    ): List<MediaItem> {
        if (live.isNotEmpty()) return imageAndLiveItems(images, live, cover)
        val size = dimensions(data)
        return if (video != null) {
            listOf(MediaItem(MediaType.LIVE_PHOTO, video, images.firstOrNull() ?: cover,
                width = size.first, height = size.second))
        } else {
            imageAndLiveItems(images, emptyList(), cover)
        }
    }

    private fun inferItems(
        video: String?,
        images: List<String>,
        live: List<MediaItem>,
        cover: String?,
        data: JsonObject
    ): List<MediaItem> = when {
        live.isNotEmpty() -> imageAndLiveItems(images, live, cover)
        video != null -> videoItems(video, cover ?: images.firstOrNull(), data)
        else -> imageAndLiveItems(images, emptyList(), cover)
    }

    private fun livePhotoItems(data: JsonObject, images: List<String>, cover: String?): List<MediaItem> {
        val parentSize = dimensions(data)
        return values(data, LIVE_PHOTO_KEYS).mapIndexedNotNull { index, value ->
            when (value) {
                is JsonPrimitive -> asUrl(value)?.let {
                    MediaItem(MediaType.LIVE_PHOTO, it, images.getOrNull(index) ?: cover,
                        width = parentSize.first, height = parentSize.second)
                }
                is JsonObject -> {
                    val video = firstUrl(value, *LIVE_VIDEO_KEYS) ?: return@mapIndexedNotNull null
                    val preview = firstUrl(value, *LIVE_PREVIEW_KEYS) ?: images.getOrNull(index) ?: cover
                    val size = dimensions(value)
                    MediaItem(MediaType.LIVE_PHOTO, video, preview,
                        width = size.first ?: parentSize.first,
                        height = size.second ?: parentSize.second)
                }
                else -> null
            }
        }.distinctBy { mediaIdentity(it.url) }
    }

    private fun contentType(raw: String?): ContentType = when (raw?.trim()?.lowercase()) {
        "video", "视频", "short_video", "shortvideo" -> ContentType.VIDEO
        "image", "images", "photo", "photos", "album", "note", "图集", "图文" -> ContentType.IMAGE
        "live", "live_photo", "livephoto", "motion_photo", "motionphoto", "实况", "实况图" -> ContentType.LIVE_PHOTO
        else -> ContentType.UNKNOWN
    }

    private fun durationSeconds(data: JsonObject): Double? {
        text(data, "duration_seconds", "durationSeconds")?.toDoubleOrNull()?.let { return it }
        return text(data, "duration")?.toDoubleOrNull()?.let { if (it >= 1_000) it / 1_000.0 else it }
    }

    private fun dimensions(data: JsonObject): Pair<Int?, Int?> =
        text(data, "width", "video_width", "videoWidth")?.toIntOrNull() to
            text(data, "height", "video_height", "videoHeight")?.toIntOrNull()

    private fun values(data: JsonObject, keys: Array<String>): List<JsonElement> = keys.asSequence()
        .mapNotNull(data::get)
        .flatMap(::flattenValues)
        .toList()

    private fun flattenValues(value: JsonElement): Sequence<JsonElement> = when (value) {
        is JsonArray -> value.asSequence().flatMap(::flattenValues)
        else -> sequenceOf(value)
    }

    private fun firstUrl(data: JsonObject, vararg keys: String): String? =
        keys.asSequence().mapNotNull(data::get).mapNotNull(::asUrl).firstOrNull()

    private fun asUrl(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.content.takeIf(::isUrl)
        is JsonObject -> firstUrl(value, "url", "src", "origin_url", "originUrl", "play_url", "playUrl",
            "download_url", "downloadUrl")
        else -> null
    }

    private fun asImageUrl(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.content.takeIf(::isUrl)
        is JsonObject -> firstUrl(value, "url", "src", "image", "image_url", "imageUrl", "origin_url",
            "originUrl", "cover", "cover_url", "coverUrl")
        else -> null
    }

    private fun text(data: JsonObject, vararg keys: String): String? = keys.asSequence()
        .mapNotNull { data[it] as? JsonPrimitive }
        .filterNot { it is JsonNull }
        .map { it.content }
        .firstOrNull { it.isNotBlank() }

    private fun isUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

    private fun sameMedia(left: String?, right: String?): Boolean =
        left != null && right != null && mediaIdentity(left) == mediaIdentity(right)

    private fun mediaIdentity(value: String): String = value.substringBefore('#').substringBefore('?')
        .trimEnd('/').lowercase()

    private enum class ContentType { VIDEO, IMAGE, LIVE_PHOTO, UNKNOWN }

    private companion object {
        val endpoint = "https://apiv1.yrain.top/jhjx.php".toHttpUrl()
        val json = Json { ignoreUnknownKeys = true }
        val SUCCESS_CODES = setOf("200", "0", "success")
        val COVER_KEYS = arrayOf("cover", "cover_url", "coverUrl", "poster", "thumbnail", "cover_image")
        val IMAGE_KEYS = arrayOf("images", "pics", "pictures", "image_list", "imageList")
        val VIDEO_KEYS = arrayOf("url", "video_url", "videoUrl", "download_url", "downloadUrl",
            "play_url", "playUrl", "playurl", "video")
        val BACKUP_VIDEO_KEYS = arrayOf("video_backup", "video_backup_urls", "backup_urls", "backupUrl")
        val LIVE_PHOTO_KEYS = arrayOf("live_photo", "live_photos", "livePhoto", "livephoto", "livephoto_list",
            "motion_photo", "motion_photos")
        val LIVE_VIDEO_KEYS = arrayOf("video", "video_url", "videoUrl", "url", "play_url", "playUrl",
            "download_url", "downloadUrl", "motion", "motion_url")
        val LIVE_PREVIEW_KEYS = arrayOf("image", "image_url", "imageUrl", "cover", "cover_url", "coverUrl",
            "preview", "preview_url", "poster", "thumbnail", "photo", "photo_url")
        val AUTHOR_AVATAR_KEYS = arrayOf("avatar", "avatar_url", "avatarUrl", "head_url", "headUrl")
        val AUDIO_URL_KEYS = arrayOf("url", "audio_url", "audioUrl", "play_url", "playUrl")
        val AUDIO_COVER_KEYS = arrayOf("cover", "cover_url", "coverUrl", "image")
    }
}
