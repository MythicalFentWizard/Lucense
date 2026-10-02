package com.exo.musicplayer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.library.DuplicateGroup

/**
 * Review step for duplicate removal.
 *
 * Deliberately not a one-tap "clean up": this deletes audio files permanently,
 * and the matching is a heuristic on tags. Every candidate is listed with the
 * copy being kept beside it, so a wrong guess is visible before anything goes.
 */
@Composable
fun DuplicatesDialog(
    groups: List<DuplicateGroup>,
    scanning: Boolean,
    /** Songs listened to so far and how many need it, while listening. */
    listening: Pair<Int, Int>?,
    onRemove: (List<Track>) -> Unit,
    onDismiss: () -> Unit
) {
    // Every candidate starts selected; unticking is how you rescue one.
    var excluded by remember(groups) { mutableStateOf(setOf<Long>()) }

    val candidates = remember(groups) { groups.flatMap { it.remove } }
    val selected = candidates.filterNot { it.id in excluded }
    val reclaimed = selected.sumOf { it.sizeBytes }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    scanning -> "Finding duplicates…"
                    groups.isEmpty() -> "No duplicates found"
                    else -> "${candidates.size} ${if (candidates.size == 1) "copy" else "copies"} of " +
                        "${groups.size} ${if (groups.size == 1) "song" else "songs"}"
                }
            )
        },
        text = {
            when {
                scanning -> Column {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { listening?.let { (done, total) -> if (total > 0) done.toFloat() / total else 0f } ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    Text(
                        listening?.let { (done, total) -> "Listening to each song: $done of $total" }
                            ?: "Comparing how the songs sound…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Matched by how they sound, not by their names. Each song is listened to " +
                            "once, thirty seconds from its middle, and remembered, so later scans only " +
                            "listen to what's new.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                groups.isEmpty() -> Text(
                    "No song is in your library twice, by how they sound.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Column {
                    Text(
                        "The largest copy of each song is kept — usually the highest " +
                            "quality. Untick anything you want to keep.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(groups, key = { it.keep.id }) { group ->
                            GroupRow(
                                group = group,
                                excluded = excluded,
                                onToggle = { id ->
                                    excluded = if (id in excluded) excluded - id else excluded + id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (groups.isNotEmpty() && !scanning) {
                TextButton(
                    onClick = { onRemove(selected) },
                    enabled = selected.isNotEmpty()
                ) {
                    Text("Remove ${selected.size} · frees ${formatSize(reclaimed)}")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (groups.isNotEmpty() && !scanning) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun GroupRow(
    group: DuplicateGroup,
    excluded: Set<Long>,
    onToggle: (Long) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "${group.artist} — ${group.title}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "keeping ${formatSize(group.keep.sizeBytes)} copy",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        group.remove.forEach { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(track.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = track.id !in excluded,
                    onCheckedChange = { onToggle(track.id) }
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${formatSize(track.sizeBytes)} · ${track.originalName ?: track.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes > 0 -> "${bytes / 1024} KB"
    else -> "—"
}
