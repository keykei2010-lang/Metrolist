package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.PlayerControlsKey
import com.metrolist.music.models.PlayerControl
import androidx.compose.material3.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference

@Composable
fun PlayerControl.label(): String = stringResource(when (this) {
    PlayerControl.SHUFFLE -> R.string.shuffle
    PlayerControl.PREVIOUS -> R.string.previous
    PlayerControl.PLAY_PAUSE -> R.string.play_pause
    PlayerControl.NEXT -> R.string.next
    PlayerControl.REPEAT -> R.string.player_control_repeat
    PlayerControl.LIKE -> R.string.like
    PlayerControl.LYRICS -> R.string.lyrics
    PlayerControl.QUEUE -> R.string.queue
})

@Composable
fun PlayerControlSettings() {
    val (raw, setRaw) = rememberPreference(PlayerControlsKey, "")
    val selected = if (raw.isEmpty()) PlayerControl.defaults else PlayerControl.decode(raw)
    val ordered = selected + PlayerControl.entries.filterNot { it in selected }
    Material3SettingsGroup(
        title = stringResource(R.string.player_controls),
        items = ordered.map { control ->
            Material3SettingsItem(
                title = { Text(control.label()) },
                trailingContent = {
                    Row {
                        val index = selected.indexOf(control)
                        IconButton(enabled = index > 0, onClick = {
                            setRaw(PlayerControl.encode(selected.toMutableList().apply { add(index - 1, removeAt(index)) }))
                        }) { Icon(painterResource(R.drawable.arrow_upward), stringResource(R.string.control_move_up)) }
                        IconButton(enabled = index >= 0 && index < selected.lastIndex, onClick = {
                            setRaw(PlayerControl.encode(selected.toMutableList().apply { add(index + 1, removeAt(index)) }))
                        }) { Icon(painterResource(R.drawable.arrow_downward), stringResource(R.string.control_move_down)) }
                        Checkbox(checked = control in selected, enabled = control != PlayerControl.PLAY_PAUSE,
                            onCheckedChange = { checked ->
                                setRaw(PlayerControl.encode(if (checked) selected + control else selected - control))
                            })
                    }
                },
            )
        } + Material3SettingsItem(
            title = { Text(stringResource(R.string.player_controls_reset)) },
            onClick = { setRaw("") },
        ),
    )
}
