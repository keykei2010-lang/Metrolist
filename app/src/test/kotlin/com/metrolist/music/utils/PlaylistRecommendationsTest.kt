package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRecommendationsTest {
    @Test fun `different seed radios contribute without playlist or duplicate songs`() {
        assertEquals(listOf("a", "c", "b", "d"), blendRecommendations(
            listOf(listOf("seed", "a", "b"), listOf("a", "c", "d")), setOf("seed"), 4, { it }))
    }

    @Test fun `empty or exhausted radios do not prevent other seeds contributing`() {
        assertEquals(listOf("a", "b"), blendRecommendations(listOf(emptyList(), listOf("a", "b")), emptySet(), 20, { it }))
        assertEquals(emptyList<String>(), blendRecommendations(listOf(listOf("a")), emptySet(), 0, { it }))
    }
}
