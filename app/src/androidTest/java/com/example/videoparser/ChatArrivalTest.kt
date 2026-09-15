package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class ChatArrivalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fastResponseWaitsForMessageEntranceAndUsesLatestResult() {
        compose.mainClock.autoAdvance = false
        val phase = mutableStateOf("waiting")
        compose.setContent {
            Column(Modifier.width(320.dp)) {
                MessageArrival(true) { Text("https://example.com/video", Modifier.testTag("link")) }
                ResponseEntrance(phase.value, true, { it }) { Text(it, Modifier.testTag("response")) }
            }
        }
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag("link").assertExists()
        compose.onNodeWithTag("response").assertDoesNotExist()
        compose.runOnIdle { phase.value = "result" }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("result").assertIsDisplayed()
        compose.onNodeWithText("waiting").assertDoesNotExist()
    }

    @Test fun historicalResponseIsNotDelayed() {
        compose.setContent {
            ResponseEntrance("history", false, { it }) { Text(it) }
        }
        compose.onNodeWithText("history").assertIsDisplayed()
    }
}
