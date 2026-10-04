package com.exo.musicplayer.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exo.musicplayer.data.discover.DiscoverArtist
import com.exo.musicplayer.data.discover.DiscoverBrowser
import com.exo.musicplayer.data.discover.DiscoverPage
import com.exo.musicplayer.data.discover.DiscoverRelease
import com.exo.musicplayer.data.discover.DiscoverTrack
import com.exo.musicplayer.data.discover.DiscoverWords
import com.exo.musicplayer.data.discover.Discovery
import com.exo.musicplayer.data.discover.GenreState
import com.exo.musicplayer.data.discover.ArtistState
import com.exo.musicplayer.data.discover.ReleaseState
import com.exo.musicplayer.data.discover.LibraryView
import com.exo.musicplayer.data.youtube.PreviewState
import com.exo.musicplayer.ui.download.DownloadUiState
import com.exo.musicplayer.ui.download.QueuedDownload
import com.exo.musicplayer.util.counted

/** What Discover asks of the rest of the app. */
class DiscoverActions(
    /** Plays or stops a song's half-minute clip. */
    val onPreview: (DiscoverTrack) -> Unit,
    /** Plays or stops the song an artist is heard by. */
    val onSample: (DiscoverArtist) -> Unit,
    /** Plays or stops a song from one of a genre's artists. */
    val onSampleGenre: (String) -> Unit,
    val onDownload: (DiscoverTrack) -> Unit,
    /** Opens YouTube search in Identify with this text. */
    val onYouTube: (String) -> Unit,
    val onOpenDownloads: () -> Unit
)

/** What every row draws itself from, gathered so each page doesn't hand six things down. */
internal class DiscoverShared(
    val preview: PreviewState,
    val queued: Map<String, QueuedDownload>,
    /** How far the song downloading now has got, 0 to 100. */
    val downloadPercent: Float,
    val library: LibraryView,
    val samples: Map<String, DiscoverTrack>,
    val actions: DiscoverActions,
    val openArtist: (DiscoverArtist) -> Unit,
    val openGenre: (String) -> Unit,
    val openRelease: (DiscoverRelease) -> Unit,
    val back: () -> Unit,
    /** Straight back to the first page, offered once a few pages deep. */
    val home: (() -> Unit)?
)

/**
 * Discover: a map of genres to wander, in the manner of Every Noise at Once.
 *
 * Pick a genre and hear who plays it; open an artist and see which genres they
 * are, what they have out and who is like them; follow that to the next genre.
 * Anything heard on the way is one tap from being downloaded.
 */
@Composable
fun DiscoverScreen(
    viewModel: DiscoverBrowser,
    preview: PreviewState,
    queued: Map<String, QueuedDownload>,
    download: DownloadUiState,
    actions: DiscoverActions,
    modifier: Modifier = Modifier
) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.open() }

    val shared = DiscoverShared(
        preview = preview,
        queued = queued,
        downloadPercent = download.percent,
        library = library,
        samples = samples,
        actions = actions,
        openArtist = viewModel::openArtist,
        openGenre = viewModel::openGenre,
        openRelease = viewModel::openRelease,
        back = { viewModel.back() },
        home = if (stack.size > 2) viewModel::home else null
    )

    val page = stack.last()
    // Each page keeps its own scroll position, so Back returns to where it was left.
    val holder = rememberSaveableStateHolder()

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider("${stack.size}:$page".take(160)) {
                when (page) {
                    DiscoverPage.Home -> HomePage(viewModel, genres, shared)
                    is DiscoverPage.Genres -> GenresPage(page, genres, shared)
                    is DiscoverPage.Genre -> GenrePage(page.name, viewModel, shared)
                    is DiscoverPage.Artist -> ArtistPage(page.artist, viewModel, shared)
                    is DiscoverPage.Release -> ReleasePage(page.release, viewModel, shared)
                    DiscoverPage.Charts -> ChartsPage(viewModel, shared)
                }
            }
        }
        DownloadStrip(download, queued, actions.onOpenDownloads)
    }
}

// ---- Home ---------------------------------------------------------------------------

@Composable
private fun HomePage(viewModel: DiscoverBrowser, genres: List<String>, shared: DiscoverShared) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val genresFailed by viewModel.genresFailed.collectAsStateWithLifecycle()
    val surprising by viewModel.surprising.collectAsStateWithLifecycle()
    var showAllFresh by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val query = search.query.trim()
    val matched = remember(genres, query) {
        if (query.isEmpty()) emptyList() else Discovery.searchGenres(genres, query)
    }
    val families = remember(genres) {
        Discovery.FAMILIES.map { word -> word to genres.count { it.contains(word) } }.filter { it.second >= 3 }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "title") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Discover",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Look again") }
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = search.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("A genre or an artist") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (query.isNotEmpty()) {
            // ---- What was typed -------------------------------------------------
            item(key = "found-genres-title") { SectionTitle("Genres") }
            item(key = "found-genres") {
                when {
                    genres.isEmpty() -> Hint(
                        if (genresFailed) "The genre list couldn't be loaded. Check the connection and press refresh."
                        else "Loading the genre list…"
                    )
                    matched.isEmpty() -> Hint("No genre has \"$query\" in its name.")
                    else -> ChipFlow {
                        matched.take(GENRES_IN_SEARCH).forEach { GenreChip(it) { shared.openGenre(it) } }
                        if (matched.size > GENRES_IN_SEARCH) {
                            GenreChip("${matched.size - GENRES_IN_SEARCH} more…") { viewModel.openGenres(query) }
                        }
                    }
                }
            }

            item(key = "found-artists-title") {
                SectionTitle("Artists", "Open one to see which genres they are")
            }
            if (search.searching) {
                item(key = "found-artists-busy") { Busy("Searching…") }
            } else {
                items(search.artists, key = { "found-artist-${it.deezerId}-${it.name}" }) { artist ->
                    ArtistRow(artist, shared)
                }
                // Deezer doesn't list everyone; MusicBrainz may still know their genres.
                if (query.length >= 2 && search.artists.none { Discovery.plain(it.name) == Discovery.plain(query) }) {
                    item(key = "found-artists-anyway") {
                        TextButton(
                            onClick = { shared.openArtist(DiscoverArtist(query)) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text("Look up \"$query\" as an artist") }
                    }
                }
            }
        } else {
            // ---- Ways in ----------------------------------------------------------
            item(key = "ways-in") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WayIn(
                        Icons.Default.Category,
                        "All genres",
                        if (genres.isEmpty()) "the map" else grouped(genres.size),
                        Modifier.weight(1f)
                    ) { viewModel.openGenres() }
                    WayIn(
                        Icons.Default.Casino,
                        "Surprise me",
                        if (surprising) "picking…" else "any genre",
                        Modifier.weight(1f)
                    ) { viewModel.surprise() }
                    WayIn(Icons.Default.Leaderboard, "Charts", "most played", Modifier.weight(1f)) {
                        viewModel.openCharts()
                    }
                }
            }

            if (genresFailed) {
                item(key = "genres-failed") {
                    Problem(
                        "The genre map couldn't be loaded. Check the connection and try again.",
                        onRetry = viewModel::refresh
                    )
                }
            }

            if (shared.library.genres.isNotEmpty()) {
                item(key = "your-genres-title") { SectionTitle("Your genres", "From the songs you have") }
                item(key = "your-genres") {
                    ChipFlow { shared.library.genres.forEach { GenreChip(it) { shared.openGenre(it) } } }
                }
            }

            // ---- New from the library's artists -----------------------------------
            item(key = "fresh-title") { SectionTitle("New from your artists", "The last four months") }
            when {
                home.unreachable -> item(key = "fresh-unreachable") {
                    Problem(DiscoverWords.UNREACHABLE_HOME, onRetry = viewModel::refresh)
                }
                home.looked && shared.library.artists.isEmpty() -> item(key = "fresh-empty-library") {
                    Hint("Once there is music in your library, what its artists release shows up here.")
                }
                else -> {
                    if (home.freshBusy) {
                        item(key = "fresh-busy") {
                            Progress(
                                if (home.freshOf > 0) "Checking your artists… ${home.freshDone} of ${home.freshOf}"
                                else "Checking your artists…",
                                if (home.freshOf > 0) home.freshDone.toFloat() / home.freshOf else null
                            )
                        }
                    }
                    val fresh = if (showAllFresh) home.fresh else home.fresh.take(FRESH_SHOWN)
                    items(fresh, key = { "fresh-${it.id}" }) { ReleaseRow(it, shared) }
                    if (home.fresh.size > FRESH_SHOWN) {
                        item(key = "fresh-more") {
                            TextButton(
                                onClick = { showAllFresh = !showAllFresh },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) { Text(if (showAllFresh) "Show fewer" else "Show all ${home.fresh.size}") }
                        }
                    }
                    if (home.looked && !home.freshBusy && home.fresh.isEmpty()) {
                        item(key = "fresh-none") { Hint("Nothing new from your artists in the last four months.") }
                    }
                }
            }

            // ---- Artists like the library's ---------------------------------------
            if (home.similar.isNotEmpty() || home.similarBusy) {
                item(key = "similar-title") { SectionTitle("You might like", "Artists like yours that you don't have") }
                item(key = "similar") {
                    if (home.similar.isEmpty()) Busy("Finding artists like yours…")
                    else ArtistStrip(home.similar, shared)
                }
            }

            // ---- The genre families -----------------------------------------------
            if (families.isNotEmpty()) {
                item(key = "families-title") { SectionTitle("Browse by family", "Every genre with the word in it") }
                item(key = "families") {
                    ChipFlow {
                        families.forEach { (word, count) ->
                            GenreChip("$word  $count") { viewModel.openGenres(word) }
                        }
                    }
                }
            }
        }
    }
}

/** One of the three large buttons at the top of Discover. */
@Composable
private fun WayIn(icon: ImageVector, label: String, note: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Every genre ------------------------------------------------------------------

@Composable
private fun GenresPage(page: DiscoverPage.Genres, genres: List<String>, shared: DiscoverShared) {
    var filter by rememberSaveable { mutableStateOf(page.filter) }
    val shown = remember(genres, filter) { Discovery.searchGenres(genres, filter) }
    val families = remember(genres) { Discovery.FAMILIES.filter { word -> genres.count { it.contains(word) } >= 3 } }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "All genres",
            subtitle = when {
                genres.isEmpty() -> "Loading…"
                filter.isBlank() -> "${grouped(genres.size)} genres"
                else -> "${grouped(shown.size)} of ${grouped(genres.size)}"
            },
            shared = shared
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Filter: metal, wave, core…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(families, key = { it }) { word ->
                FilterChip(
                    selected = filter.trim().equals(word, ignoreCase = true),
                    onClick = { filter = if (filter.trim().equals(word, ignoreCase = true)) "" else word },
                    label = { Text(word) }
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (shown.isEmpty() && genres.isNotEmpty()) {
                item { Hint("No genre has \"${filter.trim()}\" in its name.") }
            }
            items(shown, key = { it }) { genre -> GenreRow(genre, shared) }
        }
    }
}

// ---- One genre --------------------------------------------------------------------

@Composable
private fun GenrePage(name: String, viewModel: DiscoverBrowser, shared: DiscoverShared) {
    val states by viewModel.genrePages.collectAsStateWithLifecycle()
    val state = states[name] ?: GenreState()
    val page = state.page
    var showAllFresh by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "header") {
            PageHeader(
                title = name,
                subtitle = page?.let { "${grouped(it.total)} ${if (it.total == 1) "artist" else "artists"} tagged with it" },
                shared = shared
            )
        }

        if (state.loading) item(key = "busy") { Busy("Reading the genre map…") }
        state.problem?.let { problem ->
            item(key = "problem") { Problem(problem, onRetry = viewModel::refresh) }
        }

        if (page != null && page.artists.isEmpty()) {
            // Not a dead end: the genre exists, it just has nobody filed under it here.
            item(key = "nobody") {
                Hint("Nobody on MusicBrainz has been filed under this genre yet, so there is no one to list.")
            }
            item(key = "nobody-youtube") {
                FilledTonalButton(
                    onClick = { shared.actions.onYouTube("$name music") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search for it on YouTube")
                }
            }
        }

        if (page != null && page.artists.isNotEmpty()) {
            if (page.nearby.isNotEmpty()) {
                item(key = "nearby-title") { SectionTitle("Next to it", "Genres the same artists are also filed under") }
                item(key = "nearby") {
                    ChipFlow { page.nearby.forEach { GenreChip(it) { shared.openGenre(it) } } }
                }
            }

            // What the genre's artists have out lately, looked up only when asked
            // for: it is forty artists' worth of questions.
            val fresh = state.fresh
            if (fresh == null) {
                item(key = "fresh-ask") {
                    OutlinedButton(
                        onClick = { viewModel.loadFreshIn(name) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.NewReleases, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What's new in $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                item(key = "fresh-title") { SectionTitle("New in $name", "The last year, from the artists below") }
                if (state.freshBusy) {
                    item(key = "fresh-busy") {
                        Progress(
                            if (state.freshOf > 0) "Checking artists… ${state.freshDone} of ${state.freshOf}"
                            else "Checking artists…",
                            if (state.freshOf > 0) state.freshDone.toFloat() / state.freshOf else null
                        )
                    }
                }
                items(if (showAllFresh) fresh else fresh.take(FRESH_SHOWN), key = { "fresh-${it.id}" }) {
                    ReleaseRow(it, shared)
                }
                if (fresh.size > FRESH_SHOWN) {
                    item(key = "fresh-more") {
                        TextButton(
                            onClick = { showAllFresh = !showAllFresh },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text(if (showAllFresh) "Show fewer" else "Show all ${fresh.size}") }
                    }
                }
                if (!state.freshBusy && fresh.isEmpty()) {
                    item(key = "fresh-none") { Hint("Nothing from these artists in the last year, as far as Deezer knows.") }
                }
            }

            // The artists the genre is most of first; the rest only carry the tag.
            val core = page.artists.filter { (it.weight ?: 0f) >= CORE_WEIGHT }
            val rest = page.artists.filter { (it.weight ?: 0f) < CORE_WEIGHT }
            item(key = "artists-title") {
                SectionTitle(if (core.isEmpty()) "Artists" else "At its core", "Tap play to hear one")
            }
            items(core, key = { "core-${it.name}" }) { ArtistRow(it, shared) }
            if (rest.isNotEmpty()) {
                if (core.isNotEmpty()) item(key = "rest-title") { SectionTitle("Also tagged with it") }
                items(rest, key = { "rest-${it.name}" }) { ArtistRow(it, shared) }
            }

            if (page.total > state.asked) {
                item(key = "more") {
                    if (state.loadingMore) Busy("Loading more…")
                    else TextButton(
                        onClick = { viewModel.moreOfGenre(name) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) { Text("More artists") }
                }
            }
        }
    }
}

// ---- One artist -------------------------------------------------------------------

@Composable
private fun ArtistPage(artist: DiscoverArtist, viewModel: DiscoverBrowser, shared: DiscoverShared) {
    val states by viewModel.artistPages.collectAsStateWithLifecycle()
    val state = states[DiscoverBrowser.artistKey(artist)] ?: ArtistState()
    val page = state.page
    val shown = page?.artist ?: artist
    var showAllReleases by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "header") { PageHeader(title = "Artist", subtitle = null, shared = shared) }
        item(key = "who") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Picture(shown.pictureUrl, shown.name, 84.dp, round = true)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        shown.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    shown.fans?.takeIf { it > 0 }?.let {
                        Text(
                            "${DiscoverWords.fans(it)} on Deezer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item(key = "youtube") {
            FilledTonalButton(
                onClick = { shared.actions.onYouTube(shown.name) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search on YouTube")
            }
        }

        if (state.loading) item(key = "busy") { Busy("Looking up ${shown.name}…") }
        state.problem?.let { problem ->
            item(key = "problem") { Problem(problem, onRetry = viewModel::refresh) }
        }

        if (page != null) {
            item(key = "genres-title") { SectionTitle("Genres", "Open one to find more like it") }
            item(key = "genres") {
                if (page.genres.isEmpty()) {
                    Hint("Nobody has filed this artist under a genre yet.")
                } else {
                    ChipFlow { page.genres.forEach { GenreChip(it) { shared.openGenre(it) } } }
                }
            }
            state.note?.let { note -> item(key = "note") { Hint(note) } }

            if (page.popular.isNotEmpty()) {
                item(key = "popular-title") { SectionTitle("Most played", "Tap a song to hear half a minute of it") }
                itemsIndexed(page.popular, key = { index, track -> "popular-$index-${track.id}" }) { _, track ->
                    SongRow(track, shared, showArtist = false)
                }
            }

            if (page.releases.isNotEmpty()) {
                item(key = "releases-title") { SectionTitle("Releases", counted(page.releases.size, "release")) }
                items(
                    if (showAllReleases) page.releases else page.releases.take(RELEASES_SHOWN),
                    key = { "release-${it.id}" }
                ) { ReleaseRow(it, shared, showArtist = false) }
                if (page.releases.size > RELEASES_SHOWN) {
                    item(key = "releases-more") {
                        TextButton(
                            onClick = { showAllReleases = !showAllReleases },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text(if (showAllReleases) "Show fewer" else "Show all ${page.releases.size}") }
                    }
                }
            }

            if (page.similar.isNotEmpty()) {
                item(key = "similar-title") { SectionTitle("Similar artists") }
                item(key = "similar") { ArtistStrip(page.similar, shared) }
            }
        }
    }
}

// ---- One release ------------------------------------------------------------------

@Composable
private fun ReleasePage(release: DiscoverRelease, viewModel: DiscoverBrowser, shared: DiscoverShared) {
    val states by viewModel.releasePages.collectAsStateWithLifecycle()
    val state = states[release.id] ?: ReleaseState()
    // What "Download all" would fetch: songs not owned and not already on their way.
    val wanted = state.tracks.filter { track ->
        !shared.library.owns(track) && shared.queued[DiscoverBrowser.previewKey(track)].let {
            it == null || it == QueuedDownload.FAILED
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "header") { PageHeader(title = kindLabel(release.kind), subtitle = null, shared = shared) }
        item(key = "what") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Picture(release.coverUrl, release.title, 96.dp, round = false)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        release.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        release.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { shared.openArtist(DiscoverArtist(release.artist)) }
                    )
                    releaseDate(release.date)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.loading) item(key = "busy") { Busy("Loading the songs…") }
        state.problem?.let { problem ->
            item(key = "problem") { Problem(problem, onRetry = viewModel::refresh) }
        }

        if (state.tracks.isNotEmpty()) {
            item(key = "all") {
                Button(
                    onClick = { wanted.forEach(shared.actions.onDownload) },
                    enabled = wanted.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(DiscoverWords.downloadAll(wanted.size, state.tracks.size))
                }
            }
            itemsIndexed(state.tracks, key = { index, track -> "track-$index-${track.id}" }) { index, track ->
                SongRow(track, shared, showArtist = track.artist != release.artist, number = index + 1)
            }
        }
    }
}

// ---- Charts -----------------------------------------------------------------------

@Composable
private fun ChartsPage(viewModel: DiscoverBrowser, shared: DiscoverShared) {
    val chart by viewModel.chart.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "header") {
            PageHeader(title = "Charts", subtitle = "What is played most on Deezer right now", shared = shared)
        }
        if (chart.genres.isNotEmpty()) {
            item(key = "genres") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chart.genres, key = { it.first }) { (id, name) ->
                        FilterChip(
                            selected = id == chart.genreId,
                            onClick = { if (id != chart.genreId) viewModel.loadChart(id) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
        if (chart.loading) item(key = "busy") { Busy("Loading the chart…") }
        chart.problem?.let { problem ->
            item(key = "problem") { Problem(problem, onRetry = viewModel::refresh) }
        }
        itemsIndexed(chart.tracks, key = { index, track -> "chart-$index-${track.id}" }) { index, track ->
            SongRow(track, shared, showArtist = true, number = index + 1)
        }
    }
}

// ---- What is downloading ------------------------------------------------------------

/**
 * A line along the bottom while songs queued from here download, so pressing
 * download and carrying on browsing doesn't leave the download out of sight.
 */
@Composable
private fun DownloadStrip(download: DownloadUiState, queued: Map<String, QueuedDownload>, onOpen: () -> Unit) {
    val waiting = queued.values.count { it == QueuedDownload.WAITING }
    val running = queued.values.any { it == QueuedDownload.DOWNLOADING }
    if (!running && waiting == 0) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        download.title ?: download.stage.ifBlank { "Downloading…" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(download.stage.ifBlank { "Downloading…" })
                            if (waiting > 0) append(" · $waiting waiting")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (download.percent > 0f) {
                LinearProgressIndicator(
                    progress = { download.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

/** How many of a list are shown before "Show all". */
private const val FRESH_SHOWN = 6
private const val RELEASES_SHOWN = 6
private const val GENRES_IN_SEARCH = 30

/** From here up, the genre is most of what the artist is. */
private const val CORE_WEIGHT = 0.75f
