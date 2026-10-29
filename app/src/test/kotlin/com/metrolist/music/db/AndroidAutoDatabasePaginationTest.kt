/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongAlbumMap
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.metrolist.music.constants.PlaylistSortType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidAutoDatabasePaginationTest {
    private lateinit var database: InternalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `car sorting and filters apply before pagination`() = runBlocking {
        val day = LocalDateTime.of(2026, 1, 1, 0, 0)
        database.dao.insert(SongEntity(id = "old", title = "Zulu", inLibrary = day))
        database.dao.insert(SongEntity(id = "hidden", title = "Alpha", inLibrary = day.plusDays(1), explicit = true))
        database.dao.insert(SongEntity(id = "video", title = "Beta", inLibrary = day.plusDays(2), isVideo = true))
        database.dao.insert(SongEntity(id = "new", title = "Delta", inLibrary = day.plusDays(3)))
        assertEquals(listOf("hidden", "video"), database.dao.songsByCreateDateAsc(2, 0, sortByName = true).map { it.id })
        assertEquals(listOf("new", "video"), database.dao.songsByCreateDateAsc(2, 0, descending = true).map { it.id })
        assertEquals(listOf("old", "new"), database.dao.songsByCreateDateAsc(2, 0, hideExplicit = true, hideVideos = true).map { it.id })
        assertEquals(listOf("new"), database.dao.songsByCreateDateAsc(1, 1, hideExplicit = true, hideVideos = true).map { it.id })
    }

    @Test
    fun `car playlist date order uses date added rather than cache insertion order`() = runBlocking {
        val day = LocalDateTime.of(2026, 1, 1, 0, 0)
        database.dao.insert(PlaylistEntity(id = "new", name = "Zulu", bookmarkedAt = day.plusDays(1)))
        database.dao.insert(PlaylistEntity(id = "old", name = "Alpha", bookmarkedAt = day))
        assertEquals(listOf("old", "new"), database.dao.playlistsByCreateDateAsc(10, 0).map { it.id })
        assertEquals(listOf("new", "old"), database.dao.playlistsByCreateDateAsc(10, 0, descending = true).map { it.id })
    }

    @Test
    fun `playlist play time sums distinct member songs and includes empty playlists`() = runBlocking {
        val day = LocalDateTime.of(2026, 1, 1, 0, 0)
        listOf("empty", "short", "long").forEach {
            database.dao.insert(PlaylistEntity(id = it, name = it, bookmarkedAt = day))
        }
        database.dao.insert(PlaylistEntity(id = "unbookmarked", name = "unbookmarked"))
        database.dao.insert(SongEntity(id = "one", title = "One", totalPlayTime = 100))
        database.dao.insert(SongEntity(id = "two", title = "Two", totalPlayTime = 150))
        database.dao.insert(SongEntity(id = "three", title = "Three", totalPlayTime = 175))
        database.dao.insert(PlaylistSongMap(playlistId = "short", songId = "one", position = 0))
        database.dao.insert(PlaylistSongMap(playlistId = "short", songId = "one", position = 1))
        database.dao.insert(PlaylistSongMap(playlistId = "long", songId = "two", position = 0))
        assertEquals(listOf("empty", "short", "long"),
            database.dao.playlists(PlaylistSortType.PLAY_TIME, false).first().map { it.id })
        database.dao.insert(PlaylistSongMap(playlistId = "short", songId = "three", position = 2))
        assertEquals(listOf("short", "long", "empty"),
            database.dao.playlists(PlaylistSortType.PLAY_TIME, true).first().map { it.id })
    }

    @Test
    fun `song relation query stays below Room recursive fetch threshold`() = runBlocking {
        val libraryDate = LocalDateTime.of(2026, 1, 1, 0, 0)
        database.runInTransaction {
            database.dao.insert(ArtistEntity(id = "artist", name = "Artist"))
            database.dao.insert(
                AlbumEntity(
                    id = "album",
                    title = "Album",
                    songCount = 1_001,
                    duration = 0,
                ),
            )
            database.dao.insert(
                PlaylistEntity(
                    id = "playlist",
                    name = "Playlist",
                    bookmarkedAt = libraryDate,
                ),
            )
            repeat(1_001) { index ->
                val songId = "song-$index"
                val artistId = "artist-$index"
                val albumId = "album-$index"
                database.dao.insert(ArtistEntity(id = artistId, name = "Artist $index"))
                database.dao.insert(
                    AlbumEntity(
                        id = albumId,
                        title = "Album $index",
                        songCount = 1,
                        duration = 0,
                    ),
                )
                database.dao.insert(
                    PlaylistEntity(
                        id = "playlist-$index",
                        name = "Playlist $index",
                        bookmarkedAt = libraryDate,
                    ),
                )
                database.dao.insert(
                    SongEntity(
                        id = songId,
                        title = "Song $index",
                        albumId = albumId,
                        liked = true,
                        likedDate = libraryDate,
                        inLibrary = libraryDate,
                    ),
                )
                database.dao.insert(SongArtistMap(songId = songId, artistId = "artist", position = 0))
                database.dao.insert(SongArtistMap(songId = songId, artistId = artistId, position = 1))
                database.dao.insert(SongAlbumMap(songId = songId, albumId = "album", index = index / 2))
                database.dao.insert(
                    PlaylistSongMap(
                        playlistId = "playlist",
                        songId = songId,
                        position = index / 2,
                    ),
                )
            }
        }

        database.dao.songsByCreateDateAsc(limit = 500, offset = 0).let { firstPage ->
            assertEquals(500, firstPage.size)
            assertTrue(
                firstPage.all { song ->
                    song.artists.any { it.id == "artist" } && song.album?.id == "album"
                },
            )
        }
        assertCompletePagination(
            id = { it.id },
            loadPage = database.dao::songsByCreateDateAsc,
        )
        assertCompletePagination(
            id = { it.id },
            loadPage = database.dao::likedSongsByCreateDateDesc,
        )
        assertCompletePagination(
            id = { it.id },
            loadPage = { limit, offset ->
                database.dao.artistSongsByCreateDateAsc("artist", limit, offset)
            },
        )
        assertCompletePagination(
            id = { it.id },
            loadPage = { limit, offset -> database.dao.albumSongs("album", limit, offset) },
        )
        assertCompletePagination(
            id = { it.song.id },
            loadPage = { limit, offset -> database.dao.playlistSongs("playlist", limit, offset) },
        )
        assertCompletePagination(
            expectedSize = 1_002,
            id = { it.id },
            loadPage = { limit, offset -> database.dao.artistsByCreateDateAsc(limit, offset) },
        )
        assertCompletePagination(
            id = { it.id },
            loadPage = { limit, offset -> database.dao.albumsByCreateDateAsc(limit, offset) },
        )
        assertCompletePagination(
            expectedSize = 1_002,
            id = { it.id },
            loadPage = { limit, offset -> database.dao.playlistsByCreateDateAsc(limit, offset) },
        )
    }

    private suspend fun <T> assertCompletePagination(
        expectedSize: Int = 1_001,
        id: (T) -> String,
        loadPage: suspend (limit: Int, offset: Int) -> List<T>,
    ) {
        val ids = buildList {
            listOf(0, 500, 1_000).forEach { offset ->
                addAll(loadPage(500, offset).map(id))
            }
        }
        assertEquals(expectedSize, ids.size)
        assertEquals(expectedSize, ids.toSet().size)
    }
}
