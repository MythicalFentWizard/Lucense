package com.exo.musicplayer.desktop

import com.exo.musicplayer.data.NameSearch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The album and artist search: words in any order, any case, accents or not. */
class NameSearchTest {

    @Test
    fun `words in any order and either field`() {
        assertTrue(NameSearch.matches("beyonce lemonade", "Lemonade", "Beyoncé"))
        assertTrue(NameSearch.matches("LEMONADE", "Lemonade", "Beyoncé"))
        assertTrue(NameSearch.matches("  walker   faded ", "Faded", "Alan Walker"))
    }

    @Test
    fun `accents either way`() {
        assertTrue(NameSearch.matches("sigur ros", "Sigur Rós", null))
        assertTrue(NameSearch.matches("Rós", "Sigur Ros", null))
        assertTrue(NameSearch.matches("motorhead", "Motörhead", null))
    }

    @Test
    fun `every word has to be there`() {
        assertFalse(NameSearch.matches("beyonce renaissance", "Lemonade", "Beyoncé"))
        assertFalse(NameSearch.matches("zzz", "Faded", "Alan Walker"))
    }

    @Test
    fun `nothing typed shows everything`() {
        assertTrue(NameSearch.matches("", "Faded", null))
        assertTrue(NameSearch.matches("   ", null, null))
    }

    @Test
    fun `other scripts as typed`() {
        assertTrue(NameSearch.matches("米津", "米津玄師", null))
        assertTrue(NameSearch.matches("кино", "Кино", null))
    }
}
