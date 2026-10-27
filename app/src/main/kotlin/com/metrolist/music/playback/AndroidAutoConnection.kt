package com.metrolist.music.playback

import kotlinx.coroutines.flow.MutableStateFlow

internal class AndroidAutoConnection {
    val connected = MutableStateFlow(false)
    val passengerConfirmed = MutableStateFlow(false)

    fun connectionChanged(isConnected: Boolean) {
        passengerConfirmed.value = false
        connected.value = isConnected
    }

    fun confirmPassenger() {
        passengerConfirmed.value = connected.value
    }
}
