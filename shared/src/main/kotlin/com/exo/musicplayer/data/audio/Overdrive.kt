package com.exo.musicplayer.data.audio

import kotlin.math.tanh

/**
 * Distortion: the signal pushed through a soft-clipping curve.
 *
 * The idea is Riff's (its overdrive effect, github.com/rootscripts/riff, MIT);
 * the arithmetic is the usual one. The samples are driven harder the higher
 * [amount] goes, and tanh folds the peaks over smoothly instead of chopping
 * them flat, which is what makes it sound like a valve rather than a fault.
 * Dividing by tanh(drive) keeps a full-scale sample at full scale, so turning
 * it up adds grit rather than just level.
 *
 * Shared by both apps: the phone runs it on its EQ processor's buffers, the
 * PC on its effect chain's. [amount] runs 0 (off) to 1; channels are
 * interleaved, which a sample-by-sample curve doesn't care about.
 */
object Overdrive {

    /** How far a full amount drives the signal into the curve. */
    private const val MAX_DRIVE = 24f

    /** Below this it is off, and the buffer is left exactly as it was. */
    private const val OFF = 0.005f

    fun active(amount: Float): Boolean = amount > OFF

    fun process(buffer: FloatArray, amount: Float, from: Int = 0, to: Int = buffer.size) {
        if (!active(amount)) return
        val drive = 1f + amount.coerceIn(0f, 1f) * MAX_DRIVE
        val norm = 1f / tanh(drive)
        // A little quieter as it gets dirtier: a driven signal sits near full
        // scale all the time and would otherwise get much louder too.
        val trim = 1f - amount.coerceIn(0f, 1f) * 0.35f
        for (i in from until to) {
            buffer[i] = tanh(buffer[i] * drive) * norm * trim
        }
    }
}
