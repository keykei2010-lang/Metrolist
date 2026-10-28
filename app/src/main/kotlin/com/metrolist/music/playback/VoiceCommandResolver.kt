package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.LocalFirstVoiceQueue
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class VoiceCommandResolver(private val service: MusicService) {
    suspend fun resolve(text: String): Queue? = withContext(Dispatchers.IO) {
        val command = VoiceCommand.parse(text) ?: return@withContext null
        val database = service.database
        val hideExplicit = service.dataStore.get(HideExplicitKey, false)
        val hideVideos = service.dataStore.get(HideVideoSongsKey, false)
        val all = database.allSongs().first().filter {
            (!hideExplicit || !it.song.explicit) && (!hideVideos || !it.song.isVideo)
        }
        val playlists = database.songsInBookmarkedPlaylists().first().mapTo(mutableSetOf()) { it.id }
        val cacheKeys = service.playerCache.keys + service.downloadCache.keys
        val downloaded = service.downloadUtil.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
        val offline = mutableSetOf<String>()
        suspend fun cached(song: Song): Boolean {
            if (song.song.isLocal || song.id in downloaded) return true
            if (song.id !in cacheKeys) return false
            return listOf(service.downloadCache, service.playerCache).any { cache ->
                val length = ContentMetadata.getContentLength(cache.getContentMetadata(song.id)).takeIf { it > 0 }
                    ?: database.format(song.id).first()?.contentLength ?: -1L
                length > 0 && cache.isCached(song.id, 0, length)
            }
        }
        val eligible = all.filter { song ->
            if (cached(song)) offline.add(song.id)
            song.id in offline || song.id in playlists || song.song.inLibrary != null || song.song.liked
        }.sortedByDescending { it.id in offline }
        fun artistMatches(song: Song, name: String) = song.artists.any { VoiceCommand.matchesArtist(name, it.name) }
        suspend fun onlineArtist(name: String): List<MediaItem> {
            val artist = YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow().items
                .filterIsInstance<ArtistItem>().firstOrNull { VoiceCommand.matchesArtist(name, it.title) } ?: return emptyList()
            return YouTube.artist(artist.id).getOrThrow().sections.flatMap { it.items }.filterIsInstance<SongItem>()
                .filterExplicit(hideExplicit).filterVideoSongs(hideVideos)
                .filter { song -> song.artists.any { it.id == artist.id || VoiceCommand.matchesArtist(name, it.name) } }
                .distinctBy { it.id }.map { it.toMediaItem() }
        }
        if (command.artistOnly) {
            val local = eligible.filter { artistMatches(it, command.query) &&
                (it.id in offline || it.id in playlists || it.song.liked) }
            if (local.isEmpty()) {
                val online = onlineArtist(command.query)
                return@withContext if (online.isEmpty()) null else ListQueue(command.query, online)
            }
            return@withContext LocalFirstVoiceQueue(command.query, local.map { it.toMediaItem() }) {
                onlineArtist(command.query)
            }
        }
        val local = command.findSong(eligible)
        if (local != null) return@withContext ListQueue(local.title, listOf(local.toMediaItem()))
        val playlist = database.searchPlaylists(command.query).first().firstOrNull {
            VoiceCommand.normalize(it.playlist.name) == VoiceCommand.normalize(command.query)
        }
        if (playlist != null && command.artist == null) {
            val songs = database.playlistSongs(playlist.id).first().map { it.song }.filter {
                (!hideExplicit || !it.song.explicit) && (!hideVideos || !it.song.isVideo)
            }
            if (songs.isNotEmpty()) return@withContext ListQueue(playlist.playlist.name, songs.map { it.toMediaItem() })
        }
        val online = YouTube.search(command.songQuery, YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().filterExplicit(hideExplicit).filterVideoSongs(hideVideos)
        // Rank online candidates with the same matching policy as saved songs.
        database.withTransaction { online.forEach { insert(it.toMediaMetadata()) } }
        val onlineIds = online.mapTo(mutableSetOf()) { it.id }
        val songs = database.getSongsByIds(onlineIds.toList())
        val selected = command.findSong(songs) ?: return@withContext null
        ListQueue(selected.title, listOf(selected.toMediaItem()))
    }
}
