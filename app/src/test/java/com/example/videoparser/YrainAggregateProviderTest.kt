package com.example.videoparser

import com.example.videoparser.data.provider.YrainAggregateProvider
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.Platform
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YrainAggregateProviderTest {
    private val provider = YrainAggregateProvider(apiKey = "test-key")

    @Test
    fun mapsActualXiaohongshuLiveResponseShapeToSecurePairedMedia() {
        val images = (1..4).map { "http://sns-webpic-qc.xhscdn.com/still-$it.jpg?sign=a%2Fb+X&x=1" }
        val videos = (1..4).map { "http://sns-video-zl.xhscdn.com/motion-$it.mp4?sign=Ab%2fC" }
        val root = Json.parseToJsonElement("""{
            "code":200,"msg":"解析成功","data":{
                "type":"live","url":"","cover":"${images[0]}",
                "author":{"name":"Eve","avatar":"http://sns-avatar-qc.xhscdn.com/avatar.jpg"},
                "images":[${images.joinToString { "\"$it\"" }}],
                "live_photo":[${images.indices.joinToString { """{"image":"${images[it]}","video":"${videos[it]}"}""" }}],
                "music":{"title":"","author":"","url":"","cover":""}
            }
        }""")
        val result = provider.mapResponse(root, Platform.XIAOHONGSHU)
        assertEquals(4, result.items.size)
        result.items.forEachIndexed { index, item ->
            assertEquals(MediaType.LIVE_PHOTO, item.type)
            assertEquals(videos[index].replaceFirst("http:", "https:"), item.url)
            assertEquals(images[index].replaceFirst("http:", "https:"), item.previewUrl)
        }
        assertEquals(result.items[0].previewUrl, result.coverUrl)
        assertEquals("https://sns-avatar-qc.xhscdn.com/avatar.jpg", result.authorAvatarUrl)
        assertEquals(null, result.audio)
    }

    @Test
    fun requestPlacesKeyBeforeUrl() {
        val url = provider.requestUrl("https://v.douyin.com/example/?a=1")

        assertEquals(listOf("key", "url"), url.queryParameterNames.toList())
        assertEquals("test-key", url.queryParameter("key"))
        assertEquals("https://v.douyin.com/example/?a=1", url.queryParameter("url"))
        assertTrue(url.encodedQuery.orEmpty().startsWith("key=test-key&url="))
    }

    @Test
    fun mapsVerifiedVideoResponseWithoutDuplicatingBackupUrls() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "msg":"解析成功",
              "data":{
                "type":"video",
                "title":"示例视频",
                "author":{"name":"作者","avatar":"https://example.com/avatar.jpg"},
                "cover":"https://example.com/cover.jpg",
                "url":"https://example.com/video.mp4",
                "duration":166567,
                "video_backup":["https://example.com/backup-1.mp4","https://example.com/backup-2.mp4"],
                "images":[],
                "live_photo":[],
                "music":{"title":"原声","author":"音乐作者","url":"https://example.com/audio.m4a","cover":"https://example.com/audio.jpg"}
              }
            }"""
        )

        provider.validateEnvelope(root)
        val result = provider.mapResponse(root, Platform.DOUYIN)

        assertEquals("示例视频", result.title)
        assertEquals("作者", result.authorName)
        assertEquals("https://example.com/avatar.jpg", result.authorAvatarUrl)
        assertEquals(166.567, result.durationSeconds!!, 0.0001)
        assertEquals("https://example.com/audio.m4a", result.audio?.url)
        assertEquals(1, result.items.size)
        assertEquals(MediaType.VIDEO, result.items.single().type)
        assertEquals("https://example.com/video.mp4", result.items.single().url)
    }

    @Test
    fun mapsVerifiedKuaishouVideoWithNullableOptionalFields() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "msg":"解析成功",
              "data":{
                "type":"video",
                "title":"快手真实返回样例",
                "author":null,
                "cover":"https://example.com/kuaishou-cover.jpg?token=one",
                "url":"https://example.com/kuaishou-video.mp4?token=two",
                "duration":null,
                "video_backup":[],
                "images":["https://example.com/kuaishou-cover.jpg?token=three"],
                "live_photo":[],
                "music":{"title":"","author":"","url":"","cover":""},
                "platform":"快手"
              }
            }"""
        )

        val result = provider.mapResponse(root, Platform.KUAISHOU)

        assertEquals(Platform.KUAISHOU, result.platform)
        assertEquals(null, result.authorName)
        assertEquals(null, result.audio)
        assertEquals(null, result.durationSeconds)
        assertEquals(1, result.items.size)
        assertEquals(MediaType.VIDEO, result.items.single().type)
        assertEquals("https://example.com/kuaishou-cover.jpg?token=one", result.items.single().previewUrl)
    }

    @Test
    fun videoTreatsImagesAsCoverCandidatesInsteadOfExtraMedia() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "data":{
                "type":"video",
                "url":"https://example.com/main.mp4",
                "images":["https://example.com/preview.jpg"],
                "video_backup":"https://example.com/backup.mp4"
              }
            }"""
        )

        val result = provider.mapResponse(root, Platform.XIAOHONGSHU)

        assertEquals(1, result.items.size)
        assertEquals("https://example.com/main.mp4", result.items.single().url)
        assertEquals("https://example.com/preview.jpg", result.items.single().previewUrl)
        assertEquals("https://example.com/preview.jpg", result.coverUrl)
    }

    @Test
    fun mapsImageAndLivePhotoPairs() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "data":{
                "type":"image",
                "cover":"https://example.com/still-1.jpg",
                "images":["https://example.com/still-1.jpg",{"url":"https://example.com/still-2.jpg"}],
                "live_photo":[{
                  "video_url":"https://example.com/motion-1.mp4",
                  "cover_url":"https://example.com/still-1.jpg"
                }]
              }
            }"""
        )

        val result = provider.mapResponse(root, Platform.XIAOHONGSHU)

        assertEquals(2, result.items.size)
        assertEquals(MediaType.LIVE_PHOTO, result.items[0].type)
        assertEquals("https://example.com/still-1.jpg", result.items[0].previewUrl)
        assertEquals(MediaType.IMAGE_SET, result.items[1].type)
        assertEquals("https://example.com/still-2.jpg", result.items[1].url)
    }

    @Test
    fun mapsParallelLivePhotoUrlsAndNestedArrays() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "data":{
                "type":"image",
                "images":["https://example.com/still-1.jpg","https://example.com/still-2.jpg"],
                "live_photo":[["https://example.com/motion-1.mp4"],"https://example.com/motion-2.mp4"]
              }
            }"""
        )

        val result = provider.mapResponse(root, Platform.DOUYIN)

        assertEquals(2, result.items.size)
        assertTrue(result.items.all { it.type == MediaType.LIVE_PHOTO })
        assertEquals("https://example.com/still-1.jpg", result.items[0].previewUrl)
        assertEquals("https://example.com/still-2.jpg", result.items[1].previewUrl)
    }

    @Test
    fun infersLivePhotoWhenTypeIsMissing() {
        val root = Json.parseToJsonElement(
            """{
              "code":200,
              "data":{
                "images":["https://example.com/still.jpg"],
                "live_photo":[{"motion_url":"https://example.com/motion.mp4"}]
              }
            }"""
        )

        val result = provider.mapResponse(root, Platform.XIAOHONGSHU)

        assertEquals(MediaType.LIVE_PHOTO, result.items.single().type)
        assertEquals("https://example.com/still.jpg", result.items.single().previewUrl)
    }

    @Test
    fun exposesApiErrorMessage() {
        val root = Json.parseToJsonElement("""{"code":400,"msg":"缺少 url 参数","data":null,"succ":false}""")

        val error = assertThrows(IllegalStateException::class.java) { provider.validateEnvelope(root) }

        assertTrue(error.message.orEmpty().contains("缺少 url 参数"))
    }

    @Test
    fun rejectsSuccessfulEnvelopeWithoutObjectData() {
        val root = Json.parseToJsonElement("""{"code":200,"msg":"解析成功","data":[]}""")

        val error = assertThrows(IllegalStateException::class.java) {
            provider.mapResponse(root, Platform.XIAOHONGSHU)
        }

        assertTrue(error.message.orEmpty().contains("未返回可用的数据"))
    }
}
