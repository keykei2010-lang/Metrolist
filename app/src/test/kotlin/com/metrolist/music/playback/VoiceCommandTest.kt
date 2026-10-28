package com.metrolist.music.playback

import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.*
import org.junit.Test

class VoiceCommandTest {
    private fun song(id: String, title: String, artist: String) = Song(
        song = SongEntity(id = id, title = title),
        artists = listOf(ArtistEntity(id = artist, name = artist)), album = null,
    )
    @Test fun `equal song matches keep the cached first ordering`() {
        val cached = song("cached", "Hello", "Adele")
        val saved = song("saved", "Hello", "Adele")
        assertEquals("cached", VoiceCommand.parse("play Hello by Adele")!!.findSong(listOf(cached, saved))?.id)
    }
    @Test fun `by in a song title is not mistaken for a different artist`() {
        val track = song("stand", "Stand by Me", "Ben E King")
        assertEquals("stand", VoiceCommand.parse("play Stand by Me")!!.findSong(listOf(track))?.id)
    }
    @Test fun `specified artist prevents a wrong artist version being selected`() {
        assertNull(VoiceCommand.parse("play Hello by Adele")!!.findSong(listOf(song("other", "Hello", "Lionel Richie"))))
    }
    @Test fun `song command strips instruction and separates artist`() {
        assertEquals(VoiceCommand("One More Time", "Daft Punk"), VoiceCommand.parse("Play One More Time by Daft Punk"))
        assertEquals(VoiceCommand("Hello", songOnly = true), VoiceCommand.parse("play the song Hello"))
    }
    @Test fun `artist command accepts from and by`() {
        assertEquals(VoiceCommand("Muse", artistOnly = true), VoiceCommand.parse("Play songs from Muse"))
        assertEquals(VoiceCommand("Muse", artistOnly = true), VoiceCommand.parse("play music by Muse"))
    }
    @Test fun `assistant queries need not contain play and blank input does nothing`() {
        assertEquals(VoiceCommand("Hello"), VoiceCommand.parse("Hello"))
        assertNull(VoiceCommand.parse("play "))
    }
    @Test fun `artist matching tolerates accents but rejects substring matches`() {
        assertTrue(VoiceCommand.matchesArtist("Beyonce", "Beyoncé"))
        assertFalse(VoiceCommand.matchesArtist("Queen", "Queens of the Stone Age"))
    }
    @Test fun `Portuguese artist commands support Portugal and Brazil wording`() {
        listOf("Toca músicas dos Muse", "Reproduz as canções de Muse", "Toque musicas do Muse", "Põe música de Muse").forEach {
            assertEquals(VoiceCommand("Muse", artistOnly = true), VoiceCommand.parse(it))
        }
    }
    @Test fun `Portuguese song commands preserve title and specified artist`() {
        val command = VoiceCommand.parse("Reproduz a música Hello da Adele")!!
        assertEquals("Hello", command.query)
        assertEquals("Adele", command.artist)
        assertEquals("cached", command.findSong(listOf(song("cached", "Hello", "Adele")))?.id)
        assertNull(command.findSong(listOf(song("wrong", "Hello", "Lionel Richie"))))
    }
    @Test fun `Portuguese prepositions within literal song titles are preserved`() {
        val track = song("engate", "Canção de Engate", "António Variações")
        val command = VoiceCommand.parse("Toca Canção de Engate")!!
        assertEquals("Canção de Engate", command.songQuery)
        assertEquals("engate", command.findSong(listOf(track))?.id)
        assertEquals("engate", VoiceCommand.parse("Toca Canção de Engate de António Variações")!!.findSong(listOf(track))?.id)
        val band = song("band", "Hello", "Banda do Mar")
        assertEquals("band", VoiceCommand.parse("Toca Hello da Banda do Mar")!!.findSong(listOf(band))?.id)
    }
    @Test fun `Portuguese instruction without a query does nothing`() {
        listOf("Toca", "Reproduz ", "Põe").forEach { assertNull(VoiceCommand.parse(it)) }
    }
    @Test fun `playlist requests work with and without a play instruction`() {
        listOf("play playlist Road Trip", "playlist Road Trip", "play my playlist Road Trip").forEach {
            assertEquals(VoiceCommand("Road Trip", playlistOnly = true), VoiceCommand.parse(it))
        }
        val command = VoiceCommand.parse("playlist Hello")!!
        assertNull(command.findSong(listOf(song("hello", "Hello", "Adele"))))
        assertNull(command.findArtist(listOf(song("hello", "Hi", "Hello"))))
        assertNull(VoiceCommand.parse("play playlist"))
    }
    @Test fun `Portuguese playlist names keep prepositions and articles`() {
        listOf("Toca a playlist Sons de Verão", "playlist Sons de Verão", "Reproduz a minha lista de reprodução Sons de Verão").forEach {
            assertEquals(VoiceCommand("Sons de Verão", playlistOnly = true), VoiceCommand.parse(it))
        }
    }
    @Test fun `short artist requests recognize saved artists before fuzzy song titles`() {
        val songs = listOf(song("fuzzy", "Muse Live", "Other Artist"), song("muse", "Uprising", "Muse"))
        listOf("play Muse", "Toca Muse", "Muse").forEach {
            val command = VoiceCommand.parse(it)!!
            assertNull(command.findSong(songs, exactOnly = true))
            assertEquals("Muse", command.findArtist(songs))
        }
        assertNull(VoiceCommand.parse("play Queen")!!.findArtist(listOf(song("q", "No One Knows", "Queens of the Stone Age"))))
    }
    @Test fun `an explicit song request is not inferred to mean an artist`() {
        val songs = listOf(song("muse", "Uprising", "Muse"))
        assertNull(VoiceCommand.parse("play song Muse")!!.findArtist(songs))
        assertNull(VoiceCommand.parse("toca a música Muse")!!.findArtist(songs))
    }
    @Test fun `exact title selection keeps artist constraints and cached ordering`() {
        val songs = listOf(song("wrong", "Hello", "Lionel Richie"), song("cached", "Hello", "Adele"), song("saved", "Hello", "Adele"))
        assertEquals("cached", VoiceCommand.parse("play Hello by Adele")!!.findSong(songs, exactOnly = true)?.id)
        assertEquals("wrong", VoiceCommand.parse("Hello")!!.findSong(songs, exactOnly = true)?.id)
    }
    @Test fun `artist inference preserves prepositions within the artist name`() {
        val songs = listOf(song("band", "Mais Ninguém", "Banda do Mar"))
        assertEquals("Banda do Mar", VoiceCommand.parse("Toca Banda do Mar")!!.findArtist(songs))
    }
}
