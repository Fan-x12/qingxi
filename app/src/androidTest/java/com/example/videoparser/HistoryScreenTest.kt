package com.example.videoparser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.videoparser.data.history.HistoryEntry
import com.example.videoparser.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val entries = listOf(
        HistoryEntry(1, "https://example.com/1", Platform.DOUYIN, title = "First", createdAt = 1000),
        HistoryEntry(2, "https://example.com/2", Platform.DOUYIN, title = "Second", createdAt = 2000)
    )

    @Test fun singleDeleteRequiresConfirmationAndCanBeCancelled() {
        val deleted = mutableListOf<Long>()
        compose.setContent { HistoryScreen(entries, palette(), {}, {}, { deleted += it }, {}) }
        compose.onNodeWithContentDescription("删除记录：First").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithContentDescription("删除记录：First").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf(1L), deleted) }
    }

    @Test fun filteredSelectionDeletesOnlyMatchingRecords() {
        val deleted = mutableListOf<Long>()
        compose.setContent { HistoryScreen(entries, palette(), {}, {}, { deleted += it }, {}) }
        compose.onNode(hasSetTextAction()).performTextInput("Second")
        compose.onNodeWithText("选择", substring = false).performClick()
        compose.onNodeWithText("全选当前列表").performClick()
        compose.onNodeWithText("删除 (1)").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf(2L), deleted) }
    }

    @Test fun clearHistoryRequiresConfirmation() {
        var cleared = false
        compose.setContent { HistoryScreen(entries, palette(), {}, {}, {}, { cleared = true }) }
        compose.onNodeWithContentDescription("历史记录选项").performClick()
        compose.onNodeWithText("清空历史").performClick()
        compose.runOnIdle { assertTrue(!cleared) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(!cleared) }
        compose.onNodeWithContentDescription("历史记录选项").performClick()
        compose.onNodeWithText("清空历史").performClick()
        compose.onNodeWithText("清空", substring = false).performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }

    @Test fun selectionRowExposesCheckedStateWithoutOpeningRecord() {
        var opened = false
        compose.setContent { HistoryScreen(entries, palette(), {}, { opened = true }, {}, {}) }
        compose.onNodeWithText("选择", substring = false).performClick()
        val row = compose.onNodeWithContentDescription("选择记录：First")
        row.assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(!opened) }
        row.performClick().assertIsOff()
    }
}
