package com.exo.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A named colour scheme pair.
 *
 * Each palette defines both light and dark explicitly rather than deriving one
 * from the other: an algorithmic flip produces muddy containers and unreadable
 * "on" colours, which is exactly where cheap theming falls apart.
 */
enum class AppPalette(
    val label: String,
    /** Shown in the picker; roughly the palette's primary. */
    val swatch: Color,
    val accent: Color,
    /** Whether the starfield suits this palette by default. */
    val starsByDefault: Boolean,
    val light: ColorScheme,
    val dark: ColorScheme
) {
    MIDNIGHT(
        label = "Midnight",
        swatch = Color(0xFF38BDF8),
        accent = Color(0xFF7DD3FC),
        starsByDefault = true,
        light = lightColorScheme(
            primary = Color(0xFF00668A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC5E7FF),
            onPrimaryContainer = Color(0xFF001E2C),
            secondary = Color(0xFF4D616C),
            onSecondary = Color.White,
            tertiary = Color(0xFF5D5B7D),
            background = Color(0xFFF6FAFE),
            onBackground = Color(0xFF171C1F),
            surface = Color(0xFFF6FAFE),
            onSurface = Color(0xFF171C1F),
            surfaceVariant = Color(0xFFDCE4E9),
            onSurfaceVariant = Color(0xFF40484C)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF7DD3FC),
            onPrimary = Color(0xFF00344A),
            primaryContainer = Color(0xFF004C68),
            onPrimaryContainer = Color(0xFFC5E7FF),
            secondary = Color(0xFFB4CAD6),
            onSecondary = Color(0xFF1F333D),
            tertiary = Color(0xFFC6C2EA),
            background = Color(0xFF0E1417),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF0E1417),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF283238),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    ),

    AMETHYST(
        label = "Amethyst",
        swatch = Color(0xFF9D7BEA),
        accent = Color(0xFFD0BCFF),
        starsByDefault = true,
        light = lightColorScheme(
            primary = Color(0xFF6544B0),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9DDFF),
            onPrimaryContainer = Color(0xFF210F47),
            secondary = Color(0xFF635B70),
            onSecondary = Color.White,
            tertiary = Color(0xFF7E5260),
            background = Color(0xFFFDF7FF),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFFDF7FF),
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = Color(0xFFE7DFEB),
            onSurfaceVariant = Color(0xFF49454E)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF371E73),
            primaryContainer = Color(0xFF4D3283),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            tertiary = Color(0xFFEFB8C8),
            // Deep indigo rather than neutral black, so the starfield reads as
            // a night sky instead of dust on a dark screen.
            background = Color(0xFF120E1C),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF120E1C),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF2B2536),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    ),

    EMBER(
        label = "Ember",
        swatch = Color(0xFFFF7043),
        accent = Color(0xFFFFB59A),
        starsByDefault = false,
        light = lightColorScheme(
            primary = Color(0xFFA23F16),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDBCF),
            onPrimaryContainer = Color(0xFF3A0B00),
            secondary = Color(0xFF77574C),
            onSecondary = Color.White,
            tertiary = Color(0xFF6C5D2F),
            background = Color(0xFFFFF8F6),
            onBackground = Color(0xFF231917),
            surface = Color(0xFFFFF8F6),
            onSurface = Color(0xFF231917),
            surfaceVariant = Color(0xFFF5DED7),
            onSurfaceVariant = Color(0xFF53433F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB59A),
            onPrimary = Color(0xFF5E1A00),
            primaryContainer = Color(0xFF852B03),
            onPrimaryContainer = Color(0xFFFFDBCF),
            secondary = Color(0xFFE7BDB1),
            onSecondary = Color(0xFF442A21),
            tertiary = Color(0xFFD8C58D),
            background = Color(0xFF1A110F),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1A110F),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF33251F),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    ),

    FOREST(
        label = "Forest",
        swatch = Color(0xFF4CAF7D),
        accent = Color(0xFF8FD8AE),
        starsByDefault = false,
        light = lightColorScheme(
            primary = Color(0xFF19653F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA5F2C0),
            onPrimaryContainer = Color(0xFF002110),
            secondary = Color(0xFF4E6355),
            onSecondary = Color.White,
            tertiary = Color(0xFF3B6470),
            background = Color(0xFFF5FBF5),
            onBackground = Color(0xFF171D19),
            surface = Color(0xFFF5FBF5),
            onSurface = Color(0xFF171D19),
            surfaceVariant = Color(0xFFDCE5DC),
            onSurfaceVariant = Color(0xFF414942)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF8AD6A6),
            onPrimary = Color(0xFF00391E),
            primaryContainer = Color(0xFF00522F),
            onPrimaryContainer = Color(0xFFA5F2C0),
            secondary = Color(0xFFB4CCBA),
            onSecondary = Color(0xFF203527),
            tertiary = Color(0xFFA3CDDA),
            background = Color(0xFF0F1511),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF0F1511),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF262E27),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    ),

    ROSE(
        label = "Rose",
        swatch = Color(0xFFEC5F87),
        accent = Color(0xFFFFB1C6),
        starsByDefault = false,
        light = lightColorScheme(
            primary = Color(0xFFA53656),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9E0),
            onPrimaryContainer = Color(0xFF3E0016),
            secondary = Color(0xFF75565C),
            onSecondary = Color.White,
            tertiary = Color(0xFF7A5732),
            background = Color(0xFFFFF8F8),
            onBackground = Color(0xFF201A1B),
            surface = Color(0xFFFFF8F8),
            onSurface = Color(0xFF201A1B),
            surfaceVariant = Color(0xFFF3DDE0),
            onSurfaceVariant = Color(0xFF524345)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB1C6),
            onPrimary = Color(0xFF63012A),
            primaryContainer = Color(0xFF861F40),
            onPrimaryContainer = Color(0xFFFFD9E0),
            secondary = Color(0xFFE4BDC3),
            onSecondary = Color(0xFF43292E),
            tertiary = Color(0xFFECBE8F),
            background = Color(0xFF191113),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF191113),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF322528),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    ),

    SLATE(
        label = "Slate",
        swatch = Color(0xFF8A9AA8),
        accent = Color(0xFFC3CED8),
        starsByDefault = false,
        light = lightColorScheme(
            primary = Color(0xFF3F5A6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC5E2F5),
            onPrimaryContainer = Color(0xFF001E2B),
            secondary = Color(0xFF52606A),
            onSecondary = Color.White,
            tertiary = Color(0xFF635B70),
            background = Color(0xFFF8F9FB),
            onBackground = Color(0xFF191C1E),
            surface = Color(0xFFF8F9FB),
            onSurface = Color(0xFF191C1E),
            surfaceVariant = Color(0xFFDDE3E8),
            onSurfaceVariant = Color(0xFF41484D)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFA8C8DC),
            onPrimary = Color(0xFF0C3141),
            primaryContainer = Color(0xFF274859),
            onPrimaryContainer = Color(0xFFC5E2F5),
            secondary = Color(0xFFB9C8D3),
            onSecondary = Color(0xFF24323B),
            tertiary = Color(0xFFCBC3DC),
            background = Color(0xFF111417),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF111417),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF262C31),
            onSurfaceVariant = Color(0xFFDCDCE3)
        )
    );

    companion object {
        fun fromName(name: String?): AppPalette =
            entries.firstOrNull { it.name == name } ?: MIDNIGHT
    }
}

/** Light/dark preference, independent of palette. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
