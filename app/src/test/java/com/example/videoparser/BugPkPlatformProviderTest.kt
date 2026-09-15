package com.example.videoparser

import com.example.videoparser.data.provider.BugPkPlatformProvider
import com.example.videoparser.data.provider.ProviderRegistry
import com.example.videoparser.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BugPkPlatformProviderTest {
    private val provider = BugPkPlatformProvider(apiKey = "test-key")
    private val request = ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN)

    @Test fun documentedEndpointEncodesKeyAndSourceSeparately() {
        val source = request.sourceUrl + "?a=1&b=2"
        val url = provider.requestUrl(source)
        assertEquals("api-new.ifphp.com", url.host)
        assertEquals("/api/svparse", url.encodedPath)
        assertEquals("test-key", url.queryParameter("key"))
        assertEquals(source, url.queryParameter("url"))
    }

    @Test fun mapsSyntheticExampleWithoutDuplicatingMusicOrBackupVideos() {
        val raw = javaClass.getResource("/bugpk-svparse-video.json")!!.readText()
        val result = provider.mapResponse(Json.parseToJsonElement(raw), request)
        assertEquals(1, result.items.size)
        assertEquals(MediaType.VIDEO, result.items.single().type)
        assertEquals("https://example.com/video.mp4", result.items.single().url)
        assertEquals(80.01, result.durationSeconds!!, .0001)
        assertEquals("3.77MB", result.sizeLabel)
        assertEquals("Example Author", result.authorName)
        assertEquals("https://example.com/music.mp3", result.audio!!.url)
        assertEquals(ProviderRegistry.BUGPK_PLATFORM_NAME, result.providerName)
    }

    @Test fun errorsAndEmptyDataDoNotProduceFakeMedia() {
        for (raw in listOf("""{"code":401,"msg":"密钥无效"}""", """{"code":200,"data":{}}""",
            """{"data":{"url":"https://example.com/video.mp4"}}""")) {
            assertTrue(runCatching { provider.mapResponse(Json.parseToJsonElement(raw), request) }.isFailure)
        }
    }

    @Test fun registryKeepsExistingProvidersAndAddsMultiPlatformFallback() {
        val providers = ProviderRegistry.default()
        assertEquals(listOf(ProviderRegistry.YRAIN_NAME, ProviderRegistry.BUGPK_NAME,
            ProviderRegistry.BUGPK_PLATFORM_NAME), providers.map { it.name })
        assertTrue(provider.supportedPlatforms.containsAll(listOf(Platform.DOUYIN, Platform.KUAISHOU, Platform.XIAOHONGSHU)))
        assertEquals("BugPk 聚合 API", ProviderRegistry.displayName(provider.name))
    }
}
