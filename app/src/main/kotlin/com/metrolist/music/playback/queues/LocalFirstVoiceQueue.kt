package com.metrolist.music.playback.queues

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.media3.common.MediaItem
import com.metrolist.music.models.MediaMetadata

internal class LocalFirstVoiceQueue(
    private val title: String,
    private val local: List<MediaItem>,
    private val loadOnline: suspend () -> List<MediaItem>,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    @Volatile private var loaded = false
    private val loadMutex = Mutex()
    private val seen = local.mapTo(mutableSetOf()) { it.mediaId }

    override suspend fun getInitialStatus(): Queue.Status =
        Queue.Status(title, local.ifEmpty { nextPage() }, 0)

    override fun hasNextPage() = !loaded

    override suspend fun nextPage(): List<MediaItem> = loadMutex.withLock {
        if (loaded) return@withLock emptyList()
        val items = loadOnline()
        loaded = true
        items.filter { seen.add(it.mediaId) }
    }
}
