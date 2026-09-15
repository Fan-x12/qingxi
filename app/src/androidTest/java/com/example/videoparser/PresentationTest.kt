package com.example.videoparser

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videoparser.domain.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun model() = MainViewModel(context.applicationContext as Application)
    private val result = ParseResult(Platform.DOUYIN, "留住沿途的风景，让每一次出发都有回响", null,
        listOf(MediaItem(MediaType.VIDEO, "http://127.0.0.1:9/test.mp4", width = 1080, height = 1920)), "旅行记录")

    private fun orientation(value: Int) {
        compose.activity.requestedOrientation = value
        compose.waitUntil(5_000) {
            val config = compose.activity.resources.configuration
            if (value == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) config.screenWidthDp > config.screenHeightDp
            else config.screenHeightDp > config.screenWidthDp
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun streamedParagraphKeepsItsHeight() {
        orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val vm = model()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).padding(horizontal = 24.dp, vertical = 70.dp)) {
                    ThinkingBubble(ParseState.Loading(Platform.DOUYIN, 1, "test"), palette())
                }
            }
        }
        val initial = compose.onNodeWithTag("parsing-bubble").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithText("已识别抖音链接，正在获取视频信息…", substring = true).assertExists()
        assertTrue(initial == compose.onNodeWithTag("parsing-bubble").fetchSemanticsNode().boundsInRoot)
        screenshot("streaming")
    }

    private fun showResult() {
        val vm = model()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    ResultBubble(result, null, vm, context, palette())
                }
            }
        }
        compose.onNodeWithText("保存视频").assertExists()
        compose.onNodeWithText("复制链接").assertExists()
        compose.onNodeWithContentDescription("展开预览").assertDoesNotExist()
    }

    @Test fun portraitResultShowsActionsWithoutExpansion() {
        orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        showResult()
        screenshot("result-portrait")
    }

    @Test fun landscapeResultUsesColumns() {
        orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        showResult()
        val play = compose.onNodeWithText("播放预览").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(result.title!!).fetchSemanticsNode().boundsInRoot
        assertTrue("Wide layout should place the details beside the video", title.left > play.right)
        screenshot("result-landscape")
    }

    @Test fun controlsStayOutsideVideoInBothPlayerModes() {
        orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        showResult()
        compose.onNodeWithContentDescription("播放视频").performClick()
        fun assertSeparate() {
            val video = compose.onNodeWithTag("video-frame").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("transport-controls").fetchSemanticsNode().boundsInRoot
            assertTrue("Controls must never cover the video", controls.top >= video.bottom - 1f)
        }
        assertSeparate()
        screenshot("player-inline")
        compose.onNodeWithContentDescription("全屏播放").performClick()
        assertSeparate()
        screenshot("player-fullscreen")
        compose.onNodeWithContentDescription("退出全屏").performClick()
        assertSeparate()
    }
}
