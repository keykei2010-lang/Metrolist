package com.metrolist.music.ui.screens.playlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import kotlinx.coroutines.launch

@Composable
internal fun LocatePlayingSongButton(state: LazyListState, index: Int, headers: Int) {
    val scope = rememberCoroutineScope()
    IconButton(enabled = index >= 0, onClick = {
        scope.launch { state.animateScrollToItem(index + headers) }
    }) {
        Icon(painterResource(R.drawable.locate_song), stringResource(R.string.locate_playing_song))
    }
}
