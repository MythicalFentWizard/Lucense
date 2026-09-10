package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Genius as a cover-art and tag source.
 *
 * Uses the same keyless search endpoint the lyrics provider does, which already
 * carries `song_art_image_url` at 1000x1000 — so artwork costs nothing beyond
 * the request that was being made anyway.
 *
 * Placed last in the artwork chain deliberately: what Genius calls song art is
 * usually the album cover, but for some tracks it is a Genius-made graphic or a
 * single sleeve that doesn't match the album. The stores are a safer first
 * guess; this fills the gaps they don't cover.
 *
 * No download URL — Genius hosts no audio.
 */
class GeniusMetadataProvider : MetadataProvider {

    override val label = "Genius"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://genius.com/api/search/song?q=${URLEncoder.encode(query, "UTF-8")}",
                mapOf("User-Agent" to BROWSER_AGENT, "Accept" to "application/json")
            ) ?: return@withContext emptyList()

            val hits = runCatching {
                JSONObject(body)
                    .optJSONObject("response")
                    ?.optJSONArray("sections")
                    ?.optJSONObject(0)
                    ?.optJSONArray("hits")
            }.getOrNull() ?: return@withContext emptyList()

            (0 until minOf(hits.length(), limit)).mapNotNull { index ->
                val result = hits.optJSONObject(index)?.optJSONObject("result")
                    ?: return@mapNotNull null
                val title = result.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                // Full-size art first; the thumbnail is only 300px.
                val art = result.optString("song_art_image_url").takeIf { it.isNotBlank() }
                    ?: result.optString("header_image_url").takeIf { it.isNotBlank() }
                    ?: result.optString("song_art_image_thumbnail_url")
                        .takeIf { it.isNotBlank() }

                MusicMatch(
                    title = title,
                    artist = result.optJSONObject("primary_artist")?.optString("name")
                        ?.takeIf { it.isNotBlank() }
                        ?: result.optString("artist_names").takeIf { it.isNotBlank() },
                    album = null,
                    artworkUrl = art,
                    // Displayed as e.g. "March 1969"; the year is the useful part.
                    releaseYear = result.optString("release_date_for_display")
                        .split(' ').lastOrNull()?.toIntOrNull(),
                    source = MusicMatch.Source.SEARCH,
                    provider = label
                )
            }
        }

    private companion object {
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
