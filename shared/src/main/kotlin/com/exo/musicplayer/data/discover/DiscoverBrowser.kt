package com.exo.musicplayer.data.discover

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One page of Discover. The stack of them is where Back goes. */
sealed interface DiscoverPage {
    data object Home : DiscoverPage
    /** Every genre, with a word already typed into its filter. */
    data class Genres(val filter: String = "") : DiscoverPage
    data class Genre(val name: String) : DiscoverPage
    data class Artist(val artist: DiscoverArtist) : DiscoverPage
    data class Release(val release: DiscoverRelease) : DiscoverPage
    data object Charts : DiscoverPage
}

/** A genre's page as far as it has loaded. */
data class GenreState(
    val page: GenrePage? = null,
    val loading: Boolean = true,
    val problem: String? = null,
    val loadingMore: Boolean = false,
    /** How many of the genre's artists have been asked for, a hundred at a time. */
    val asked: Int = 100,
    /** What the genre's artists have put out lately; null until asked for. */
    val fresh: List<DiscoverRelease>? = null,
    val freshBusy: Boolean = false,
    val freshDone: Int = 0,
    val freshOf: Int = 0
)

data class ArtistState(
    val page: ArtistPage? = null,
    val loading: Boolean = true,
    val problem: String? = null,
    /** Why there is nothing to play, where the artist is known only by genre. */
    val note: String? = null
)

data class ReleaseState(
    val tracks: List<DiscoverTrack> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null
)

data class ChartState(
    val genres: List<Pair<Long, String>> = emptyList(),
    val genreId: Long = 0,
    val tracks: List<DiscoverTrack> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null
)

data class HomeState(
    /** What the library's artists have released lately, newest first. */
    val fresh: List<DiscoverRelease> = emptyList(),
    val freshBusy: Boolean = false,
    val freshDone: Int = 0,
    val freshOf: Int = 0,
    /** Artists like the library's that aren't in it. */
    val similar: List<DiscoverArtist> = emptyList(),
    val similarBusy: Boolean = false,
    /** Deezer couldn't be reached, so nothing above could be looked up. */
    val unreachable: Boolean = false,
    val looked: Boolean = false
)

data class DiscoverSearch(
    val query: String = "",
    val artists: List<DiscoverArtist> = emptyList(),
    val searching: Boolean = false
)

/** What Discover needs to know about the library: who is in it and what is owned. */
data class LibraryView(
    /** Artist fields, the most songs first. */
    val artists: List<String> = emptyList(),
    /** Each owned title, as [Discovery.titleKey], with the artists it is owned by. */
    val titles: Map<String, List<String>> = emptyMap(),
    /** The library's genres that are genres here too, the most songs first. */
    val genres: List<String> = emptyList()
) {
    fun owns(track: DiscoverTrack): Boolean {
        val artists = titles[Discovery.titleKey(track.title)] ?: return false
        val wanted = Discovery.plain(track.artist)
        return artists.any { it.isEmpty() || wanted.isEmpty() || it.contains(wanted) || wanted.contains(it) }
    }
}

/** A song as the library has it, as far as Discover needs to know. */
data class LibrarySong(val title: String, val artist: String?, val genre: String?)

/**
 * Discover: genres to wander, artists to follow from one to the next, and
 * what's new. The looking-up is [Discovery]'s; this is the pages themselves -
 * which one is open, what each has loaded - kept so going Back is instant and
 * nothing is asked for twice.
 *
 * Shared, so the phone and Windows browse the same way; each draws these
 * pages in its own idiom. [scope] is the screen's: when it ends, so does any
 * looking-up still under way.
 */
class DiscoverBrowser(private val scope: CoroutineScope, private val discovery: Discovery) {

    private val _stack = MutableStateFlow<List<DiscoverPage>>(listOf(DiscoverPage.Home))
    val stack: StateFlow<List<DiscoverPage>> = _stack.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    /** True once the genre list has been asked for and nothing came back. */
    private val _genresFailed = MutableStateFlow(false)
    val genresFailed: StateFlow<Boolean> = _genresFailed.asStateFlow()

    private val _genrePages = MutableStateFlow<Map<String, GenreState>>(emptyMap())
    val genrePages: StateFlow<Map<String, GenreState>> = _genrePages.asStateFlow()

    private val _artistPages = MutableStateFlow<Map<String, ArtistState>>(emptyMap())
    val artistPages: StateFlow<Map<String, ArtistState>> = _artistPages.asStateFlow()

    private val _releasePages = MutableStateFlow<Map<Long, ReleaseState>>(emptyMap())
    val releasePages: StateFlow<Map<Long, ReleaseState>> = _releasePages.asStateFlow()

    private val _chart = MutableStateFlow(ChartState())
    val chart: StateFlow<ChartState> = _chart.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _search = MutableStateFlow(DiscoverSearch())
    val search: StateFlow<DiscoverSearch> = _search.asStateFlow()

    private val _library = MutableStateFlow(LibraryView())
    val library: StateFlow<LibraryView> = _library.asStateFlow()

    /** The song each artist sample turned out to be, by [sampleKey], for the row to name. */
    private val _samples = MutableStateFlow<Map<String, DiscoverTrack>>(emptyMap())
    val samples: StateFlow<Map<String, DiscoverTrack>> = _samples.asStateFlow()

    /** The library has been read at least once, even if it turned out empty. */
    private val libraryRead = MutableStateFlow(false)

    private var opened = false
    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var chartJob: Job? = null

    private var songs: List<LibrarySong> = emptyList()
    private var libraryJob: Job? = null

    /**
     * The library as it is now; called again whenever it changes. What
     * Discover wants from it - who is in it, what is owned, which genres - is
     * worked out off the caller's thread.
     */
    fun setLibrary(library: List<LibrarySong>) {
        songs = library
        libraryJob?.cancel()
        libraryJob = scope.launch {
            val known = _genres.value.toHashSet()
            _library.value = withContext(Dispatchers.Default) {
                val titles = HashMap<String, MutableList<String>>()
                val tagged = HashMap<String, Int>()
                library.forEach { song ->
                    titles.getOrPut(Discovery.titleKey(song.title)) { ArrayList(1) } +=
                        Discovery.plain(song.artist.orEmpty())
                    song.genre.orEmpty().split('/', ';', ',').forEach { part ->
                        val genre = part.trim().lowercase(Locale.ROOT)
                        if (genre in known) tagged.merge(genre, 1, Int::plus)
                    }
                }
                LibraryView(
                    artists = Discovery.rankArtists(library.map { it.artist }),
                    titles = titles,
                    genres = tagged.entries.sortedByDescending { it.value }.take(16).map { it.key }
                )
            }
            libraryRead.value = true
        }
    }

    /** Called when the page is first shown: nothing is fetched for someone who never opens it. */
    fun open() {
        if (opened) return
        opened = true
        loadGenres()
        loadHome()
    }

    private fun loadGenres() {
        scope.launch {
            _genresFailed.value = false
            val all = discovery.genres()
            _genres.value = all
            _genresFailed.value = all.isEmpty()
            // "Your genres" are the library's that are genres here too, so
            // they can only be told once this list has arrived.
            if (all.isNotEmpty() && libraryRead.value) setLibrary(songs)
        }
    }

    /** New releases and suggestions from the library's own artists. */
    private fun loadHome() {
        homeJob?.cancel()
        homeJob = scope.launch {
            // The first library snapshot arrives a moment after the page does.
            libraryRead.first { it }
            val artists = _library.value.artists
            if (artists.isEmpty()) {
                _home.value = HomeState(looked = true)
                return@launch
            }
            _home.update { it.copy(freshBusy = true, similarBusy = true, unreachable = false, freshDone = 0, freshOf = 0) }
            val fresh = discovery.newFrom(artists) { found, done, of ->
                _home.update { it.copy(fresh = found, freshDone = done, freshOf = of) }
            }
            // Nothing at all usually means nothing could be asked, not that
            // nobody released anything; only then is it worth checking which.
            if (fresh.isEmpty() && !discovery.reachable()) {
                _home.value = HomeState(unreachable = true, looked = true)
                return@launch
            }
            _home.update { it.copy(fresh = fresh, freshBusy = false, looked = true) }
            val similar = discovery.similarTo(artists)
            _home.update { it.copy(similar = similar, similarBusy = false) }
        }
    }

    /** Asks again for whatever the page on top couldn't load, and for the home page's lists. */
    fun refresh() {
        if (_genres.value.isEmpty()) loadGenres()
        when (val page = _stack.value.last()) {
            DiscoverPage.Home -> loadHome()
            is DiscoverPage.Genres -> Unit
            is DiscoverPage.Genre -> loadGenre(page.name)
            is DiscoverPage.Artist -> loadArtist(page.artist)
            is DiscoverPage.Release -> loadRelease(page.release)
            DiscoverPage.Charts -> loadChart(_chart.value.genreId)
        }
    }

    // ---- Moving about --------------------------------------------------------------

    private fun push(page: DiscoverPage) {
        if (_stack.value.last() == page) return
        _stack.value = _stack.value + page
    }

    /** One page back. False when already on the first page. */
    fun back(): Boolean {
        val pages = _stack.value
        if (pages.size <= 1) return false
        _stack.value = pages.dropLast(1)
        return true
    }

    fun home() {
        _stack.value = listOf(DiscoverPage.Home)
    }

    fun openGenres(filter: String = "") = push(DiscoverPage.Genres(filter))

    fun openGenre(name: String) {
        val genre = name.trim().lowercase(Locale.ROOT)
        if (genre.isEmpty()) return
        push(DiscoverPage.Genre(genre))
        if (_genrePages.value[genre]?.page == null) loadGenre(genre)
    }

    private val _surprising = MutableStateFlow(false)

    /** True while a random genre is being looked for. */
    val surprising: StateFlow<Boolean> = _surprising.asStateFlow()

    /**
     * A genre picked at random, which is half of what the page is for. The
     * list has genres nobody has been filed under yet; those are passed over,
     * so the surprise is always somewhere with something to hear.
     */
    fun surprise() {
        val all = _genres.value
        if (all.isEmpty() || _surprising.value) return
        scope.launch {
            _surprising.value = true
            try {
                var genre = all.random()
                repeat(SURPRISE_TRIES) {
                    // Null is the map not answering; the genre's own page says so.
                    val page = discovery.genre(genre) ?: return@repeat
                    if (page.artists.isNotEmpty()) {
                        setGenre(genre) { GenreState(page = page, loading = false) }
                        push(DiscoverPage.Genre(genre))
                        return@launch
                    }
                    genre = all.random()
                }
                openGenre(genre)
            } finally {
                _surprising.value = false
            }
        }
    }

    fun openArtist(artist: DiscoverArtist) {
        push(DiscoverPage.Artist(artist))
        if (_artistPages.value[artistKey(artist)]?.page == null) loadArtist(artist)
    }

    fun openRelease(release: DiscoverRelease) {
        push(DiscoverPage.Release(release))
        if (_releasePages.value[release.id]?.tracks.isNullOrEmpty()) loadRelease(release)
    }

    fun openCharts() {
        push(DiscoverPage.Charts)
        if (_chart.value.tracks.isEmpty()) loadChart(_chart.value.genreId)
    }

    // ---- Genres --------------------------------------------------------------------

    private fun loadGenre(genre: String) {
        scope.launch {
            setGenre(genre) { GenreState(loading = true) }
            val page = discovery.genre(genre)
            setGenre(genre) {
                when {
                    page == null -> GenreState(
                        loading = false,
                        problem = "The genre map couldn't be reached. Check the connection and try again."
                    )
                    else -> GenreState(page = page, loading = false)
                }
            }
        }
    }

    /** The next hundred artists of a genre, added below the ones already shown. */
    fun moreOfGenre(genre: String) {
        val state = _genrePages.value[genre] ?: return
        val shown = state.page ?: return
        if (state.loadingMore) return
        scope.launch {
            setGenre(genre) { it.copy(loadingMore = true) }
            val next = discovery.genre(genre, offset = state.asked)
            setGenre(genre) { now ->
                val have = shown.artists.map { it.name }.toHashSet()
                now.copy(
                    loadingMore = false,
                    // Counted only when it answered, so a failed try can be tried again.
                    asked = if (next == null) now.asked else now.asked + 100,
                    page = shown.copy(artists = shown.artists + next?.artists.orEmpty().filter { it.name !in have })
                )
            }
        }
    }

    /** What a genre's artists have released in the last year. */
    fun loadFreshIn(genre: String) {
        val state = _genrePages.value[genre] ?: return
        val page = state.page ?: return
        if (state.freshBusy) return
        scope.launch {
            setGenre(genre) { it.copy(freshBusy = true, fresh = emptyList(), freshDone = 0, freshOf = 0) }
            val names = page.artists.take(GENRE_FRESH_ARTISTS).map { it.name }
            val fresh = discovery.newFrom(names, days = 365, limit = GENRE_FRESH_ARTISTS) { found, done, of ->
                setGenre(genre) { it.copy(fresh = found, freshDone = done, freshOf = of) }
            }
            setGenre(genre) { it.copy(fresh = fresh, freshBusy = false) }
        }
    }

    private fun setGenre(genre: String, change: (GenreState) -> GenreState) {
        _genrePages.update { it + (genre to change(it[genre] ?: GenreState())) }
    }

    // ---- Artists -------------------------------------------------------------------

    private fun loadArtist(artist: DiscoverArtist) {
        val key = artistKey(artist)
        scope.launch {
            _artistPages.update { it + (key to ArtistState(loading = true)) }
            val page = discovery.artist(artist)
            val state = when {
                page == null -> ArtistState(
                    loading = false,
                    problem = if (discovery.reachable()) {
                        "Neither Deezer nor MusicBrainz knows an artist by this name. YouTube may."
                    } else {
                        UNREACHABLE
                    }
                )
                page.artist.deezerId == null -> ArtistState(
                    page = page,
                    loading = false,
                    note = if (discovery.reachable()) {
                        "Deezer doesn't list this artist, so there is nothing to play here. YouTube may have them."
                    } else {
                        UNREACHABLE
                    }
                )
                else -> ArtistState(page = page, loading = false)
            }
            _artistPages.update { it + (key to state) }
        }
    }

    /**
     * The clip an artist is heard by, for the preview player: the address of
     * their most played song. Which song it was is kept for the row to show.
     */
    suspend fun sample(artist: DiscoverArtist): String? {
        val key = sampleKey(artist)
        // Asked for each time: the clip's address stops working after a couple of hours.
        val track = discovery.sample(artist) ?: return null
        _samples.update { it + (key to track) }
        return track.previewUrl
    }

    /**
     * The clip a genre is heard by: a song from one of the artists it is most
     * of. One of the first few at random, so asking again gives another taste.
     */
    suspend fun sampleGenre(genre: String): String? {
        val artists = (_genrePages.value[genre]?.page ?: discovery.genre(genre))?.artists.orEmpty()
        for (artist in artists.take(GENRE_SAMPLE_ARTISTS).shuffled()) {
            val track = discovery.sample(artist) ?: continue
            _samples.update { it + (genreSampleKey(genre) to track) }
            return track.previewUrl
        }
        return null
    }

    // ---- Releases and charts -------------------------------------------------------

    private fun loadRelease(release: DiscoverRelease) {
        scope.launch {
            _releasePages.update { it + (release.id to ReleaseState(loading = true)) }
            val tracks = discovery.tracks(release)
            _releasePages.update {
                it + (
                    release.id to ReleaseState(
                        tracks = tracks,
                        loading = false,
                        problem = if (tracks.isEmpty()) "The songs on this release couldn't be loaded." else null
                    )
                )
            }
        }
    }

    fun loadChart(genreId: Long) {
        chartJob?.cancel()
        chartJob = scope.launch {
            _chart.update { it.copy(genreId = genreId, loading = true, problem = null, tracks = emptyList()) }
            val genres = _chart.value.genres.ifEmpty { discovery.chartGenres() }
            val tracks = discovery.chart(genreId)
            _chart.update {
                it.copy(
                    genres = genres,
                    tracks = tracks,
                    loading = false,
                    problem = when {
                        tracks.isNotEmpty() -> null
                        discovery.reachable() -> "Deezer has no chart for this one right now."
                        else -> UNREACHABLE
                    }
                )
            }
        }
    }

    // ---- Search --------------------------------------------------------------------

    /**
     * Genres are matched as each letter is typed, from the list already here.
     * Artists are asked for once the typing pauses.
     */
    fun setQuery(text: String) {
        _search.update { it.copy(query = text, searching = text.trim().length >= 2) }
        searchJob?.cancel()
        val query = text.trim()
        if (query.length < 2) {
            _search.update { it.copy(artists = emptyList(), searching = false) }
            return
        }
        searchJob = scope.launch {
            delay(450)
            val artists = discovery.searchArtists(query)
            _search.update { it.copy(artists = artists, searching = false) }
        }
    }

    companion object {
        /** How many random genres are tried before one with nobody in it is shown anyway. */
        private const val SURPRISE_TRIES = 5

        /** How many of a genre's artists are checked for new releases. */
        private const val GENRE_FRESH_ARTISTS = 40

        private const val UNREACHABLE =
            "Deezer couldn't be reached, so there are no songs to show. It is blocked in some " +
                "countries; with a VPN on, try again."

        /** What an artist's page is kept under: Deezer's id where there is one, the name otherwise. */
        fun artistKey(artist: DiscoverArtist): String =
            artist.deezerId?.let { "id:$it" } ?: ("name:" + Discovery.plain(artist.name))

        /** The preview player's name for an artist's sample. */
        fun sampleKey(artist: DiscoverArtist): String = "artist:" + Discovery.plain(artist.name)

        /** The preview player's name for a genre's sample. */
        fun genreSampleKey(genre: String): String = "genre:$genre"

        /** How many of a genre's first artists its sample is drawn from. */
        private const val GENRE_SAMPLE_ARTISTS = 6

        /** The preview player's name for a song's own clip. */
        fun previewKey(track: DiscoverTrack): String = "dz:${track.id}"
    }
}
