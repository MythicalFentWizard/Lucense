package com.exo.musicplayer.desktop.audio

import kotlin.math.pow

/**
 * Turning a slider position into an amount of gain.
 *
 * Loudness is heard roughly logarithmically, and multiplying the samples by the
 * slider's own position ignores that. Half way along is only six decibels down,
 * which is barely a change at all, so the whole top half of the travel does
 * almost nothing and everything audible is crammed into the bottom fifth. That
 * is exactly what "the slider doesn't really do much" is.
 *
 * A decibel taper spreads the change evenly across the travel instead: each
 * quarter of the way down is another ten decibels, and ten decibels is roughly
 * half the loudness. So half way along sounds about a quarter as loud, which is
 * what people expect a slider at half to do.
 */
object Volume {

    /**
     * How far down the bottom of the travel reaches, in decibels.
     *
     * Forty is the usual sort of range for a music fader. Much less and the
     * bottom of the slider is still audible, which makes the last stretch feel
     * useless in the other direction; much more and the middle is already so
     * quiet that the top half is doing all the work again.
     */
    const val RANGE_DB = 40f

    /** The gain for a slider at [position], where 0 is silent and 1 is full. */
    fun gainFor(position: Float): Float {
        val at = position.coerceIn(0f, 1f)
        // A taper approaches silence but never arrives, so the very bottom of
        // the slider is made to mean off rather than very nearly off.
        if (at <= 0f) return 0f
        return 10f.pow((at - 1f) * RANGE_DB / 20f)
    }

    /** What [position] works out to in decibels, for anything that wants to say. */
    fun decibelsAt(position: Float): Float = (position.coerceIn(0f, 1f) - 1f) * RANGE_DB
}
