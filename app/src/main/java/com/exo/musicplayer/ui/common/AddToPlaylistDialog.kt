package com.exo.musicplayer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.PlaylistSummary

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(playlist.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${playlist.trackCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = { onCreate(name); onDismiss() },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (creating && playlists.isNotEmpty()) {
                TextButton(onClick = { creating = false }) { Text("Back") }
            }
        }
    )
}
