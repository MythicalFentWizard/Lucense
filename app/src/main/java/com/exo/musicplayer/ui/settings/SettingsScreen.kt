package com.exo.musicplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.BuildConfig
import com.exo.musicplayer.data.lyrics.LyricsTerm
import com.exo.musicplayer.ui.UpdateState

/** The values the settings page shows. */
class SettingsValues(
    val crossfadeSeconds: Int,
    val levelling: Boolean,
    val sleepFade: Boolean,
    val lyricsTerm: LyricsTerm,
    val lyricsPattern: String,
    val coverProvider: String,
    val coverProviders: List<String>,
    val update: UpdateState
)

/** What the settings page can change. */
class SettingsActions(
    val onBack: () -> Unit,
    val onAppearance: () -> Unit,
    val onSound: () -> Unit,
    val onContact: () -> Unit,
    val onCrossfade: (Int) -> Unit,
    val onLevelling: (Boolean) -> Unit,
    val onSleepFade: (Boolean) -> Unit,
    val onLyricsTerm: (LyricsTerm) -> Unit,
    val onLyricsPattern: (String) -> Unit,
    val onCoverProvider: (String) -> Unit,
    val onBackUp: () -> Unit,
    val onRestore: () -> Unit,
    val onCheckUpdate: () -> Unit,
    val onOpenUpdate: (String) -> Unit
)

@Composable
fun SettingsScreen(values: SettingsValues, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))

        LinkRow(Icons.Default.Palette, "Appearance", "Colour scheme, backgrounds, wallpaper, lyric colours", actions.onAppearance)
        LinkRow(Icons.AutoMirrored.Filled.VolumeUp, "Sound", "Calls and interruptions", actions.onSound)

        Section("Playback")
        Setting("Crossfade", "One song runs into the next. Off still means gapless - no silence between songs.") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 2, 4, 6, 8, 12).forEach { seconds ->
                    FilterChip(
                        selected = values.crossfadeSeconds == seconds,
                        onClick = { actions.onCrossfade(seconds) },
                        label = { Text(if (seconds == 0) "Off" else "$seconds s") }
                    )
                }
            }
        }
        SwitchSetting(
            "Volume levelling",
            "Songs measured with Level volumes in Library tools all play at the same loudness. Nothing is written to the files.",
            values.levelling,
            actions.onLevelling
        )
        SwitchSetting(
            "Fade out the sleep timer",
            "The last half minute fades down instead of stopping mid-bar.",
            values.sleepFade,
            actions.onSleepFade
        )

        Section("Lyrics and artwork")
        Setting("Lyrics search", values.lyricsTerm.note) {
            Picker(
                current = values.lyricsTerm.label,
                options = LyricsTerm.entries.map { it.label to { actions.onLyricsTerm(it) } }
            )
            if (values.lyricsTerm == LyricsTerm.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                var pattern by remember(values.lyricsPattern) { mutableStateOf(values.lyricsPattern) }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; actions.onLyricsPattern(it) },
                    label = { Text("Pattern") },
                    supportingText = { Text("{artist}  {title}  {album}  {file}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Setting(
            "Cover art service",
            "Asked first when covers are fetched in bulk. The others are still tried when it has nothing."
        ) {
            Picker(
                current = values.coverProvider.ifBlank { "Any, in the usual order" },
                options = listOf("Any, in the usual order" to { actions.onCoverProvider("") }) +
                    values.coverProviders.map { it to { actions.onCoverProvider(it) } }
            )
        }

        Section("Backups")
        Setting(
            "Favourites, ratings, plays and playlists",
            "Saved as a plain text file wherever you choose. Restoring adds back what's missing and never deletes anything."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onBackUp) {
                    Icon(Icons.Default.Backup, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back up…")
                }
                OutlinedButton(onClick = actions.onRestore) {
                    Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore…")
                }
            }
        }

        Section("Updates")
        Setting(
            title = when (val update = values.update) {
                is UpdateState.Available -> "Version ${update.version} is out"
                is UpdateState.UpToDate -> "This is the newest version"
                UpdateState.Checking -> "Checking…"
                UpdateState.Failed -> "Couldn't reach GitHub"
                UpdateState.Idle -> "Updates"
            },
            note = "You're on ${BuildConfig.VERSION_NAME}. New versions are published on GitHub."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val update = values.update
                if (update is UpdateState.Available) {
                    Button(onClick = { actions.onOpenUpdate(update.url) }) {
                        Icon(Icons.Default.SystemUpdate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Get it")
                    }
                }
                OutlinedButton(onClick = actions.onCheckUpdate, enabled = values.update != UpdateState.Checking) {
                    Text("Check now")
                }
            }
        }

        Section("About")
        LinkRow(Icons.AutoMirrored.Filled.Send, "Contact me", "t.me/Eth4wn — bugs, requests, or to say it broke", actions.onContact)

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Lucense ${BuildConfig.VERSION_NAME}\nmade by lucent",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Setting(title: String, note: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SwitchSetting(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Picker(current: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(current)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, pick) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontWeight = if (label == current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (label == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { open = false; pick() }
                )
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
