package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.PlaylistManualOrderKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.PlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistSortDescendingKey
import com.metrolist.music.models.libraryPlaylistEntries
import com.metrolist.music.models.titleResource
import com.metrolist.music.models.LibraryPlaylistEntry
import com.metrolist.music.models.playlistIsVisible
import com.metrolist.music.models.setPlaylistVisible
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistLibraryDialog(entries: List<LibraryPlaylistEntry>, preferences: Preferences, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (sortType, setSortType) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (savedOrder, setOrder) = rememberPreference(PlaylistManualOrderKey, "")
    val manual = sortType == PlaylistSortType.MANUAL
    val moveEarlier = stringResource(R.string.playlist_move_earlier)
    val moveLater = stringResource(R.string.playlist_move_later)
    var ids by remember(entries.map { it.id }) { mutableStateOf(entries.map { it.id }) }
    val byId = entries.associateBy { it.id }
    val state = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(state) { from, to ->
        if (manual && from.index in ids.indices && to.index in ids.indices) {
            ids = ids.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_library_playlists)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.playlist_manual_sort), Modifier.weight(1f))
                    Switch(checked = manual, onCheckedChange = {
                        if (it && savedOrder.isBlank()) setOrder(ids.joinToString(","))
                        setSortType(if (it) PlaylistSortType.MANUAL else PlaylistSortType.CREATE_DATE)
                    })
                }
                Text(stringResource(R.string.playlist_organize_help), style = MaterialTheme.typography.bodySmall)
                LazyColumn(state = state, modifier = Modifier.heightIn(max = 420.dp)) {
                    items(ids, key = { it }) { id ->
                        val entry = byId[id] ?: return@items
                        ReorderableItem(reorderable, key = id) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (manual) {
                                    IconButton(onClick = {}, modifier = Modifier.semantics {
                                        customActions = listOf(-1 to moveEarlier, 1 to moveLater).map { (step, label) ->
                                            CustomAccessibilityAction(label) {
                                                val index = ids.indexOf(id)
                                                val target = index + step
                                                if (index in ids.indices && target in ids.indices) {
                                                    ids = ids.toMutableList().apply { add(target, removeAt(index)) }
                                                    setOrder(ids.joinToString(","))
                                                    true
                                                } else false
                                            }
                                        }
                                    }.draggableHandle(
                                        onDragStopped = { setOrder(ids.joinToString(",")) },
                                    )) {
                                        Icon(painterResource(R.drawable.drag_handle), stringResource(R.string.playlist_drag_handle))
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(entry.playlist.playlist.name, maxLines = 2)
                                    Text(stringResource(if (playlistIsVisible(id, preferences)) R.string.playlist_shown else R.string.playlist_hidden),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(modifier = Modifier.semantics {
                                    contentDescription = entry.playlist.playlist.name
                                }, checked = playlistIsVisible(id, preferences), onCheckedChange = { visible ->
                                    scope.launch { context.dataStore.setPlaylistVisible(id, visible) }
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
fun PlaylistLibrarySettings() {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val (sort, setSort) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (descending, setDescending) = rememberPreference(PlaylistSortDescendingKey, true)
    val preferences by remember { context.dataStore.data }.collectAsStateWithLifecycle(emptyPreferences())
    val playlists by remember(sort, descending) { database.playlists(sort, descending) }
        .collectAsStateWithLifecycle(emptyList())
    var manage by remember { mutableStateOf(false) }
    Material3SettingsGroup(title = stringResource(R.string.playlists), items = listOf(
        Material3SettingsItem(
            title = { Text(stringResource(R.string.library_playlist_sort)) },
            description = {
                Column {
                    Text(stringResource(R.string.library_playlist_sort_help))
                    SortHeader(sort, descending, onSortTypeChange = {
                        setSort(it)
                        if (it == PlaylistSortType.MANUAL) manage = true
                    }, onSortDescendingChange = setDescending,
                        sortTypeText = { it.titleResource() }, showDescending = sort != PlaylistSortType.MANUAL)
                }
            },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.manage_library_playlists)) },
            description = { Text(stringResource(R.string.playlist_organize_help)) },
            onClick = { manage = true },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.cached_playlist)) },
            description = { Text(stringResource(R.string.cached_playlist_explanation)) },
        ),
    ))
    if (manage) PlaylistLibraryDialog(libraryPlaylistEntries(context, playlists, preferences, includeHidden = true),
        preferences, onDismiss = { manage = false })
}
