package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResultFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun feedbackKeepsItsContentUntilThePopupIsRemoved() {
        var feedback by mutableStateOf<String?>("已保存到相册")
        compose.setContent {
            ImageSaveControl(ImageSavePhase.Idle, true, false, false, false, feedback,
                Modifier.width(104.dp), onClick = {})
        }
        compose.waitForIdle()
        val initial = compose.onNodeWithTag("image-save-feedback").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { feedback = null }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("image-save-feedback").assertTextEquals("已保存到相册")
        assertTrue(compose.onNodeWithTag("image-save-feedback").fetchSemanticsNode().boundsInRoot.width > initial.width * .9f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("image-save-feedback").assertDoesNotExist()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        compose.mainClock.autoAdvance = true
    }

    @Test fun savingAndFeedbackDoNotResizeTheAction() {
        var phase by mutableStateOf(ImageSavePhase.Idle)
        compose.setContent {
            ImageSaveControl(phase, enabled = phase != ImageSavePhase.Saving,
                compact = false, dark = false, grouped = false,
                feedback = if (phase == ImageSavePhase.Saved) "已保存到相册" else null,
                modifier = Modifier.width(104.dp).testTag("save-action"), onClick = {})
        }
        val initial = compose.onNodeWithTag("save-action").fetchSemanticsNode().boundsInRoot
        listOf(ImageSavePhase.Saving, ImageSavePhase.Saved, ImageSavePhase.Failed).forEach { next ->
            compose.runOnIdle { phase = next }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("save-action").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.width, bounds.width, .5f)
            assertEquals(initial.height, bounds.height, .5f)
        }
    }

    @Test fun textExpansionHasIntermediateHeightAndCanReverse() {
        compose.setContent {
            Box(Modifier.width(240.dp)) {
                ExpandableResultText(List(12) { "第 ${it + 1} 行作品说明" }.joinToString("\n"))
            }
        }
        fun height() = compose.onNodeWithTag("result-text-viewport").fetchSemanticsNode().boundsInRoot.height
        val collapsed = height()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("展开全文").performClick()
        compose.mainClock.advanceTimeBy(80)
        val intermediate = height()
        assertTrue(intermediate > collapsed)
        compose.onNodeWithText("收起").performClick()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(collapsed, height(), 1f)
        compose.mainClock.autoAdvance = true
    }
}
