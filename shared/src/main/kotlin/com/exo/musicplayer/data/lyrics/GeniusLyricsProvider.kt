package com.exo.musicplayer.data.lyrics

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Genius, as a last resort.
 *
 * Deliberately the final tier. Genius returns plain text only — its API has no
 * lyrics field at all, so the words have to be read off the song page — which
 * means no timestamps and therefore no line-by-line highlighting. LRCLIB and
 * NetEase both supply timed LRC, so falling back here is a downgrade in
 * capability and is only worth doing when the earlier tiers have nothing.
 *
 * It is also the most brittle provider by some distance: it depends on Genius's
 * page structure rather than a JSON contract, so a redesign on their side
 * breaks it where the other three would be unaffected. Every failure path
 * returns NotFound so the chain simply ends without lyrics rather than erroring.
 */
class GeniusLyricsProvider : LyricsProvider {

    override val label = "Genius"

    override suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): LyricsFetch = withContext(Dispatchers.IO) {
        val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
        val songUrl = findSongUrl(query, title, artist) ?: return@withContext LyricsFetch.NotFound

        val page = Http.get(songUrl, mapOf("User-Agent" to BROWSER_AGENT))
            ?: return@withContext LyricsFetch.NotFound

        val text = extractLyrics(page)
        if (text.isNullOrBlank()) LyricsFetch.NotFound else LyricsFetch.Found(text, null)
    }

    /** Genius's own search endpoint needs no key; the public API would. */
    private fun findSongUrl(query: String, title: String, artist: String?): String? {
        val body = Http.get(
            "https://genius.com/api/search/song?q=${URLEncoder.encode(query, "UTF-8")}",
            mapOf("User-Agent" to BROWSER_AGENT, "Accept" to "application/json")
        ) ?: return null

        val hits = runCatching {
            JSONObject(body)
                .optJSONObject("response")
                ?.optJSONArray("sections")
                ?.optJSONObject(0)
                ?.optJSONArray("hits")
        }.getOrNull() ?: return null

        for (index in 0 until hits.length()) {
            val result = hits.optJSONObject(index)?.optJSONObject("result") ?: continue
            // "incomplete" and "unreleased" pages exist and hold nothing useful.
            if (result.optString("lyrics_state") != "complete") continue
            val url = result.optString("url").takeIf { it.isNotBlank() } ?: continue

            // Guard against confidently returning the wrong song's words.
            if (!plausible(result, title, artist)) continue
            return url
        }
        return null
    }

    private fun plausible(result: JSONObject, title: String, artist: String?): Boolean {
        val resultTitle = result.optString("title").lowercase(Locale.ROOT)
        val resultArtist = result.optJSONObject("primary_artist")
            ?.optString("name").orEmpty().lowercase(Locale.ROOT)
        val wantedTitle = title.lowercase(Locale.ROOT)

        val titleMatches = resultTitle.contains(wantedTitle) || wantedTitle.contains(resultTitle)
        if (!titleMatches) return false
        if (artist.isNullOrBlank()) return true

        val wantedArtist = artist.lowercase(Locale.ROOT)
        return resultArtist.contains(wantedArtist) || wantedArtist.contains(resultArtist)
    }

    /**
     * Pulls the text out of the lyrics containers.
     *
     * The containers nest other divs, so a non-greedy regex stops at the first
     * inner `</div>` and truncates the song. Div depth is tracked instead.
     */
    private fun extractLyrics(html: String): String? {
        val marker = "data-lyrics-container=\"true\""
        val parts = mutableListOf<String>()
        var from = 0

        while (true) {
            val attribute = html.indexOf(marker, from)
            if (attribute < 0) break
            val tagEnd = html.indexOf('>', attribute)
            if (tagEnd < 0) break

            val start = tagEnd + 1
            var index = start
            var depth = 1
            var contentEnd = -1

            while (index < html.length) {
                val nextOpen = html.indexOf("<div", index, ignoreCase = true)
                val nextClose = html.indexOf("</div", index, ignoreCase = true)
                if (nextClose < 0) { contentEnd = html.length; break }
                if (nextOpen in 0 until nextClose) {
                    depth++
                    index = nextOpen + 4
                } else {
                    depth--
                    if (depth == 0) { contentEnd = nextClose; index = nextClose + 5; break }
                    index = nextClose + 5
                }
            }
            if (contentEnd <= start) break

            parts += html.substring(start, contentEnd)
            from = index
        }

        if (parts.isEmpty()) return null
        return parts.joinToString("\n").let(::toPlainText).takeIf { it.isNotBlank() }
    }

    private fun toPlainText(fragment: String): String = fragment
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .lineSequence()
        .map { it.trim() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private companion object {
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
