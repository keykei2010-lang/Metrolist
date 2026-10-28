package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.AndroidAutoBoostKey
import com.metrolist.music.constants.AndroidAutoDescendingKey
import com.metrolist.music.constants.AndroidAutoLyricsKey
import com.metrolist.music.constants.AndroidAutoSortKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun AndroidAutoPlaybackSettings() {
    val service = LocalPlayerConnection.current?.service
    val connected = service?.androidAutoConnected?.collectAsStateWithLifecycle()?.value == true
    val confirmed = service?.androidAutoLyrics?.passengerConfirmed?.collectAsStateWithLifecycle()?.value == true
    val (boost, setBoost) = rememberPreference(AndroidAutoBoostKey, 0f)
    val safeBoost = if (boost.isFinite()) boost.coerceIn(0f, 12f) else 0f
    val (lyrics, setLyrics) = rememberPreference(AndroidAutoLyricsKey, false)
    val (sort, setSort) = rememberPreference(AndroidAutoSortKey, "date")
    val (descending, setDescending) = rememberPreference(AndroidAutoDescendingKey, false)
    val (hideExplicit, setHideExplicit) = rememberPreference(HideExplicitKey, false)
    val (hideVideos, setHideVideos) = rememberPreference(HideVideoSongsKey, false)
    var showConfirmation by remember { mutableStateOf(false) }
    Material3SettingsGroup(
        title = stringResource(R.string.android_auto),
        items = listOf(
            Material3SettingsItem(
                title = { Text(stringResource(R.string.voice_commands)) },
                description = { Text(stringResource(R.string.android_auto_voice_help)) },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.android_auto_boost)) },
                description = {
                    Column {
                        Text(stringResource(R.string.android_auto_boost_description))
                        Text("+${safeBoost.roundToInt()} dB")
                        Slider(
                            value = safeBoost,
                            onValueChange = { setBoost(it.roundToInt().toFloat()) },
                            valueRange = 0f..12f,
                            steps = 11,
                        )
                    }
                },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.android_auto_lyrics)) },
                description = { Text(stringResource(R.string.android_auto_lyrics_description)) },
                trailingContent = {
                    Switch(checked = lyrics, onCheckedChange = {
                        if (it) showConfirmation = true else {
                            setLyrics(false)
                            service?.androidAutoLyrics?.revokeConfirmation()
                        }
                    })
                },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(if (confirmed) R.string.android_auto_passenger_confirmed else R.string.android_auto_confirm_passenger)) },
                description = { Text(stringResource(if (connected) R.string.android_auto_passenger_description else R.string.android_auto_connect_first)) },
                onClick = if (connected) ({ showConfirmation = true }) else null,
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.android_auto_sort)) },
                description = { Text(stringResource(if (sort == "name") R.string.sort_by_name else R.string.sort_by_create_date)) },
                onClick = { setSort(if (sort == "name") "date" else "name") },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.hide_explicit)) },
                trailingContent = { Switch(checked = hideExplicit, onCheckedChange = setHideExplicit) },
                onClick = { setHideExplicit(!hideExplicit) },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.hide_video_songs)) },
                trailingContent = { Switch(checked = hideVideos, onCheckedChange = setHideVideos) },
                onClick = { setHideVideos(!hideVideos) },
            ),
            Material3SettingsItem(
                title = { Text(stringResource(R.string.android_auto_descending)) },
                trailingContent = { Switch(checked = descending, onCheckedChange = setDescending) },
                onClick = { setDescending(!descending) },
            ),
        ),
    )
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(stringResource(R.string.android_auto_confirm_passenger)) },
            text = { Text(stringResource(R.string.android_auto_passenger_description)) },
            confirmButton = {
                TextButton(onClick = {
                    setLyrics(true)
                    service?.androidAutoLyrics?.confirmPassenger()
                    showConfirmation = false
                }) { Text(stringResource(R.string.android_auto_passenger_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
