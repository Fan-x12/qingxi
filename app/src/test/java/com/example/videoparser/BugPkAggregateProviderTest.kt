package com.example.videoparser

import com.example.videoparser.data.provider.BugPkAggregateProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class BugPkAggregateProviderTest {
    @Test
    fun requestUrlUsesDocumentedGatewayAndQueryAuthentication() {
        val provider = BugPkAggregateProvider(apiKey = "test-key")

        val url = provider.requestUrl("https://v.douyin.com/example/?a=1")

        assertEquals("api-new.ifphp.com", url.host)
        assertEquals("/api/dyjx", url.encodedPath)
        assertEquals("test-key", url.queryParameter("key"))
        assertEquals("https://v.douyin.com/example/?a=1", url.queryParameter("url"))
    }
}
