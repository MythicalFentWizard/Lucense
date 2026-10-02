package com.exo.musicplayer.data.youtube

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Proxy
import java.net.URLEncoder

/**
 * Where a song comes from when YouTube won't serve this connection.
 *
 * YouTube judges the address a request comes from. Cheap VPNs put many people
 * behind a few addresses, and YouTube answers those with "Sign in to confirm
 * you're not a bot" for nearly every video. Measured through one such exit:
 * every one of yt-dlp's YouTube clients got the same refusal, the public Piped
 * relays were down or refused too, and the one Invidious server that answered
 * put the audio behind its own bot check. Searching still worked; only playback
 * was refused.
 *
 * SoundCloud doesn't refuse those addresses, and a lot of what gets shared on
 * Telegram is on it, often uploaded by the artists themselves. So the same song
 * is looked up there and picked by the same rules as a YouTube search - the
 * title has to match, edits are refused, a known length refuses cuts - and when
 * nothing fits, nothing is downloaded. Major-label songs are often DRM-locked
 * on SoundCloud and simply won't be found there.
 */
object SoundCloudFallback {

    const val LABEL = "SoundCloud"

    /** How yt-dlp reports YouTube refusing the connection rather than the video. */
    private val REFUSALS = listOf(
        "Sign in to confirm", "not a bot", "HTTP Error 403", "HTTP Error 429", "Too Many Requests"
    )

    fun refusedByYouTube(message: String?): Boolean =
        message != null && REFUSALS.any { message.contains(it, ignoreCase = true) }

    /** yt-dlp's own SoundCloud search scheme, run with [YtDlpFlatSearch.ARGUMENTS]. */
    fun target(query: String, limit: Int = 10): String = "scsearch$limit:${query.trim()}"

    fun parse(json: String): List<YouTubeVideo> = YtDlpFlatSearch.parse(json, LABEL)

    /**
     * What to type into SoundCloud's search: artist and title without bracketed
     * parts or "Official Video". SoundCloud matches every word, so searching for
     * "Fazerdaze Little Uneasy (Official Video)" finds nothing at all, while
     * without the brackets the official upload comes first.
     */
    fun searchQuery(wanted: YouTubeLinkFinder.Wanted): String =
        wanted.query
            .replace(BRACKETED, " ")
            .replace(PROMO, " ")
            .replace(SPACES, " ")
            .trim()

    /**
     * What is being looked for: the song, and how long the YouTube video was
     * when that's known.
     */
    data class Lookup(val wanted: YouTubeLinkFinder.Wanted, val videoSeconds: Int? = null)

    /**
     * The upload that is the song, or null when none of them is.
     *
     * The video's length is a looser check than a song's would be, because a
     * music video often runs longer than the song: an upload under 60% of it is
     * a snippet or a preview - which artists post on SoundCloud titled just the
     * song's name - and one over 120% is a loop or a mix.
     */
    fun pick(lookup: Lookup, found: List<YouTubeVideo>): YouTubeVideo? {
        val video = lookup.videoSeconds?.takeIf { it > 0 }
        val plausible = if (video == null) found else found.filter { upload ->
            val seconds = upload.durationSeconds ?: return@filter true
            seconds >= video * 0.6 && seconds <= video * 1.2 + 5
        }
        return YouTubeLinkFinder.rank(lookup.wanted, plausible)
            .firstOrNull { it.rejected == null && it.score >= YouTubeLinkFinder.MIN_SCORE }
            ?.video
    }

    /**
     * What a YouTube link is - its title from oEmbed, its length from a YouTube
     * search for that title - both of which a refusing YouTube still answers.
     */
    suspend fun lookUp(link: String, search: YouTubeSearch, proxy: Proxy? = null): Lookup? {
        val wanted = withContext(Dispatchers.IO) { describe(link, proxy) } ?: return null
        val id = videoId(link)
        val seconds = if (id == null) null else runCatching {
            search.search(searchQuery(wanted), 10).videos.firstOrNull { it.id == id }?.durationSeconds
        }.getOrNull()
        return Lookup(wanted, seconds)
    }

    /** The video id of a youtube.com/watch, youtu.be, /shorts/ or music.youtube.com link. */
    fun videoId(link: String): String? = VIDEO_ID.find(link)?.groupValues?.get(1)

    private val VIDEO_ID = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

    /**
     * What a YouTube link is, from its oEmbed card, which YouTube still answers
     * when it refuses to play the video. Blocking; call it off the main thread.
     */
    fun describe(link: String, proxy: Proxy? = null): YouTubeLinkFinder.Wanted? {
        val url = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(link, "UTF-8")
        val json = Http.get(url, proxy = proxy) ?: return null
        return runCatching {
            val card = JSONObject(json)
            wantedFrom(card.optString("title"), card.optString("author_name"))
        }.getOrNull()
    }

    /**
     * "Artist - Song (Official Video) ft. Someone" becomes artist and song, with
     * the featured names dropped: they'd stop a plain "Song" upload matching.
     * Without a dash, the channel stands in for the artist, minus "VEVO" and
     * " - Topic".
     */
    internal fun wantedFrom(title: String, channel: String): YouTubeLinkFinder.Wanted? {
        val clean = title.replace(FEATURING, "").trim()
        if (clean.isEmpty()) return null
        val dash = clean.split(DASH, limit = 2).takeIf { it.size == 2 && it.all { part -> part.isNotBlank() } }
        return if (dash != null) {
            YouTubeLinkFinder.Wanted(title = dash[1].trim(), artist = dash[0].trim())
        } else {
            val artist = channel.replace(CHANNEL_NOISE, "").trim().takeIf { it.isNotEmpty() }
            YouTubeLinkFinder.Wanted(title = clean, artist = artist)
        }
    }

    private val BRACKETED = Regex("""\(.*?\)|\[.*?]|【.*?】""")
    private val PROMO = Regex(
        """\b(official\s+(music\s+)?(video|audio|visuali[sz]er|lyric\s+video)|lyric\s+video|lyrics?|hd|hq|4k)\b""",
        RegexOption.IGNORE_CASE
    )
    private val SPACES = Regex("""\s+""")
    private val FEATURING = Regex("""\s*[(\[]?\b(ft|feat|featuring)\b\.?\s[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)
    private val DASH = Regex("""\s+[-–—]\s+""")
    private val CHANNEL_NOISE = Regex("""(VEVO$|\s*-\s*Topic$|\bOfficial\b)""", RegexOption.IGNORE_CASE)

    /** What the progress line says while SoundCloud is searched. */
    fun searching(refused: Boolean): String =
        if (refused) "YouTube refused this connection. Looking on SoundCloud…"
        else "YouTube couldn't provide it. Looking on SoundCloud…"

    /** For the progress note: "…so it came from SoundCloud: svard · 2:58". */
    fun note(upload: YouTubeVideo, refused: Boolean): String = buildString {
        append(if (refused) "YouTube refused this connection" else "YouTube couldn't provide it")
        append(", so it came from $LABEL: ${upload.channel}")
        upload.durationSeconds?.let { append(" · ${YouTubeFormat.duration(it)}") }
    }

    /**
     * When neither place had it. A refusal gets the VPN advice; anything else -
     * a removed video, a region block - says what YouTube said.
     */
    fun notFound(refused: Boolean, youTubeSaid: String?): String =
        if (refused) {
            "YouTube refused this connection - it does that to VPN addresses it has flagged - " +
                "and SoundCloud has no upload of this song. Try another VPN server, or download " +
                "without the VPN if YouTube isn't blocked where you are."
        } else {
            val said = youTubeSaid?.trim()?.takeIf { it.isNotEmpty() }?.let { reason(it) }
                ?: "YouTube couldn't provide this one"
            (if (said.endsWith(".")) said else "$said.") + " SoundCloud has no upload of this song either."
        }

    /** yt-dlp's error line without its "ERROR: [youtube] abc123:" prefix. */
    fun reason(line: String): String = line.replace(ERROR_PREFIX, "").trim()

    private val ERROR_PREFIX = Regex("""^\s*ERROR:\s*(\[[^\]]+]\s*)?([\w-]+:\s*)?""")
}
