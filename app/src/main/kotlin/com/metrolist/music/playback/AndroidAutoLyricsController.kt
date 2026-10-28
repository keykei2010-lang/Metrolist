package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.constants.AndroidAutoLyricsKey
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAutoLyricsController(private val service: MusicService) {
    private var original: MediaItem? = null
    private var pushedLine: String? = null
    private var released = false

    fun release() {
        restorePushedItem()
        released = true
    }

    fun launchIn(scope: CoroutineScope) = scope.launch {
        combine(
            service.androidAutoConnected,
            service.dataStore.data.map { it[AndroidAutoLyricsKey] ?: false }.distinctUntilChanged(),
            service.currentMediaMetadata.distinctUntilChangedBy { it?.id },
        ) { connected, enabled, metadata ->
            metadata?.takeIf { connected && enabled }
        }.collectLatest media@{ metadata ->
            restorePushedItem()
            if (metadata == null || released) return@media
            service.database.lyrics(metadata.id).collectLatest lyrics@{ lyrics ->
                restorePushedItem()
                try {
                    val lines = lyrics?.lyrics?.let(LyricsUtils::parseLyrics).orEmpty()
                        .filter { !it.isBackground }.sortedBy { it.time }
                    if (lines.none { it.time > 0L }) return@lyrics
                    var lastLine: String? = null
                    while (!released && isActive && service.player.currentMediaItem?.mediaId == metadata.id) {
                        val position = service.player.currentPosition
                        val line = lines.lastOrNull { it.time <= position }?.text?.takeIf { it.isNotBlank() }
                        if (line != lastLine) {
                            val item = service.player.currentMediaItem ?: break
                            if (line == null) {
                                restorePushedItem()
                            } else {
                                if (original == null) original = item
                                val display = "♪ $line"
                                pushedLine = display
                                // Preserve the tag and source so this is a metadata-only update.
                                val updated = item.mediaMetadata.buildUpon()
                                    .setArtist(display).setSubtitle(display).build()
                                service.player.replaceMediaItem(service.player.currentMediaItemIndex,
                                    item.buildUpon().setMediaMetadata(updated).build())
                            }
                            lastLine = line
                        }
                        delay(250L)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    restorePushedItem()
                }
            }
        }
    }

    private fun restorePushedItem() {
        val base = original ?: return
        val display = pushedLine
        original = null
        pushedLine = null
        // A skipped item is still in the queue; restore it before the next song emits lyrics.
        val player = service.player
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId == base.mediaId && item.mediaMetadata.artist?.toString() == display &&
                item.mediaMetadata.subtitle?.toString() == display) {
                player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(
                    item.mediaMetadata.buildUpon().setArtist(base.mediaMetadata.artist)
                        .setSubtitle(base.mediaMetadata.subtitle).build(),
                ).build())
            }
        }
    }
}
