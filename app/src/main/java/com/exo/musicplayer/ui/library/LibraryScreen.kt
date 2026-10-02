package com.exo.musicplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.prefs.GroupSort
import com.exo.musicplayer.data.prefs.LibrarySort
import com.exo.musicplayer.data.prefs.LibraryView
import com.exo.musicplayer.ui.TrackGroup
import com.exo.musicplayer.ui.common.ArtworkLarge
import com.exo.musicplayer.ui.common.TrackRow
import com.exo.musicplayer.util.asDuration
import kotlinx.coroutines.launch

/** Everything the selection bar can do with the songs picked out. */
class SelectionActions(
    val play: () -> Unit,
    val playNext: () -> Unit,
    val queue: () -> Unit,
    val favourite: () -> Unit,
    val playlist: () -> Unit,
    val edit: () -> Unit,
    val fix: () -> Unit,
    val revert: () -> Unit,
    val share: () -> Unit,
    val zip: () -> Unit,
    val delete: () -> Unit
)

@Composable
fun LibraryScreen(
    view: LibraryView,
    onViewChange: (LibraryView) -> Unit,
    tracks: List<Track>,
    searchResults: List<Track>,
    query: String,
    recentSearches: List<String>,
    sort: LibrarySort,
    albums: List<TrackGroup>,
    artists: List<TrackGroup>,
    albumSort: GroupSort,
    artistSort: GroupSort,
    openGroup: TrackGroup?,
    currentTrackId: Long?,
    isPlaying: Boolean,
    selectedIds: Set<Long>,
    onQueryChange: (String) -> Unit,
    onRememberSearch: () -> Unit,
    onForgetSearches: () -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onAlbumSortChange: (GroupSort) -> Unit,
    onArtistSortChange: (GroupSort) -> Unit,
    onOpenGroup: (String?) -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayFrom: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (List<Track>) -> Unit,
    selection: SelectionActions,
    modifier: Modifier = Modifier
) {
    var searchOpen by rememberSaveable { mutableStateOf(query.isNotBlank()) }
    val searching = query.isNotBlank()
    val shown = if (searching) searchResults else tracks
    val selecting = selectedIds.isNotEmpty()

    if (tracks.isEmpty() && !searching) {
        Column(modifier.fillMaxSize()) {
            Header(
                onSearch = null,
                onAddFiles = onAddFiles,
                onAddFolder = onAddFolder,
                onOpenDownload = onOpenDownload,
                onOpenTools = onOpenTools,
                onOpenSettings = onOpenSettings
            )
            EmptyLibrary(onAddFiles, onAddFolder)
        }
        return
    }

    // Leaving selection, an open album, or the search, with the back gesture,
    // which is what the gesture is for.
    BackHandler(enabled = selecting, onBack = onClearSelection)
    BackHandler(enabled = !selecting && openGroup != null) { onOpenGroup(null) }
    BackHandler(enabled = !selecting && openGroup == null && searchOpen) {
        searchOpen = false
        onQueryChange("")
    }

    // The selection bar floats over the header, at the header's height, rather
    // than taking its place: a taller bar pushed the whole list down the
    // moment a song was long-pressed, so the next tap landed on another song.
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (openGroup == null) Column(Modifier.onSizeChanged { headerHeight = it.height }) {
                Header(
                    onSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) onQueryChange("")
                        if (searchOpen && view != LibraryView.SONGS) onViewChange(LibraryView.SONGS)
                    },
                    searchOpen = searchOpen,
                    onAddFiles = onAddFiles,
                    onAddFolder = onAddFolder,
                    onOpenDownload = onOpenDownload,
                    onOpenTools = onOpenTools,
                    onOpenSettings = onOpenSettings
                )
                if (!searchOpen) {
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        LibraryView.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = view == entry,
                                onClick = { onViewChange(entry) },
                                shape = SegmentedButtonDefaults.itemShape(index, LibraryView.entries.size),
                                label = { Text(entry.label) }
                            )
                        }
                    }
                }
            }

            if (openGroup != null) {
                GroupDetail(
                    group = openGroup,
                    isAlbum = view == LibraryView.ALBUMS,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    selectedIds = selectedIds,
                    onBack = { onOpenGroup(null) },
                    onPlayFrom = onPlayFrom,
                    onShuffle = onShuffle,
                    onToggleSelect = onToggleSelect
                )
                return@Column
            }

            when {
                searchOpen || view == LibraryView.SONGS -> SongsView(
                    tracks = tracks,
                    shown = shown,
                    searching = searching,
                    searchOpen = searchOpen,
                    query = query,
                    recentSearches = recentSearches,
                    sort = sort,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    selectedIds = selectedIds,
                    onQueryChange = onQueryChange,
                    onRememberSearch = onRememberSearch,
                    onForgetSearches = onForgetSearches,
                    onSortChange = onSortChange,
                    onPlayFrom = onPlayFrom,
                    onShuffle = onShuffle,
                    onToggleSelect = onToggleSelect
                )

                view == LibraryView.ALBUMS -> GroupGrid(
                    groups = albums,
                    isAlbum = true,
                    sort = albumSort,
                    onSortChange = onAlbumSortChange,
                    onOpen = { onOpenGroup(it.key) },
                    emptyNote = "No albums yet. Songs with an album in their details gather here."
                )

                else -> GroupGrid(
                    groups = artists,
                    isAlbum = false,
                    sort = artistSort,
                    onSortChange = onArtistSortChange,
                    onOpen = { onOpenGroup(it.key) },
                    emptyNote = "No artists yet. Songs with an artist in their details gather here."
                )
            }
        }
        if (selecting) {
            SelectionBar(
                count = selectedIds.size,
                total = (openGroup?.tracks ?: shown).size,
                onClear = onClearSelection,
                onSelectAll = { onSelectAll(openGroup?.tracks ?: shown) },
                actions = selection,
                minHeight = with(density) { (if (openGroup == null) headerHeight else 0).toDp() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun Header(
    onSearch: (() -> Unit)?,
    searchOpen: Boolean = false,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                    contentDescription = if (searchOpen) "Close search" else "Search"
                )
            }
        }
        AddMenu(onAddFiles, onAddFolder, onOpenDownload)
        IconButton(onClick = onOpenTools) {
            Icon(Icons.Default.Build, contentDescription = "Library tools")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsView(
    tracks: List<Track>,
    shown: List<Track>,
    searching: Boolean,
    searchOpen: Boolean,
    query: String,
    recentSearches: List<String>,
    sort: LibrarySort,
    currentTrackId: Long?,
    isPlaying: Boolean,
    selectedIds: Set<Long>,
    onQueryChange: (String) -> Unit,
    onRememberSearch: () -> Unit,
    onForgetSearches: () -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onPlayFrom: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onToggleSelect: (Track) -> Unit
) {
    val selecting = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val playingIndex = shown.indexOfFirst { it.id == currentTrackId }

    AnimatedVisibility(visible = searchOpen) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Songs, artists, albums, or artist:… genre:…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear search") } }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onRememberSearch() }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focus)
            )
            if (query.isBlank()) {
                if (recentSearches.isNotEmpty()) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        recentSearches.forEach { recent ->
                            AssistChip(
                                onClick = { onQueryChange(recent) },
                                label = { Text(recent, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) }
                            )
                        }
                        TextButton(onClick = onForgetSearches) { Text("Clear") }
                    }
                }
                Text(
                    "Filters work as on Windows: artist:  album:  title:  genre:  fav:  unplayed:  " +
                        "rating:4+  year:2015-2020  plays:0  added:30d  \"quoted phrases\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
    }
    LaunchedEffect(searchOpen) { if (searchOpen) runCatching { focus.requestFocus() } }

    if (!searching) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onPlayFrom(tracks, 0) }, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onShuffle(tracks) }, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
            Spacer(Modifier.weight(1f))
            SortChip(sort.label, LibrarySort.entries.map { it to it.label }, sort, onSortChange)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                searching && shown.isEmpty() -> "No matches for \"$query\""
                searching -> "${shown.size} ${if (shown.size == 1) "match" else "matches"}"
                else -> "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        // Jump to the song playing, as J does on Windows.
        if (playingIndex >= 0) {
            TextButton(onClick = { scope.launch { listState.animateScrollToItem(playingIndex) } }) {
                Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Playing")
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(shown, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                // Once a selection exists, tapping extends it rather than
                // starting playback - otherwise picking a second song would
                // throw the first away.
                onClick = { if (selecting) onToggleSelect(track) else onPlayFrom(shown, index) },
                isSelected = track.id in selectedIds,
                onLongPress = { onToggleSelect(track) },
                modifier = Modifier.animateItem()
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun <T> SortChip(
    label: String,
    options: List<Pair<T, String>>,
    current: T,
    onChange: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            fontWeight = if (value == current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (value == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onChange(value); open = false }
                )
            }
        }
    }
}

@Composable
private fun GroupGrid(
    groups: List<TrackGroup>,
    isAlbum: Boolean,
    sort: GroupSort,
    onSortChange: (GroupSort) -> Unit,
    onOpen: (TrackGroup) -> Unit,
    emptyNote: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${groups.size} ${if (isAlbum) (if (groups.size == 1) "album" else "albums") else (if (groups.size == 1) "artist" else "artists")}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        SortChip(
            sort.label,
            GroupSort.entries.filter { if (isAlbum) it.albums else it.artists }.map { it to it.label },
            sort,
            onSortChange
        )
    }
    if (groups.isEmpty()) {
        Text(
            emptyNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.key }) { group ->
            Column(Modifier.clickable { onOpen(group) }) {
                ArtworkLarge(track = group.cover, modifier = Modifier.aspectRatio(1f))
                Spacer(Modifier.height(8.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        group.subtitle,
                        "${group.tracks.size} ${if (group.tracks.size == 1) "song" else "songs"}"
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GroupDetail(
    group: TrackGroup,
    isAlbum: Boolean,
    currentTrackId: Long?,
    isPlaying: Boolean,
    selectedIds: Set<Long>,
    onBack: () -> Unit,
    onPlayFrom: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onToggleSelect: (Track) -> Unit
) {
    val selecting = selectedIds.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (isAlbum) "Album" else "Artist",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkLarge(track = group.cover, modifier = Modifier.size(120.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    group.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        listOfNotNull(
                            group.year?.toString(),
                            "${group.tracks.size} ${if (group.tracks.size == 1) "song" else "songs"}",
                            group.tracks.sumOf { it.durationMs }.takeIf { it > 0 }?.asDuration()
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Button(onClick = { onPlayFrom(group.tracks, 0) }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { onShuffle(group.tracks) }) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        itemsIndexed(group.tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { if (selecting) onToggleSelect(track) else onPlayFrom(group.tracks, index) },
                isSelected = track.id in selectedIds,
                onLongPress = { onToggleSelect(track) },
                number = if (isAlbum) track.trackNumber ?: (index + 1) else null,
                artSize = 44.dp
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AddMenu(
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenDownload: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Add, contentDescription = "Add songs")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Add files…") },
                leadingIcon = { Icon(Icons.Default.LibraryMusic, null) },
                onClick = { open = false; onAddFiles() }
            )
            DropdownMenuItem(
                text = { Text("Import a folder…") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                onClick = { open = false; onAddFolder() }
            )
            DropdownMenuItem(
                text = { Text("Download from a link…") },
                leadingIcon = { Icon(Icons.Default.Download, null) },
                onClick = { open = false; onOpenDownload() }
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onAddFiles: () -> Unit, onAddFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No songs yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Open a song in Telegram, tap Share, and pick Lucense. " +
                "It gets copied here and stays in your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddFiles) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add files")
            }
            OutlinedButton(onClick = onAddFolder) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import folder")
            }
        }
    }
}

/**
 * Actions for a multi-track selection: the five used most as buttons, the
 * rest under More, so nothing is squeezed to fit a phone's width.
 */
@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    actions: SelectionActions,
    minHeight: Dp,
    modifier: Modifier = Modifier
) {
    var more by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .heightIn(min = minHeight)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (count < total) {
                    TextButton(onClick = onSelectAll) { Text("All $total") }
                }
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More for the selection")
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit details…") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { more = false; actions.edit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Fix…") },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                            onClick = { more = false; actions.fix() }
                        )
                        DropdownMenuItem(
                            text = { Text("Revert to the files' own details") },
                            leadingIcon = { Icon(Icons.Default.Undo, null) },
                            onClick = { more = false; actions.revert() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { more = false; actions.share() }
                        )
                        DropdownMenuItem(
                            text = { Text("Zip and ship") },
                            leadingIcon = { Icon(Icons.Default.Archive, null) },
                            onClick = { more = false; actions.zip() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete from library…") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { more = false; actions.delete() }
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BarButton("Play", Icons.Default.PlayArrow, actions.play)
                BarButton("Play next", Icons.Default.QueuePlayNext, actions.playNext)
                BarButton("Queue", Icons.AutoMirrored.Filled.QueueMusic, actions.queue)
                BarButton("Favourite", Icons.Default.FavoriteBorder, actions.favourite)
                BarButton("Playlist", Icons.AutoMirrored.Filled.PlaylistAdd, actions.playlist)
                BarButton("Fix", Icons.Default.AutoFixHigh, actions.fix)
            }
        }
    }
}

@Composable
private fun BarButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
