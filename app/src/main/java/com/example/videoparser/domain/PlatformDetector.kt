package com.example.videoparser.domain

import java.net.URI

/** 从分享文本中提取链接，并识别支持的平台。 */
object PlatformDetector {
    private val douyinHosts = setOf("douyin.com", "iesdouyin.com", "v.douyin.com")
    private val kuaishouHosts = setOf("kuaishou.com", "kuaishouapp.com", "kwai.com", "v.kuaishou.com", "v.kuaishouapp.com")
    private val xiaohongshuHosts = setOf("xiaohongshu.com", "xhslink.com", "xhslink.cn")
    private val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = setOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}',
        '。', '，', '；', '：', '！', '？', '）', '】', '》', '"', '\'', '”', '’'
    )

    /** 从完整分享文本提取首个链接，并清除句末标点。 */
    fun clean(raw: String): String {
        val candidate = urlPattern.find(raw.trim())?.value ?: raw.trim()
        return candidate.trimEnd { it in trailingPunctuation }
    }

    fun detect(raw: String): Platform {
        val value = clean(raw)
        val host = runCatching { URI(value).host?.lowercase() }.getOrNull() ?: return Platform.UNKNOWN
        return when {
            douyinHosts.any { host == it || host.endsWith(".$it") } -> Platform.DOUYIN
            kuaishouHosts.any { host == it || host.endsWith(".$it") } -> Platform.KUAISHOU
            xiaohongshuHosts.any { host == it || host.endsWith(".$it") } -> Platform.XIAOHONGSHU
            else -> Platform.UNKNOWN
        }
    }

    fun isHttpUrl(raw: String): Boolean {
        val uri = runCatching { URI(clean(raw)) }.getOrNull() ?: return false
        return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }
}
