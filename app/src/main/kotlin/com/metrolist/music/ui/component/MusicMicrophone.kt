package com.metrolist.music.ui.component

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.VoiceCommandLanguageKey
import com.metrolist.music.constants.VoiceCommandPreferOfflineKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun rememberMusicMicrophoneAction(onRecognize: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val defaultLanguage = remember {
        val locale = Locale.getDefault()
        if (locale.language == "pt") {
            if (locale.country == "BR") "pt-BR" else "pt-PT"
        } else "en-US"
    }
    val (savedLanguage, setLanguage) = rememberPreference(VoiceCommandLanguageKey, defaultLanguage)
    val (preferOffline, setPreferOffline) = rememberPreference(VoiceCommandPreferOfflineKey, false)
    val languages = listOf(
        "pt-PT" to R.string.voice_language_portugal,
        "pt-BR" to R.string.voice_language_brazil,
        "en-US" to R.string.voice_language_english,
    )
    val language = savedLanguage.takeIf { tag -> languages.any { it.first == tag } } ?: defaultLanguage
    var showLanguages by remember { mutableStateOf(false) }
    val prompt = stringResource(R.string.voice_command_prompt)
    val unavailable = stringResource(R.string.voice_command_unavailable)
    val noMatch = stringResource(R.string.voice_command_no_match)
    val failed = stringResource(R.string.voice_command_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank() && player != null) {
                job?.cancel()
                job = scope.launch {
                    try {
                        val queue = player.service.voiceCommands.resolve(text)
                        if (queue == null) Toast.makeText(context, noMatch, Toast.LENGTH_LONG).show()
                        else player.playQueue(queue)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(stringResource(R.string.microphone_actions)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(prompt)
                    Box {
                        TextButton(onClick = { showLanguages = true }) {
                            Text(stringResource(R.string.voice_command_language, stringResource(languages.first { it.first == language }.second)))
                        }
                        DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                            languages.forEach { (tag, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    setLanguage(tag)
                                    showLanguages = false
                                })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.voice_command_prefer_offline), Modifier.weight(1f))
                        Switch(checked = preferOffline, onCheckedChange = setPreferOffline)
                    }
                    Text(stringResource(R.string.voice_command_offline_help))
                }
            },
            confirmButton = {
                TextButton(enabled = player != null, onClick = {
                    showActions = false
                    try {
                        launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                        })
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.voice_commands)) }
            },
            dismissButton = {
                TextButton(onClick = { showActions = false; onRecognize() }) {
                    Text(stringResource(R.string.recognize_music))
                }
            },
        )
    }
    return { showActions = true }
}
