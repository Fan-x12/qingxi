package com.example.videoparser.domain

import java.net.URI

// These CDN hosts were verified to serve both JPEG and MP4 over HTTPS.
private val httpsMediaHosts = setOf(
    "sns-webpic-qc.xhscdn.com",
    "sns-video-zl.xhscdn.com",
    "sns-avatar-qc.xhscdn.com"
)

internal fun secureMediaUrl(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true)) return url
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.host?.lowercase() !in httpsMediaHosts || uri.rawUserInfo != null || uri.port != -1) return url
    // Do not re-encode paths or signed query parameters.
    return "https://" + url.substring(7)
}

internal fun ParseResult.withSecureMediaUrls(): ParseResult = copy(
    coverUrl = coverUrl?.let(::secureMediaUrl),
    authorAvatarUrl = authorAvatarUrl?.let(::secureMediaUrl),
    items = items.map { it.copy(url = secureMediaUrl(it.url), previewUrl = it.previewUrl?.let(::secureMediaUrl)) },
    audio = audio?.let { it.copy(url = secureMediaUrl(it.url), coverUrl = it.coverUrl?.let(::secureMediaUrl)) }
)
