package com.exo.musicplayer.data.playlist

import com.exo.musicplayer.data.net.Http
import com.exo.musicplayer.data.youtube.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder

/**
 * Playlists brought over from other services by their link, after Riff's
 * playlist migration (github.com/rootscripts/riff, MIT).
 *
 * Only the list is read - names, artists and lengths - and then matched
 * against the library like a playlist file, so what is already here is linked
 * up and what isn't can be downloaded. Nothing signs in anywhere: every source
 * here is what the service shows anyone with the link.
 *
 * - Spotify: the embed page carries the first hundred songs and an anonymous
 *   token, which pages through the rest the way Spotify's own web player does.
 * - Apple Music: a playlist page carries its songs in the data it renders
 *   from; an album comes from the iTunes lookup, which is public.
 * - YouTube, YouTube Music, SoundCloud and anything else yt-dlp reads: the
 *   app runs yt-dlp and hands the listing to [fromVideos] or [fromPrinted].
 */
object PlaylistLinks {

    enum class Service(val label: String) {
        SPOTIFY("Spotify"),
        APPLE_MUSIC("Apple Music"),
        YOUTUBE("YouTube"),
        SOUNDCLOUD("SoundCloud"),
        OTHER("that site")
    }

    /** What reading a link came to. */
    sealed interface Outcome {
        /** [note] says what was left out, when something was. */
        data class Read(val playlist: ParsedPlaylist, val note: String? = null) : Outcome
        data class Problem(val message: String) : Outcome
    }

    /** Which service a link belongs to, or null when it isn't a web link. */
    fun serviceOf(link: String): Service? {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        return when {
            host == "spotify.com" || host.endsWith(".spotify.com") || host == "spotify.link" -> Service.SPOTIFY
            host == "music.apple.com" || host == "itunes.apple.com" -> Service.APPLE_MUSIC
            host.endsWith("youtube.com") || host == "youtu.be" -> Service.YOUTUBE
            host.endsWith("soundcloud.com") -> Service.SOUNDCLOUD
            else -> Service.OTHER
        }
    }

    /** Spotify and Apple Music are read here; everything else goes to yt-dlp. */
    fun readsDirectly(service: Service): Boolean = service == Service.SPOTIFY || service == Service.APPLE_MUSIC

    /**
     * A SoundCloud set's flat listing names no songs, only their links, so its
     * songs are read in full instead: slower, a second or so a song, but with
     * their titles, artists and lengths.
     */
    fun needsFullListing(service: Service): Boolean = service == Service.SOUNDCLOUD

    /** Arguments after the link for [needsFullListing]: one `title, artist, seconds` line a song, then the name. */
    val PRINT_ARGUMENTS: List<String> = listOf(
        "--skip-download",
        "--no-warnings",
        "--ignore-config",
        "--yes-playlist",
        "--print", "%(title)s\t%(artist,creator,uploader)s\t%(duration)s",
        "--print", "playlist:$NAME_MARK\t%(title)s"
    )

    private const val NAME_MARK = "#LUCENSE-NAME"

    suspend fun read(link: String, proxy: Proxy? = null): Outcome = withContext(Dispatchers.IO) { readBlocking(link, proxy) }

    /** [read], on the calling thread. */
    fun readBlocking(link: String, proxy: Proxy? = null): Outcome = runCatching {
        when (serviceOf(link)) {
            Service.SPOTIFY -> spotify(link.trim(), proxy)
            Service.APPLE_MUSIC -> apple(link.trim(), proxy)
            null -> Outcome.Problem("That isn't a link.")
            else -> Outcome.Problem("That link is read with yt-dlp, not here.")
        }
    }.getOrElse { Outcome.Problem("Couldn't read that page: ${it.message ?: it.javaClass.simpleName}") }

    // ---- Spotify -----------------------------------------------------------------

    private const val BROWSER =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** Spotify's own web player asks for a playlist's pages with this saved query. */
    private const val FETCH_PLAYLIST_HASH = "8964e8eafb21aa992a7d951d256d83285c04be2105d209262901de70cb97584a"

    /** More than any real playlist; a stop for one that never ends. */
    private const val MOST_SONGS = 10_000

    private fun spotify(link: String, proxy: Proxy?): Outcome {
        val path = URI(link).path.orEmpty().split('/').filter { it.isNotBlank() }
        val at = path.indexOfFirst { it == "playlist" || it == "album" || it == "track" }
        if (at < 0 || at + 1 >= path.size) {
            return Outcome.Problem("That Spotify link isn't a playlist or an album.")
        }
        val kind = path[at]
        val id = path[at + 1]
        val page = Http.get("https://open.spotify.com/embed/$kind/$id", mapOf("User-Agent" to BROWSER), proxy)
            ?: return Outcome.Problem("Spotify didn't answer. A private playlist can't be read; make it public first.")
        val data = nextData(page) ?: return Outcome.Problem("Spotify's page has changed and couldn't be read.")
        val state = data.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
        val entity = state?.optJSONObject("data")?.optJSONObject("entity")
            ?: return Outcome.Problem("Spotify said that playlist doesn't exist, or isn't public.")
        val name = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Spotify playlist" }

        val list = entity.optJSONArray("trackList") ?: JSONArray()
        val entries = ArrayList<PlaylistEntry>()
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            val title = t.optString("title").takeIf { it.isNotBlank() } ?: continue
            entries += PlaylistEntry(t.optString("subtitle").takeIf { it.isNotBlank() }, title, t.optLong("duration", 0L))
        }

        // The embed stops at a hundred; the rest come in pages.
        var note: String? = null
        if (kind == "playlist" && entries.size >= 100) {
            val token = state.optJSONObject("settings")?.optJSONObject("session")?.optString("accessToken")
            val rest = token?.takeIf { it.isNotBlank() }?.let { spotifyPages(id, it, entries.size, proxy) }
            when {
                rest == null -> note = "Spotify only showed the first ${entries.size} songs."
                else -> entries += rest
            }
        }
        if (entries.isEmpty()) return Outcome.Problem("That Spotify list has no songs that can be read.")
        return Outcome.Read(ParsedPlaylist(name, entries), note)
    }

    /** The songs after the first [from], a hundred at a time. Null when Spotify wouldn't page. */
    private fun spotifyPages(id: String, token: String, from: Int, proxy: Proxy?): List<PlaylistEntry>? {
        val out = ArrayList<PlaylistEntry>()
        var offset = from
        var total = Int.MAX_VALUE
        while (offset < total && offset < MOST_SONGS) {
            val body = JSONObject()
                .put("operationName", "fetchPlaylist")
                .put(
                    "variables",
                    JSONObject()
                        .put("uri", "spotify:playlist:$id")
                        .put("offset", offset)
                        .put("limit", 100)
                        .put("enableWatchFeedEntrypoint", false)
                )
                .put(
                    "extensions",
                    JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", FETCH_PLAYLIST_HASH))
                )
            val answer = Http.post(
                "https://api-partner.spotify.com/pathfinder/v1/query",
                body.toString(),
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json;charset=UTF-8",
                    "User-Agent" to BROWSER
                ),
                proxy
            ) ?: return if (out.isEmpty()) null else out
            val content = JSONObject(answer).optJSONObject("data")?.optJSONObject("playlistV2")?.optJSONObject("content")
                ?: return if (out.isEmpty()) null else out
            total = content.optInt("totalCount", 0)
            val items = content.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i)?.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                // Podcast episodes and local files sit in playlists too, and aren't songs to find.
                if (track.optString("__typename", "Track") != "Track") continue
                val title = track.optString("name").takeIf { it.isNotBlank() } ?: continue
                val artists = track.optJSONObject("artists")?.optJSONArray("items")
                    ?: track.optJSONObject("albumOfTrack")?.optJSONObject("artists")?.optJSONArray("items")
                val artist = artists?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("profile")?.optString("name") }
                        .filter { it.isNotBlank() }.joinToString(", ")
                }
                val length = track.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L) ?: 0L
                out += PlaylistEntry(artist?.takeIf { it.isNotBlank() }, title, length)
            }
            offset += items.length()
        }
        return out
    }

    private fun nextData(page: String): JSONObject? {
        val match = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(page) ?: return null
        return runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
    }

    // ---- Apple Music -------------------------------------------------------------

    private fun apple(link: String, proxy: Proxy?): Outcome {
        val uri = URI(link)
        val path = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val storefront = path.firstOrNull()?.takeIf { it.length == 2 } ?: "us"
        return when {
            "album" in path -> appleAlbum(path.last().removePrefix("id"), storefront, proxy)
            "playlist" in path -> applePlaylist(link, proxy)
            else -> Outcome.Problem("That Apple Music link isn't a playlist or an album.")
        }
    }

    private fun appleAlbum(id: String, storefront: String, proxy: Proxy?): Outcome {
        if (id.isBlank() || !id.all(Char::isDigit)) return Outcome.Problem("That Apple Music album link has no album number in it.")
        val json = Http.get(
            "https://itunes.apple.com/lookup?id=$id&entity=song&limit=300&country=" + URLEncoder.encode(storefront, "UTF-8"),
            proxy = proxy
        ) ?: return Outcome.Problem("Apple didn't answer.")
        val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
        var name = "Apple Music album"
        val entries = ArrayList<PlaylistEntry>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            when (r.optString("wrapperType")) {
                "collection" -> name = r.optString("collectionName").ifBlank { name }
                "track" -> r.optString("trackName").takeIf { it.isNotBlank() }?.let { title ->
                    entries += PlaylistEntry(r.optString("artistName").takeIf { it.isNotBlank() }, title, r.optLong("trackTimeMillis", 0L))
                }
            }
        }
        if (entries.isEmpty()) return Outcome.Problem("Apple has no songs listed for that album here.")
        return Outcome.Read(ParsedPlaylist(name, entries))
    }

    private fun applePlaylist(link: String, proxy: Proxy?): Outcome {
        val page = Http.get(link, mapOf("User-Agent" to BROWSER), proxy)
            ?: return Outcome.Problem("Apple Music didn't answer. A private playlist can't be read; share it publicly first.")
        val serialized = Regex(
            """<script[^>]*id="serialized-server-data"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(page)?.groupValues?.get(1)
            ?: return Outcome.Problem("Apple Music's page has changed and couldn't be read.")
        val songs = LinkedHashMap<String, PlaylistEntry>()
        walk(JSONArray("[$serialized]")) { node ->
            val kind = node.optJSONObject("contentDescriptor")?.optString("kind")
            if (kind == "song" && node.has("title") && node.has("artistName")) {
                val id = node.optJSONObject("contentDescriptor")?.optJSONObject("identifiers")?.optString("storeAdamID")
                val title = node.optString("title")
                val key = id?.takeIf { it.isNotBlank() } ?: "$title|${node.optString("artistName")}"
                if (title.isNotBlank() && key !in songs) {
                    songs[key] = PlaylistEntry(node.optString("artistName").takeIf { it.isNotBlank() }, title, node.optLong("duration", 0L))
                }
            }
        }
        val name = Regex(""""@type"\s*:\s*"MusicPlaylist"\s*,\s*"name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(page)?.groupValues?.get(1)
            ?.let { runCatching { JSONArray("[\"$it\"]").getString(0) }.getOrNull() }
            ?: Regex("""<meta property="og:title" content="([^"]*)"""").find(page)?.groupValues?.get(1)?.let(::unescape)
            ?: "Apple Music playlist"
        if (songs.isEmpty()) return Outcome.Problem("That Apple Music playlist has no songs that can be read.")
        return Outcome.Read(ParsedPlaylist(name, songs.values.toList()))
    }

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                for (key in node.keys()) walk(node.opt(key), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun unescape(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    // ---- What yt-dlp listed ------------------------------------------------------

    /**
     * A YouTube playlist as songs. Music videos are mostly titled "Artist -
     * Song", so that is split where it is there; otherwise the channel is the
     * artist, without the " - Topic" YouTube Music puts on its channels.
     */
    fun fromVideos(name: String?, videos: List<YouTubeVideo>): ParsedPlaylist = ParsedPlaylist(
        name?.takeIf { it.isNotBlank() } ?: "YouTube playlist",
        videos.map { video ->
            val channel = video.channel.removeSuffix(" - Topic").removeSuffix("VEVO").trim()
                .takeUnless { it == "Unknown channel" || it.isBlank() }
            val dash = video.title.indexOf(" - ")
            val topic = video.channel.endsWith(" - Topic")
            val (artist, title) = if (!topic && dash > 0) {
                video.title.substring(0, dash).trim() to video.title.substring(dash + 3).trim()
            } else {
                channel to video.title.trim()
            }
            PlaylistEntry(artist, title, (video.durationSeconds ?: 0) * 1000L)
        }
    )

    /** The output of [PRINT_ARGUMENTS]: songs, and the list's name when yt-dlp gave it. */
    fun fromPrinted(output: String, fallbackName: String): ParsedPlaylist {
        var name: String? = null
        val entries = ArrayList<PlaylistEntry>()
        for (line in output.lineSequence()) {
            val parts = line.split('\t')
            if (parts.firstOrNull() == NAME_MARK) {
                name = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "NA" }
                continue
            }
            if (parts.size < 3) continue
            val rawTitle = parts[0].trim().takeIf { it.isNotBlank() && it != "NA" } ?: continue
            val artist = parts[1].trim().takeIf { it.isNotBlank() && it != "NA" }
            // SoundCloud uploads are often titled "Artist - Song" whoever posted them.
            val dash = rawTitle.indexOf(" - ")
            val (who, title) = if (dash > 0) {
                rawTitle.substring(0, dash).trim() to rawTitle.substring(dash + 3).trim()
            } else {
                artist to rawTitle
            }
            val seconds = parts[2].trim().toDoubleOrNull() ?: 0.0
            entries += PlaylistEntry(who, title, (seconds * 1000).toLong())
        }
        return ParsedPlaylist(name ?: fallbackName, entries)
    }
}
