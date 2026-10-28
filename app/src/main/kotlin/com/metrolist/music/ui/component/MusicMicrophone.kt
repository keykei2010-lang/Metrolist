package com.metrolist.music.ui.component

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberMusicMicrophoneAction(onRecognize: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
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
            text = { Text(prompt) },
            confirmButton = {
                TextButton(enabled = player != null, onClick = {
                    showActions = false
                    try {
                        launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
