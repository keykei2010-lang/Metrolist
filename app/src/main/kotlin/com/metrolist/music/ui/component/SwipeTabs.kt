package com.metrolist.music.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.music.utils.swipedTabIndex

@Composable
internal fun Modifier.swipeTabs(index: Int, count: Int, enabled: Boolean = true, onSelect: (Int) -> Unit): Modifier {
    val select by rememberUpdatedState(onSelect)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(index, count, enabled, rtl) {
        if (!enabled || index !in 0 until count) return@pointerInput
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragCancel = { distance = 0f },
            onHorizontalDrag = { change, delta ->
                change.consume()
                distance += delta
            },
            onDragEnd = {
                swipedTabIndex(index, count, distance, 72.dp.toPx(), rtl)?.let(select)
            },
        )
    }
}
