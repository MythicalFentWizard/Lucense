package com.exo.musicplayer.desktop.audio

/**
 * Sample-rate conversion for playback, one buffer at a time.
 *
 * Playback used to call the fingerprinting resampler on every buffer: a 32-tap
 * windowed sinc that computes a sine and a cosine for each tap of each sample.
 * That filter is right for fingerprinting, where 44.1 kHz is folded down to
 * 16 kHz once and aliasing would corrupt the match. For playback it was
 * measured at 11.6 ms of every 92.9 ms buffer on a 48 kHz file, and 48 kHz is
 * what YouTube downloads arrive as. Because each buffer was also resampled on
 * its own, every buffer boundary was a small discontinuity.
 *
 * This keeps its read position and the last few input frames between calls, so
 * the output is one continuous signal however it is chunked, and interpolates
 * with a four-point Catmull-Rom cubic: a handful of multiplications per sample.
 * The ratios playback meets are close to 1 (48 to 44.1 kHz is 0.92), and there
 * that is transparent for music: the only band it could fold back lies above
 * 22 kHz, which lossy encoders have already removed.
 */
class StreamingResampler(private val channels: Int) {

    /** Input frames carried from the previous call; the loop below leaves at most three. */
    private var carry = FloatArray(channels * 4)
    private var carryFrames = 0

    /** Read position in frames, counted from the start of carry-then-input. */
    private var position = 1.0

    private var joined = FloatArray(0)
    private var output = FloatArray(0)

    fun reset() {
        carryFrames = 0
        position = 1.0
    }

    /**
     * Converts [frames] interleaved frames of [input], advancing [step] input
     * frames per output frame (source rate divided by target rate), and returns
     * a new array holding exactly what was produced.
     */
    fun process(input: FloatArray, frames: Int, step: Double): FloatArray {
        val ch = channels
        val total = carryFrames + frames
        if (joined.size < total * ch) joined = FloatArray(total * ch)
        System.arraycopy(carry, 0, joined, 0, carryFrames * ch)
        System.arraycopy(input, 0, joined, carryFrames * ch, frames * ch)

        val capacity = ((((total - position) / step).toInt() + 2).coerceAtLeast(0)) * ch
        if (output.size < capacity) output = FloatArray(capacity)

        var p = position
        var produced = 0
        while (true) {
            val i = p.toInt()
            // The cubic needs the frame before i and the two after it.
            if (i + 2 >= total || (produced + 1) * ch > output.size) break
            val t = (p - i).toFloat()
            val before = (i - 1) * ch
            for (c in 0 until ch) {
                val y0 = joined[before + c]
                val y1 = joined[before + ch + c]
                val y2 = joined[before + 2 * ch + c]
                val y3 = joined[before + 3 * ch + c]
                // Catmull-Rom passes through the samples themselves and matches
                // the slope on either side of each, so the curve stays smooth.
                val a = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3
                val b = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
                val d = -0.5f * y0 + 0.5f * y2
                output[produced * ch + c] = ((a * t + b) * t + d) * t + y1
            }
            produced++
            p += step
        }

        // Keep what the next call still needs, from the frame before p onward.
        // Clamped at the end, in which case the overshoot stays in position.
        val keepFrom = (p.toInt() - 1).coerceIn(0, total)
        carryFrames = total - keepFrom
        if (carry.size < carryFrames * ch) carry = FloatArray(carryFrames * ch)
        System.arraycopy(joined, keepFrom * ch, carry, 0, carryFrames * ch)
        position = p - keepFrom

        return output.copyOf(produced * ch)
    }
}
