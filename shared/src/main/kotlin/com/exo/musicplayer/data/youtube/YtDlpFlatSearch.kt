package com.exo.musicplayer.data.youtube

import org.json.JSONObject

/**
 * The yt-dlp side of YouTube search, minus the part that runs the binary.
 *
 * Both platforms already ship yt-dlp — Windows as a bundled exe, Android as the
 * Python binding — so it is the one backend that is always present and always
 * current. What differs is only how a process gets started, so that is left to
 * the caller and everything else lives here.
 *
 * `--flat-playlist` is what makes this usable interactively. Without it yt-dlp
 * resolves every hit in full, which means one player-JS extraction per result;
 * with it a search is a single request and the fields a list needs — title,
 * channel, duration, view count, description — come back anyway.
 *
 * The cost of flat mode is that upload dates and channel avatars are absent.
 * Those are left null rather than guessed at, and [PipedYouTubeBackend] fills
 * them in when it is reachable.
 */
object YtDlpFlatSearch {

    /** Arguments after the target, identical on both platforms. */
    val ARGUMENTS: List<String> = listOf(
        "--flat-playlist",
        "-J",              // one JSON object on stdout
        "--no-warnings",
        "--no-playlist",
        "--ignore-config"  // never pick up a user's ~/.config/yt-dlp
    )

    /**
     * Arguments for listing what a playlist link holds: the same flat document
     * as a search, but following the list rather than the one video.
     */
    val PLAYLIST_ARGUMENTS: List<String> = listOf(
        "--flat-playlist",
        "-J",
        "--no-warnings",
        "--yes-playlist",
        "--ignore-config"
    )

    /** What a playlist link turned out to hold. */
    data class Playlist(
        val title: String?,
        val songs: List<YouTubeVideo>,
        /** How many entries are private or deleted, and so can't be had at all. */
        val gone: Int
    )

    /** Parses a playlist's `-J` document. Null when it isn't one. */
    fun parsePlaylist(json: String, source: String = "yt-dlp"): Playlist? = runCatching {
        val document = JSONObject(json)
        val entries = document.optJSONArray("entries") ?: return null
        val all = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
        // A removed video stays in its playlist as a placeholder: named
        // "[Private video]" by one yt-dlp, left without a title by another.
        val (gone, there) = all.partition { it.isNull("title") || it.optString("title") in PLACEHOLDERS }
        Playlist(
            title = document.optString("title").takeIf { it.isNotBlank() },
            songs = there.mapNotNull { toVideo(it, source) },
            gone = gone.size
        )
    }.getOrNull()

    private val PLACEHOLDERS = setOf("[Private video]", "[Deleted video]", "")

    /**
     * The search target. `ytsearchN:` is yt-dlp's own search scheme, so this
     * takes the same code path as any other URL it is handed.
     */
    fun target(query: String, limit: Int): String = "ytsearch$limit:${query.trim()}"

    /** Parses the `-J` document. Returns empty on anything unexpected. */
    fun parse(json: String, source: String = "yt-dlp"): List<YouTubeVideo> = runCatching {
        val entries = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        (0 until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.let { toVideo(it, source) }
        }
    }.getOrDefault(emptyList())

    private fun toVideo(entry: JSONObject, source: String): YouTubeVideo? {
        val id = entry.optString("id").takeIf { it.isNotBlank() } ?: return null
        // A null title reads back as the word "null" on Android, which is not a title.
        val title = entry.optString("title").takeIf { it.isNotBlank() && !entry.isNull("title") } ?: return null

        // Live and upcoming entries are not songs, and a download of one either
        // never finishes or produces a fragment.
        val liveStatus = entry.optString("live_status")
        if (liveStatus == "is_live" || liveStatus == "is_upcoming") return null

        return YouTubeVideo(
            id = id,
            title = title,
            // "channel" is the display name; "uploader" is the same thing on
            // search results but survives on some other extractors.
            channel = entry.optString("channel").takeIf { it.isNotBlank() }
                ?: entry.optString("uploader").takeIf { it.isNotBlank() }
                ?: "Unknown channel",
            channelVerified = entry.optBoolean("channel_is_verified", false),
            channelAvatarUrl = null,
            viewCount = entry.optLong("view_count", -1L).takeIf { it >= 0 },
            uploadedText = null,
            description = entry.optString("description").takeIf { it.isNotBlank() },
            durationSeconds = entry.optDouble("duration", -1.0)
                .takeIf { !it.isNaN() && it > 0 }?.toInt(),
            // Flat search does not say, and the duration is a better signal
            // anyway: a Short is at most three minutes.
            isShort = false,
            source = source
        )
    }
}
