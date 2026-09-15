package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.videoparser.domain.MediaItem
import com.example.videoparser.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitControlsUseCanvasWidthEvenWhenVideoRatioChanges() {
        val ratio = mutableFloatStateOf(9f / 16f)
        compose.setContent {
            Box(Modifier.width(280.dp).height(240.dp).testTag("canvas")) {
                MediaPreview(MediaItem(MediaType.VIDEO, "http://127.0.0.1:9/test.mp4"), null,
                    Modifier.fillMaxSize(), ratio.floatValue, {})
            }
        }
        compose.onNodeWithContentDescription("播放视频").performClick()
        for (value in listOf(9f / 16f, 1f / 3f, 16f / 9f)) {
            compose.runOnIdle { ratio.floatValue = value }
            val canvas = compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("transport-controls", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(canvas.width, controls.width, 1f)
            assertTrue(controls.bottom <= canvas.bottom + 1f)
            assertTrue(controls.top >= canvas.top)
            compose.onNodeWithContentDescription("全屏播放").assertIsDisplayed()
        }
    }
}
