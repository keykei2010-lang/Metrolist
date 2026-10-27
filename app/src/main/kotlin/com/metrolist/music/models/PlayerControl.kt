package com.metrolist.music.models

enum class PlayerControl {
    SHUFFLE, PREVIOUS, PLAY_PAUSE, NEXT, REPEAT, LIKE, LYRICS, QUEUE;

    companion object {
        val defaults = listOf(SHUFFLE, PREVIOUS, PLAY_PAUSE, NEXT, LIKE)

        fun decode(raw: String): List<PlayerControl> {
            val controls = raw.split(',').mapNotNull { token -> entries.find { it.name == token } }.distinct()
            return if (PLAY_PAUSE in controls) controls else controls + PLAY_PAUSE
        }

        fun encode(controls: List<PlayerControl>) = controls.distinct().joinToString(",") { it.name }
    }
}
