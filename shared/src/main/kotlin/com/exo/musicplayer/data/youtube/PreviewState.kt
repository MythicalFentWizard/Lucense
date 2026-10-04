package com.exo.musicplayer.data.youtube

/** What a preview is, for the small player that says so: a song, or what stands for an artist or a genre. */
data class PreviewLabel(
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null
)

/**
 * What the preview player is doing.
 *
 * Shared because both platforms show the same things - a spinner while the
 * stream is being found, a stop affordance while it plays, how far it has got
 * - even though what produces the audio underneath is completely different:
 * ffmpeg piping PCM on Windows, ExoPlayer on Android.
 *
 * One player serves every kind of preview: a YouTube result in Identify, a
 * song's half-minute clip in Discover, the song an artist or a genre is heard
 * by. So there is one thing playing at a time, and one small player on screen
 * that says what it is and can pause it or close it from any page.
 */
data class PreviewState(
    /** What is being previewed, by the key it was asked for under; null when nothing is. */
    val videoId: String? = null,
    /** True while the stream is still being found. */
    val loading: Boolean = false,
    val playing: Boolean = false,
    val secondsPlayed: Int = 0,
    val error: String? = null,
    /** Held, by the small player's pause button or by having played to the end. */
    val paused: Boolean = false,
    /** Played to the end; pressing play starts it again. */
    val ended: Boolean = false,
    val label: PreviewLabel? = null
) {
    /** Whether there is anything for the small player to show. */
    val active: Boolean get() = videoId != null

    /** The small player's second line: whose it is, and how it is getting on. */
    val status: String
        get() = when {
            error != null -> "Couldn't be played"
            loading -> label?.subtitle ?: "Finding it…"
            ended -> listOfNotNull(label?.subtitle, "finished").joinToString(" · ")
            paused -> listOfNotNull(label?.subtitle, "paused at ${YouTubeFormat.duration(secondsPlayed)}").joinToString(" · ")
            else -> listOfNotNull(label?.subtitle, YouTubeFormat.duration(secondsPlayed)).joinToString(" · ")
        }
}
