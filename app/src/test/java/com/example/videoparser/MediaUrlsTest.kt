package com.example.videoparser

import com.example.videoparser.domain.secureMediaUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrlsTest {
    @Test fun preservesSignedPathAndQueryExactly() {
        val tail = "sns-webpic-qc.xhscdn.com/a%2Fb/作品.jpg?sign=Ab%2fc+X&n=1&n=2#preview"
        assertEquals("https://$tail", secureMediaUrl("http://$tail"))
    }

    @Test fun leavesUnverifiedHostsAndLocalFilesUnchanged() {
        listOf(
            "http://example.com/a.jpg",
            "http://sns-video-zl.xhscdn.com.example.com/a.mp4",
            "http://sns-video-zl.xhscdn.com@other.com/a.mp4",
            "http://sns-video-zl.xhscdn.com:8080/a.mp4",
            "https://sns-video-zl.xhscdn.com/a.mp4",
            "/storage/emulated/0/Pictures/a.jpg"
        ).forEach { assertEquals(it, secureMediaUrl(it)) }
    }
}
