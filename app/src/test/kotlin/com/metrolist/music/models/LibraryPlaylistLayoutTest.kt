package com.metrolist.music.models

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import org.junit.Assert.*
import org.junit.Test
class LibraryPlaylistLayoutTest {
    private fun playlist(id: String) = Playlist(PlaylistEntity(id = id, name = id), 0, emptyList())
    private fun entries(playlists: List<Playlist>) = BuiltInPlaylist.entries.map {
        LibraryPlaylistEntry(playlist(it.id), it)
    } + playlists.map { LibraryPlaylistEntry(it) }

    @Test fun `hidden playlists keep their saved position when shown again`() {
        val liked = BuiltInPlaylist.LIKED.id
        val preferences = mutablePreferencesOf(
            PlaylistSortTypeKey to PlaylistSortType.MANUAL.name,
            PlaylistManualOrderKey to "b,$liked,a,deleted,b",
            HiddenPlaylistsKey to setOf("b"),
            ShowLikedPlaylistKey to false,
        )
        val playlists = listOf(playlist("a"), playlist("b"))
        val entries = entries(playlists)
        val visible = arrangeLibraryPlaylistEntries(entries, preferences).map { it.id }
        assertEquals("a", visible.first())
        assertFalse("b" in visible)
        assertFalse(liked in visible)
        val manager = arrangeLibraryPlaylistEntries(entries, preferences, includeHidden = true).map { it.id }
        assertEquals(listOf("b", liked, "a"), manager.take(3))
        preferences[HiddenPlaylistsKey] = emptySet()
        preferences[ShowLikedPlaylistKey] = true
        assertEquals(manager, arrangeLibraryPlaylistEntries(entries, preferences).map { it.id })
    }

    @Test fun `manual order survives removed playlists and appends new playlists exactly once`() {
        assertEquals(listOf("b", "a", "new"), orderedPlaylistIds(listOf("a", "b", "new"), "gone,b,b,a"))
    }

    @Test fun `automatic sorting preserves the database order while builtins remain stable`() {
        val preferences = mutablePreferencesOf(PlaylistSortTypeKey to PlaylistSortType.PLAY_TIME.name,
            PlaylistManualOrderKey to "a,b")
        val playlists = listOf(playlist("b"), playlist("a"))
        val entries = entries(playlists)
        val first = arrangeLibraryPlaylistEntries(entries, preferences)
        val second = arrangeLibraryPlaylistEntries(entries, preferences)
        assertEquals(first, second)
        assertEquals(listOf("b", "a"), first.filter { it.builtIn == null }.map { it.id })
        assertEquals(BuiltInPlaylist.entries.map { it.id }, first.filter { it.builtIn != null }.map { it.id })
    }

    @Test fun `car sort action visits every phone sort mode and wraps safely`() {
        var mode = PlaylistSortType.CREATE_DATE
        val seen = mutableSetOf(mode)
        repeat(PlaylistSortType.entries.size) {
            mode = nextPlaylistSortType(mode.name)
            seen.add(mode)
        }
        assertEquals(PlaylistSortType.entries.toSet(), seen)
        assertEquals(PlaylistSortType.CREATE_DATE, mode)
        assertEquals(PlaylistSortType.NAME, nextPlaylistSortType("obsolete"))
    }
}
