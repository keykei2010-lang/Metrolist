package com.metrolist.music.playback

import com.metrolist.music.db.entities.Song
import java.text.Normalizer
import java.util.Locale

internal data class VoiceCommand(
    val query: String,
    val artist: String? = null,
    val artistOnly: Boolean = false,
    val songQuery: String = if (artist == null) query else "$query by $artist",
    val playlistOnly: Boolean = false,
    val songOnly: Boolean = false,
) {

    fun findSong(candidates: List<Song>, exactOnly: Boolean = false): Song? {
        if (playlistOnly || artistOnly) return null
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
        val matchingArtists = candidates.filter { song ->
            artist == null || song.artists.any { matchesArtist(artist, it.name) }
        }
        val title = matchingArtists.firstOrNull { normalize(it.title) == normalize(query) }
        return title ?: if (exactOnly) null else VoiceSearchMatcher.findBest(query, matchingArtists)
    }

    fun findArtist(candidates: List<Song>): String? {
        if (playlistOnly || songOnly) return null
        return candidates.asSequence().flatMap { it.artists.asSequence() }
            .firstOrNull { matchesArtist(songQuery, it.name) }?.name
    }

    companion object {
        fun parse(text: String): VoiceCommand? {
            val instruction = Regex("(?iu)^(?:play|toca|toque|tocar|reproduz|reproduza|reproduzir|põe|poe|ponha|mete|coloca|coloque)(?:\\s+|$)")
            val query = text.trim().replace(instruction, "").trim()
            if (query.isBlank()) return null
            val playlist = Regex("(?iu)^(?:(?:the|my|a|minha|a minha)\\s+)?(?:playlist|lista(?:\\s+de\\s+reprodução)?)(?:\\s+|$)").find(query)
            if (playlist != null) {
                val name = query.substring(playlist.range.last + 1).trim()
                return name.takeIf { it.isNotBlank() }?.let { VoiceCommand(it, playlistOnly = true) }
            }
            val artist = Regex("(?iu)^(?:(?:as?|os?)\\s+)?(?:songs|music|tracks|músicas?|musicas?|canções|cancoes|faixas)\\s+(?:from|by|de|do|da|dos|das)\\s+(.+)$").matchEntire(query)
            if (artist != null) return VoiceCommand(artist.groupValues[1].trim(), artistOnly = true)
            val song = query.replace(Regex("(?iu)^(?:(?:the\\s+)?song\\s+|(?:(?:a|o)\\s+)?(?:música|musica|canção|cancao|faixa)\\s+(?!(?:de|do|da|dos|das)\\s+))"), "")
            val by = Regex("(?iu)^(.+?)\\s+(?:by|de|do|da|dos|das)\\s+(.+)$").matchEntire(song)
            return if (by != null) {
                VoiceCommand(by.groupValues[1].trim(), by.groupValues[2].trim(), songQuery = song, songOnly = song != query)
            } else VoiceCommand(song, songOnly = song != query)
        }

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun matchesArtist(query: String, artist: String) = normalize(query) == normalize(artist)
    }
}
