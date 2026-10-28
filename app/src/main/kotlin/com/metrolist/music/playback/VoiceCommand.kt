package com.metrolist.music.playback

import com.metrolist.music.db.entities.Song
import java.text.Normalizer
import java.util.Locale

internal data class VoiceCommand(
    val query: String,
    val artist: String? = null,
    val artistOnly: Boolean = false,
    val songQuery: String = if (artist == null) query else "$query by $artist",
) {

    fun findSong(candidates: List<Song>): Song? {
        val literal = candidates.firstOrNull { normalize(it.title) == normalize(songQuery) }
        if (literal != null) return literal
        // Match the full phrase first: titles and artist names can also contain "de" or "by".
        val fullPhrase = normalize(songQuery)
        val exact = candidates.firstOrNull { song ->
            song.artists.any { artist ->
                listOf("by", "de", "do", "da", "dos", "das").any { connector ->
                    normalize("${song.title} $connector ${artist.name}") == fullPhrase
                }
            }
        }
        if (exact != null) return exact
        return VoiceSearchMatcher.findBest(query, candidates.filter { song ->
            artist == null || song.artists.any { matchesArtist(artist, it.name) }
        })
    }

    companion object {
        fun parse(text: String): VoiceCommand? {
            val instruction = Regex("(?iu)^(?:play|toca|toque|tocar|reproduz|reproduza|reproduzir|põe|poe|ponha|mete|coloca|coloque)(?:\\s+|$)")
            val query = text.trim().replace(instruction, "").trim()
            if (query.isBlank()) return null
            val artist = Regex("(?iu)^(?:(?:as?|os?)\\s+)?(?:songs|music|tracks|músicas?|musicas?|canções|cancoes|faixas)\\s+(?:from|by|de|do|da|dos|das)\\s+(.+)$").matchEntire(query)
            if (artist != null) return VoiceCommand(artist.groupValues[1].trim(), artistOnly = true)
            val song = query.replace(Regex("(?iu)^(?:(?:the\\s+)?song\\s+|(?:(?:a|o)\\s+)?(?:música|musica|canção|cancao|faixa)\\s+(?!(?:de|do|da|dos|das)\\s+))"), "")
            val by = Regex("(?iu)^(.+?)\\s+(?:by|de|do|da|dos|das)\\s+(.+)$").matchEntire(song)
            return if (by != null) VoiceCommand(by.groupValues[1].trim(), by.groupValues[2].trim(), songQuery = song) else VoiceCommand(song)
        }

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun matchesArtist(query: String, artist: String) = normalize(query) == normalize(artist)
    }
}
