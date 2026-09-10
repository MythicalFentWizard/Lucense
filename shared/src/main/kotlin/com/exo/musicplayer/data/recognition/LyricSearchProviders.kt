package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Finding a song from a scrap of its words.
 *
 * The reverse of the lyrics chain: there you know the song and want the words,
 * here you have the words and want the song — half-remembered, usually
 * misremembered. Two services index lyric text itself and need no key. LRCLIB is
 * deliberately absent: its search matches track and artist names only, and
 * returns nothing at all for a lyric line, so including it would just add a
 * request that never contributes.
 *
 * Both return [MusicMatch], so results drop into the same list as everything
 * else in Identify.
 */

/**
 * Genius, via the `lyric` section of its multi-search.
 *
 * The plain song search matches titles and artists and finds nothing for a lyric
 * line; multi-search splits its answer into sections and one of them is a real
 * lyric index. Only that section is read — `top_hit` repeats it and `artist`
 * matches the words against band names, which produces nonsense.
 */
class GeniusLyricSearch : MetadataProvider {

    override val label = "Genius"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://genius.com/api/search/multi?q=${URLEncoder.encode(query, "UTF-8")}",
                mapOf("User-Agent" to BROWSER_AGENT, "Accept" to "application/json")
            ) ?: return@withContext emptyList()

            val sections = runCatching {
                JSONObject(body).optJSONObject("response")?.optJSONArray("sections")
            }.getOrNull() ?: return@withContext emptyList()

            val hits = (0 until sections.length())
                .mapNotNull { sections.optJSONObject(it) }
                .firstOrNull { it.optString("type") == "lyric" }
                ?.optJSONArray("hits")
                ?: return@withContext emptyList()

            (0 until minOf(hits.length(), limit)).mapNotNull { index ->
                val result = hits.optJSONObject(index)?.optJSONObject("result")
                    ?: return@mapNotNull null
                val title = result.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                MusicMatch(
                    title = title,
                    artist = result.optJSONObject("primary_artist")
                        ?.optString("name")?.takeIf { it.isNotBlank() },
                    album = null,
                    artworkUrl = result.optString("song_art_image_url")
                        .takeIf { it.isNotBlank() },
                    releaseYear = result.optString("release_date_for_display")
                        .takeLast(4).toIntOrNull(),
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

/**
 * NetEase Cloud Music, whose search type 1006 queries lyrics rather than titles.
 *
 * Strong on exactly the case Genius is weakest at — non-English words, and songs
 * with no Genius page at all. It happily returns the same recording several
 * times over as separate releases, so near-identical rows are collapsed.
 */
class NeteaseLyricSearch : MetadataProvider {

    override val label = "NetEase"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://music.163.com/api/search/get" +
                    "?s=${URLEncoder.encode(query, "UTF-8")}&type=$LYRICS_SEARCH&limit=$limit",
                mapOf("Referer" to "https://music.163.com/")
            ) ?: return@withContext emptyList()

            val songs = runCatching {
                JSONObject(body).optJSONObject("result")?.optJSONArray("songs")
            }.getOrNull() ?: return@withContext emptyList()

            (0 until songs.length()).mapNotNull { index ->
                val song = songs.optJSONObject(index) ?: return@mapNotNull null
                val title = song.optString("name").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val artists = song.optJSONArray("artists")
                val artist = (0 until (artists?.length() ?: 0))
                    .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }
                val album = song.optJSONObject("album")

                MusicMatch(
                    title = title,
                    artist = artist,
                    album = album?.optString("name")?.takeIf { it.isNotBlank() },
                    artworkUrl = album?.optString("picUrl")?.takeIf { it.isNotBlank() },
                    durationMs = song.optLong("duration", 0L).takeIf { it > 0 },
                    source = MusicMatch.Source.SEARCH,
                    provider = label,
                    drmProtected = true
                )
            }.distinctBy { "${it.artist.orEmpty()}|${it.title}".lowercase() }
        }

    private companion object {
        /** NetEase search types: 1 is songs, 1006 is lyrics. */
        const val LYRICS_SEARCH = 1006
    }
}
