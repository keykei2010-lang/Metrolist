package com.metrolist.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoSectionTest {
    @Test fun `new home section is added without losing old visibility and order`() {
        val sections = deserializeSections("songs:false,liked:true,artists:false,albums:true,playlists:true")
        assertEquals(AndroidAutoSection.SONGS, sections.first().first)
        assertFalse(sections.first().second)
        assertTrue(sections.contains(AndroidAutoSection.HOME to true))
        assertEquals(AndroidAutoSection.entries.size, sections.size)
    }

    @Test fun `unknown and duplicate sections cannot create duplicate lazy list keys`() {
        val sections = deserializeSections("songs:false,unknown:true,songs:true,invalid")
        assertEquals(1, sections.count { it.first == AndroidAutoSection.SONGS })
        assertFalse(sections.first().second)
        assertEquals(AndroidAutoSection.entries.size, sections.size)
    }

    @Test fun `saved visibility and order round trip`() {
        val sections = AndroidAutoSection.entries.reversed().map { it to (it != AndroidAutoSection.HOME) }
        assertEquals(sections, deserializeSections(serializeSections(sections)))
    }
}
