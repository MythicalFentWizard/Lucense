package com.exo.musicplayer.data.download

/**
 * What a download is saved as.
 *
 * The list is Riff's (github.com/rootscripts/riff, MIT). A lossless format
 * can't add back what the site's stream never had - YouTube serves Opus at
 * about 130 kbps - but it does stop a second lossy encode on top of the first,
 * and some people want FLAC or WAV for editing.
 */
enum class DownloadFormat(
    val label: String,
    /** yt-dlp `--audio-format`. */
    val ytDlp: String,
    /** spotdl `--format`. */
    val spotdl: String,
    val note: String,
    /** Whether the bitrate ladder means anything for it. */
    val lossy: Boolean = true,
    /** Whether a cover can be embedded in it. */
    val holdsCover: Boolean = true
) {
    MP3("MP3", "mp3", "mp3", "plays everywhere"),
    M4A("M4A", "m4a", "m4a", "AAC; a little smaller than MP3 for the same sound"),
    OPUS("Opus", "opus", "opus", "YouTube's own format, kept without converting it again"),
    OGG("OGG", "vorbis", "ogg", "Vorbis"),
    FLAC("FLAC", "flac", "flac", "lossless, so nothing is lost converting; about five times the size", lossy = false),
    WAV("WAV", "wav", "wav", "uncompressed and largest; no room for a cover", lossy = false, holdsCover = false);

    companion object {
        val DEFAULT = MP3

        fun fromName(name: String?): DownloadFormat = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * yt-dlp arguments that make a download arrive as a tidy song rather than a
 * video's leftovers. Both apps add them to every download.
 */
object YtDlpTidy {

    /**
     * Artist and title tags from a video called "Artist - Title (Official
     * Video)", with the brackets that only make sense on YouTube dropped.
     *
     * Only when YouTube hasn't named the artist itself, as it does for its
     * music uploads: their titles are just the song, sometimes with a dash in
     * it ("Song - Remastered 2011"), and splitting that would be wrong. The
     * template gives the line a marker at the front when there is an artist,
     * and the pattern refuses anything starting with the marker.
     */
    val TAGS: List<String> = listOf(
        "--parse-metadata",
        "%(artist&$KNOWN|)s%(title)s:^(?!$KNOWN)(?P<meta_artist>.+?) - (?P<meta_title>.+)$",
        "--replace-in-metadata",
        "meta_title,title",
        """\s*[(\[][^)\]]*\b(?i:official|lyrics?|audio|video|visuali[sz]er|music video|mv|hd|hq|4k)\b[^)\]]*[)\]]""",
        ""
    )

    /**
     * A square cover from a video's 16:9 thumbnail, cut from the middle, as
     * players show covers.
     *
     * The crop has commas in it, which ffmpeg would take for the end of the
     * filter unless they are inside single quotes, and those quotes have to
     * get through yt-dlp splitting the line, which takes double quotes round
     * them. On Windows the process launcher drops a bare double quote from an
     * argument, so there it goes over escaped; elsewhere arguments arrive as
     * they are. Measured both ways on 2026-10-24: unquoted, ffmpeg said
     * "Filter not found"; as here it made the square.
     */
    fun squareCover(windows: Boolean): List<String> {
        val quote = if (windows) "\\\"" else "\""
        return listOf(
            "--convert-thumbnails", "jpg",
            "--ppa", "ThumbnailsConvertor+FFmpeg_o:-vf ${quote}crop='min(iw,ih)':'min(iw,ih)'$quote"
        )
    }

    /**
     * Cuts what SponsorBlock's users have marked as not music in a music
     * video: the spoken intro, the skit at the end. If SponsorBlock can't be
     * reached the song downloads whole.
     */
    val NON_MUSIC_CUT: List<String> = listOf("--sponsorblock-remove", "music_offtopic")

    private const val KNOWN = "ARTIST-KNOWN"
}
