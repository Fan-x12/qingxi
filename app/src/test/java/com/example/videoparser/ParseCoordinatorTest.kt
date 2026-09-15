package com.example.videoparser

import com.example.videoparser.data.provider.ParseProvider
import com.example.videoparser.domain.MediaItem
import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.ParseCoordinator
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseCoordinatorTest {
    @Test
    fun nextRequestImmediatelyRetriesRecoveredPrimary() = runBlocking {
        val primary = StubProvider("主接口", Result.failure(IllegalStateException("HTTP 500")))
        val backup = StubProvider("备用接口", Result.success(successResult()))
        val coordinator = ParseCoordinator(listOf(primary, backup))
        val request = ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN)

        assertEquals("备用接口", coordinator.parse(request) { _, _ -> }.getOrThrow().providerName)
        primary.result = Result.success(successResult())
        assertEquals("主接口", coordinator.parse(request) { _, _ -> }.getOrThrow().providerName)
        assertEquals(2, primary.calls)
        assertEquals(1, backup.calls)
    }

    @Test
    fun manualRetryImmediatelyCallsSelectedProviderAfterFailure() = runBlocking {
        val selected = StubProvider("夜雨聚合解析", Result.failure(IllegalStateException("HTTP 500")))
        val coordinator = ParseCoordinator(listOf(selected))
        val request = ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN)

        assertTrue(coordinator.parse(request, selected.name) { _, _ -> }.isFailure)
        selected.result = Result.success(successResult())
        assertTrue(coordinator.parse(request, selected.name) { _, _ -> }.isSuccess)
        assertEquals(2, selected.calls)
    }

    @Test
    fun immediatelyUsesNextProviderAfterFailure() = runBlocking {
        val primary = StubProvider("主接口", Result.failure(IllegalStateException("不可用")))
        val backup = StubProvider("备用接口", Result.success(successResult()))
        val attempts = mutableListOf<String>()

        val result = ParseCoordinator(listOf(primary, backup)).parse(
            ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN)
        ) { _, provider -> attempts += provider.name }

        assertTrue(result.isSuccess)
        assertEquals(listOf("主接口", "备用接口"), attempts)
        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)
        assertEquals("备用接口", result.getOrThrow().providerName)
    }

    @Test
    fun selectedProviderIsTheOnlyProviderTried() = runBlocking {
        val primary = StubProvider("夜雨聚合解析", Result.success(successResult()))
        val backup = StubProvider("BugPk 聚合解析", Result.success(successResult()))
        val attempts = mutableListOf<String>()

        val result = ParseCoordinator(listOf(primary, backup)).parse(
            ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN),
            selectedProviderName = "BugPk 聚合解析"
        ) { _, provider -> attempts += provider.name }

        assertTrue(result.isSuccess)
        assertEquals(listOf("BugPk 聚合解析"), attempts)
        assertEquals(0, primary.calls)
        assertEquals(1, backup.calls)
    }

    @Test
    fun selectedProviderFailureDoesNotSilentlyUseAnotherProvider() = runBlocking {
        val automaticPrimary = StubProvider("夜雨聚合解析", Result.success(successResult()))
        val selected = StubProvider("BugPk 聚合解析", Result.failure(IllegalStateException("不可用")))
        val attempts = mutableListOf<String>()

        val result = ParseCoordinator(listOf(automaticPrimary, selected)).parse(
            ParseRequest("https://v.douyin.com/example/", Platform.DOUYIN),
            selectedProviderName = "BugPk 聚合解析"
        ) { _, provider -> attempts += provider.name }

        assertFalse(result.isSuccess)
        assertEquals(listOf("BugPk 聚合解析"), attempts)
        assertEquals(0, automaticPrimary.calls)
        assertEquals(1, selected.calls)
    }

    private fun successResult() = ParseResult(
        platform = Platform.DOUYIN,
        title = "ok",
        coverUrl = null,
        items = listOf(MediaItem(MediaType.VIDEO, "https://example.com/video.mp4"))
    )

    private class StubProvider(
        override val name: String,
        var result: Result<ParseResult>
    ) : ParseProvider {
        var calls = 0
        override val supportedPlatforms = setOf(Platform.DOUYIN)
        override val supportedMediaTypes = setOf(MediaType.VIDEO)

        override suspend fun parse(request: ParseRequest): Result<ParseResult> {
            calls += 1
            return result
        }
    }
}
