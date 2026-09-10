package com.exo.musicplayer.data.weather

import com.exo.musicplayer.data.model.TrackPlayCount

/**
 * How strongly a track is associated with one weather condition.
 *
 * @param lift how much more often this track is played in the condition than the
 *   track's own baseline would predict. 1.0 means "no relationship"; 2.0 means
 *   "twice as likely as chance".
 */
data class Affinity(
    val trackId: Long,
    val playsInCondition: Int,
    val totalPlays: Int,
    val lift: Double
)

/**
 * Scores tracks against a weather condition.
 *
 * The naive approach -- count plays while it rained and sort -- just returns your
 * most-played songs, because a song you play constantly will top every condition.
 * What actually matters is whether a song is played *disproportionately* in that
 * weather, so this compares each track's rate against the global base rate:
 *
 *     baseline p_c = plays in condition c / all plays with weather
 *     track rate   = plays of t in c / plays of t          (smoothed)
 *     lift         = track rate / p_c
 *
 * Smoothing pulls sparse tracks toward the baseline, so a song played once, in
 * one rain shower, does not outrank a song played thirty times of which twenty
 * were rainy. [MIN_PLAYS_IN_CONDITION] then discards outright coincidences.
 */
object WeatherAffinity {

    /** Pseudo-observations of "average behaviour" mixed into every track. */
    private const val SMOOTHING = 3.0

    /** Below this, one coincidence looks like a preference. */
    const val MIN_PLAYS_IN_CONDITION = 2

    /** Ignore relationships weaker than this; 1.0 would be pure chance. */
    private const val MIN_LIFT = 1.15

    /** Total weather-tagged plays before the feature says anything at all. */
    const val MIN_HISTORY = 12

    fun score(
        conditionCounts: List<TrackPlayCount>,
        totalCounts: List<TrackPlayCount>,
        totalPlaysInCondition: Int,
        totalPlaysWithWeather: Int
    ): List<Affinity> {
        if (totalPlaysWithWeather < MIN_HISTORY) return emptyList()
        if (totalPlaysInCondition <= 0) return emptyList()

        val baseline = totalPlaysInCondition.toDouble() / totalPlaysWithWeather
        if (baseline <= 0.0 || baseline >= 1.0) return emptyList()

        val totalsByTrack = totalCounts.associate { it.trackId to it.plays }

        return conditionCounts.mapNotNull { row ->
            if (row.plays < MIN_PLAYS_IN_CONDITION) return@mapNotNull null
            val total = totalsByTrack[row.trackId] ?: return@mapNotNull null
            if (total <= 0) return@mapNotNull null

            val smoothedRate = (row.plays + SMOOTHING * baseline) / (total + SMOOTHING)
            val lift = smoothedRate / baseline
            if (lift < MIN_LIFT) return@mapNotNull null

            Affinity(
                trackId = row.trackId,
                playsInCondition = row.plays,
                totalPlays = total,
                lift = lift
            )
        }.sortedWith(
            compareByDescending<Affinity> { it.lift }.thenByDescending { it.playsInCondition }
        )
    }
}
