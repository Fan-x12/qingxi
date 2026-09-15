package com.example.videoparser

import com.example.videoparser.data.history.HistoryEntry
import com.example.videoparser.data.history.HistoryStatus
import com.example.videoparser.data.history.mergeHistoryEntry
import com.example.videoparser.domain.Platform
import com.example.videoparser.domain.AudioTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HistoryEntryTest {
    @Test fun oldHttpHistoryRestoresSecureLiveMediaAndLegacyVideo() {
        val image = "http://sns-webpic-qc.xhscdn.com/still.jpg?sign=A%2fb"
        val video = "http://sns-video-zl.xhscdn.com/live.mp4?sign=X+Y"
        val legacy = entry(1).copy(platform = Platform.XIAOHONGSHU, coverUrl = image, mediaUrl = video)
        val live = legacy.copy(mediaItems = listOf(com.example.videoparser.domain.MediaItem(
            com.example.videoparser.domain.MediaType.LIVE_PHOTO, video, image
        )))
        listOf(legacy, live).forEach { saved ->
            val result = Json.decodeFromString<HistoryEntry>(Json.encodeToString(saved)).toParseResult()!!
            assertEquals(video.replaceFirst("http:", "https:"), result.items.single().url)
            assertEquals(image.replaceFirst("http:", "https:"), result.items.single().previewUrl)
            assertEquals(image.replaceFirst("http:", "https:"), result.coverUrl)
        }
    }

    private fun entry(id: Long) = HistoryEntry(
        id = id, sourceUrl = "https://v.douyin.com/same/", platform = Platform.DOUYIN,
        mediaUrl = "https://example.com/video.mp4", createdAt = id
    )

    @Test fun repeatedLinkKeepsBothTurns() {
        val updated = mergeHistoryEntry(listOf(entry(1)), entry(2), 50)
        assertEquals(listOf(2L, 1L), updated.map { it.id })
    }

    @Test fun retryUpdatesInPlaceAndPreservesTimestamp() {
        val old = listOf(entry(3), entry(2).copy(status = HistoryStatus.ERROR), entry(1))
        val updated = mergeHistoryEntry(old, entry(2).copy(createdAt = 99), 50)
        assertEquals(listOf(3L, 2L, 1L), updated.map { it.id })
        assertEquals(2L, updated[1].createdAt)
        assertEquals(HistoryStatus.SUCCESS, updated[1].status)
    }

    @Test fun historyLimitDropsOnlyOldestTurn() {
        assertEquals(listOf(3L, 2L), mergeHistoryEntry(listOf(entry(2), entry(1)), entry(3), 2).map { it.id })
    }

    @Test fun savedDimensionsKeepPreviewAspectRatio() {
        val video = entry(1).copy(mediaWidth = 1080, mediaHeight = 1920).toParseResult()!!.items.single()
        assertEquals(1080, video.width)
        assertEquals(1920, video.height)
    }

    @Test fun legacyHistoryStillLoadsWithoutDimensions() {
        val saved = """{"id":1,"sourceUrl":"https://v.douyin.com/same/","platform":"DOUYIN","mediaUrl":"https://example.com/video.mp4","createdAt":1}"""
        val restored = Json.decodeFromString<HistoryEntry>(saved)
        assertNotNull(restored.toParseResult())
        assertNull(restored.mediaWidth)
    }

    @Test fun expiredMediaCannotBeReused() {
        assertNull(entry(1).copy(mediaExpiresAtEpochMillis = 10).toParseResult(now = 10))
    }

    @Test fun extendedMetadataSurvivesHistorySerialization() {
        val audio = AudioTrack("作品原声", "作者", "https://example.com/audio.mp3", "https://example.com/music.jpg")
        val original = entry(1).copy(
            authorName = "作者",
            authorAvatarUrl = "https://example.com/avatar.jpg",
            audio = audio,
            durationSeconds = 12.5,
            sizeLabel = "3.2MB",
            publishTime = "2026-09-10 10:30:00",
            quality = "original"
        )
        val restored = Json.decodeFromString<HistoryEntry>(Json.encodeToString(original)).toParseResult()!!
        assertEquals("https://example.com/avatar.jpg", restored.authorAvatarUrl)
        assertEquals(audio, restored.audio)
        assertEquals(12.5, restored.durationSeconds!!, 0.0)
        assertEquals("3.2MB", restored.sizeLabel)
        assertEquals("2026-09-10 10:30:00", restored.publishTime)
        assertEquals("original", restored.quality)
    }
}
