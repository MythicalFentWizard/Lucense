package com.exo.musicplayer.ui.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.ui.theme.AppPalette
import com.exo.musicplayer.ui.theme.Starfield
import com.exo.musicplayer.ui.theme.ThemeMode
import com.exo.musicplayer.ui.theme.ThemeState

@Composable
fun AppearanceScreen(
    theme: ThemeState,
    onBack: () -> Unit,
    onPalette: (AppPalette) -> Unit,
    onMode: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onStars: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item { SectionLabel("Theme") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    SegmentChip(
                        label = mode.label,
                        selected = theme.mode == mode,
                        onClick = { onMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { SectionLabel("Palette") }
        items(AppPalette.entries.chunked(2)) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { palette ->
                    PaletteCard(
                        palette = palette,
                        selected = theme.palette == palette && !theme.dynamicColor,
                        dark = theme.resolvedDark(),
                        onClick = { onPalette(palette) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                ToggleRow(
                    title = "Use wallpaper colours",
                    subtitle = "Material You — overrides the palette above",
                    checked = theme.dynamicColor,
                    onCheckedChange = onDynamic
                )
            }
        }

        item { SectionLabel("Extras") }
        item {
            ToggleRow(
                title = "Starfield",
                subtitle = "Drifting, twinkling stars behind the app. Dark themes only.",
                checked = theme.stars,
                onCheckedChange = onStars,
                icon = Icons.Default.AutoAwesome
            )
        }
        item {
            // A live sample rather than a description: stars are the one setting
            // where a still screenshot tells you nothing.
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(120.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (theme.stars) {
                        Starfield(starColor = MaterialTheme.colorScheme.primary, starCount = 55)
                    }
                    Text(
                        text = if (theme.stars) "✨ Starfield on" else "Starfield off",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Shows the palette's real colours, in whichever mode is currently active. */
@Composable
private fun PaletteCard(
    palette: AppPalette,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = if (dark) palette.dark else palette.light
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.background,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(scheme.primary, 26.dp)
                Spacer(Modifier.width(6.dp))
                Swatch(scheme.primaryContainer, 18.dp)
                Spacer(Modifier.width(6.dp))
                Swatch(scheme.surfaceVariant, 14.dp)
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = palette.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            // A miniature of the mini-player, so the choice is judged on how the
            // app will actually look rather than on three coloured dots.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.primary.copy(alpha = 0.55f))
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeState.resolvedDark(): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}
