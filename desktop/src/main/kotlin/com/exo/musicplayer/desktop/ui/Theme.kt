package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Desktop visual language.
 *
 * Deliberately not the phone's theme scaled up. Desktop wants a denser type
 * ramp, tighter corners and a layered surface stack (window / sidebar / content
 * / raised bar) rather than the phone's single flat background — that layering
 * is most of what separates a native-feeling desktop app from a stretched
 * mobile one.
 */
object Palette {

    /**
     * Every surface is derived from the chosen accent's hue.
     *
     * This used to be a fixed stack with a comment claiming it was "one
     * considered set of greys". It was not: every value carried Amethyst's
     * violet hue baked in, so picking Ember gave you a purple window with
     * orange buttons and the theme picker looked broken. It effectively was.
     *
     * Saturation and lightness per layer are fixed and were reverse-engineered
     * from the old Amethyst values, so that accent looks exactly as it did
     * (within 1/255 on every channel) while the others finally get their own
     * hue. Keeping lightness fixed is what preserves the layering: the
     * window/sidebar/content/raised stack reads the same whichever accent is
     * chosen, because only the hue moves.
     *
     * [AccentChoice.surfaceTint] scales saturation by how saturated the accent
     * itself is, so Slate - which is nearly neutral - produces nearly neutral
     * surfaces instead of blue-grey ones.
     */
    private val current = mutableStateOf(AccentChoice.AMETHYST)

    val choice: AccentChoice get() = current.value

    private fun surface(saturation: Float, lightness: Float): Color =
        Color.hsl(current.value.hue, saturation * current.value.surfaceTint, lightness)

    val Base: Color get() = surface(0.30f, 0.061f)       // window, deepest layer
    val Sidebar: Color get() = surface(0.33f, 0.094f)    // navigation rail
    val Content: Color get() = surface(0.24f, 0.098f)    // main surface
    val Raised: Color get() = surface(0.24f, 0.129f)     // transport bar, headers
    val Hover: Color get() = surface(0.26f, 0.169f)
    val Line: Color get() = surface(0.22f, 0.180f)

    val Text: Color get() = surface(0.30f, 0.961f)
    val TextDim: Color get() = surface(0.18f, 0.680f)
    val TextFaint: Color get() = surface(0.11f, 0.449f)

    val Accent: Color get() = current.value.accent
    val AccentSoft: Color get() = current.value.soft
    val Selected: Color get() = current.value.selected

    /** Foreground for anything sitting on [Accent]. */
    val OnAccent: Color get() = current.value.onAccent

    fun use(choice: AccentChoice) { current.value = choice }
}

/**
 * The accent palettes.
 *
 * Purple leads because it is what Resonate has always been. [hue] drives the
 * whole surface stack, and [surfaceTint] is the accent's own saturation - a
 * near-grey accent has no business tinting the chrome.
 */
enum class AccentChoice(
    val label: String,
    val accent: Color,
    val soft: Color,
    val selected: Color,
    val onAccent: Color,
    /** Hue in degrees, taken from [accent]. */
    val hue: Float,
    /** How strongly the surfaces take that hue, 0..1. */
    val surfaceTint: Float
) {
    AMETHYST(
        "Amethyst", Color(0xFFB99BFF), Color(0xFF6E56A8), Color(0xFF322A48),
        Color(0xFF25143F), hue = 258f, surfaceTint = 1.00f
    ),
    EMBER(
        "Ember", Color(0xFFFF9B7A), Color(0xFFA85B41), Color(0xFF48302A),
        Color(0xFF3F1A0E), hue = 15f, surfaceTint = 1.00f
    ),
    MERIDIAN(
        "Meridian", Color(0xFF7ACBFF), Color(0xFF3F7CA8), Color(0xFF243A48),
        Color(0xFF06283F), hue = 203f, surfaceTint = 1.00f
    ),
    MOSS(
        "Moss", Color(0xFF8FE0A8), Color(0xFF448A5C), Color(0xFF26402F),
        Color(0xFF0C2E18), hue = 139f, surfaceTint = 0.57f
    ),
    ROSE(
        "Rose", Color(0xFFFF9BC4), Color(0xFFA85177), Color(0xFF482A38),
        Color(0xFF3F0E24), hue = 335f, surfaceTint = 1.00f
    ),
    SLATE(
        "Slate", Color(0xFFC7CBD6), Color(0xFF6C7183), Color(0xFF32353F),
        Color(0xFF1B1D24), hue = 224f, surfaceTint = 0.15f
    );

    companion object {
        fun fromName(name: String?): AccentChoice =
            entries.firstOrNull { it.name == name } ?: AMETHYST
    }
}

@Composable
private fun desktopColors() = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Color(0xFFEFE6FF),
    background = Palette.Base,
    onBackground = Palette.Text,
    surface = Palette.Content,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Line
)

/** Smaller than mobile Material: desktop reads at arm's length, not 30cm. */
private val DesktopTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.5.sp,
        letterSpacing = 0.4.sp
    )
)

private val DesktopShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

@Composable
fun ResonateDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = desktopColors(),
        typography = DesktopTypography,
        shapes = DesktopShapes,
        content = content
    )
}
