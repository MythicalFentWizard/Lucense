package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * Audius: a free, keyless catalogue whose tracks are also freely streamable.
 *
 * Unlike the store APIs, a hit here carries a real download target, so the
 * downloader can fetch the track itself instead of hunting for a match.
 */
class AudiusProvider : MetadataProvider {

    override val label = "Audius"

    /** Audius is a network of hosts; the entry point hands out a live one. */
    @Volatile
    private var host: String? = null

    private fun host(): String? {
        host?.let { return it }
        val body = Http.get("https://api.audius.co") ?: return null
        val first = runCatching {
            JSONObject(body).optJSONArray("data")?.optString(0)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        host = first
        return first
    }

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val base = host() ?: return@withContext emptyList()
            val body = Http.get(
                "$base/v1/tracks/search?query=${encode(query)}&app_name=Resonate"
            ) ?: return@withContext emptyList()

            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
                ?: return@withContext emptyList()

            (0 until minOf(data.length(), limit)).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val permalink = item.optString("permalink").takeIf { it.isNotBlank() }
                MusicMatch(
                    title = title,
                    artist = item.optJSONObject("user")?.optString("name")
                        ?.takeIf { it.isNotBlank() },
                    album = null,
                    artworkUrl = item.optJSONObject("artwork")
                        ?.optString("480x480")?.takeIf { it.isNotBlank() },
                    durationMs = item.optLong("duration", 0L).takeIf { it > 0 }?.times(1000),
                    source = MusicMatch.Source.SEARCH,
                    provider = label,
                    downloadUrl = permalink?.let { "https://audius.co$it" }
                )
            }
        }
}

/**
 * Internet Archive: live sets, sessions and public-domain recordings that no
 * commercial catalogue lists. yt-dlp has an archive.org extractor, so results
 * are directly downloadable.
 */
class InternetArchiveProvider : MetadataProvider {

    override val label = "Archive"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val q = encode("$query AND mediatype:(audio)")
            val body = Http.get(
                "https://archive.org/advancedsearch.php?q=$q" +
                    "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=year" +
                    "&rows=$limit&page=1&output=json"
            ) ?: return@withContext emptyList()

            val docs = runCatching {
                JSONObject(body).optJSONObject("response")?.optJSONArray("docs")
            }.getOrNull() ?: return@withContext emptyList()

            (0 until docs.length()).mapNotNull { index ->
                val item = docs.optJSONObject(index) ?: return@mapNotNull null
                val identifier = item.optString("identifier").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                // "creator" is sometimes a string, sometimes an array.
                val creator = item.optString("creator").takeIf { it.isNotBlank() }
                    ?: item.optJSONArray("creator")?.optString(0)?.takeIf { it.isNotBlank() }

                MusicMatch(
                    title = title,
                    artist = creator,
                    album = null,
                    releaseYear = item.optString("year").take(4).toIntOrNull(),
                    source = MusicMatch.Source.SEARCH,
                    provider = label,
                    downloadUrl = "https://archive.org/details/$identifier"
                )
            }
        }
}
