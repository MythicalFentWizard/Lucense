package com.exo.musicplayer.data.weather

/**
 * Weather buckets used for matching songs.
 *
 * Deliberately coarse. Open-Meteo reports ~28 distinct WMO codes, but splitting
 * listening history 28 ways would leave every bucket too sparse to say anything.
 * Drizzle and showers fold into RAIN for the same reason.
 */
enum class WeatherCondition(val label: String, val emoji: String) {
    CLEAR("Clear", "☀️"),
    CLOUDY("Cloudy", "☁️"),
    FOG("Fog", "🌫️"),
    RAIN("Rain", "🌧️"),
    SNOW("Snow", "❄️"),
    THUNDERSTORM("Storm", "⛈️");

    companion object {
        /** Maps a WMO weather interpretation code to a bucket. */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2, 3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> RAIN      // drizzle
            61, 63, 65, 66, 67 -> RAIN      // rain
            80, 81, 82 -> RAIN              // rain showers
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> CLOUDY
        }

        fun fromName(name: String?): WeatherCondition? =
            entries.firstOrNull { it.name == name }
    }
}

/** A weather reading at a point in time. */
data class WeatherSnapshot(
    val condition: WeatherCondition,
    val wmoCode: Int,
    val temperatureC: Double,
    val isDay: Boolean,
    val observedAt: Long
)
