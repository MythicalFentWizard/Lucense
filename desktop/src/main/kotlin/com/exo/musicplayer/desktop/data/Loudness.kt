package com.exo.musicplayer.desktop.data

import com.exo.musicplayer.desktop.audio.Decoder
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How loud a file actually is, so one song doesn't arrive at twice the volume
 * of the last.
 *
 * A library pulled from Telegram, YouTube rips and CDs has no agreed level:
 * one track is mastered to the ceiling and the next sits ten decibels below
 * it, and the volume knob gets used between every song. Measuring lets each
 * one be brought to the same place on the way out.
 *
 * This is root-mean-square loudness, not the full EBU R128 loudness standard:
 * no K-weighting and no gating. Those matter when matching broadcast material;
 * for making a shuffled library sit still they are detail, and RMS is a tenth
 * of the work. The first ninety seconds are measured rather than the whole
 * file, because a compressed file cannot be seeked without decoding everything
 * before the seek, and a song is rarely a different loudness after its first
 * minute and a half.
 */
object Loudness {

    /** Loudness in decibels below full scale, and the loudest single sample. */
    class Reading(val dbfs: Float, val peak: Float)

    /** Where everything is brought to. Low enough that raising a quiet track rarely clips. */
    const val TARGET_DBFS = -14f

    private const val MOST_DB = 12f
    private const val SECONDS = 90

    fun measure(file: File): Reading? = runCatching {
        var squares = 0.0
        var samples = 0L
        var peak = 0f
        Decoder.open(file).use { decoder ->
            val wanted = SECONDS.toLong() * 44_100
            var frames = 0L
            while (frames < wanted) {
                val chunk = decoder.read(8192) ?: break
                if (chunk.isEmpty()) break
                for (value in chunk) {
                    squares += value.toDouble() * value
                    val size = abs(value)
                    if (size > peak) peak = size
                }
                samples += chunk.size
                frames += chunk.size / 2
            }
        }
        if (samples < 44_100) return@runCatching null
        val rms = sqrt(squares / samples).toFloat()
        if (rms <= 1e-6f) return@runCatching null
        Reading(20f * log10(rms), peak)
    }.getOrNull()

    /**
     * What to multiply a track by on the way out: towards [TARGET_DBFS], never
     * by more than twelve decibels either way, and never so far that its
     * loudest sample is pushed into clipping.
     */
    fun gainFor(dbfs: Float, peak: Float): Float {
        val wanted = (TARGET_DBFS - dbfs).coerceIn(-MOST_DB, MOST_DB)
        val gain = 10f.pow(wanted / 20f)
        val headroom = if (peak > 0.001f) 0.99f / peak else gain
        return max(0.05f, min(gain, headroom))
    }
}
