package com.exo.musicplayer.data.discover

/** An artist as Discover shows one: from a genre, from "similar to", or looked up by name. */
data class DiscoverArtist(
    val name: String,
    /** Deezer's id once the artist has been found there; null straight off a genre page. */
    val deezerId: Long? = null,
    val pictureUrl: String? = null,
    val fans: Int? = null,
    /** How much of this artist the genre is, 0 to 1, on a genre page. */
    val weight: Float? = null
)

data class DiscoverTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    /** A 30-second clip. Signed and short-lived, so never kept for long. */
    val previewUrl: String?,
    val coverUrl: String?,
    /** Deezer's popularity rank; higher is more played. */
    val rank: Int,
    val artistId: Long? = null
)

data class DiscoverRelease(
    val id: Long,
    val title: String,
    val artist: String,
    /** yyyy-mm-dd, or blank when Deezer has no date. */
    val date: String,
    /** "single", "album" or "ep". */
    val kind: String,
    val coverUrl: String?
)

/** One genre: who plays it, and which genres sit next to it. */
data class GenrePage(
    val genre: String,
    val artists: List<DiscoverArtist>,
    /** Genres the same artists are also tagged with, commonest first. */
    val nearby: List<String>,
    /** How many artists carry the tag in all. */
    val total: Int
)

data class ArtistPage(
    val artist: DiscoverArtist,
    val popular: List<DiscoverTrack>,
    val releases: List<DiscoverRelease>,
    val similar: List<DiscoverArtist>,
    val genres: List<String>
)
