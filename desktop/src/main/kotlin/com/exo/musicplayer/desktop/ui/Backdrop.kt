package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalWindowInfo
import com.exo.musicplayer.data.audio.SpectrumAnalyser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** What is drawn behind the sidebar, the library and the lyrics. */
enum class BackdropStyle(val label: String, val note: String) {
    NONE("None", "A plain background."),
    STARS("Stars", "A slow twinkle, as on the phone."),
    AURORA("Aurora", "Soft light drifting slowly across the window."),
    FIREFLIES("Fireflies", "Warm specks of light wandering and glowing."),
    SNOW("Snow", "Flakes falling gently."),
    WAVES("Waves", "Slow lines rolling along the bottom."),
    REACTIVE("Reactive", "Glows, rises and pulses with whatever is playing.");

    companion object {
        fun fromName(name: String?): BackdropStyle = entries.firstOrNull { it.name == name } ?: STARS
    }
}

private const val TAU = (2 * PI).toFloat()
private const val REACTIVE_BANDS = 24

private class Mote(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

/**
 * The chosen background effect, in [color].
 *
 * Every style follows Starfield's rules: a graphics layer of its own, the clock
 * read inside the draw call so a tick repaints without recomposing anything in
 * front, and no ticking while the window isn't focused - unless
 * [pauseWhenUnfocused] is false, as for the lyrics window, which mostly sits
 * beside whatever has focus. Reactive reads the playback spectrum, so it keeps
 * the analyser switched on for as long as it is shown.
 */
@Composable
fun Backdrop(
    style: BackdropStyle,
    color: Color,
    spectrum: SpectrumAnalyser?,
    modifier: Modifier = Modifier,
    count: Int = 90,
    pauseWhenUnfocused: Boolean = true
) {
    when (style) {
        BackdropStyle.NONE -> Unit
        BackdropStyle.STARS -> Starfield(
            enabled = true,
            color = color,
            modifier = modifier,
            count = count,
            pauseWhenUnfocused = pauseWhenUnfocused
        )
        else -> Animated(style, color, spectrum, modifier, count, pauseWhenUnfocused)
    }
}

@Composable
private fun Animated(
    style: BackdropStyle,
    color: Color,
    spectrum: SpectrumAnalyser?,
    modifier: Modifier,
    count: Int,
    pauseWhenUnfocused: Boolean
) {
    val motes = remember(count) {
        val random = Random(11)
        List(count) {
            Mote(
                random.nextFloat(), random.nextFloat(), random.nextFloat(),
                random.nextFloat() * TAU, 0.5f + random.nextFloat()
            )
        }
    }
    val seconds = remember { mutableFloatStateOf(0f) }
    // Smoothed band levels for Reactive: they rise at once and fall away gently.
    val levels = remember { FloatArray(REACTIVE_BANDS) }
    val focused = LocalWindowInfo.current.isWindowFocused || !pauseWhenUnfocused
    val reactive = style == BackdropStyle.REACTIVE && spectrum != null

    LaunchedEffect(focused, style) {
        if (!focused) return@LaunchedEffect
        val origin = System.nanoTime() - (seconds.floatValue * 1e9f).toLong()
        val raw = FloatArray(spectrum?.bands() ?: 1)
        while (isActive) {
            if (reactive && spectrum != null) {
                // Re-asserted every tick: the effects panel's meter switches the
                // analyser off when it closes, and this still needs it.
                spectrum.enabled = true
                val snapshot = spectrum.snapshot(raw)
                for (i in levels.indices) {
                    val value = snapshot[(i * snapshot.size / levels.size).coerceAtMost(snapshot.size - 1)]
                    levels[i] = max(value, levels[i] * 0.86f)
                }
            }
            seconds.floatValue = (System.nanoTime() - origin) / 1e9f
            delay(if (reactive) 33L else 50L)
        }
    }
    DisposableEffect(reactive) {
        onDispose { if (reactive) spectrum?.enabled = false }
    }

    Spacer(
        modifier
            .graphicsLayer()
            .drawBehind {
                val t = seconds.floatValue
                when (style) {
                    BackdropStyle.AURORA -> aurora(t, color)
                    BackdropStyle.FIREFLIES -> fireflies(t, color, motes)
                    BackdropStyle.SNOW -> snow(t, color, motes)
                    BackdropStyle.WAVES -> waves(t, color)
                    BackdropStyle.REACTIVE -> reactive(t, color, levels, motes)
                    else -> Unit
                }
            }
    )
}

private fun wrap(value: Float) = ((value % 1f) + 1f) % 1f

/** Large soft pools of light, each drifting on its own slow orbit. */
private fun DrawScope.aurora(t: Float, color: Color) {
    val w = size.width
    val h = size.height
    val tints = listOf(color, lerp(color, Color(0xFF3FE0C8), 0.5f), lerp(color, Color(0xFFFF6FD8), 0.4f))
    for (i in 0 until 4) {
        val tint = tints[i % tints.size]
        val center = Offset(
            w * (0.5f + 0.38f * sin(t * 0.045f * (i + 1) + i * 1.7f)),
            h * (0.4f + 0.3f * cos(t * 0.035f * (i + 1) + i * 0.9f))
        )
        val radius = max(w, h) * (0.38f + 0.08f * sin(t * 0.08f + i))
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.13f), Color.Transparent), center, radius),
            radius,
            center
        )
    }
}

private fun DrawScope.fireflies(t: Float, color: Color, motes: List<Mote>) {
    val glow = lerp(color, Color(0xFFFFE08A), 0.4f)
    for (m in motes) {
        val center = Offset(
            wrap(m.x + 0.03f * sin(t * 0.25f * m.speed + m.phase)) * size.width,
            wrap(m.y + 0.03f * cos(t * 0.19f * m.speed + m.phase * 1.3f) - t * 0.004f * m.speed) * size.height
        )
        val pulse = 0.5f + 0.5f * sin(t * 1.1f * m.speed + m.phase)
        val core = (1.2f + m.size * 1.4f) * density
        drawCircle(glow.copy(alpha = 0.12f * pulse), core * 5f, center)
        drawCircle(glow.copy(alpha = 0.25f + 0.6f * pulse), core, center)
    }
}

private fun DrawScope.snow(t: Float, color: Color, motes: List<Mote>) {
    val flake = lerp(color, Color.White, 0.65f)
    for (m in motes) {
        val center = Offset(
            wrap(m.x + 0.012f * sin(t * 0.7f * m.speed + m.phase)) * size.width,
            wrap(m.y + t * 0.025f * m.speed) * size.height
        )
        drawCircle(flake.copy(alpha = 0.18f + m.size * 0.45f), (0.7f + m.size * 1.9f) * density, center)
    }
}

private fun DrawScope.waves(t: Float, color: Color) {
    val w = size.width
    val h = size.height
    val step = 6f * density
    for (k in 0 until 5) {
        val path = Path()
        val base = h * (0.58f + k * 0.075f)
        val amplitude = h * (0.03f + k * 0.006f)
        val frequency = TAU * (1.1f + k * 0.35f) / w
        var x = 0f
        while (x <= w + step) {
            val y = base + sin(x * frequency + t * (0.3f + k * 0.11f) + k) * amplitude
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }
        drawPath(path, color.copy(alpha = 0.20f - k * 0.03f), style = Stroke(width = 1.4f * density))
    }
}

/**
 * A glow from the bottom edge that swells with the bass, bars mirrored out
 * from the centre, and motes that brighten with the overall level. When
 * nothing plays the glow just breathes.
 */
private fun DrawScope.reactive(t: Float, color: Color, levels: FloatArray, motes: List<Mote>) {
    val w = size.width
    val h = size.height
    val bass = (levels[0] + levels[1] + levels[2] + levels[3]) / 4f
    val energy = levels.average().toFloat()
    val breathe = 0.5f + 0.5f * sin(t * 0.6f)

    val center = Offset(w / 2f, h * 1.05f)
    val radius = max(w, h) * (0.45f + bass * 0.45f)
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.10f + 0.04f * breathe + bass * 0.28f), Color.Transparent),
            center,
            radius
        ),
        radius,
        center
    )

    val barWidth = w / (levels.size * 2)
    for (i in levels.indices) {
        val height = h * 0.32f * levels[i]
        if (height < 1f) continue
        val brush = Brush.verticalGradient(
            listOf(Color.Transparent, color.copy(alpha = 0.35f)),
            startY = h - height,
            endY = h
        )
        val barSize = Size(barWidth * 0.7f, height)
        drawRect(brush, Offset(w / 2f + i * barWidth + barWidth * 0.15f, h - height), barSize)
        drawRect(brush, Offset(w / 2f - (i + 1) * barWidth + barWidth * 0.15f, h - height), barSize)
    }

    for (m in motes.take(40)) {
        drawCircle(
            color.copy(alpha = (0.08f + energy * 0.9f * m.size).coerceAtMost(0.8f)),
            (0.8f + m.size * 1.6f + energy * 3f) * density,
            Offset(m.x * w, wrap(m.y - t * 0.01f * m.speed) * h)
        )
    }
}
