package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabNavigationTest {
    @Test fun `swipes move in reading direction and respect rtl`() {
        assertEquals(2, swipedTabIndex(1, 3, -100f, 72f, false))
        assertEquals(0, swipedTabIndex(1, 3, 100f, 72f, false))
        assertEquals(0, swipedTabIndex(1, 3, -100f, 72f, true))
    }

    @Test fun `small gestures and boundary swipes cannot switch tabs`() {
        assertNull(swipedTabIndex(1, 3, 20f, 72f, false))
        assertNull(swipedTabIndex(0, 3, 100f, 72f, false))
        assertNull(swipedTabIndex(2, 3, -100f, 72f, false))
        assertNull(swipedTabIndex(-1, 3, -100f, 72f, false))
    }
}
