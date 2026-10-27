package com.metrolist.music.models

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlTest {
    @Test fun `saved controls retain order and discard unknown duplicate controls`() {
        assertEquals(listOf(PlayerControl.NEXT, PlayerControl.PLAY_PAUSE),
            PlayerControl.decode("NEXT,unknown,NEXT,PLAY_PAUSE"))
    }

    @Test fun `play pause remains accessible even with corrupt preferences`() {
        assertEquals(listOf(PlayerControl.PLAY_PAUSE), PlayerControl.decode("corrupt"))
        assertEquals(listOf(PlayerControl.LYRICS, PlayerControl.PLAY_PAUSE), PlayerControl.decode("LYRICS"))
    }
}
