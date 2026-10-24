package com.exo.musicplayer.data.audio

import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cutting a song down without re-encoding it, after Riff's lossless trimmer
 * (github.com/rootscripts/riff, MIT).
 *
 * ffmpeg copies the audio packets between the two points, so what is kept
 * sounds exactly as it did and saving takes a moment however long the song.
 * The price is that a cut lands on the nearest packet, which for every common
 * format is a few hundredths of a second at most. The cover and the tags come
 * across as they are; only the title gains "(trimmed)", so the new song can be
 * told from the old one in a list.
 */
object Trim {

    /** The shortest piece worth saving. */
    const val MIN_LENGTH_MS = 1_000L

    /** How much a nudge button moves a cut. */
    const val NUDGE_MS = 100L

    /** How much of the end is played by "Hear the end". */
    const val END_PREVIEW_MS = 4_000L

    fun ffmpegArgs(input: String, output: String, startMs: Long, endMs: Long, title: String?): List<String> =
        buildList {
            addAll(listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y"))
            // Before the input, so ffmpeg seeks rather than reading up to the cut.
            if (startMs > 0) addAll(listOf("-ss", seconds(startMs)))
            addAll(listOf("-i", input))
            addAll(listOf("-t", seconds(endMs - startMs)))
            // The audio, and the cover when there is one.
            addAll(listOf("-map", "0:a", "-map", "0:v?", "-c", "copy", "-map_metadata", "0"))
            // A copied FLAC keeps the old length in its header, and players
            // believe the header. FLAC to FLAC is lossless anyway, so it is
            // encoded again instead, which writes the right one.
            if (output.endsWith(".flac", ignoreCase = true)) addAll(listOf("-c:a", "flac"))
            if (title != null) {
                // Ogg and Opus keep their tags on the audio stream, so it goes there too.
                addAll(listOf("-metadata", "title=$title", "-metadata:s:a", "title=$title"))
            }
            add(output)
        }

    /** "Song (trimmed).mp3", or "Song (trimmed 2).mp3" beside an earlier one. */
    fun outputName(name: String, taken: (String) -> Boolean): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$stem (trimmed${if (n == 1) "" else " $n"})$ext"
            if (!taken(candidate)) return candidate
            n++
        }
    }

    fun title(original: String): String = "$original (trimmed)"

    /** 83,450 ms as "1:23.4". */
    fun clock(ms: Long): String {
        val tenths = max(0L, ms) / 100
        return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10)
    }

    private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.3f", ms / 1000.0)

    /**
     * A song's loudness as [count] bars from 0 to 1, from its loudness in
     * short slices, of which the first [filled] are known. Bars past what is
     * known are 0, so the picture fills in as the song is read.
     */
    fun bars(slices: FloatArray, filled: Int, count: Int): FloatArray {
        val out = FloatArray(count)
        if (slices.isEmpty() || count == 0) return out
        val known = filled.coerceIn(0, slices.size)
        var loudest = 0f
        for (bar in 0 until count) {
            val from = (bar.toLong() * slices.size / count).toInt()
            val to = max(from + 1, ((bar + 1).toLong() * slices.size / count).toInt()).coerceAtMost(known)
            if (from >= known) break
            var sum = 0.0
            for (i in from until to) sum += slices[i].toDouble() * slices[i]
            val value = sqrt(sum / max(1, to - from)).toFloat()
            out[bar] = value
            if (value > loudest) loudest = value
        }
        if (loudest > 0f) for (i in out.indices) out[i] = (out[i] / loudest).coerceIn(0f, 1f)
        return out
    }
}
