package com.exo.musicplayer.data.art

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * An accent colour taken from a song's cover, after Riff's colours from the
 * artwork (github.com/rootscripts/riff, MIT).
 *
 * The cover's pixels are grouped by hue and the group that is both large and
 * vivid wins, so a red logo on a big grey sleeve still gives red, and a cover
 * that is all greys gives nothing (the theme's own accent stays). The colour is
 * then brightened or darkened until it reads on the theme's background.
 */
object CoverColour {

    private const val BINS = 24

    /**
     * The accent for a cover, as ARGB, or null when the cover has no colour
     * worth taking.
     *
     * [pixels] are ARGB; a few thousand is plenty, more are sampled. [background]
     * is the colour the accent will sit on.
     */
    fun accentFrom(pixels: IntArray, background: Int): Int? {
        if (pixels.isEmpty()) return null
        val step = max(1, pixels.size / 6_000)
        val weight = DoubleArray(BINS)
        val count = IntArray(BINS)
        val red = DoubleArray(BINS)
        val green = DoubleArray(BINS)
        val blue = DoubleArray(BINS)
        var seen = 0

        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            i += step
            if ((p ushr 24) < 128) continue
            seen++
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val (h, _, l) = hsl(r, g, b)
            // How far from grey it is. Saturation alone would call a faint
            // tint in a dark or pale pixel vivid.
            val chroma = max(r, max(g, b)) - min(r, min(g, b))
            // Greys, near-black and near-white say nothing about the cover's colour.
            if (chroma < 0.16 || l < 0.08 || l > 0.92) continue
            val bin = ((h / 360.0) * BINS).toInt().coerceIn(0, BINS - 1)
            // Vivid, mid-bright pixels count most.
            val w = chroma * (1.0 - abs(l - 0.5))
            weight[bin] += w
            count[bin]++
            red[bin] += r * w
            green[bin] += g * w
            blue[bin] += b * w
        }
        if (seen == 0) return null

        // A hue spread over two neighbouring groups is still one colour.
        var best = -1
        var bestScore = 0.0
        for (bin in 0 until BINS) {
            val score = weight[bin] + 0.5 * (weight[(bin + BINS - 1) % BINS] + weight[(bin + 1) % BINS])
            if (score > bestScore) {
                bestScore = score
                best = bin
            }
        }
        // Too little colour to call it the cover's: a speck on a grey sleeve.
        if (best < 0 || count[best] < seen * 0.03) return null

        val (h, s, _) = hsl(red[best] / weight[best], green[best] / weight[best], blue[best] / weight[best])
        return readable(h, s.coerceIn(0.45, 0.85), background)
    }

    /**
     * The colour at hue [h] and saturation [s], as light as it needs to be to
     * stand out on [background]: lighter on a dark theme, darker on a light one.
     */
    private fun readable(h: Double, s: Double, background: Int): Int {
        val bg = luminance(background)
        val dark = bg < 0.4
        var l = if (dark) 0.68 else 0.42
        var colour = fromHsl(h, s, l)
        while (contrast(luminance(colour), bg) < 4.5) {
            l += if (dark) 0.02 else -0.02
            if (l !in 0.2..0.9) break
            colour = fromHsl(h, s, l)
        }
        return colour
    }

    private fun hsl(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2
        if (hi == lo) return Triple(0.0, 0.0, l)
        val d = hi - lo
        val s = d / (1 - abs(2 * l - 1))
        val h = when (hi) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Triple((h + 360) % 360, s, l)
    }

    private fun fromHsl(h: Double, s: Double, l: Double): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun channel(v: Double) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Relative luminance, as WCAG defines it. */
    fun luminance(argb: Int): Double {
        fun linear(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)
    }

    private fun contrast(a: Double, b: Double): Double = (max(a, b) + 0.05) / (min(a, b) + 0.05)
}
