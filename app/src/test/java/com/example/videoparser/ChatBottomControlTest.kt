package com.example.videoparser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBottomControlTest {
    @Test fun smallScrollDoesNotShowButton() {
        assertFalse(shouldShowChatBottom(true, 60f, false, 180f, 64f, false))
    }
    @Test fun deliberateScrollBeyondThresholdShowsButton() {
        assertTrue(shouldShowChatBottom(true, 181f, false, 180f, 64f, false))
        assertFalse(shouldShowChatBottom(true, 180f, false, 180f, 64f, false))
    }
    @Test fun keyboardOrResultGrowthWithoutGestureDoesNotShowButton() {
        assertFalse(shouldShowChatBottom(false, 600f, false, 180f, 64f, false))
        assertFalse(shouldShowChatBottom(false, 0f, true, 180f, 64f, false))
    }
    @Test fun visibleButtonUsesSmallerHideThreshold() {
        assertTrue(shouldShowChatBottom(true, 150f, false, 180f, 64f, true))
        assertFalse(shouldShowChatBottom(true, 64f, false, 180f, 64f, true))
    }
    @Test fun latestResponseEntirelyBelowViewportShowsButton() {
        assertTrue(shouldShowChatBottom(true, 0f, true, 180f, 64f, false))
    }
}
