package com.exo.musicplayer.data.recognition

/** A candidate song, from either fingerprinting or a metadata search. */
data class MusicMatch(
    val title: String,
    val artist: String?,
    val album: String?,
    /** As the service words it: "Hip-Hop/Rap", "Alternative", and so on. */
    val genre: String? = null,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Long? = null,
    val source: Source,
    /** Which service produced this row, shown as a badge. */
    val provider: String = "",
    /**
     * A page yt-dlp can fetch. Present for services that host audio (YouTube,
     * Audius, Internet Archive); null for pure metadata catalogues, where the
     * downloader falls back to searching by name.
     */
    val downloadUrl: String? = null,
    /**
     * Whether this came from a service whose audio is DRM-protected.
     *
     * Apple Music, Deezer and NetEase stream encrypted audio and expose no
     * fetchable file, so there is nothing for a downloader to take. Offering the
     * button anyway would either fail outright or quietly fetch some other
     * recording of the same song from somewhere else and present it as this one
     * — which is worse than not offering it, because it looks like it worked.
     * These rows are still perfectly good for names, years and cover art.
     */
    val drmProtected: Boolean = false,
    /** Match confidence 0-100 where the source reports one; usually null. */
    val confidence: Int? = null
) {
    enum class Source(val label: String) {
        FINGERPRINT("Identified"),
        SEARCH("Search result")
    }

    val display: String get() = if (artist.isNullOrBlank()) title else "$artist — $title"
}

/** Outcome of an identification or search attempt. */
sealed interface RecognitionResult {
    data class Found(val matches: List<MusicMatch>) : RecognitionResult
    data object NoMatch : RecognitionResult
    data class Error(val message: String) : RecognitionResult
}
