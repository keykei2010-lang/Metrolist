/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var scrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var previous = firstVisibleItemIndex to firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }.collect { current ->
            if (current != previous) {
                scrollingUp = current.first < previous.first ||
                    (current.first == previous.first && current.second < previous.second)
                previous = current
            }
        }
    }
    return scrollingUp
}
