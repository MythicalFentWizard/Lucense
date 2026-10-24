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
            if (lines.none { it.isNotBlank() }) return@withContext Translation(lines, null)
            val out = ArrayList<String>(lines.size)
            var from: String? = null
            for (chunk in chunks(lines)) {
                val text = chunk.joinToString("\n")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t" +
                    "&tl=" + URLEncoder.encode(to, "UTF-8") + "&q=" + URLEncoder.encode(text, "UTF-8")
                val json = Http.get(url) ?: return@withContext null
                val parsed = runCatching { JSONArray(json) }.getOrNull() ?: return@withContext null
                val pieces = parsed.optJSONArray(0) ?: return@withContext null
                val joined = buildString {
                    for (i in 0 until pieces.length()) append(pieces.optJSONArray(i)?.optString(0).orEmpty())
                }
                from = from ?: parsed.optString(2).takeIf { it.isNotBlank() }
                val back = joined.split('\n')
                // Lined up by position, so a different count means it can't be trusted.
                val fitted = if (back.size >= chunk.size) back.take(chunk.size) else return@withContext null
                chunk.forEachIndexed { i, original -> out += if (original.isBlank()) "" else fitted[i].trim() }
            }
            Translation(out, from)
        }

    private fun chunks(lines: List<String>): List<List<String>> {
        val result = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var size = 0
        for (line in lines) {
            if (size + line.length + 1 > CHUNK_CHARS && current.isNotEmpty()) {
                result += current
                current = ArrayList()
                size = 0
            }
            current += line
            size += line.length + 1
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    /** "pt" as "Portuguese", in English, for saying what it was translated from. */
    fun languageName(code: String?): String? =
        code?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).takeIf { name -> name.isNotBlank() && name != it } }
}
