package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ButtonFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun videoRippleDoesNotPaintTouchPaddingAndClickFiresOnce() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.background(Color.White)) {
                ChatSaveAction("保存视频", Icons.Default.Download,
                    Modifier.width(104.dp).testTag("video-save")) { clicks++ }
            }
        }
        val action = compose.onNodeWithTag("video-save")
        val before = action.captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        val pressed = action.captureToImage().toPixelMap()
        assertEquals(before[before.width / 2, 1], pressed[pressed.width / 2, 1])
        assertEquals(before[before.width / 2, before.height - 2], pressed[pressed.width / 2, pressed.height - 2])
        action.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun expandAndCollapseKeepTheSameButtonWidth() {
        compose.setContent {
            Box(Modifier.width(240.dp)) { ExpandableResultText(List(8) { "这是作品说明" }.joinToString("\n")) }
        }
        val before = compose.onNodeWithText("展开全文").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("展开全文").performClick()
        compose.waitForIdle()
        val after = compose.onNodeWithText("收起").fetchSemanticsNode().boundsInRoot.width
        assertEquals(before, after, .5f)
    }
}
