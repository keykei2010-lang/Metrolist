package com.metrolist.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoConnectionTest {
    @Test fun `confirmation cannot be prearmed before connecting`() {
        val state = AndroidAutoConnection()
        state.confirmPassenger()
        assertFalse(state.passengerConfirmed.value)
        state.connectionChanged(true)
        assertFalse(state.passengerConfirmed.value)
        state.confirmPassenger()
        assertTrue(state.passengerConfirmed.value)
    }

    @Test fun `every reconnection and disconnect clears passenger confirmation`() {
        val state = AndroidAutoConnection()
        state.connectionChanged(true)
        state.confirmPassenger()
        state.connectionChanged(true)
        assertFalse(state.passengerConfirmed.value)
        state.confirmPassenger()
        state.connectionChanged(false)
        assertFalse(state.connected.value)
        assertFalse(state.passengerConfirmed.value)
        state.connectionChanged(true)
        assertFalse(state.passengerConfirmed.value)
    }
}
