package com.exo.musicplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.PlaylistSummary
import com.exo.musicplayer.data.db.SmartPlaylist
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.library.AutoPlaylist
import com.exo.musicplayer.ui.common.ArtworkLarge
import com.exo.musicplayer.ui.common.TrackRow
import com.exo.musicplayer.util.asDuration

/** Everything the Playlists page can do, gathered so the page isn't handed twenty lambdas loose. */
class PlaylistActions(
    val openPlaylist: (PlaylistSummary) -> Unit,
    val openAuto: (AutoPlaylist) -> Unit,
    val playPlaylist: (PlaylistSummary) -> Unit,
    val playAuto: (AutoPlaylist) -> Unit,
    val create: (String) -> Unit,
    val rename: (PlaylistSummary, String) -> Unit,
    val delete: (PlaylistSummary) -> Unit,
    val export: (PlaylistSummary) -> Unit,
    val zip: (PlaylistSummary) -> Unit,
    val import: () -> Unit,
    val setPrompt: (String) -> Unit,
    val playPrompt: () -> Unit,
    val savePrompt: () -> Unit,
    val keepAsRule: (name: String, rule: String) -> Unit,
    val playSmart: (SmartPlaylist) -> Unit,
    val deleteSmart: (SmartPlaylist) -> Unit
)

private fun AutoPlaylist.icon(): ImageVector = when (this) {
    AutoPlaylist.RECENT -> Icons.Default.NewReleases
    AutoPlaylist.HISTORY -> Icons.Default.History
    AutoPlaylist.MOST_PLAYED -> Icons.Default.TrendingUp
    AutoPlaylist.NEVER_PLAYED -> Icons.Default.VisibilityOff
    AutoPlaylist.TOP_RATED -> Icons.Default.Star
    AutoPlaylist.FAVOURITES -> Icons.Default.Favorite
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    autoLists: Map<AutoPlaylist, List<Track>>,
    smartLists: List<SmartPlaylist>,
    smartCounts: Map<Long, Int>,
    prompt: String,
    promptCount: Int,
    genres: List<Pair<String, Int>>,
    actions: PlaylistActions,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }
    var newRule by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Playlists travel as plain text, so two people can end up with
                // the same list without either uploading anything anywhere.
                IconButton(onClick = actions.import) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Import a playlist file")
                }
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New playlist")
                }
            }
        }

        // ---- Made for you ---------------------------------------------------
        item { SectionTitle("Made for you", "Worked out from your library and what you play. Nothing leaves the phone.") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AutoPlaylist.entries.toList()) { kind ->
                    val songs = autoLists[kind].orEmpty()
                    AutoCard(
                        kind = kind,
                        songs = songs,
                        onOpen = { actions.openAuto(kind) },
                        onPlay = { actions.playAuto(kind) }
                    )
                }
            }
        }

        // ---- Your playlists -------------------------------------------------
        item { SectionTitle("Your playlists", null) }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "None yet. Make one with +, from a selection in the library, or from a genre below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist, actions)
            }
        }

        // ---- Smart lists ----------------------------------------------------
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionTitle("Smart lists", "A rule rather than a fixed set of songs, so it refills itself.")
                }
                TextButton(onClick = { newRule = true }, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        if (smartLists.isEmpty()) {
            item {
                Text(
                    "Try rating:4+, year:2020+, genre:rock or plays:0 — the same rules the search box takes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
        items(smartLists, key = { "smart-${it.id}" }) { list ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.playSmart(list) }
                    .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Rule, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${list.rule}  ·  ${smartCounts[list.id] ?: 0} songs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { actions.playSmart(list) }) { Icon(Icons.Default.PlayArrow, "Play ${list.name}") }
                IconButton(onClick = { actions.deleteSmart(list) }) {
                    Icon(Icons.Default.Delete, "Delete ${list.name}", Modifier.size(18.dp))
                }
            }
        }

        // ---- Genres and rules -----------------------------------------------
        item {
            SectionTitle(
                "Genres and rules",
                "Type a genre and get a list. A bare word is tried as a genre first, then as an ordinary search."
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = actions.setPrompt,
                placeholder = { Text("A genre, an artist, or a rule like year:2020+") },
                singleLine = true,
                supportingText = if (prompt.isNotBlank()) {
                    { Text(if (promptCount == 1) "1 song" else "$promptCount songs") }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (genres.isNotEmpty() && prompt.isBlank()) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genres.take(14).forEach { (genre, count) ->
                        SuggestionChip(onClick = { actions.setPrompt(genre) }, label = { Text("$genre · $count") })
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = actions.playPrompt, enabled = prompt.isNotBlank() && promptCount > 0) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
                OutlinedButton(onClick = actions.savePrompt, enabled = prompt.isNotBlank() && promptCount > 0) {
                    Text("Save as playlist")
                }
                OutlinedButton(
                    onClick = { actions.keepAsRule(prompt.replaceFirstChar { it.uppercase() }, prompt) },
                    enabled = prompt.isNotBlank()
                ) { Text("Keep as rule") }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { actions.create(it); creating = false },
            onDismiss = { creating = false }
        )
    }
    if (newRule) {
        var name by remember { mutableStateOf("") }
        var rule by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newRule = false },
            title = { Text("New smart list") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(
                        value = rule,
                        onValueChange = { rule = it },
                        label = { Text("Rule") },
                        placeholder = { Text("rating:4+ year:2015-2020") },
                        singleLine = true
                    )
                    Text(
                        "artist:  album:  genre:  fav:  unplayed:  rating:4+  year:2020+  plays:10+  added:30d",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.keepAsRule(name, rule); newRule = false },
                    enabled = rule.isNotBlank()
                ) { Text("Keep") }
            },
            dismissButton = { TextButton(onClick = { newRule = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(title: String, note: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AutoCard(kind: AutoPlaylist, songs: List<Track>, onOpen: () -> Unit, onPlay: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = songs.isNotEmpty(), onClick = onOpen)
            .padding(12.dp)
    ) {
        Box {
            ArtworkLarge(track = songs.firstOrNull { it.artPath != null }, modifier = Modifier.aspectRatio(1f))
            Icon(
                kind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(kind.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (songs.size == 1) "1 song" else "${songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(onClick = onPlay, enabled = songs.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlayArrow, "Play ${kind.label}", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, actions: PlaylistActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { actions.openPlaylist(playlist) }
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildString {
                    append(playlist.trackCount)
                    append(if (playlist.trackCount == 1) " song" else " songs")
                    if (playlist.totalDurationMs > 0) {
                        append("  ·  ")
                        append(playlist.totalDurationMs.asDuration())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { actions.playPlaylist(playlist) }, enabled = playlist.trackCount > 0) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play ${playlist.name}")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More for ${playlist.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename…") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { menuOpen = false; renaming = true }
                )
                DropdownMenuItem(
                    text = { Text("Export as a file…") },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                    onClick = { menuOpen = false; actions.export(playlist) }
                )
                DropdownMenuItem(
                    text = { Text("Zip and ship") },
                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                    onClick = { menuOpen = false; actions.zip(playlist) }
                )
                DropdownMenuItem(
                    text = { Text("Delete playlist…") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; confirmDelete = true }
                )
            }
        }
    }
    if (renaming) {
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirm = "Rename",
            onConfirm = { actions.rename(playlist, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The playlist goes; the songs in it stay in your library.") },
            confirmButton = {
                TextButton(onClick = { actions.delete(playlist); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One list's songs: a playlist, or one of the made-for-you lists. Songs can
 * be taken out of a playlist; a made-for-you list is worked out, so it can't
 * be edited, only played.
 */
@Composable
fun PlaylistDetailScreen(
    name: String,
    note: String?,
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayFrom: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRemove: ((Track) -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            if (tracks.size == 1) "1 song" else "${tracks.size} songs",
                            tracks.sumOf { it.durationMs }.takeIf { it > 0 }?.asDuration(),
                            note
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(onClick = { onPlayFrom(0) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onShuffle, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
            if (tracks.isEmpty()) {
                Text(
                    "Nothing here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        itemsIndexed(tracks, key = { index, t -> "$index-${t.id}" }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onPlayFrom(index) },
                onRemove = onRemove?.let { { it(track) } }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
