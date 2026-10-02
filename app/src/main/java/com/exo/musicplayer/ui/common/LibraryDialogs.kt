package com.exo.musicplayer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.ui.LibraryTool
import com.exo.musicplayer.ui.ToolJob

private fun LibraryTool.icon(): ImageVector = when (this) {
    LibraryTool.NAMES_TAGS -> Icons.AutoMirrored.Filled.Label
    LibraryTool.IDENTIFY -> Icons.Default.Fingerprint
    LibraryTool.TELEGRAM -> Icons.Default.Healing
    LibraryTool.COVERS -> Icons.Default.Album
    LibraryTool.LYRICS -> Icons.Default.Lyrics
    LibraryTool.LEVELS -> Icons.Default.Tune
}

/** A job's progress, in place where it was started. */
@Composable
fun JobPanel(job: ToolJob, onStop: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (job.running) "${job.label} — ${job.done} of ${job.total}" else "${job.label} finished",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (job.running) TextButton(onClick = onStop) { Text("Stop") }
                else TextButton(onClick = onDismiss) { Text("OK") }
            }
            if (job.running) {
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                if (job.current.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        job.current,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            job.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * The library tools, as on Windows: each says what it does and how many
 * songs it would visit. Each one remembers which songs it has already been
 * through, so a second run only touches what's new - unless Redo is ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolsSheet(
    trackCount: Int,
    counts: Map<LibraryTool, Int>,
    job: ToolJob?,
    onRun: (LibraryTool, redo: Boolean, names: Boolean, tags: Boolean) -> Unit,
    onStop: () -> Unit,
    onDismissJob: () -> Unit,
    onDuplicates: () -> Unit,
    onZip: () -> Unit,
    onDismiss: () -> Unit
) {
    var redo by remember { mutableStateOf(false) }
    var choosingParts by remember { mutableStateOf(false) }
    val running = job?.running == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            item {
                Text("Library tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "$trackCount songs in the library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                job?.let {
                    JobPanel(it, onStop, onDismissJob)
                    Spacer(Modifier.height(10.dp))
                }
                CheckRow(
                    label = "Redo songs that have already been through a tool",
                    note = "Off means each tool only visits songs it hasn't seen before",
                    checked = redo,
                    enabled = !running,
                    onChange = { redo = it }
                )
            }
            LibraryTool.entries.groupBy { it.group }.forEach { (group, tools) ->
                item {
                    Text(
                        group.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                }
                items(tools) { tool ->
                    ToolRow(
                        icon = tool.icon(),
                        title = tool.label,
                        note = tool.note,
                        count = if (redo) null else counts[tool],
                        action = if (tool == LibraryTool.NAMES_TAGS) "Choose…" else "Run",
                        enabled = !running
                    ) {
                        if (tool == LibraryTool.NAMES_TAGS) choosingParts = true else onRun(tool, redo, true, true)
                    }
                }
            }
            item {
                Text(
                    "TIDYING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                ToolRow(
                    Icons.Default.ContentCopy, "Duplicates",
                    "Finds the same song stored twice. Nothing is removed until you've seen the list.",
                    null, "Find", !running, onDuplicates
                )
                Spacer(Modifier.height(8.dp))
                ToolRow(
                    Icons.Default.Archive, "Zip and ship",
                    "Packs the whole library into one file you can send or keep.",
                    null, "Zip", !running, onZip
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (choosingParts) {
        var names by remember { mutableStateOf(true) }
        var tags by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { choosingParts = false },
            title = { Text("Names & tags") },
            text = {
                Column {
                    Text(
                        "Choose what gets written",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    CheckRow("Names", "The song's own title, and the artist", names, true) { names = it }
                    CheckRow("Tags", "Album, year and genre", tags, true) { tags = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (redo) "Every song will be visited." else
                            "Only songs missing what you picked are visited. Tick Redo to include the rest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { choosingParts = false; onRun(LibraryTool.NAMES_TAGS, redo, names, tags) },
                    enabled = names || tags
                ) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { choosingParts = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    note: String,
    count: Int?,
    action: String,
    enabled: Boolean,
    onRun: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (count != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (count == 0) "all done" else "$count to do",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onRun, enabled = enabled) { Text(action) }
        }
    }
}

@Composable
fun CheckRow(label: String, note: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Fix, for the songs picked out: which parts to go and get. A song whose name
 * is only an id - 9239d7ef-aeb9-… - is identified by listening to it, so this
 * works on exactly the songs a search by name can do nothing with.
 */
@Composable
fun FixDialog(
    targets: List<Track>,
    job: ToolJob?,
    onRun: (tags: Boolean, genres: Boolean, lyrics: Boolean) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var tags by remember { mutableStateOf(true) }
    var genres by remember { mutableStateOf(true) }
    var lyrics by remember { mutableStateOf(true) }
    val mine = job?.label == com.exo.musicplayer.ui.MainViewModel.FIX_LABEL
    val running = job?.running == true
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Fix") },
        text = {
            Column {
                Text(
                    if (targets.size == 1) targets.first().title else "${targets.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                CheckRow("Tags", "Title, artist, album and year", tags, !running) { tags = it }
                CheckRow("Genres", "From the same lookup", genres, !running) { genres = it }
                CheckRow("Lyrics", "Four services in turn, timed where they exist", lyrics, !running) { lyrics = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Each song is looked up by its name. One whose name is only a file name like " +
                        "9239d7ef-aeb9-… is identified by listening to it instead.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job != null && (mine || running)) {
                    Spacer(Modifier.height(12.dp))
                    if (mine) JobPanel(job, onStop, onDismiss)
                    else Text(
                        "${job.label} is running. It has to finish first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (mine && !running) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(onClick = { onRun(tags, genres, lyrics) }, enabled = !running && (tags || genres || lyrics)) {
                    Text("Run")
                }
            }
        },
        dismissButton = if (mine && !running) null else {
            { TextButton(onClick = onDismiss, enabled = !running) { Text("Cancel") } }
        }
    )
}

/**
 * Sets one thing about many songs at once. A field left blank is left alone
 * rather than cleared: that's the point of editing thirty songs together.
 */
@Composable
fun EditManyDialog(
    targets: List<Track>,
    onApply: (artist: String, album: String, year: String, genre: String) -> Unit,
    onDismiss: () -> Unit
) {
    fun shared(pick: (Track) -> String?): String =
        targets.map { pick(it).orEmpty() }.distinct().singleOrNull().orEmpty()

    var artist by remember { mutableStateOf(shared { it.artist }) }
    var album by remember { mutableStateOf(shared { it.album }) }
    var year by remember { mutableStateOf(shared { it.year?.toString() }) }
    var genre by remember { mutableStateOf(shared { it.genre }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (targets.size == 1) "Edit 1 song" else "Edit ${targets.size} songs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Anything left blank stays as it is on each song.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true)
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Year") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(artist, album, year, genre) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Five stars, as on Windows. Tapping the star already set clears the rating. */
@Composable
fun RateDialog(track: Track, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    var stars by remember { mutableIntStateOf(track.rating) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate") },
        text = {
            Column {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            if (star <= stars) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star of 5",
                            tint = if (star <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { stars = if (stars == star) 0 else star }
                                .padding(4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRate(stars) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Deleting removes the file itself, so it asks first. */
@Composable
fun ConfirmDeleteDialog(tracks: List<Track>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (tracks.size == 1) "Delete \"${tracks.first().title}\"?" else "Delete ${tracks.size} songs?")
        },
        text = {
            Text("The ${if (tracks.size == 1) "file goes" else "files go"} from the phone, along with plays, ratings and lyrics.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
