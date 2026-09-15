package com.example.videoparser

import com.example.videoparser.data.provider.IfphpDouyinProvider
import com.example.videoparser.domain.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class IfphpDouyinProviderTest {
    private val provider = IfphpDouyinProvider()

    @Test
    fun livePhotoKeepsItsVideoAndCoverTogether() {
        val result = provider.mapResponse(Json.parseToJsonElement("""
            {"data":{"type":"live","live_photo":[{
                "video_url":"https://example.com/motion.mp4",
                "cover_url":"https://example.com/still.jpg"
            }]}}
        """), "https://v.douyin.com/example/")

        val item = result.items.single()
        assertEquals(MediaType.LIVE_PHOTO, item.type)
        assertEquals("https://example.com/motion.mp4", item.url)
        assertEquals("https://example.com/still.jpg", item.previewUrl)
    }

    @Test
    fun topLevelLiveVideoUsesTopLevelCover() {
        val result = provider.mapResponse(Json.parseToJsonElement("""
            {"data":{
                "type":"live_photo",
                "video":"https://example.com/motion.mp4",
                "poster":"https://example.com/poster.jpg"
            }}
        """), "https://v.douyin.com/example/")

        val item = result.items.single()
        assertEquals(MediaType.LIVE_PHOTO, item.type)
        assertEquals("https://example.com/poster.jpg", item.previewUrl)
        assertEquals("https://example.com/poster.jpg", result.coverUrl)
    }
}
