package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.discover.ArtistState
import com.exo.musicplayer.data.discover.DiscoverArtist
import com.exo.musicplayer.data.discover.DiscoverBrowser
import com.exo.musicplayer.data.discover.DiscoverPage
import com.exo.musicplayer.data.discover.DiscoverRelease
import com.exo.musicplayer.data.discover.DiscoverTrack
import com.exo.musicplayer.data.discover.DiscoverWords
import com.exo.musicplayer.data.discover.Discovery
import com.exo.musicplayer.data.discover.GenreState
import com.exo.musicplayer.data.discover.LibrarySong
import com.exo.musicplayer.data.discover.LibraryView
import com.exo.musicplayer.data.discover.ReleaseState
import com.exo.musicplayer.data.youtube.PreviewLabel
import com.exo.musicplayer.data.youtube.PreviewState
import com.exo.musicplayer.data.youtube.YouTubeFormat
import com.exo.musicplayer.desktop.data.DesktopController
import kotlinx.coroutines.delay

/** What every row on the page draws itself from. */
private class Shared(
    val controller: DesktopController,
    val browser: DiscoverBrowser,
    val preview: PreviewState,
    val library: LibraryView,
    val samples: Map<String, DiscoverTrack>,
    /** Opens YouTube search in Identify with this text. */
    val youTube: (String) -> Unit
) {
    fun hearSong(track: DiscoverTrack) = controller.hear(
        DiscoverBrowser.previewKey(track),
        PreviewLabel(track.title, track.artist, track.coverUrl)
    ) { track.previewUrl }

    // Which song an artist or a genre is heard by isn't known until it has been found.
    fun hearArtist(artist: DiscoverArtist) {
        val key = DiscoverBrowser.sampleKey(artist)
        controller.hear(
            key,
            PreviewLabel(artist.name, "Finding a song…", artist.pictureUrl),
            found = {
                browser.samples.value[key]?.let { PreviewLabel(it.title, it.artist, it.coverUrl ?: artist.pictureUrl) }
            }
        ) { browser.sample(artist) }
    }

    fun hearGenre(genre: String) {
        val key = DiscoverBrowser.genreSampleKey(genre)
        controller.hear(
            key,
            PreviewLabel(genre, "Finding a song…"),
            found = { browser.samples.value[key]?.let { PreviewLabel(it.title, "${it.artist} · $genre", it.coverUrl) } }
        ) { browser.sampleGenre(genre) }
    }
}

/**
 * Discover: a map of genres to wander, in the manner of Every Noise at Once.
 *
 * Pick a genre and hear who plays it; open an artist and see which genres they
 * are, what they have out and who is like them; follow that to the next genre.
 * Anything heard on the way is one click from being downloaded. The pages and
 * what they load are the phone's (DiscoverBrowser); this lays them out for a
 * wide window.
 */
@Composable
fun DiscoverScreen(controller: DesktopController, onOpenIdentify: () -> Unit) {
    val browser = controller.discover
    val stack by browser.stack.collectAsState()
    val genres by browser.genres.collectAsState()
    val library by browser.library.collectAsState()
    val samples by browser.samples.collectAsState()
    val preview by controller.preview.state.collectAsState()

    LaunchedEffect(Unit) { browser.open() }
    // The library a moment after it last changed, rather than once per file of a scan.
    LaunchedEffect(controller.tracks) {
        delay(500)
        browser.setLibrary(controller.tracks.map { LibrarySong(it.title, it.artist, it.genre) })
    }
    val shared = Shared(controller, browser, preview, library, samples) { text ->
        controller.searchYouTubeFor(text)
        onOpenIdentify()
    }
    val page = stack.last()
    // Each page keeps its own scroll position, so Back returns to where it was left.
    val holder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (page != DiscoverPage.Home) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Back", icon = Icons.AutoMirrored.Filled.ArrowBack) { browser.back() }
                if (stack.size > 2) {
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Discover", icon = Icons.Default.Home) { browser.home() }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        holder.SaveableStateProvider("${stack.size}:$page".take(160)) {
            when (page) {
                DiscoverPage.Home -> HomePage(genres, shared)
                is DiscoverPage.Genres -> GenresPage(page, genres, shared)
                is DiscoverPage.Genre -> GenrePage(page.name, shared)
                is DiscoverPage.Artist -> ArtistPage(page.artist, shared)
                is DiscoverPage.Release -> ReleasePage(page.release, shared)
                DiscoverPage.Charts -> ChartsPage(shared)
            }
        }
    }
}

// ---- Home ---------------------------------------------------------------------------

@Composable
private fun HomePage(genres: List<String>, shared: Shared) {
    val browser = shared.browser
    val home by browser.home.collectAsState()
    val search by browser.search.collectAsState()
    val genresFailed by browser.genresFailed.collectAsState()
    val surprising by browser.surprising.collectAsState()
    var showAllFresh by remember { mutableStateOf(false) }

    val query = search.query.trim()
    val matched = remember(genres, query) {
        if (query.isEmpty()) emptyList() else Discovery.searchGenres(genres, query)
    }
    val families = remember(genres) {
        Discovery.FAMILIES.map { word -> word to genres.count { it.contains(word) } }.filter { it.second >= 3 }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "ways-in") {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput(
                        value = search.query,
                        onValueChange = browser::setQuery,
                        placeholder = "A genre or an artist",
                        leading = Icons.Default.Search,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    GhostButton(
                        if (genres.isEmpty()) "All genres" else "All ${DiscoverWords.grouped(genres.size)} genres",
                        icon = Icons.Default.Category
                    ) { browser.openGenres() }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(
                        if (surprising) "Picking…" else "Surprise me",
                        enabled = genres.isNotEmpty() && !surprising,
                        icon = Icons.Default.Casino
                    ) { browser.surprise() }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Charts", icon = Icons.Default.Leaderboard) { browser.openCharts() }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Look again", icon = Icons.Default.Refresh) { browser.refresh() }
                }
                Spacer(Modifier.height(8.dp))
                Hint(
                    "Pick a genre to see who plays it, or type an artist to see which genres they are. " +
                        "Play buttons give half a minute; anything you like is one click from downloaded."
                )
                if (genresFailed) {
                    Spacer(Modifier.height(8.dp))
                    Hint("The genre map couldn't be loaded. Check the connection, then Look again.")
                }
            }
        }

        if (query.isNotEmpty()) {
            // ---- What was typed -------------------------------------------------
            item(key = "found-genres") {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Genres")
                    Spacer(Modifier.height(10.dp))
                    when {
                        genres.isEmpty() -> Hint(if (genresFailed) "The genre list couldn't be loaded." else "Loading the genre list…")
                        matched.isEmpty() -> Hint("No genre has \"$query\" in its name.")
                        else -> ChipFlow {
                            matched.take(GENRES_IN_SEARCH).forEach { Chip(it) { browser.openGenre(it) } }
                            if (matched.size > GENRES_IN_SEARCH) {
                                Chip("${matched.size - GENRES_IN_SEARCH} more…") { browser.openGenres(query) }
                            }
                        }
                    }
                }
            }
            item(key = "found-artists") {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Artists")
                    Hint("Open one to see which genres they are.")
                    Spacer(Modifier.height(8.dp))
                    if (search.searching) {
                        Busy("Searching…")
                    } else {
                        search.artists.forEach { ArtistRow(it, shared) }
                        // Deezer doesn't list everyone; MusicBrainz may still know their genres.
                        if (query.length >= 2 && search.artists.none { Discovery.plain(it.name) == Discovery.plain(query) }) {
                            Spacer(Modifier.height(6.dp))
                            GhostButton("Look up \"$query\" as an artist") { browser.openArtist(DiscoverArtist(query)) }
                        }
                    }
                }
            }
        } else {
            if (shared.library.genres.isNotEmpty()) {
                item(key = "your-genres") {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("Your genres")
                        Hint("From the songs you have.")
                        Spacer(Modifier.height(10.dp))
                        ChipFlow { shared.library.genres.forEach { Chip(it) { browser.openGenre(it) } } }
                    }
                }
            }

            item(key = "for-you") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ---- New from the library's artists -------------------------
                    Panel(Modifier.weight(1f)) {
                        SectionTitle("New from your artists")
                        Hint("The last four months.")
                        Spacer(Modifier.height(8.dp))
                        when {
                            home.unreachable -> Hint(DiscoverWords.UNREACHABLE_HOME)
                            home.looked && shared.library.artists.isEmpty() ->
                                Hint("Once there is music in your library, what its artists release shows up here.")
                            else -> {
                                if (home.freshBusy) {
                                    Hint(
                                        if (home.freshOf > 0) "Checking your artists… ${home.freshDone} of ${home.freshOf}"
                                        else "Checking your artists…"
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    ThinProgress(if (home.freshOf > 0) home.freshDone.toFloat() / home.freshOf else null)
                                    Spacer(Modifier.height(6.dp))
                                }
                                (if (showAllFresh) home.fresh else home.fresh.take(FRESH_SHOWN)).forEach {
                                    ReleaseRow(it, shared, withArtist = true)
                                }
                                if (home.fresh.size > FRESH_SHOWN) {
                                    Spacer(Modifier.height(6.dp))
                                    GhostButton(if (showAllFresh) "Show fewer" else "Show all ${home.fresh.size}") {
                                        showAllFresh = !showAllFresh
                                    }
                                }
                                if (home.looked && !home.freshBusy && home.fresh.isEmpty()) {
                                    Hint("Nothing new from your artists in the last four months.")
                                }
                            }
                        }
                    }
                    // ---- Artists like the library's -----------------------------
                    if (home.similar.isNotEmpty() || home.similarBusy) {
                        Panel(Modifier.width(360.dp)) {
                            SectionTitle("You might like")
                            Hint("Artists like yours that you don't have.")
                            Spacer(Modifier.height(8.dp))
                            if (home.similar.isEmpty()) Busy("Finding artists like yours…")
                            else home.similar.take(SIMILAR_SHOWN).forEach { ArtistRow(it, shared) }
                        }
                    }
                }
            }

            if (families.isNotEmpty()) {
                item(key = "families") {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("Browse by family")
                        Hint("Every genre with the word in it.")
                        Spacer(Modifier.height(10.dp))
                        ChipFlow {
                            families.forEach { (word, count) -> Chip("$word  $count") { browser.openGenres(word) } }
                        }
                    }
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(10.dp)) }
    }
}

// ---- Every genre ------------------------------------------------------------------

/**
 * All of them at once, as a wall to read across: the nearest this gets to the
 * map itself. Click a name to open the genre, the play button to hear it.
 */
@Composable
private fun GenresPage(page: DiscoverPage.Genres, genres: List<String>, shared: Shared) {
    var filter by remember { mutableStateOf(page.filter) }
    val shown = remember(genres, filter) { Discovery.searchGenres(genres, filter) }
    val families = remember(genres) { Discovery.FAMILIES.filter { word -> genres.count { it.contains(word) } >= 3 } }

    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    when {
                        genres.isEmpty() -> "All genres"
                        filter.isBlank() -> "All ${DiscoverWords.grouped(genres.size)} genres"
                        else -> "${DiscoverWords.grouped(shown.size)} of ${DiscoverWords.grouped(genres.size)} genres"
                    }
                ) {
                    TextInput(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = "Filter: metal, wave, core…",
                        leading = Icons.Default.Search,
                        modifier = Modifier.width(300.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                families.forEach { word ->
                    val on = filter.trim().equals(word, ignoreCase = true)
                    Chip(word, selected = on) { filter = if (on) "" else word }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty() && genres.isNotEmpty()) Hint("No genre has \"${filter.trim()}\" in its name.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(shown, key = { it }) { genre -> GenreCell(genre, shared) }
        }
    }
}

@Composable
private fun GenreCell(genre: String, shared: Shared) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val key = DiscoverBrowser.genreSampleKey(genre)
    val sampling = shared.preview.videoId == key
    val sample = shared.samples[key]

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered || sampling) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable { shared.browser.openGenre(genre) }
            .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                genre,
                style = MaterialTheme.typography.bodyMedium,
                color = if (sampling) Palette.Accent else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val note = when {
                sampling && shared.preview.error != null -> "Nothing from this genre could be played"
                sampling && shared.preview.loading -> "Finding a song…"
                sampling && sample != null -> "♪ ${sample.artist} · ${sample.title}"
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PlayAction(sampling, shared.preview, "Hear $genre", quiet = !hovered && !sampling) { shared.hearGenre(genre) }
    }
}

// ---- One genre --------------------------------------------------------------------

@Composable
private fun GenrePage(name: String, shared: Shared) {
    val browser = shared.browser
    val states by browser.genrePages.collectAsState()
    val state = states[name] ?: GenreState()
    val page = state.page
    var showAllFresh by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- Who plays it -----------------------------------------------------
        LazyColumn(Modifier.weight(1f).fillMaxSize()) {
            item(key = "title") {
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                    if (page != null) {
                        Text(
                            "${DiscoverWords.grouped(page.total)} ${if (page.total == 1) "artist" else "artists"} tagged with it",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextDim
                        )
                    }
                }
            }
            if (state.loading) item(key = "busy") { Busy("Reading the genre map…") }
            state.problem?.let { problem -> item(key = "problem") { Problem(problem) { browser.refresh() } } }

            if (page != null && page.artists.isEmpty()) {
                item(key = "nobody") {
                    Column {
                        Hint("Nobody on MusicBrainz has been filed under this genre yet, so there is no one to list.")
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Search for it on YouTube", icon = Icons.Default.SmartDisplay) {
                            shared.youTube("$name music")
                        }
                    }
                }
            }

            if (page != null && page.artists.isNotEmpty()) {
                // The artists the genre is most of first; the rest only carry the tag.
                val core = page.artists.filter { (it.weight ?: 0f) >= CORE_WEIGHT }
                val rest = page.artists.filter { (it.weight ?: 0f) < CORE_WEIGHT }
                item(key = "core-title") { Label(if (core.isEmpty()) "Artists" else "At its core", "Play to hear one") }
                items(core, key = { "core-${it.name}" }) { ArtistRow(it, shared) }
                if (rest.isNotEmpty()) {
                    if (core.isNotEmpty()) item(key = "rest-title") { Label("Also tagged with it", null) }
                    items(rest, key = { "rest-${it.name}" }) { ArtistRow(it, shared) }
                }
                if (page.total > state.asked) {
                    item(key = "more") {
                        Box(Modifier.padding(vertical = 8.dp)) {
                            if (state.loadingMore) Busy("Loading more…")
                            else GhostButton("More artists") { browser.moreOfGenre(name) }
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(20.dp)) }
        }

        // ---- What is next to it, and what is new in it --------------------------
        if (page != null && page.artists.isNotEmpty()) {
            LazyColumn(Modifier.width(400.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (page.nearby.isNotEmpty()) {
                    item(key = "nearby") {
                        Panel(Modifier.fillMaxWidth()) {
                            SectionTitle("Next to it")
                            Hint("Genres the same artists are also filed under.")
                            Spacer(Modifier.height(10.dp))
                            ChipFlow { page.nearby.forEach { Chip(it) { browser.openGenre(it) } } }
                        }
                    }
                }
                item(key = "fresh") {
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle("New in $name")
                        Hint("The last year, from the first forty artists.")
                        Spacer(Modifier.height(8.dp))
                        val fresh = state.fresh
                        if (fresh == null) {
                            // Looked up only when asked for: it is forty artists' worth of questions.
                            GhostButton("See what's new", icon = Icons.Default.NewReleases) { browser.loadFreshIn(name) }
                        } else {
                            if (state.freshBusy) {
                                Hint(
                                    if (state.freshOf > 0) "Checking artists… ${state.freshDone} of ${state.freshOf}"
                                    else "Checking artists…"
                                )
                                Spacer(Modifier.height(6.dp))
                                ThinProgress(if (state.freshOf > 0) state.freshDone.toFloat() / state.freshOf else null)
                                Spacer(Modifier.height(6.dp))
                            }
                            (if (showAllFresh) fresh else fresh.take(FRESH_SHOWN)).forEach {
                                ReleaseRow(it, shared, withArtist = true)
                            }
                            if (fresh.size > FRESH_SHOWN) {
                                Spacer(Modifier.height(6.dp))
                                GhostButton(if (showAllFresh) "Show fewer" else "Show all ${fresh.size}") {
                                    showAllFresh = !showAllFresh
                                }
                            }
                            if (!state.freshBusy && fresh.isEmpty()) {
                                Hint("Nothing from these artists in the last year, as far as Deezer knows.")
                            }
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

// ---- One artist -------------------------------------------------------------------

@Composable
private fun ArtistPage(artist: DiscoverArtist, shared: Shared) {
    val browser = shared.browser
    val states by browser.artistPages.collectAsState()
    val state = states[DiscoverBrowser.artistKey(artist)] ?: ArtistState()
    val page = state.page
    val shown = page?.artist ?: artist
    var showAllReleases by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picture(shown.pictureUrl, shown.name, 92.dp, round = true)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        shown.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    shown.fans?.takeIf { it > 0 }?.let {
                        Text(
                            "${DiscoverWords.fans(it)} on Deezer",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextDim
                        )
                    }
                    if (page != null) {
                        Spacer(Modifier.height(8.dp))
                        if (page.genres.isEmpty()) Hint("Nobody has filed this artist under a genre yet.")
                        else ChipFlow { page.genres.forEach { Chip(it) { browser.openGenre(it) } } }
                    }
                }
                Spacer(Modifier.width(12.dp))
                GhostButton("Search on YouTube", icon = Icons.Default.SmartDisplay) { shared.youTube(shown.name) }
            }
            state.note?.let {
                Spacer(Modifier.height(10.dp))
                Hint(it)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (state.loading) Busy("Looking up ${shown.name}…")
        state.problem?.let { problem -> Problem(problem) { browser.refresh() } }

        if (page != null && page.artist.deezerId != null) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxSize()) {
                    item(key = "popular-title") { Label("Most played", "Click one to hear it") }
                    itemsIndexed(page.popular, key = { index, track -> "popular-$index-${track.id}" }) { _, track ->
                        SongRow(track, shared, withArtist = false)
                    }
                    if (page.popular.isEmpty()) item(key = "popular-none") { Hint("Deezer lists no songs for this artist.") }
                    item(key = "end") { Spacer(Modifier.height(20.dp)) }
                }
                LazyColumn(Modifier.width(400.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (page.releases.isNotEmpty()) {
                        item(key = "releases") {
                            Panel(Modifier.fillMaxWidth()) {
                                SectionTitle("Releases")
                                Spacer(Modifier.height(8.dp))
                                (if (showAllReleases) page.releases else page.releases.take(RELEASES_SHOWN)).forEach {
                                    ReleaseRow(it, shared, withArtist = false)
                                }
                                if (page.releases.size > RELEASES_SHOWN) {
                                    Spacer(Modifier.height(6.dp))
                                    GhostButton(if (showAllReleases) "Show fewer" else "Show all ${page.releases.size}") {
                                        showAllReleases = !showAllReleases
                                    }
                                }
                            }
                        }
                    }
                    if (page.similar.isNotEmpty()) {
                        item(key = "similar") {
                            Panel(Modifier.fillMaxWidth()) {
                                SectionTitle("Similar artists")
                                Spacer(Modifier.height(8.dp))
                                page.similar.take(SIMILAR_SHOWN).forEach { ArtistRow(it, shared) }
                            }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
}

// ---- One release ------------------------------------------------------------------

@Composable
private fun ReleasePage(release: DiscoverRelease, shared: Shared) {
    val states by shared.browser.releasePages.collectAsState()
    val state = states[release.id] ?: ReleaseState()
    // What "Download all" would fetch: songs not owned and not already asked for.
    val wanted = state.tracks.filter { track ->
        !shared.library.owns(track) && shared.controller.discoveredDownload(track).let { it == null || it.failed }
    }

    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picture(release.coverUrl, release.title, 104.dp, round = false)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        release.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        release.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Accent,
                        modifier = Modifier.clickable { shared.browser.openArtist(DiscoverArtist(release.artist)) }
                    )
                    Text(
                        DiscoverWords.release(release, withArtist = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (state.tracks.isNotEmpty()) {
                    AccentButton(
                        DiscoverWords.downloadAll(wanted.size, state.tracks.size),
                        enabled = wanted.isNotEmpty(),
                        icon = Icons.Default.Download
                    ) { wanted.forEach(shared.controller::downloadDiscovered) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.loading) Busy("Loading the songs…")
        state.problem?.let { problem -> Problem(problem) { shared.browser.refresh() } }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.tracks, key = { index, track -> "track-$index-${track.id}" }) { index, track ->
                SongRow(track, shared, withArtist = track.artist != release.artist, number = index + 1)
            }
            item(key = "end") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ---- Charts -----------------------------------------------------------------------

@Composable
private fun ChartsPage(shared: Shared) {
    val browser = shared.browser
    val chart by browser.chart.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Charts")
            Hint("What is played most on Deezer right now.")
            if (chart.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    chart.genres.forEach { (id, name) ->
                        Chip(name, selected = id == chart.genreId) { if (id != chart.genreId) browser.loadChart(id) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (chart.loading) Busy("Loading the chart…")
        chart.problem?.let { problem -> Problem(problem) { browser.refresh() } }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(chart.tracks, key = { index, track -> "chart-$index-${track.id}" }) { index, track ->
                SongRow(track, shared, withArtist = true, number = index + 1)
            }
            item(key = "end") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ---- Rows ---------------------------------------------------------------------------

/**
 * A song: click it to hear half a minute, the arrow to download it. The
 * YouTube button is for picking the upload by hand.
 */
@Composable
private fun SongRow(track: DiscoverTrack, shared: Shared, withArtist: Boolean, number: Int? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val key = DiscoverBrowser.previewKey(track)
    val previewing = shared.preview.videoId == key
    val owned = remember(track.id, shared.library) { shared.library.owns(track) }
    val entry = shared.controller.discoveredDownload(track)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered || previewing) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = track.previewUrl != null) { shared.hearSong(track) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextFaint,
                modifier = Modifier.width(30.dp)
            )
        }
        Box(Modifier.size(42.dp)) {
            Picture(track.coverUrl, track.title, 42.dp, round = false)
            if (previewing || (hovered && track.previewUrl != null)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewing && shared.preview.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (previewing && shared.preview.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null,
                            Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (previewing) Palette.Accent else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val failed = previewing && shared.preview.error != null
            val note = when {
                failed -> "The preview couldn't be played"
                previewing && shared.preview.loading -> "Loading the preview…"
                previewing && shared.preview.playing ->
                    "Preview · ${YouTubeFormat.duration(shared.preview.secondsPlayed)} of 0:30"
                previewing && shared.preview.ended -> "Preview finished · click to hear it again"
                previewing && shared.preview.paused -> "Preview paused · click to carry on"
                entry != null && !entry.done -> entry.status.lineSequence().firstOrNull().orEmpty()
                entry != null && entry.failed -> entry.status.lineSequence().firstOrNull().orEmpty()
                else -> listOfNotNull(
                    track.artist.takeIf { withArtist && it.isNotBlank() },
                    track.album?.takeIf { withArtist },
                    when {
                        entry != null && entry.done -> "downloaded"
                        owned -> "in your library"
                        else -> null
                    }
                ).joinToString(" · ")
            }
            // Left out when there is nothing to say, so the title sits in the middle of its row.
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed || entry?.failed == true) Palette.Accent else Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            (track.durationMs / 1000).toInt().takeIf { it > 0 }?.let(YouTubeFormat::duration).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextFaint
        )
        Spacer(Modifier.width(8.dp))
        if (withArtist && track.artist.isNotBlank() && hovered) {
            IconAction(Icons.Default.Person, "Go to ${track.artist}", Palette.TextDim) {
                shared.browser.openArtist(DiscoverArtist(track.artist, deezerId = track.artistId))
            }
        }
        IconAction(Icons.Default.SmartDisplay, "Search on YouTube", if (hovered) Palette.TextDim else Palette.TextFaint) {
            shared.youTube("${track.artist} ${track.title}".trim())
        }
        when {
            entry != null && !entry.done -> Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                if (entry.percent > 0f) {
                    CircularProgressIndicator(
                        progress = { entry.percent.coerceIn(0f, 1f) },
                        modifier = Modifier.size(18.dp),
                        color = Palette.Accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Accent, strokeWidth = 2.dp)
                }
            }
            entry != null && entry.failed ->
                IconAction(Icons.Default.ErrorOutline, "Try the download again", Palette.Accent) {
                    shared.controller.downloadDiscovered(track)
                }
            entry != null || owned -> Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, "In your library", Modifier.size(18.dp), tint = Palette.Accent)
            }
            else -> IconAction(Icons.Default.Download, "Download", Palette.Text) {
                shared.controller.downloadDiscovered(track)
            }
        }
    }
}

/** An artist: click the row for their page, the play button to hear them. */
@Composable
private fun ArtistRow(artist: DiscoverArtist, shared: Shared) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val key = DiscoverBrowser.sampleKey(artist)
    val sampling = shared.preview.videoId == key
    val sample = shared.samples[key]

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered || sampling) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable { shared.browser.openArtist(artist) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Picture(artist.pictureUrl ?: sample?.coverUrl, artist.name, 36.dp, round = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (sampling) Palette.Accent else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val note = when {
                sampling && shared.preview.error != null -> "Nothing of theirs could be played"
                sampling && shared.preview.loading -> "Finding a song…"
                sample != null -> "♪ ${sample.title}"
                artist.fans != null && artist.fans!! > 0 -> DiscoverWords.fans(artist.fans!!)
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PlayAction(sampling, shared.preview, "Hear ${artist.name}", quiet = !hovered && !sampling) { shared.hearArtist(artist) }
    }
}

@Composable
private fun ReleaseRow(release: DiscoverRelease, shared: Shared, withArtist: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) Palette.Hover else Color.Transparent)
            .hoverable(interaction)
            .clickable { shared.browser.openRelease(release) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Picture(release.coverUrl, release.title, 42.dp, round = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                release.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                DiscoverWords.release(release, withArtist),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Small parts --------------------------------------------------------------------

/** A cover or an artist's picture, with the first letter of the name where there is none. */
@Composable
private fun Picture(url: String?, name: String, size: Dp, round: Boolean) {
    val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
    Box(
        Modifier.size(size).clip(shape).background(Palette.Hover),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = if (size >= 72.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
            color = Palette.TextFaint
        )
        if (url != null) RemoteImage(url, Modifier.fillMaxSize(), corner = 0.dp, placeholder = false)
    }
}

/** The play, stop or still-loading button of a row that can be heard. */
@Composable
private fun PlayAction(active: Boolean, preview: PreviewState, description: String, quiet: Boolean, onClick: () -> Unit) {
    if (active && preview.loading) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Accent, strokeWidth = 2.dp)
        }
    } else {
        IconAction(
            if (active && preview.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            description,
            if (active) Palette.Accent else if (quiet) Palette.TextFaint else Palette.Text,
            onClick
        )
    }
}

@Composable
private fun IconAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (hovered) Palette.Line else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, Modifier.size(18.dp), tint = if (hovered) Palette.Text else tint) }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Selected else if (hovered) Palette.Hover else Palette.Content)
            .border(1.dp, if (selected) Palette.Accent else Palette.Line, RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected || hovered) Palette.Text else Palette.TextDim,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) { content() }
}

/** A heading inside a list, where a panel would be too much. */
@Composable
private fun Label(title: String, note: String?) {
    Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        if (note != null) {
            Spacer(Modifier.width(10.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun Busy(text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Hint(text)
    }
}

@Composable
private fun Problem(text: String, onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Hint(text)
        Spacer(Modifier.height(8.dp))
        GhostButton("Try again", icon = Icons.Default.Refresh, onClick = onRetry)
    }
}

/** How many of a list are shown before "Show all". */
private const val FRESH_SHOWN = 8
private const val RELEASES_SHOWN = 8
private const val SIMILAR_SHOWN = 12
private const val GENRES_IN_SEARCH = 60

/** From here up, the genre is most of what the artist is. */
private const val CORE_WEIGHT = 0.75f
