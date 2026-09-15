package com.example.videoparser

import com.example.videoparser.domain.Platform
import com.example.videoparser.domain.PlatformDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformDetectorTest {
    @Test fun detectsDouyin() {
        assertEquals(Platform.DOUYIN, PlatformDetector.detect("https://v.douyin.com/abc/"))
    }

    @Test fun extractsDouyinUrlFromShareText() {
        val shareText = "复制打开抖音，看看这个视频 https://v.douyin.com/abc123/ 作品分享"
        assertEquals("https://v.douyin.com/abc123/", PlatformDetector.clean(shareText))
        assertEquals(Platform.DOUYIN, PlatformDetector.detect(shareText))
    }

    @Test fun removesTrailingChinesePunctuation() {
        assertEquals("https://v.douyin.com/abc123/", PlatformDetector.clean("https://v.douyin.com/abc123/。"))
    }

    @Test fun detectsKuaishou() {
        assertEquals(Platform.KUAISHOU, PlatformDetector.detect("https://www.kuaishou.com/short-video/abc"))
    }

    @Test fun extractsAndRecognizesXiaohongshuCnShare() {
        val share = "原图直出 | 南昌竟然有此等仙品草地 江中药谷的草坪修... https://xhslink.cn/o/19jTIibfxRg 这篇笔记在【小红书】等你发现~"
        assertEquals("https://xhslink.cn/o/19jTIibfxRg", PlatformDetector.clean(share))
        assertEquals(Platform.XIAOHONGSHU, PlatformDetector.detect(share))
        assertTrue(PlatformDetector.isHttpUrl(share))
    }

    @Test fun supportsExistingXiaohongshuHostsWithoutMatchingImpostors() {
        for (url in listOf("https://xhslink.com/example", "https://www.xiaohongshu.com/explore/example")) {
            assertEquals(Platform.XIAOHONGSHU, PlatformDetector.detect(url))
        }
        for (url in listOf("https://notxhslink.cn/o/example", "https://xhslink.cn.example.com/o/example")) {
            assertEquals(Platform.UNKNOWN, PlatformDetector.detect(url))
        }
    }

    @Test fun rejectsUnknownAndInvalidUrls() {
        assertEquals(Platform.UNKNOWN, PlatformDetector.detect("https://example.com/video"))
        assertTrue(!PlatformDetector.isHttpUrl("not a url"))
    }
}
