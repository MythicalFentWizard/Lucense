package com.exo.musicplayer.util

/**
 * Telegram is inconsistent about MIME types: a track sent as music arrives as
 * "audio/mpeg", the same file forwarded as a document can arrive as
 * "application/octet-stream", and voice notes are "audio/ogg". So we accept on
 * MIME *or* on file extension, and only reject when both look wrong.
 */
object AudioTypes {

    private val AUDIO_EXTENSIONS = setOf(
        // MPEG family
        "mp3", "mp2", "mpga", "mpeg", "mpg", "m2a",
        // MPEG-4 / AAC
        "m4a", "m4b", "m4r", "mp4a", "aac", "adts",
        // Lossless
        "flac", "alac", "ape", "wv", "tta", "shn",
        // Uncompressed
        "wav", "wave", "aiff", "aif", "aifc", "au", "snd", "pcm",
        // Ogg family
        "ogg", "oga", "opus", "spx",
        // Matroska / other containers
        "mka", "webm",
        // Windows / misc
        "wma", "asf", "ac3", "eac3", "dts",
        // Mobile / voice
        "amr", "awb", "3ga", "3gp", "3gpp", "gsm",
        // Playlists carry audio often enough to try
        "caf", "dsf", "dff"
    )

    /**
     * Container MIME types outside the audio family that carry audio anyway.
     * Telegram and some file providers
     * label audio as octet-stream or a container type, so accepting these is the
     * difference between a song importing and being silently rejected.
     */
    private val AUDIO_APPLICATION_MIMES = setOf(
        "application/ogg", "application/x-ogg", "application/flac",
        "application/x-flac", "application/itunes", "application/mp4"
    )

    /**
     * Types that mean "I don't know" rather than "this is audio". Telegram uses
     * octet-stream for anything it can't classify, so these must be decided on
     * the filename — accepting them outright would import zip files.
     */
    private val AMBIGUOUS_MIMES = setOf(
        "application/octet-stream", "application/binary"
    )

    /** A wildcard type carries no information either. */
    private fun isWildcard(mime: String): Boolean = mime.endsWith("/*") || mime == "*"

    private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val REPEATED_WHITESPACE = Regex("\\s+")

    fun isProbablyAudio(mimeType: String?, displayName: String?): Boolean {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim()
        if (mime != null && mime !in AMBIGUOUS_MIMES && !isWildcard(mime)) {
            if (mime.startsWith("audio/")) return true
            if (mime in AUDIO_APPLICATION_MIMES) return true
        }
        // Unknown or ambiguous type: the filename is all we have to go on.
        return extensionOf(displayName) in AUDIO_EXTENSIONS
    }

    fun extensionOf(name: String?): String? {
        if (name == null) return null
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase()
    }

    /** Best-effort extension for the file we write to disk. */
    fun extensionFor(mimeType: String?, displayName: String?): String {
        val fromName = extensionOf(displayName)
        if (fromName != null && fromName in AUDIO_EXTENSIONS) return fromName
        return when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/flac", "audio/x-flac", "application/flac", "application/x-flac" -> "flac"
            "audio/ogg", "application/ogg", "application/x-ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/amr" -> "amr"
            else -> "mp3"
        }
    }

    /** Strips characters that are illegal in FAT/ext filenames. */
    fun sanitizeFileName(raw: String): String {
        val cleaned = ILLEGAL_FILENAME_CHARS.replace(raw, "_")
            .replace(REPEATED_WHITESPACE, " ")
            .trim()
            .trim('.')
        val safe = cleaned.ifBlank { "track" }
        return if (safe.length > 120) safe.take(120).trim() else safe
    }
}
