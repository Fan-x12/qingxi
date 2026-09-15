package com.example.videoparser

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SecondaryMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val expanded = mutableStateOf(true)
    private var clicks = 0
    private var dismissals = 0
    private val rowCount = mutableStateOf(3)
    private var hostView: View? = null

    private fun showMenu(bottom: Boolean = false, count: Int = 3) {
        rowCount.value = count
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(StudioStyle.canvas).padding(24.dp)) {
                    Box(Modifier.align(if (bottom) Alignment.BottomEnd else Alignment.TopEnd).size(48.dp)) {
                        AppIconButton(onClick = { expanded.value = !expanded.value },
                            modifier = Modifier.testTag("menu-trigger")) { }
                        SecondaryMenuPopup(expanded.value, {
                            dismissals++
                            expanded.value = false
                        }, alignToEnd = true) {
                            repeat(rowCount.value) { index ->
                                if (index > 0) SecondaryMenuDivider()
                                SecondaryMenuItem("接口 $index", Icons.Default.CloudQueue, selected = index == 0) {
                                    clicks++
                                    expanded.value = false
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    // Inject through WindowManager; semantic clicks bypass the very popup touch interception
    // this regression test needs to exercise.
    private fun tapTriggerThroughWindow() {
        val center = compose.onNodeWithTag("menu-trigger").fetchSemanticsNode().boundsInRoot.center
        val location = IntArray(2)
        compose.runOnIdle { hostView!!.getLocationOnScreen(location) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                center.x + location[0], center.y + location[1], 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun triggerCanCloseAndReopenWithoutWaitingForAnimation() {
        showMenu()
        compose.mainClock.autoAdvance = false
        try {
            repeat(3) {
                tapTriggerThroughWindow()
                compose.runOnIdle { assertFalse("Trigger should close the menu immediately", expanded.value) }
                compose.onNodeWithTag("secondary-menu").assertExists()
                tapTriggerThroughWindow()
                compose.runOnIdle { assertTrue("Closing popup must not block a new tap", expanded.value) }
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithText("接口 1").performClick()
        assertEquals(1, clicks)
    }

    @Test fun menuHeightTracksChangingRowCount() {
        showMenu(count = 2)
        fun height() = compose.onNodeWithTag("secondary-menu").fetchSemanticsNode().boundsInRoot.height
        val twoRows = height()
        compose.runOnIdle { rowCount.value = 3 }
        val threeRows = height()
        compose.runOnIdle { rowCount.value = 4 }
        val fourRows = height()
        assertTrue(threeRows > twoRows)
        assertEquals(threeRows - twoRows, fourRows - threeRows, 2f)
        compose.runOnIdle { rowCount.value = 2 }
        assertEquals(twoRows, height(), 1f)
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun selectionIsAccessibleAndCommitsOnce() {
        showMenu()
        compose.onNodeWithContentDescription("当前选项").assertExists()
        screenshot("secondary-menu-down")
        compose.onNodeWithText("接口 1").performClick()
        compose.onNodeWithTag("secondary-menu").assertDoesNotExist()
        assertEquals(1, clicks)
    }

    @Test fun panelRevealCanReverseDuringClosing() {
        showMenu()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded.value = false }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("secondary-menu").assertExists()
        screenshot("secondary-menu-closing")
        compose.runOnIdle { expanded.value = true }
        compose.mainClock.advanceTimeBy(96)
        screenshot("secondary-menu-reopening")
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("接口 1").assertIsEnabled().assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun backDismissesMenu() {
        showMenu(bottom = true)
        screenshot("secondary-menu-up")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        )
        compose.waitUntil(3_000) { !expanded.value }
        compose.waitForIdle()
        compose.onNodeWithTag("secondary-menu").assertDoesNotExist()
        assertEquals(0, clicks)
    }
}
