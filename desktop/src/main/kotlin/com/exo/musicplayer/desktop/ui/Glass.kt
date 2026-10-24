package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Liquid glass: the window as panes of frosted glass floating over a soft
 * field of the theme's colours, after the look Apple calls Liquid Glass.
 *
 * Each pane blurs what is behind it - the colour field, or the wallpaper -
 * and tints it with the theme, so the colours glow through without getting
 * in the way of the text. Its edge catches the light at the top left, a sheen
 * runs along its top, and a soft shadow lifts it off the background. Inside a
 * pane, panels, buttons and fields are thinner glass: see-through, with the
 * same lit edge, and rounder than the solid look.
 *
 * The blur is Haze's (github.com/chrisbanes/haze), which does on Skia what
 * the platform does for Apple's.
 */
object Glass {
    /** Whether the window is drawn as glass; set from the setting. */
    var on by mutableStateOf(false)

    /** Corners of the big panes. */
    val PANE = 20.dp

    /** The gap between panes, and round the edge of the window. */
    val GAP = 10.dp
}

/** What the panes blur: the colour field or wallpaper behind everything. Null when not glass. */
val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

/** Light catching the edge: brightest at the top left, fading, a little back at the bottom right. */
fun glassEdge(strength: Float = 1f): Brush {
    // On a light theme a white edge vanishes, so the edge darkens instead.
    val light = Palette.Content.luminance() > 0.5f
    val ink = if (light) Color.Black else Color.White
    val scale = if (light) 0.45f * strength else strength
    return Brush.linearGradient(
        0f to ink.copy(alpha = 0.34f * scale),
        0.45f to ink.copy(alpha = 0.07f * scale),
        1f to ink.copy(alpha = 0.16f * scale),
        start = Offset.Zero,
        end = Offset.Infinite
    )
}

/**
 * A pane of glass: what is behind it blurred and tinted with [tint], an edge
 * that catches the light, a sheen along the top and a soft shadow. Without a
 * [state] to blur, it is the tint alone, nearly solid.
 */
fun Modifier.glassPane(
    state: HazeState?,
    shape: Shape,
    tint: Color,
    tintAlpha: Float = 0.5f,
    shadow: Dp = 0.dp
): Modifier = this
    .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f)) else Modifier)
    .clip(shape)
    .then(
        if (state != null) {
            Modifier.hazeEffect(
                state,
                HazeStyle(
                    backgroundColor = Palette.Base,
                    tint = HazeTint(tint.copy(alpha = tintAlpha)),
                    blurRadius = 36.dp,
                    noiseFactor = 0.03f
                )
            )
        } else {
            Modifier.background(tint.copy(alpha = 0.92f))
        }
    )
    .drawBehind {
        // The sheen: a little light along the top of the pane.
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.07f),
                0.28f to Color.Transparent,
                endY = size.height
            )
        )
    }
    .border(1.dp, glassEdge(), shape)

/**
 * Thinner glass, for what sits on a pane: a panel, a button, a field. It
 * shows the pane through it, with the same lit edge.
 */
fun Modifier.glassChip(shape: Shape, fill: Float = 0.06f, edge: Float = 0.8f): Modifier {
    // White frost reads on both: faint over a dark pane, stronger over a light one.
    val light = Palette.Content.luminance() > 0.5f
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = if (light) (fill * 4f).coerceAtMost(0.5f) else fill))
        .border(1.dp, glassEdge(edge), shape)
}

/**
 * A card or a row on a page: thinner glass when glass is on, rounder; else
 * the solid raised surface with its line. [edge] other than the line colour
 * (a row being previewed, say) is kept in both.
 */
fun Modifier.card(corner: Dp, hovered: Boolean = false, edge: Color = Palette.Line): Modifier =
    if (Glass.on) {
        val shape = RoundedCornerShape(corner + 6.dp)
        val lit = this.glassChip(shape, fill = if (hovered) 0.11f else 0.05f)
        if (edge != Palette.Line) lit.border(1.dp, edge, shape) else lit
    } else {
        val shape = RoundedCornerShape(corner)
        this.clip(shape).background(if (hovered) Palette.Hover else Palette.Raised).border(1.dp, edge, shape)
    }

/**
 * Something floating over the page - a note, a dialog: nearly solid, so what
 * is under it doesn't show through the words, with glass's lit edge and a
 * deeper shadow when glass is on.
 */
fun Modifier.floating(corner: Dp): Modifier =
    if (Glass.on) {
        val shape = RoundedCornerShape(corner + 8.dp)
        this.shadow(24.dp, shape).clip(shape).background(Palette.Raised.copy(alpha = 0.9f)).border(1.dp, glassEdge(1.2f), shape)
    } else {
        val shape = RoundedCornerShape(corner)
        this.clip(shape).background(Palette.Raised).border(1.dp, Palette.Line, shape)
    }

/** What the glass floats over: the wallpaper when there is one, else a slowly drifting field of the theme's colours. */
@Composable
fun GlassBackground(wallpaper: ImageBitmap?, dim: Float, modifier: Modifier = Modifier) {
    Box(modifier.background(Palette.Base)) {
        if (wallpaper != null) {
            // Dimmed less than behind solid panes: the glass does its own dimming.
            Wallpaper(wallpaper, dim * 0.6f, Modifier.matchParentSize())
        } else {
            ColourField(Modifier.matchParentSize())
        }
    }
}

/**
 * Four soft lights in the theme's colours, drifting a full slow circle about
 * once a minute. Redrawn fifteen times a second, and not at all while the
 * window isn't in front: every redraw is a blur for every pane.
 */
@Composable
private fun ColourField(modifier: Modifier) {
    val focused = LocalWindowInfo.current.isWindowFocused
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(focused) {
        while (focused) {
            delay(66)
            phase = (phase + 0.0011f) % 1f
        }
    }
    val light = Palette.Base.luminance() > 0.5f
    val accent = remember(Palette.Accent, light) { richer(Palette.Accent, light) }
    val soft = remember(Palette.AccentSoft, light) { richer(Palette.AccentSoft, light) }
    val neighbour = remember(accent) { shiftHue(accent, 42f) }
    val far = remember(accent) { shiftHue(accent, -55f) }
    val base = Palette.Base
    // Brighter lights on a dark theme, where the panes darken what comes through.
    val lift = if (light) 1f else 1.35f
    Canvas(modifier) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val reach = max(w, h)
        val t = phase * 2 * PI
        fun light(colour: Color, x: Double, y: Double, radius: Float, strength: Float) {
            val centre = Offset((w * x).toFloat(), (h * y).toFloat())
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = strength),
                    0.45f to colour.copy(alpha = strength * 0.45f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = radius
                ),
                radius = radius,
                center = centre
            )
        }
        light(accent, 0.18 + 0.12 * sin(t), 0.22 + 0.10 * cos(t * 2), reach * 0.55f, (0.55f * lift).coerceAtMost(0.85f))
        light(neighbour, 0.86 + 0.08 * cos(t), 0.30 + 0.14 * sin(t * 2 + 1), reach * 0.50f, (0.45f * lift).coerceAtMost(0.85f))
        light(soft, 0.55 + 0.16 * sin(t + 2), 0.92 + 0.07 * cos(t * 3), reach * 0.60f, (0.50f * lift).coerceAtMost(0.85f))
        light(far, 0.04 + 0.06 * cos(t * 2 + 2), 0.96 + 0.05 * sin(t), reach * 0.40f, (0.35f * lift).coerceAtMost(0.85f))
    }
}

/** The colour with more colour in it: pastel accents wash out to grey behind tinted glass. */
private fun richer(colour: Color, light: Boolean): Color {
    val hsb = java.awt.Color.RGBtoHSB((colour.red * 255).toInt(), (colour.green * 255).toInt(), (colour.blue * 255).toInt(), null)
    val saturation = (hsb[1] * 1.6f).coerceIn(0.45f, 0.9f)
    val brightness = if (light) hsb[2].coerceIn(0.75f, 1f) else hsb[2].coerceIn(0.6f, 0.95f)
    return Color(java.awt.Color.HSBtoRGB(hsb[0], saturation, brightness))
}

private fun shiftHue(colour: Color, degrees: Float): Color {
    val hsb = java.awt.Color.RGBtoHSB(
        (colour.red * 255).toInt(),
        (colour.green * 255).toInt(),
        (colour.blue * 255).toInt(),
        null
    )
    val hue = ((hsb[0] * 360f + degrees) % 360f + 360f) % 360f / 360f
    return Color(java.awt.Color.HSBtoRGB(hue, hsb[1], hsb[2]))
}
