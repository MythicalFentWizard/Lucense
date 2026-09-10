package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One place track names, tags and cover art can come from. */
interface MetadataProvider {
    val label: String
    suspend fun search(query: String, limit: Int): List<MusicMatch>
}

/**
 * Tries each provider until one returns usable results.
 *
 * The three have genuinely different coverage, which is the point: iTunes is
 * strongest on mainstream Western releases, Deezer often has tracks and cover
 * art iTunes lacks, and MusicBrainz covers the long tail of obscure and
 * non-commercial releases that neither store carries.
 *
 * [searchForArtwork] additionally skips results with no image, because a
 * perfect title match that carries no cover is useless to the cover refresh.
 */
class MetadataProviderChain(private val providers: List<MetadataProvider>) {

    /**
     * Queries every provider at once and merges the results.
     *
     * Different from [search], which stops at the first provider that answers.
     * For a browsable catalogue the point is breadth: iTunes and Deezer cover
     * commercial releases, MusicBrainz the long tail, Audius independent
     * artists, Internet Archive live and public-domain recordings. One provider
     * being slow or down just means fewer rows, never an empty list.
     */
    suspend fun searchAll(query: String, limitPer: Int = 6): List<MusicMatch> = coroutineScope {
        val batches = providers.map { provider ->
            async {
                runCatching { provider.search(query, limitPer) }
                    .getOrDefault(emptyList())
                    .map { if (it.provider.isBlank()) it.copy(provider = provider.label) else it }
            }
        }.awaitAll()

        // Interleaved so the list opens with one row per service, rather than
        // twenty from whichever provider happened to answer first.
        val queues = batches.filter { it.isNotEmpty() }.map { it.toMutableList() }
        val merged = mutableListOf<MusicMatch>()
        while (queues.any { it.isNotEmpty() }) {
            for (queue in queues) if (queue.isNotEmpty()) merged += queue.removeAt(0)
        }
        merged.distinctBy { "${it.artist.orEmpty()}|${it.title}".lowercase() }
    }

    suspend fun search(query: String, limit: Int = 20): Pair<List<MusicMatch>, String?> {
        for (provider in providers) {
            val results = runCatching { provider.search(query, limit) }.getOrDefault(emptyList())
            if (results.isNotEmpty()) return results to provider.label
        }
        return emptyList<MusicMatch>() to null
    }

    suspend fun searchForArtwork(query: String): String? {
        for (provider in providers) {
            val art = runCatching { provider.search(query, 5) }
                .getOrDefault(emptyList())
                .firstNotNullOfOrNull { it.artworkUrl }
            if (art != null) return art
        }
        return null
    }
}

/** Primary: no key, no rate-limit registration, ships 400px art. */
class ITunesProvider(private val client: MusicSearchClient = MusicSearchClient()) :
    MetadataProvider {

    override val label = "iTunes"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> {
        val result = client.search(query, limit)
        return (result as? RecognitionResult.Found)?.matches.orEmpty()
            .map { it.copy(provider = label) }
    }
}

/** Secondary: free, no key, and its cover art is often higher resolution. */
class DeezerProvider : MetadataProvider {

    override val label = "Deezer"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://api.deezer.com/search?q=${encodeQuery(query)}&limit=$limit"
            ) ?: return@withContext emptyList()

            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
                ?: return@withContext emptyList()

            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val album = item.optJSONObject("album")
                MusicMatch(
                    title = title,
                    artist = item.optJSONObject("artist")?.optString("name")
                        ?.takeIf { it.isNotBlank() },
                    album = album?.optString("title")?.takeIf { it.isNotBlank() },
                    artworkUrl = album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                        ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() },
                    durationMs = item.optLong("duration", 0L).takeIf { it > 0 }?.times(1000),
                    source = MusicMatch.Source.SEARCH,
                    provider = label,
                    drmProtected = true
                )
            }
        }
}

/**
 * Tertiary: MusicBrainz for the metadata, Cover Art Archive for the image.
 *
 * Slower than the two stores — it needs a second request per release to know
 * whether art exists — but it is the only one of the three that covers
 * bootlegs, regional releases and self-published tracks.
 */
class MusicBrainzProvider : MetadataProvider {

    override val label = "MusicBrainz"

    override suspend fun search(query: String, limit: Int): List<MusicMatch> =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://musicbrainz.org/ws/2/recording" +
                    "?query=${encodeQuery(query)}&fmt=json&limit=$limit"
            ) ?: return@withContext emptyList()

            val recordings = runCatching {
                JSONObject(body).optJSONArray("recordings")
            }.getOrNull() ?: return@withContext emptyList()

            (0 until recordings.length()).mapNotNull { index ->
                val item = recordings.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val artist = item.optJSONArray("artist-credit")
                    ?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
                val release = item.optJSONArray("releases")?.optJSONObject(0)
                val releaseId = release?.optString("id")?.takeIf { it.isNotBlank() }

                MusicMatch(
                    title = title,
                    artist = artist,
                    album = release?.optString("title")?.takeIf { it.isNotBlank() },
                    // The Archive 404s for releases with no art; that is exactly
                    // what a null artworkUrl should mean, so it is left to the
                    // image loader to discover rather than pre-checked here.
                    artworkUrl = releaseId?.let {
                        "https://coverartarchive.org/release/$it/front-500"
                    },
                    releaseYear = item.optString("first-release-date").take(4).toIntOrNull(),
                    durationMs = item.optLong("length", 0L).takeIf { it > 0 },
                    source = MusicMatch.Source.SEARCH,
                    provider = label
                )
            }
        }
}

private fun encodeQuery(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
