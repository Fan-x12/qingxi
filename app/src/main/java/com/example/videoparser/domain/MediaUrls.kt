package com.example.videoparser.domain

import java.net.URI

// 这些 CDN 域名已验证支持通过 HTTPS 访问 JPEG 和 MP4。
private val httpsMediaHosts = setOf(
    "sns-webpic-qc.xhscdn.com",
    "sns-video-zl.xhscdn.com",
    "sns-avatar-qc.xhscdn.com"
)

internal fun secureMediaUrl(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true)) return url
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.host?.lowercase() !in httpsMediaHosts || uri.rawUserInfo != null || uri.port != -1) return url
    // 保留路径和签名查询参数原样，避免重新编码导致签名失效。
    return "https://" + url.substring(7)
}

internal fun ParseResult.withSecureMediaUrls(): ParseResult = copy(
    coverUrl = coverUrl?.let(::secureMediaUrl),
    authorAvatarUrl = authorAvatarUrl?.let(::secureMediaUrl),
    items = items.map { it.copy(url = secureMediaUrl(it.url), previewUrl = it.previewUrl?.let(::secureMediaUrl)) },
    audio = audio?.let { it.copy(url = secureMediaUrl(it.url), coverUrl = it.coverUrl?.let(::secureMediaUrl)) }
)
