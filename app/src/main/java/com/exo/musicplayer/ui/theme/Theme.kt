package com.exo.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * True when the starfield is painted behind the app.
 *
 * Screens read this to drop their own opaque background — a Scaffold's default
 * container colour would otherwise cover the stars completely.
 */
val LocalStarfieldActive = staticCompositionLocalOf { false }

/** Slightly rounder than the Material default; softer without looking like a toy. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MusicPlayerTheme(
    theme: ThemeState = ThemeState(),
    content: @Composable () -> Unit
) {
    val dark = when (theme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val colorScheme = when {
        theme.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> theme.palette.dark
        else -> theme.palette.light
    }

    // Stars only in dark mode: on a light background they read as smudges.
    val starsActive = theme.stars && dark

    MaterialTheme(colorScheme = colorScheme, shapes = AppShapes) {
        CompositionLocalProvider(LocalStarfieldActive provides starsActive) {
            if (starsActive) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colorScheme.background,
                                        colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        colorScheme.background
                                    )
                                )
                            )
                    )
                    Starfield(starColor = starTint(colorScheme.primary))
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/** Stars pick up a hint of the palette so they belong to the theme. */
private fun starTint(primary: Color): Color = Color(
    red = (primary.red * 0.35f + 0.65f).coerceIn(0f, 1f),
    green = (primary.green * 0.35f + 0.65f).coerceIn(0f, 1f),
    blue = (primary.blue * 0.35f + 0.68f).coerceIn(0f, 1f),
    alpha = 1f
)
