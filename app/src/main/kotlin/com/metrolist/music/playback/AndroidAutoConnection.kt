package com.metrolist.music.playback

import kotlinx.coroutines.flow.MutableStateFlow

internal class AndroidAutoConnection {
    val connected = MutableStateFlow(false)

    fun connectionChanged(isConnected: Boolean) {
        connected.value = isConnected
    }
}
