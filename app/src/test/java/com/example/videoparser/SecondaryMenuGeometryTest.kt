package com.example.videoparser

import org.junit.Assert.*
import org.junit.Test

class SecondaryMenuGeometryTest {
    @Test fun allCornersStayAnchoredAndReachFullBoundsForShortAndTallMenus() {
        for (height in listOf(46f, 140f, 280f, 460f)) {
            for (end in listOf(false, true)) for (up in listOf(false, true)) {
                var lastWidth = 0f
                var lastHeight = 0f
                for (frame in 0..100) {
                    val b = menuRevealBounds(184f, height, frame / 100f, end, up)
                    assertTrue("Fixed 20px corners must fit at every frame", b[2] - b[0] >= 40f - .001f)
                    assertTrue("Fixed 20px corners must fit at every frame", b[3] - b[1] >= 40f - .001f)
                    assertEquals(if (end) 184f else 0f, if (end) b[2] else b[0], .001f)
                    assertEquals(if (up) height else 0f, if (up) b[3] else b[1], .001f)
                    assertTrue(b[2] - b[0] >= lastWidth - .001f)
                    assertTrue(b[3] - b[1] >= lastHeight - .001f)
                    lastWidth = b[2] - b[0]
                    lastHeight = b[3] - b[1]
                }
                assertEquals(184f, lastWidth, .001f)
                assertEquals(height, lastHeight, .001f)
                val initial = menuRevealBounds(184f, height, 0f, end, up)
                assertTrue("Entrance must have visible horizontal travel", initial[2] - initial[0] < 184f * .5f)
                val middle = menuRevealBounds(184f, height, .5f, end, up)
                assertTrue("Width must keep unfolding at mid-animation", middle[2] - middle[0] < 184f * .8f)
                assertEquals(maxOf(height * .08f, 40f), initial[3] - initial[1], .001f)
            }
        }
    }
}
