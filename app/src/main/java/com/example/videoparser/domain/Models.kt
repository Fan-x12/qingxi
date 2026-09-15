package com.example.videoparser.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val label: String) {
    DOUYIN("抖音"), KUAISHOU("快手"), XIAOHONGSHU("小红书"), UNKNOWN("未知平台")
}

@Serializable
enum class MediaType { VIDEO, IMAGE_SET, LIVE_PHOTO }

data class ParseRequest(val sourceUrl: String, val platform: Platform)

@Serializable
data class MediaItem(
    val type: MediaType,
    val url: String,
    val previewUrl: String? = null,
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val expiresAtEpochMillis: Long? = null
)

@Serializable
data class AudioTrack(
    val title: String? = null,
    val author: String? = null,
    val url: String,
    val coverUrl: String? = null
)

data class ParseResult(
    val platform: Platform,
    val title: String?,
    val coverUrl: String?,
    val items: List<MediaItem>,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val audio: AudioTrack? = null,
    val durationSeconds: Double? = null,
    val sizeLabel: String? = null,
    val publishTime: String? = null,
    val quality: String? = null,
    val providerName: String? = null
)

sealed interface ParseState {
    data object Idle : ParseState
    data class Loading(val platform: Platform, val attempt: Int, val provider: String) : ParseState
    data class Success(val value: ParseResult) : ParseState
    data class Error(val message: String, val details: List<String> = emptyList()) : ParseState
}
