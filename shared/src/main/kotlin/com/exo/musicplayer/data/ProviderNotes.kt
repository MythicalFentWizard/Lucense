package com.exo.musicplayer.data

/**
 * A line about each lyrics and cover service, for the lists in Settings where
 * each can be turned off. Both apps show the same words.
 */
object ProviderNotes {

    fun lyrics(label: String): String = when (label) {
        "LRCLIB" -> "Timed lyrics, the most of any. Asked first."
        "NetEase" -> "Timed lyrics, strongest on Chinese, Japanese and Korean songs."
        "lyrics.ovh" -> "Words without timings, for well-known songs."
        "Genius" -> "Words without timings. Asked last."
        else -> ""
    }

    fun cover(label: String): String = when (label) {
        "YouTube" -> "The video's picture, cropped square: often a still from the music video."
        "iTunes" -> "Apple's album covers, large and clean."
        "Deezer" -> "Album covers from Deezer's catalogue."
        "Audius" -> "Covers of independent artists' songs on Audius."
        "Genius" -> "The song's picture on Genius."
        "MusicBrainz" -> "Album covers from the Cover Art Archive, for releases MusicBrainz knows."
        else -> ""
    }

    /** A set of names kept as one line each, as both apps store the services turned off. */
    fun parse(text: String?): Set<String> = text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun write(labels: Set<String>): String = labels.sorted().joinToString("\n")
}
