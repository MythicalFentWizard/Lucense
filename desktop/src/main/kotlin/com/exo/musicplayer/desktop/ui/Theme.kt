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
    // The surface stack stays fixed across accents. Recolouring the chrome as
    // well is what makes themed desktop apps look like skins; keeping one
    // considered set of greys and moving only the accent is what the apps people
    // call premium actually do.
    val Base = Color(0xFF0E0B14)          // window background, deepest layer
    val Sidebar = Color(0xFF141020)       // navigation rail
    val Content = Color(0xFF17131F)       // main surface
    val Raised = Color(0xFF1E1929)        // transport bar, headers
    val Hover = Color(0xFF262036)
    val Line = Color(0xFF2A2438)

    val Text = Color(0xFFF4F2F8)
    val TextDim = Color(0xFFA79FBC)
    val TextFaint = Color(0xFF6E667F)

    // Snapshot state, so choosing an accent redraws everything reading it
    // without every screen having to take a theme parameter.
    private val current = mutableStateOf(AccentChoice.AMETHYST)

    val choice: AccentChoice get() = current.value
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
 * Purple leads because it is what Resonate has always been; the rest are chosen
 * to hold the same contrast against the dark stack rather than to be a spread of
 * hues for its own sake.
 */
enum class AccentChoice(
    val label: String,
    val accent: Color,
    val soft: Color,
    val selected: Color,
    val onAccent: Color
) {
    AMETHYST("Amethyst", Color(0xFFB99BFF), Color(0xFF6E56A8), Color(0xFF322A48), Color(0xFF25143F)),
    EMBER("Ember", Color(0xFFFF9B7A), Color(0xFFA85B41), Color(0xFF48302A), Color(0xFF3F1A0E)),
    MERIDIAN("Meridian", Color(0xFF7ACBFF), Color(0xFF3F7CA8), Color(0xFF243A48), Color(0xFF06283F)),
    MOSS("Moss", Color(0xFF8FE0A8), Color(0xFF448A5C), Color(0xFF26402F), Color(0xFF0C2E18)),
    ROSE("Rose", Color(0xFFFF9BC4), Color(0xFFA85177), Color(0xFF482A38), Color(0xFF3F0E24)),
    SLATE("Slate", Color(0xFFC7CBD6), Color(0xFF6C7183), Color(0xFF32353F), Color(0xFF1B1D24));

    companion object {
        fun fromName(name: String?): AccentChoice =
            entries.firstOrNull { it.name == name } ?: AMETHYST
    }
}

// Read inside the theme composable rather than held in a top-level val, so
// switching accent takes effect without a restart.
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
