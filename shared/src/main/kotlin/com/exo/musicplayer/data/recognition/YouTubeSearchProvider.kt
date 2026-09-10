package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Searches YouTube without an API key, via Piped.
 *
 * Piped is a privacy front-end that exposes a plain JSON search endpoint, so no
 * Google API key, quota or billing account is involved. Deliberately *not* done
 * with yt-dlp: yt-dlp's Android binding maps a single video and cannot return a
 * result list, and shelling out to it for every keystroke would be slow. This is
 * one HTTP call, it lives in the shared module, and Windows gets it unchanged.
 *
 * What comes back is a real watch URL, which is then handed to yt-dlp to
 * download — search and download stay separate concerns.
 *
 * Public instances come and go constantly, so several are tried in turn and the
 * first that answers wins. If every one is down, YouTube rows are simply absent
 * from the results rather than the whole search failing.
 */
class YouTubeSearchProvider : MetadataProvider {

    override val label = "YouTube"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            for (instance in INSTANCES) {
                val body = Http.get(
                    "$instance/search?q=$encoded&filter=music_songs",
                    mapOf("Accept" to "application/json")
                ) ?: continue

                val parsed = parse(body, limit)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
            emptyList()
        }

    private fun parse(body: String, limit: Int): List<MusicMatch> = runCatching {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        (0 until minOf(items.length(), limit)).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") !in setOf("stream", "video")) return@mapNotNull null

            val title = item.optString("title").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            // Piped returns a site-relative path like "/watch?v=ID".
            val path = item.optString("url").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val videoId = path.substringAfter("v=", "").substringBefore('&')
            if (videoId.isBlank()) return@mapNotNull null

            MusicMatch(
                title = title,
                artist = item.optString("uploaderName").takeIf { it.isNotBlank() },
                album = null,
                artworkUrl = item.optString("thumbnail").takeIf { it.isNotBlank() },
                durationMs = item.optLong("duration", 0L).takeIf { it > 0 }?.times(1000),
                source = MusicMatch.Source.SEARCH,
                provider = label,
                // Canonical youtube.com URL rather than the Piped proxy, so
                // yt-dlp fetches from the source.
                downloadUrl = "https://www.youtube.com/watch?v=$videoId"
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        /** Verified reachable at time of writing; they rot, hence the fallback. */
        val INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://piped-api.lunar.icu",
            "https://pipedapi.reallyaweso.me",
            "https://pipedapi.adminforge.de"
        )
    }
}
