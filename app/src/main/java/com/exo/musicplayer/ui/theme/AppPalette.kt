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
    val dark: ColorScheme,
    /** Where a published scheme comes from, shown under its swatch; null for Lucense's own. */
    val credit: String? = null,
    /**
     * True or false for a scheme that is only ever dark or only ever light -
     * the published ones are one or the other by design - so the light/dark
     * setting is set aside while it's chosen. Null for palettes that have both.
     */
    val fixedDark: Boolean? = null
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
    ),

    // ---- The published schemes, the same values Windows uses -----------------------
    MOCHA(
        label = "Mocha",
        swatch = Color(0xFFCBA6F7),
        accent = Color(0xFFCBA6F7),
        starsByDefault = true,
        light = published(0xFFCBA6F7, 0xFF11111B, 0xFF181825, 0xFF1E1E2E, 0xFF313244, 0xFF45475A, 0xFF585B70, 0xFFCDD6F4, 0xFFBAC2DE, 0xFFA6ADC8, dark = true),
        dark = published(0xFFCBA6F7, 0xFF11111B, 0xFF181825, 0xFF1E1E2E, 0xFF313244, 0xFF45475A, 0xFF585B70, 0xFFCDD6F4, 0xFFBAC2DE, 0xFFA6ADC8, dark = true),
        credit = "Catppuccin Mocha",
        fixedDark = true
    ),
    DRACULA(
        label = "Dracula",
        swatch = Color(0xFFBD93F9),
        accent = Color(0xFFBD93F9),
        starsByDefault = true,
        light = published(0xFFBD93F9, 0xFF1E1F29, 0xFF242531, 0xFF282A36, 0xFF343746, 0xFF44475A, 0xFF525569, 0xFFF8F8F2, 0xFFD5D6E0, 0xFFA8AEC8, dark = true),
        dark = published(0xFFBD93F9, 0xFF1E1F29, 0xFF242531, 0xFF282A36, 0xFF343746, 0xFF44475A, 0xFF525569, 0xFFF8F8F2, 0xFFD5D6E0, 0xFFA8AEC8, dark = true),
        credit = "Dracula",
        fixedDark = true
    ),
    TOKYO(
        label = "Tokyo",
        swatch = Color(0xFF7AA2F7),
        accent = Color(0xFF7AA2F7),
        starsByDefault = true,
        light = published(0xFF7AA2F7, 0xFF16161E, 0xFF1A1B26, 0xFF1F2130, 0xFF292E42, 0xFF343A52, 0xFF3B4261, 0xFFC0CAF5, 0xFFA9B1D6, 0xFF8A93B8, dark = true),
        dark = published(0xFF7AA2F7, 0xFF16161E, 0xFF1A1B26, 0xFF1F2130, 0xFF292E42, 0xFF343A52, 0xFF3B4261, 0xFFC0CAF5, 0xFFA9B1D6, 0xFF8A93B8, dark = true),
        credit = "Tokyo Night",
        fixedDark = true
    ),
    ROSE_PINE(
        label = "Rosé",
        swatch = Color(0xFFC4A7E7),
        accent = Color(0xFFC4A7E7),
        starsByDefault = true,
        light = published(0xFFC4A7E7, 0xFF16141F, 0xFF191724, 0xFF1F1D2E, 0xFF26233A, 0xFF302C4A, 0xFF403C5C, 0xFFE0DEF4, 0xFFC7C4DE, 0xFF9E9ABA, dark = true),
        dark = published(0xFFC4A7E7, 0xFF16141F, 0xFF191724, 0xFF1F1D2E, 0xFF26233A, 0xFF302C4A, 0xFF403C5C, 0xFFE0DEF4, 0xFFC7C4DE, 0xFF9E9ABA, dark = true),
        credit = "Rosé Pine",
        fixedDark = true
    ),
    NORD(
        label = "Nord",
        swatch = Color(0xFF88C0D0),
        accent = Color(0xFF88C0D0),
        starsByDefault = true,
        light = published(0xFF88C0D0, 0xFF272C36, 0xFF2E3440, 0xFF333B4A, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A, 0xFFECEFF4, 0xFFD8DEE9, 0xFFAEB8C8, dark = true),
        dark = published(0xFF88C0D0, 0xFF272C36, 0xFF2E3440, 0xFF333B4A, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A, 0xFFECEFF4, 0xFFD8DEE9, 0xFFAEB8C8, dark = true),
        credit = "Nord",
        fixedDark = true
    ),
    GRUVBOX(
        label = "Gruvbox",
        swatch = Color(0xFFFABD2F),
        accent = Color(0xFFFABD2F),
        starsByDefault = true,
        light = published(0xFFFABD2F, 0xFF1D2021, 0xFF232728, 0xFF282828, 0xFF32302F, 0xFF3C3836, 0xFF504945, 0xFFFBF1C7, 0xFFEBDBB2, 0xFFBDAE93, dark = true),
        dark = published(0xFFFABD2F, 0xFF1D2021, 0xFF232728, 0xFF282828, 0xFF32302F, 0xFF3C3836, 0xFF504945, 0xFFFBF1C7, 0xFFEBDBB2, 0xFFBDAE93, dark = true),
        credit = "Gruvbox dark",
        fixedDark = true
    ),
    EVERFOREST(
        label = "Everforest",
        swatch = Color(0xFFA7C080),
        accent = Color(0xFFA7C080),
        starsByDefault = true,
        light = published(0xFFA7C080, 0xFF1E2326, 0xFF272E33, 0xFF2D353B, 0xFF343F44, 0xFF3D484D, 0xFF4F585E, 0xFFD3C6AA, 0xFFBEC5AE, 0xFF9DA9A0, dark = true),
        dark = published(0xFFA7C080, 0xFF1E2326, 0xFF272E33, 0xFF2D353B, 0xFF343F44, 0xFF3D484D, 0xFF4F585E, 0xFFD3C6AA, 0xFFBEC5AE, 0xFF9DA9A0, dark = true),
        credit = "Everforest dark",
        fixedDark = true
    ),
    SOLARIZED(
        label = "Solarized",
        swatch = Color(0xFF4FA3DB),
        accent = Color(0xFF4FA3DB),
        starsByDefault = true,
        light = published(0xFF4FA3DB, 0xFF002B36, 0xFF04303B, 0xFF073642, 0xFF0E4451, 0xFF17505E, 0xFF2C5D68, 0xFFFDF6E3, 0xFFEEE8D5, 0xFFA9B5B5, dark = true),
        dark = published(0xFF4FA3DB, 0xFF002B36, 0xFF04303B, 0xFF073642, 0xFF0E4451, 0xFF17505E, 0xFF2C5D68, 0xFFFDF6E3, 0xFFEEE8D5, 0xFFA9B5B5, dark = true),
        credit = "Solarized dark",
        fixedDark = true
    ),
    ONE_DARK(
        label = "One Dark",
        swatch = Color(0xFF61AFEF),
        accent = Color(0xFF61AFEF),
        starsByDefault = true,
        light = published(0xFF61AFEF, 0xFF21252B, 0xFF23272E, 0xFF282C34, 0xFF2F343D, 0xFF3A3F4B, 0xFF474C55, 0xFFDCDFE4, 0xFFABB2BF, 0xFF8B93A1, dark = true),
        dark = published(0xFF61AFEF, 0xFF21252B, 0xFF23272E, 0xFF282C34, 0xFF2F343D, 0xFF3A3F4B, 0xFF474C55, 0xFFDCDFE4, 0xFFABB2BF, 0xFF8B93A1, dark = true),
        credit = "One Dark",
        fixedDark = true
    ),
    AYU(
        label = "Ayu",
        swatch = Color(0xFFFFCC66),
        accent = Color(0xFFFFCC66),
        starsByDefault = true,
        light = published(0xFFFFCC66, 0xFF1A1F29, 0xFF1F2430, 0xFF242936, 0xFF2C3242, 0xFF343B4D, 0xFF434A5C, 0xFFD9D7CF, 0xFFC0BEB5, 0xFF9AA1AB, dark = true),
        dark = published(0xFFFFCC66, 0xFF1A1F29, 0xFF1F2430, 0xFF242936, 0xFF2C3242, 0xFF343B4D, 0xFF434A5C, 0xFFD9D7CF, 0xFFC0BEB5, 0xFF9AA1AB, dark = true),
        credit = "Ayu Mirage",
        fixedDark = true
    ),
    LATTE(
        label = "Latte",
        swatch = Color(0xFF8839EF),
        accent = Color(0xFF8839EF),
        starsByDefault = false,
        light = published(0xFF8839EF, 0xFFDCE0E8, 0xFFE6E9EF, 0xFFEFF1F5, 0xFFE6E9EF, 0xFFCCD0DA, 0xFFBCC0CC, 0xFF3C3F54, 0xFF5C5F77, 0xFF7C7F93, dark = false),
        dark = published(0xFF8839EF, 0xFFDCE0E8, 0xFFE6E9EF, 0xFFEFF1F5, 0xFFE6E9EF, 0xFFCCD0DA, 0xFFBCC0CC, 0xFF3C3F54, 0xFF5C5F77, 0xFF7C7F93, dark = false),
        credit = "Catppuccin Latte",
        fixedDark = false
    ),
    DAYLIGHT(
        label = "Daylight",
        swatch = Color(0xFF1C6FA5),
        accent = Color(0xFF1C6FA5),
        starsByDefault = false,
        light = published(0xFF1C6FA5, 0xFFEEE8D5, 0xFFF3EDDC, 0xFFFDF6E3, 0xFFEEE8D5, 0xFFE0D9C0, 0xFFCFC8AF, 0xFF073642, 0xFF3F5B63, 0xFF6C7D7D, dark = false),
        dark = published(0xFF1C6FA5, 0xFFEEE8D5, 0xFFF3EDDC, 0xFFFDF6E3, 0xFFEEE8D5, 0xFFE0D9C0, 0xFFCFC8AF, 0xFF073642, 0xFF3F5B63, 0xFF6C7D7D, dark = false),
        credit = "Solarized Light",
        fixedDark = false
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

/**
 * A Material colour scheme from a published scheme's own tones, mapped the way
 * Windows lays them out: [content] is the page, [raised] and [hover] the
 * surfaces on it, [base] and [sidebar] the chrome around it, and the three
 * text tones as the scheme's authors paired them with those surfaces.
 */
private fun published(
    accent: Long, base: Long, sidebar: Long, content: Long, raised: Long,
    hover: Long, line: Long, text: Long, textDim: Long, textFaint: Long,
    dark: Boolean
): ColorScheme {
    val a = Color(accent)
    val page = Color(content)
    val up = Color(raised)
    val words = Color(text)
    // A tint of the accent over the raised surface, for selected things.
    val container = Color(
        red = up.red * 0.72f + a.red * 0.28f,
        green = up.green * 0.72f + a.green * 0.28f,
        blue = up.blue * 0.72f + a.blue * 0.28f
    )
    val onAccent = if (dark) Color(base) else Color.White
    return if (dark) {
        darkColorScheme(
            primary = a, onPrimary = onAccent,
            primaryContainer = container, onPrimaryContainer = words,
            secondary = Color(textDim), onSecondary = Color(base),
            secondaryContainer = up, onSecondaryContainer = words,
            tertiary = a, onTertiary = onAccent,
            background = page, onBackground = words,
            surface = page, onSurface = words,
            surfaceVariant = up, onSurfaceVariant = Color(textDim),
            surfaceContainerLowest = Color(base), surfaceContainerLow = Color(sidebar),
            surfaceContainer = Color(sidebar), surfaceContainerHigh = up,
            surfaceContainerHighest = Color(hover),
            outline = Color(textFaint), outlineVariant = Color(line),
            inverseSurface = words, inverseOnSurface = page, inversePrimary = a
        )
    } else {
        lightColorScheme(
            primary = a, onPrimary = onAccent,
            primaryContainer = container, onPrimaryContainer = words,
            secondary = Color(textDim), onSecondary = Color.White,
            secondaryContainer = up, onSecondaryContainer = words,
            tertiary = a, onTertiary = onAccent,
            background = page, onBackground = words,
            surface = page, onSurface = words,
            surfaceVariant = up, onSurfaceVariant = Color(textDim),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(sidebar),
            surfaceContainer = Color(sidebar), surfaceContainerHigh = Color(base),
            surfaceContainerHighest = Color(hover),
            outline = Color(textFaint), outlineVariant = Color(line),
            inverseSurface = words, inverseOnSurface = page, inversePrimary = a
        )
    }
}
