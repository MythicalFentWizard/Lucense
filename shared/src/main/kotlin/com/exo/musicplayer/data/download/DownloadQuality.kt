package com.exo.musicplayer.data.download

/**
 * How good the downloaded audio should be.
 *
 * The ladder is deliberately short. Streaming sites do not offer a continuous
 * range of bitrates — they serve a handful of fixed renditions — so a slider
 * would imply precision that does not exist on the other end.
 *
 * Medium is the default because it is transparent to most listeners on most
 * equipment while being roughly a third the size of the top rung, and because
 * the source is usually a lossy stream already: re-encoding it at 320 kbps
 * cannot recover detail the site never sent, it only preserves the artefacts
 * more faithfully.
 *
 * [formatSelector] narrows what yt-dlp fetches in the first place rather than
 * only what it re-encodes to, so a lower setting saves bandwidth as well as
 * disk. The fallbacks after each `/` matter: plenty of sources publish a single
 * rendition, and a filter with no alternative would fail the download outright
 * instead of taking what is there.
 */
enum class DownloadQuality(
    val label: String,
    val description: String,
    /** yt-dlp `--audio-quality`, used when converting to MP3. */
    val audioQuality: String,
    /** yt-dlp `-f`, choosing what to pull down. */
    val formatSelector: String,
    /** spotdl `--bitrate`. */
    val spotdlBitrate: String
) {
    HIGH(
        label = "High",
        description = "320 kbps · biggest files, no audible loss from the source",
        audioQuality = "320K",
        formatSelector = "bestaudio/best",
        spotdlBitrate = "320k"
    ),
    MEDIUM(
        label = "Medium",
        description = "192 kbps · transparent for most listening, a third the size",
        audioQuality = "192K",
        formatSelector = "bestaudio[abr<=192]/bestaudio/best",
        spotdlBitrate = "192k"
    ),
    LOW(
        label = "Low",
        description = "128 kbps · smallest, fine for phones and background listening",
        audioQuality = "128K",
        formatSelector = "bestaudio[abr<=128]/bestaudio/best",
        spotdlBitrate = "128k"
    );

    companion object {
        val DEFAULT = MEDIUM

        fun fromName(name: String?): DownloadQuality =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
