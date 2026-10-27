/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.utils.get
import com.metrolist.music.utils.blendRecommendations
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.reversed
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _onlinePlaylist = MutableStateFlow<PlaylistItem?>(null)
    val onlinePlaylist: StateFlow<PlaylistItem?> = _onlinePlaylist
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(
                        compareBy<PlaylistSong, String>(collator) { song -> song.song.artists.joinToString("") { it.name } }
                            .thenBy(collator) { it.song.album?.title.orEmpty() },
                    )
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recommendations = MutableStateFlow<List<SongItem>>(emptyList())
    val recommendationsLoading = MutableStateFlow(false)
    val recommendationsFailed = MutableStateFlow(false)
    val recommendationsLoaded = MutableStateFlow(false)
    private var recommendationJob: Job? = null

    fun loadRecommendations() {
        if (recommendationJob?.isActive == true) return
        recommendationJob = viewModelScope.launch {
            recommendationsLoading.value = true
            recommendationsFailed.value = false
            try {
                val items = withContext(Dispatchers.IO) {
                    val songs = database.playlistSongs(playlistId).first()
                    val seeds = songs.map { it.song }.filterNot { it.song.isLocal || it.song.isUploaded || it.song.isEpisode }
                        .distinctBy { it.id }.shuffled().take(5)
                    val radios = seeds.mapNotNull { seed ->
                        YouTube.next(WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}"))
                            .onFailure { if (it is CancellationException) throw it else reportException(it) }
                            .getOrNull()?.items
                    }
                    if (seeds.isNotEmpty() && radios.isEmpty()) error("No seed radio could be loaded")
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoSongsKey, false)
                    blendRecommendations(
                        radios.map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideo) },
                        songs.map { it.song.id }.toSet(), 20, SongItem::id,
                    )
                }
                recommendations.value = items
                recommendationsLoaded.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                recommendationsFailed.value = true
            } finally {
                recommendationsLoading.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            val sortedSongs =
                database.playlistSongs(playlistId).first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }

        viewModelScope.launch {
            val localPlaylist = playlist.first { it != null }
            val browseId = localPlaylist?.playlist?.browseId
            if (browseId != null) {
                val page = withContext(Dispatchers.IO) {
                    YouTube.playlist(browseId).getOrNull()
                }
                val online = page?.playlist
                _onlinePlaylist.value = online
            }
        }
    }
}
