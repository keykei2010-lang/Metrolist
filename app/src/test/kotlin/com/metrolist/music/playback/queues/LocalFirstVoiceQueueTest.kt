package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalFirstVoiceQueueTest {
    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()
    @Test fun `local songs start immediately and online songs are appended without duplicates`() = runBlocking {
        var calls = 0
        val queue = LocalFirstVoiceQueue("Artist", listOf(item("cached"), item("playlist"))) {
            calls++
            listOf(item("cached"), item("online"), item("online"))
        }
        assertEquals(listOf("cached", "playlist"), queue.getInitialStatus().items.map { it.mediaId })
        assertEquals(0, calls)
        assertTrue(queue.hasNextPage())
        assertEquals(listOf("online"), queue.nextPage().map { it.mediaId })
        assertFalse(queue.hasNextPage())
        assertTrue(queue.nextPage().isEmpty())
    }
    @Test fun `rapid skips cannot start duplicate online loads`() = runBlocking {
        var calls = 0
        val queue = LocalFirstVoiceQueue("Artist", listOf(item("local"))) {
            calls++
            delay(20)
            listOf(item("online"))
        }
        val results = listOf(async { queue.nextPage() }, async { queue.nextPage() }).awaitAll().flatten()
        assertEquals(1, calls)
        assertEquals(listOf("online"), results.map { it.mediaId })
    }
    @Test fun `online songs are fetched initially only if no local songs exist`() = runBlocking {
        val queue = LocalFirstVoiceQueue("Artist", emptyList()) { listOf(item("online")) }
        assertEquals("online", queue.getInitialStatus().items.single().mediaId)
        assertFalse(queue.hasNextPage())
    }
}
