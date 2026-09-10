package com.exo.musicplayer.util

import java.util.Locale

/** Formats a millisecond duration as m:ss, or h:mm:ss past an hour. */
fun Long.asDuration(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
