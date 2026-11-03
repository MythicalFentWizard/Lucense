package com.exo.musicplayer.data.lyrics

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

/**
 * Lyrics in another language, line for line.
 *
 * Riff translates lyrics inside its lyrics panel (github.com/rootscripts/riff,
 * MIT) through the same free Google Translate endpoint used here, which needs
 * no key. The lines go as one text with a line break between each, and the
 * breaks come back where they were, so line five of the translation sits under
 * line five of the song, timestamps and all.
 */
object LyricsTranslator {

    /** The translated lines, one for each line given, and the language they were in. */
    data class Translation(val lines: List<String>, val from: String?)

    /** Long lyrics go in pieces: the request is a URL, and URLs have a length. */
    private const val CHUNK_CHARS = 1_500

    /** The language to translate into: the device's, as a two-letter code. */
    fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    /**
     * [lines] in [to], or null when the service couldn't be reached or the
     * answer didn't line up. Blank lines stay blank.
     */
    suspend fun translate(lines: List<String>, to: String = deviceLanguage()): Translation? =
        withContext(Dispatchers.IO) {
            // Only the lines with words go. Google drops blank lines at the end
            // of a text, which would throw every line after them out of step,
            // so the blanks are put back afterwards instead.
            val worded = lines.indices.filter { lines[it].isNotBlank() }
            if (worded.isEmpty()) return@withContext Translation(lines.map { "" }, null)
            val translated = HashMap<Int, String>(worded.size)
            val languages = HashMap<String, Int>()
            val target = to.substringBefore('-').lowercase()
            for (chunk in chunks(worded) { lines[it].trim().length }) {
                // One q per line, answered as [[translation, its language], ...] in order.
                // Asked as one text, a song mostly in English with a few lines of
                // French was taken for English and handed back untranslated.
                val url = "https://translate.googleapis.com/translate_a/t?client=gtx&sl=auto&tl=" +
                    URLEncoder.encode(to, "UTF-8") +
                    chunk.joinToString("") { "&q=" + URLEncoder.encode(lines[it].trim(), "UTF-8") }
                val json = Http.get(url) ?: return@withContext null
                val back = runCatching { JSONArray(json) }.getOrNull() ?: return@withContext null
                // Lined up by position, so a different count means it can't be trusted.
                if (back.length() != chunk.size) return@withContext null
                chunk.forEachIndexed { i, index ->
                    val pair = back.optJSONArray(i)
                    val text = (pair?.optString(0) ?: back.optString(i)).trim()
                    val language = pair?.optString(1)?.lowercase().orEmpty()
                    // A line already in [to] stays blank, so only the foreign ones show a translation.
                    val same = language.substringBefore('-') == target ||
                        text.equals(lines[index].trim(), ignoreCase = true)
                    translated[index] = if (same) "" else text
                    if (!same && language.isNotBlank()) languages[language] = (languages[language] ?: 0) + 1
                }
            }
            Translation(lines.indices.map { translated[it].orEmpty() }, languages.maxByOrNull { it.value }?.key)
        }

    /** [items] in runs whose [length]s, plus a line break each, stay under [CHUNK_CHARS]. */
    private fun <T> chunks(items: List<T>, length: (T) -> Int): List<List<T>> {
        val result = ArrayList<List<T>>()
        var current = ArrayList<T>()
        var size = 0
        for (item in items) {
            val n = length(item) + 1
            if (size + n > CHUNK_CHARS && current.isNotEmpty()) {
                result += current
                current = ArrayList()
                size = 0
            }
            current += item
            size += n
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    /** "pt" as "Portuguese", in English, for saying what it was translated from. */
    fun languageName(code: String?): String? =
        code?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).takeIf { name -> name.isNotBlank() && name != it } }
}
