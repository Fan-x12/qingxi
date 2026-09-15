package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewportTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun focusingEmptyComposerDoesNotChangeReservedHeight() {
        var requestedHeight = 0
        compose.setContent {
            Composer("", isLoading = false, colors = palette(),
                onValueChange = {}, onPaste = {}, onCancel = {},
                onSurfaceHeightChanged = { requestedHeight = it }, onSend = {})
        }
        var initialHeight = 0
        compose.runOnIdle { initialHeight = requestedHeight }
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(initialHeight, requestedHeight) }
        compose.onNodeWithText("发送一个分享链接…").assertIsDisplayed()
    }

    @Test fun composerSizesWithTextAndCapsAtFiveLines() {
        var surfaceHeight = 0
        compose.setContent {
            var value by remember { mutableStateOf("") }
            Box(Modifier.width(320.dp)) {
                Composer(value, isLoading = false, colors = palette(),
                    onValueChange = { value = it }, onPaste = {}, onCancel = {},
                    onSurfaceHeightChanged = { surfaceHeight = it }, onSend = {})
            }
        }
        val field = compose.onNode(hasSetTextAction())
        var emptyHeight = 0
        compose.runOnIdle { emptyHeight = surfaceHeight }
        field.performTextReplacement("Hello")
        compose.runOnIdle { assertEquals(emptyHeight, surfaceHeight) }
        field.performTextReplacement("Hello\nworld")
        var twoLineHeight = 0
        compose.runOnIdle {
            twoLineHeight = surfaceHeight
            assertTrue(twoLineHeight > emptyHeight)
        }
        field.performTextReplacement(List(5) { "Line" }.joinToString("\n"))
        var fiveLineHeight = 0
        compose.runOnIdle {
            fiveLineHeight = surfaceHeight
            assertTrue(fiveLineHeight > twoLineHeight)
        }
        field.performTextReplacement(List(20) { "Line" }.joinToString("\n"))
        compose.runOnIdle { assertEquals(fiveLineHeight, surfaceHeight) }
        field.performTextClearance()
        compose.runOnIdle { assertEquals(emptyHeight, surfaceHeight) }
    }

    @Test fun whitespaceDoesNotOverlayPlaceholder() {
        compose.setContent {
            var value by remember { mutableStateOf("") }
            Composer(value, isLoading = false, colors = palette(),
                onValueChange = { value = it }, onPaste = {}, onCancel = {},
                onSurfaceHeightChanged = {}, onSend = {})
        }
        compose.onNode(hasSetTextAction()).performTextReplacement(" \n")
        compose.onNodeWithText("发送一个分享链接…").assertDoesNotExist()
    }

    @Test fun composerAnimatesViewportAndSurfaceTogether() {
        val value = mutableStateOf("Hello")
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                Composer(value.value, isLoading = false, colors = palette(),
                    onValueChange = { value.value = it }, onPaste = {}, onCancel = {},
                    onSurfaceHeightChanged = {}, onSend = {})
            }
        }
        val surface = compose.onNodeWithTag("composer-surface")
        val viewport = compose.onNodeWithTag("composer-text-viewport")
        val initial = surface.fetchSemanticsNode().boundsInRoot
        val initialViewport = viewport.fetchSemanticsNode().boundsInRoot
        val chromeHeight = initial.height - initialViewport.height
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { value.value = "Hello\nsecond\nthird\nfourth\nfifth" }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val expanding = surface.fetchSemanticsNode().boundsInRoot
        val expandingViewport = viewport.fetchSemanticsNode().boundsInRoot
        assertTrue(expanding.height > initial.height)
        assertEquals(initial.bottom, expanding.bottom, 1f)
        assertEquals(chromeHeight, expanding.height - expandingViewport.height, 1f)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val expanded = surface.fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height > expanding.height)
        compose.runOnIdle { value.value = "Hello" }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val shrinking = surface.fetchSemanticsNode().boundsInRoot
        assertTrue(shrinking.height > initial.height && shrinking.height < expanded.height)
        assertEquals(initial.bottom, shrinking.bottom, 1f)
        // Retarget before collapse completes: the new edit must continue from the visible size.
        compose.runOnIdle { value.value = "Hello\nsecond\nthird" }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val retargeted = surface.fetchSemanticsNode().boundsInRoot
        assertTrue(retargeted.height > initial.height && retargeted.height < expanded.height)
        assertEquals(initial.bottom, retargeted.bottom, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun tallResponseKeepsBeginningVisibleAndUserScrollWins() {
        val height = mutableStateOf(120.dp)
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            list = rememberLazyListState()
            val control = rememberChatBottomControl(list)
            KeepLatestResponseReadable(list, control)
            LazyColumn(Modifier.width(300.dp).height(400.dp).nestedScroll(control.connection).testTag("list"),
                state = list, reverseLayout = true, contentPadding = PaddingValues(top = 40.dp, bottom = 60.dp)) {
                item(key = "latest") { Box(Modifier.fillMaxWidth().height(height.value).background(Color.Gray).testTag("response")) }
                item(key = "history") { Box(Modifier.fillMaxWidth().height(600.dp)) }
            }
        }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemScrollOffset); height.value = 700.dp }
        compose.waitForIdle()
        compose.runOnIdle {
            val info = list.layoutInfo
            val usable = info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding
            assertEquals(latestResponseOffset(info.visibleItemsInfo.first { it.index == 0 }.size, usable), list.firstVisibleItemScrollOffset)
        }
        // Drag toward the bottom of the long result; automatic top alignment must stop.
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        var userOffset = 0
        compose.runOnIdle { userOffset = list.firstVisibleItemScrollOffset; height.value = 850.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(userOffset, list.firstVisibleItemScrollOffset) }
    }
}
