package com.exo.musicplayer.data.library

/** Lists worked out from the library and what has been played, rather than kept. */
enum class AutoPlaylist(val label: String, val note: String) {
    RECENT("Recently added", "The newest files in the library."),
    HISTORY("Recently played", "What you actually listened to, most recent first."),
    MOST_PLAYED("Most played", "What you keep coming back to."),
    NEVER_PLAYED("Never played", "In the library, never started."),
    TOP_RATED("Top rated", "Four stars and up, best first."),
    FAVOURITES("Favourites", "Everything you hearted.");

    companion object {
        /** Recently added and Most played stop here, as they do on Windows. */
        const val CAP = 60
    }
}
