package com.exo.musicplayer.data

import java.text.Normalizer

/**
 * Searching names the way people type them: words in any order, any case,
 * with or without accents. "beyonce lemonade" finds Beyoncé's Lemonade. For
 * the album and artist pages, where there is a name and who it's by.
 */
object NameSearch {

    private val MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")

    /** [text] lower-cased and without accents. */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(MARKS, "").lowercase()

    /** Whether every word of [query] is somewhere in [fields]; an empty query matches everything. */
    fun matches(query: String, vararg fields: String?): Boolean {
        val words = fold(query).split(SPACES).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = fields.filterNotNull().joinToString(" ") { fold(it) }
        return words.all { it in text }
    }
}
