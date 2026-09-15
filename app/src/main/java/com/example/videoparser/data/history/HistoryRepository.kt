package com.example.videoparser.data.history

import android.content.Context
import com.example.videoparser.domain.MediaItem
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.AudioTrack
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform
import com.example.videoparser.domain.secureMediaUrl
import com.example.videoparser.domain.withSecureMediaUrls
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class HistoryStatus { SUCCESS, ERROR, CANCELLED }

@Serializable
data class HistoryEntry(
    val id: Long,
    val sourceUrl: String,
    val platform: Platform,
    val title: String? = null,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val audio: AudioTrack? = null,
    val durationSeconds: Double? = null,
    val sizeLabel: String? = null,
    val publishTime: String? = null,
    val quality: String? = null,
    val providerName: String? = null,
    val coverUrl: String? = null,
    val mediaUrl: String? = null,
    val mediaExpiresAtEpochMillis: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val createdAt: Long,
    val status: HistoryStatus = HistoryStatus.SUCCESS,
    val errorMessage: String? = null,
    val mediaItems: List<MediaItem> = emptyList()
) {
    fun toParseResult(now: Long = System.currentTimeMillis()): ParseResult? {
        if (mediaItems.isNotEmpty()) {
            if (mediaItems.any { it.expiresAtEpochMillis?.let { expiry -> now >= expiry } == true }) return null
            return ParseResult(
                platform = platform, title = title, coverUrl = coverUrl, items = mediaItems,
                authorName = authorName, authorAvatarUrl = authorAvatarUrl, audio = audio,
                durationSeconds = durationSeconds, sizeLabel = sizeLabel,
                publishTime = publishTime, quality = quality, providerName = providerName
            ).withSecureMediaUrls()
        }
        if (mediaExpiresAtEpochMillis != null && now >= mediaExpiresAtEpochMillis) return null
        return mediaUrl?.let { url -> ParseResult(
        platform = platform,
        title = title,
        coverUrl = coverUrl,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        audio = audio,
        durationSeconds = durationSeconds,
        sizeLabel = sizeLabel,
        publishTime = publishTime,
        quality = quality,
        providerName = providerName,
        items = listOf(MediaItem(MediaType.VIDEO, url, previewUrl = coverUrl, width = mediaWidth, height = mediaHeight, expiresAtEpochMillis = mediaExpiresAtEpochMillis))
    ).withSecureMediaUrls() }
    }
}

/** 负责本地解析历史的读写，供历史列表和结果恢复使用。 */
class HistoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("parse_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<HistoryEntry> = runCatching {
        json.decodeFromString<List<HistoryEntry>>(preferences.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList()).map { entry ->
        // History cards also render their cover before opening a ParseResult.
        entry.copy(coverUrl = entry.coverUrl?.let(::secureMediaUrl))
    }

    fun add(sourceUrl: String, result: ParseResult, id: Long = System.currentTimeMillis()): List<HistoryEntry> {
        val media = result.items.firstOrNull() ?: return load()
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(
            id = id,
            sourceUrl = sourceUrl,
            platform = result.platform,
            title = result.title,
            authorName = result.authorName,
            authorAvatarUrl = result.authorAvatarUrl,
            audio = result.audio,
            durationSeconds = result.durationSeconds,
            sizeLabel = result.sizeLabel,
            publishTime = result.publishTime,
            quality = result.quality,
            providerName = result.providerName,
            coverUrl = result.coverUrl,
            mediaUrl = media.url,
            mediaExpiresAtEpochMillis = media.expiresAtEpochMillis,
            mediaWidth = media.width,
            mediaHeight = media.height,
            createdAt = now,
            mediaItems = result.items
        )
        val updated = upsert(entry)
        save(updated)
        return updated
    }

    fun addFailure(sourceUrl: String, platform: Platform, message: String, id: Long = System.currentTimeMillis()): List<HistoryEntry> =
        addStatus(sourceUrl, platform, HistoryStatus.ERROR, message, id)

    fun addCancelled(sourceUrl: String, platform: Platform, id: Long = System.currentTimeMillis()): List<HistoryEntry> =
        addStatus(sourceUrl, platform, HistoryStatus.CANCELLED, "用户停止了解析", id)

    fun delete(id: Long): List<HistoryEntry> = load().filterNot { it.id == id }.also(::save)

    fun clear(): List<HistoryEntry> = emptyList<HistoryEntry>().also(::save)

    private fun save(items: List<HistoryEntry>) {
        preferences.edit().putString(KEY, json.encodeToString(items)).apply()
    }

    private fun upsert(entry: HistoryEntry): List<HistoryEntry> {
        return mergeHistoryEntry(load(), entry, MAX_ITEMS)
    }

    private fun addStatus(sourceUrl: String, platform: Platform, status: HistoryStatus, message: String, id: Long): List<HistoryEntry> {
        val now = System.currentTimeMillis()
        val updated = upsert(HistoryEntry(
            id = id,
            sourceUrl = sourceUrl,
            platform = platform,
            createdAt = now,
            status = status,
            errorMessage = message
        ))
        save(updated)
        return updated
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 50
    }
}

internal fun mergeHistoryEntry(existing: List<HistoryEntry>, entry: HistoryEntry, limit: Int): List<HistoryEntry> =
    if (existing.any { it.id == entry.id }) {
        existing.map { if (it.id == entry.id) entry.copy(createdAt = it.createdAt) else it }
    } else (listOf(entry) + existing).take(limit)
