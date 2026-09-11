package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
     * Every surface is derived from the theme's hue.
     *
     * Saturation and lightness per layer are fixed (reverse-engineered from the
     * original Amethyst values), so only the hue moves between themes and the
     * window/sidebar/content/raised layering reads the same whichever is chosen.
     * [ThemeColors.surfaceTint] scales saturation, so a near-grey theme gives
     * near-neutral surfaces.
     */
    private val chosen = mutableStateOf(AccentChoice.AMETHYST)
    private val scheme = mutableStateOf(AccentChoice.AMETHYST.colors)
    private val customScheme = mutableStateOf(AccentChoice.AMETHYST.colors)
    private val starsOverride = mutableStateOf<Color?>(null)

    val choice: AccentChoice get() = chosen.value

    /** The colours in use right now. */
    val colors: ThemeColors get() = scheme.value

    /** The Custom theme's colours, whether or not it is the one in use. */
    val custom: ThemeColors get() = customScheme.value

    private fun surface(saturation: Float, lightness: Float): Color =
        Color.hsl(scheme.value.hue, (saturation * scheme.value.surfaceTint).coerceIn(0f, 1f), lightness)

    val Base: Color get() = surface(0.30f, 0.061f)       // window, deepest layer
    val Sidebar: Color get() = surface(0.33f, 0.094f)    // navigation rail
    val Content: Color get() = surface(0.24f, 0.098f)    // main surface
    val Raised: Color get() = surface(0.24f, 0.129f)     // transport bar, headers
    val Hover: Color get() = surface(0.26f, 0.169f)
    val Line: Color get() = surface(0.22f, 0.180f)

    val Text: Color get() = surface(0.30f, 0.961f)
    val TextDim: Color get() = surface(0.18f, 0.680f)
    val TextFaint: Color get() = surface(0.11f, 0.449f)

    val Accent: Color get() = scheme.value.accent
    val AccentSoft: Color get() = scheme.value.soft
    val Selected: Color get() = scheme.value.selected

    /** Foreground for anything sitting on [Accent]. */
    val OnAccent: Color get() = scheme.value.onAccent

    /** The resting fill of the main buttons. */
    val Button: Color get() = scheme.value.button

    /** Foreground for anything sitting on [Button]. */
    val OnButton: Color get() = if (Button.luminance() > 0.45f) Color(0xFF14121A) else Text

    /** The background effect's colour: the user's own pick, or the accent. */
    val Stars: Color get() = starsOverride.value ?: scheme.value.accent

    fun use(choice: AccentChoice) {
        chosen.value = choice
        scheme.value = if (choice == AccentChoice.CUSTOM) customScheme.value else choice.colors
    }

    fun setCustom(colors: ThemeColors) {
        customScheme.value = colors
        if (chosen.value == AccentChoice.CUSTOM) scheme.value = colors
    }

    fun setStars(color: Color?) {
        starsOverride.value = color
    }
}

/** One theme's colours, and what the surfaces take from them. */
data class ThemeColors(
    val accent: Color,
    val soft: Color,
    val selected: Color,
    val onAccent: Color,
    val button: Color,
    /** Hue in degrees, which every surface takes. */
    val hue: Float,
    /** How strongly the surfaces take that hue, 0..1. */
    val surfaceTint: Float
) {
    /** Primary, secondary, tertiary and button colours as ARGB hex, for the settings file. */
    fun encode(): String = listOf(accent, soft, selected, button).joinToString(",") { hex(it) }

    companion object {
        fun hex(color: Color): String = "%08X".format(color.toArgb())

        fun parse(text: String?): Color? {
            val digits = text?.trim()?.removePrefix("#") ?: return null
            val value = digits.toLongOrNull(16) ?: return null
            return when (digits.length) {
                6 -> Color(0xFF000000L or value)
                8 -> Color(value)
                else -> null
            }
        }

        fun decode(text: String): ThemeColors? {
            val parts = text.split(",").mapNotNull { parse(it) }
            return if (parts.size == 4) from(parts[0], parts[1], parts[2], parts[3]) else null
        }

        /** A custom theme: the surfaces take the primary colour's hue, as far as it has one. */
        fun from(primary: Color, secondary: Color, tertiary: Color, button: Color): ThemeColors {
            val hsb = java.awt.Color.RGBtoHSB(
                (primary.red * 255).roundToInt(),
                (primary.green * 255).roundToInt(),
                (primary.blue * 255).roundToInt(),
                null
            )
            return ThemeColors(
                accent = primary,
                soft = secondary,
                selected = tertiary,
                onAccent = if (primary.luminance() > 0.45f) Color(0xFF14121A) else Color.White,
                button = button,
                hue = (hsb[0] * 360f) % 360f,
                surfaceTint = hsb[1].coerceIn(0.1f, 1f)
            )
        }
    }
}

/**
 * The colour themes.
 *
 * Purple leads because it is what Resonate has always been. [hue] drives the
 * whole surface stack, and [surfaceTint] is the accent's own saturation - a
 * near-grey accent has no business tinting the chrome. Custom takes its
 * colours from the editor; its values here are only where it starts.
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
    ),
    CITRINE(
        "Citrine", Color(0xFFF2D36B), Color(0xFF9C8436), Color(0xFF45402A),
        Color(0xFF3A2E05), hue = 47f, surfaceTint = 0.85f
    ),
    LAGOON(
        "Lagoon", Color(0xFF5FE0CF), Color(0xFF2F8C80), Color(0xFF22403C),
        Color(0xFF05302A), hue = 172f, surfaceTint = 0.80f
    ),
    CRIMSON(
        "Crimson", Color(0xFFFF6B7D), Color(0xFFA83A4A), Color(0xFF48262C),
        Color(0xFF3F0710), hue = 353f, surfaceTint = 1.00f
    ),
    COBALT(
        "Cobalt", Color(0xFF7D8CFF), Color(0xFF4652A8), Color(0xFF2A2E48),
        Color(0xFF0E133F), hue = 233f, surfaceTint = 1.00f
    ),
    CUSTOM(
        "Custom", Color(0xFFB99BFF), Color(0xFF6E56A8), Color(0xFF322A48),
        Color(0xFF25143F), hue = 258f, surfaceTint = 1.00f
    );

    /** Preset buttons rest in the soft accent, as they always have. */
    val colors: ThemeColors get() = ThemeColors(accent, soft, selected, onAccent, soft, hue, surfaceTint)

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
