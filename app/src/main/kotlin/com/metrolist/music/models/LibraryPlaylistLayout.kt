package com.metrolist.music.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.toEnum

enum class BuiltInPlaylist(
    val id: String,
    val title: Int,
    val icon: Int,
    val route: String,
    val visibilityKey: Preferences.Key<Boolean>,
) {
    LIKED(PlaylistEntity.LIKED_PLAYLIST_ID, R.string.liked, R.drawable.favorite, "auto_playlist/liked", ShowLikedPlaylistKey),
    DOWNLOADED(PlaylistEntity.DOWNLOADED_PLAYLIST_ID, R.string.offline, R.drawable.download, "auto_playlist/downloaded", ShowDownloadedPlaylistKey),
    CACHED("LP_CACHED", R.string.cached_playlist, R.drawable.cached, "cache_playlist/cached", ShowCachedPlaylistKey),
    TOP("LP_TOP", R.string.my_top, R.drawable.trending_up, "top_playlist", ShowTopPlaylistKey),
    UPLOADED("LP_UPLOADED", R.string.uploaded_playlist, R.drawable.cloud, "auto_playlist/uploaded", ShowUploadedPlaylistKey),
}

data class LibraryPlaylistEntry(val playlist: Playlist, val builtIn: BuiltInPlaylist? = null) {
    val id: String get() = playlist.id
    val route: String get() = builtIn?.let {
        if (it == BuiltInPlaylist.TOP) "${it.route}/${playlist.songCount}" else it.route
    } ?: "local_playlist/$id"
}

fun playlistIsVisible(id: String, preferences: Preferences): Boolean =
    BuiltInPlaylist.entries.find { it.id == id }?.let { preferences[it.visibilityKey] ?: true }
        ?: (id !in preferences[HiddenPlaylistsKey].orEmpty())

suspend fun DataStore<Preferences>.setPlaylistVisible(id: String, visible: Boolean) {
    edit { preferences ->
        val builtIn = BuiltInPlaylist.entries.find { it.id == id }
        if (builtIn != null) preferences[builtIn.visibilityKey] = visible
        else preferences[HiddenPlaylistsKey] = preferences[HiddenPlaylistsKey].orEmpty().let {
            if (visible) it - id else it + id
        }
    }
}

fun orderedPlaylistIds(ids: List<String>, savedOrder: String): List<String> {
    val available = ids.toSet()
    return (savedOrder.split(',').filter { it in available } + ids).distinct()
}

fun PlaylistSortType.titleResource(): Int = when (this) {
    PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
    PlaylistSortType.NAME -> R.string.sort_by_name
    PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
    PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
    PlaylistSortType.PLAY_TIME -> R.string.sort_by_play_time
    PlaylistSortType.MANUAL -> R.string.playlist_manual_sort
}

fun nextPlaylistSortType(current: String?): PlaylistSortType {
    val type = current.toEnum(PlaylistSortType.CREATE_DATE)
    return PlaylistSortType.entries[(type.ordinal + 1) % PlaylistSortType.entries.size]
}

fun libraryPlaylistEntries(
    context: Context,
    playlists: List<Playlist>,
    preferences: Preferences,
    includeHidden: Boolean = false,
): List<LibraryPlaylistEntry> {
    val topSize = preferences[TopSize]?.toIntOrNull()?.coerceIn(1, 1000) ?: 50
    val entries = BuiltInPlaylist.entries.map { builtIn ->
        val title = context.getString(builtIn.title) + if (builtIn == BuiltInPlaylist.TOP) " $topSize" else ""
        LibraryPlaylistEntry(Playlist(PlaylistEntity(id = builtIn.id, name = title, createdAt = null, lastUpdateTime = null),
            if (builtIn == BuiltInPlaylist.TOP) topSize else 0, emptyList()), builtIn)
    } + playlists.distinctBy { it.id }.map { LibraryPlaylistEntry(it) }
    return arrangeLibraryPlaylistEntries(entries, preferences, includeHidden)
}

fun arrangeLibraryPlaylistEntries(
    entries: List<LibraryPlaylistEntry>,
    preferences: Preferences,
    includeHidden: Boolean = false,
): List<LibraryPlaylistEntry> {
    val ordered = if (preferences[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) == PlaylistSortType.MANUAL) {
        val byId = entries.associateBy { it.id }
        orderedPlaylistIds(entries.map { it.id }, preferences[PlaylistManualOrderKey].orEmpty()).mapNotNull(byId::get)
    } else entries
    return if (includeHidden) ordered else ordered.filter { playlistIsVisible(it.id, preferences) }
}
