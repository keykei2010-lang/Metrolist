package com.metrolist.music.playback

import com.metrolist.music.db.entities.Song
import java.text.Normalizer
import java.util.Locale

internal data class VoiceCommand(val query: String, val artist: String? = null, val artistOnly: Boolean = false) {
    val songQuery: String get() = if (artist == null) query else "$query by $artist"

    fun findSong(candidates: List<Song>): Song? {
        val literal = candidates.firstOrNull { normalize(it.title) == normalize(songQuery) }
        if (literal != null) return literal
        return VoiceSearchMatcher.findBest(query, candidates.filter { song ->
            artist == null || song.artists.any { matchesArtist(artist, it.name) }
        })
    }

    companion object {
        fun parse(text: String): VoiceCommand? {
            if (text.trim().equals("play", ignoreCase = true)) return null
            val query = text.trim().replace(Regex("(?i)^play\\s+"), "").trim()
            if (query.isBlank()) return null
            val artist = Regex("(?i)^(?:songs|music|tracks)\\s+(?:from|by)\\s+(.+)$").matchEntire(query)
            if (artist != null) return VoiceCommand(artist.groupValues[1].trim(), artistOnly = true)
            val song = query.replace(Regex("(?i)^(?:the\\s+)?song\\s+"), "")
            val by = Regex("(?i)^(.+?)\\s+by\\s+(.+)$").matchEntire(song)
            return if (by != null) VoiceCommand(by.groupValues[1].trim(), by.groupValues[2].trim()) else VoiceCommand(song)
        }

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun matchesArtist(query: String, artist: String) = normalize(query) == normalize(artist)
    }
}
