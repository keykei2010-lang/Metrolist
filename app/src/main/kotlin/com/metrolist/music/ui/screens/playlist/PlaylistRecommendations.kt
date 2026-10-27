package com.metrolist.music.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.playlistDuplicatesBatched
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.LocalPlaylistViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistRecommendations(viewModel: LocalPlaylistViewModel, playlist: Playlist, existingIds: Set<String>) {
    val database = LocalDatabase.current
    val sync = LocalSyncUtils.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val suggestions by viewModel.recommendations.collectAsStateWithLifecycle()
    val loading by viewModel.recommendationsLoading.collectAsStateWithLifecycle()
    val failed by viewModel.recommendationsFailed.collectAsStateWithLifecycle()
    val loaded by viewModel.recommendationsLoaded.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<String?>(null) }
    var addFailed by remember { mutableStateOf(false) }
    Column {
        TextButton(enabled = !loading, onClick = viewModel::loadRecommendations) {
            Text(stringResource(R.string.playlist_recommendations))
        }
        if (loading) CircularProgressIndicator()
        if (failed || addFailed) Text(stringResource(R.string.playlist_recommendations_failed))
        if (loaded && !loading && !failed && suggestions.none { it.id !in existingIds }) {
            Text(stringResource(R.string.playlist_recommendations_empty))
        }
        suggestions.filterNot { it.id in existingIds }.forEach { song ->
            YouTubeListItem(
                item = song,
                modifier = Modifier.fillMaxWidth().clickable {
                    player?.playQueue(ListQueue(title = playlist.playlist.name, items = listOf(song.toMediaItem())))
                },
                trailingContent = {
                    TextButton(enabled = adding == null, onClick = {
                        adding = song.id
                        addFailed = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    var added = false
                                    database.withTransaction {
                                        insert(song.toMediaMetadata())
                                        if (playlistDuplicatesBatched(playlist.id, listOf(song.id)).isEmpty()) {
                                            addSongsToPlaylist(playlist, listOf(song.id to null))
                                            added = true
                                        }
                                    }
                                    playlist.playlist.browseId?.takeIf { added }?.let { browseId ->
                                        sync.scheduleAddToPlaylist(browseId, playlist.id, listOf(song.id))
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                                addFailed = true
                            } finally {
                                adding = null
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.add), stringResource(R.string.add_to_playlist))
                    }
                },
            )
        }
    }
}
