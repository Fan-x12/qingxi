package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaThumbnailStripTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun stripStartsOnLeftAndKeepsDistantSelectionVisible() {
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        compose.setContent {
            pager = rememberPagerState { 20 }
            scope = rememberCoroutineScope()
            Column(Modifier.width(320.dp)) {
                HorizontalPager(pager, Modifier.height(200.dp)) { Box(Modifier.fillMaxSize()) }
                MediaThumbnailStrip(pager) { }
            }
        }
        compose.runOnIdle { scope.launch { pager.scrollToPage(19) } }
        compose.waitForIdle()
        compose.onNodeWithTag("media-thumbnail-19").assertIsDisplayed().assertIsSelected()
        val viewport = compose.onNodeWithTag("media-filmstrip").fetchSemanticsNode().boundsInRoot
        val selected = compose.onNodeWithTag("media-thumbnail-19").fetchSemanticsNode().boundsInRoot
        assertTrue(selected.right <= viewport.right)
        compose.onNodeWithTag("media-thumbnail-18").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(18, pager.currentPage) }
        compose.onNodeWithTag("media-thumbnail-18").assertIsSelected()
        compose.runOnIdle { scope.launch { pager.scrollToPage(0) } }
        compose.waitForIdle()
        val first = compose.onNodeWithTag("media-thumbnail-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(first.center.x < viewport.center.x)
    }
}
