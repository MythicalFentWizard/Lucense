package com.exo.musicplayer.data.model

/** How many qualifying plays a track has. Shared so scoring can live off-Android. */
data class TrackPlayCount(val trackId: Long, val plays: Int)
