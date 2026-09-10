package com.exo.musicplayer.data.weather

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** A place to fetch weather for. */
data class GeoPlace(
    val name: String,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val display: String get() = if (country.isNullOrBlank()) name else "$name, $country"
}

/**
 * Open-Meteo: current conditions, place search, and approximate location.
 *
 * No API key on any of the three endpoints. Kept in the shared module because
 * none of it is platform-specific — Android supplies coordinates from
 * LocationManager, Windows from [locateByIp] or a place the user picked.
 */
object OpenMeteo {

    suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val body = Http.get(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day"
            ) ?: return@withContext null

            val current = runCatching { JSONObject(body).optJSONObject("current") }.getOrNull()
                ?: return@withContext null
            val code = current.optInt("weather_code", -1)
            if (code < 0) return@withContext null

            WeatherSnapshot(
                condition = WeatherCondition.fromWmoCode(code),
                wmoCode = code,
                temperatureC = current.optDouble("temperature_2m", Double.NaN),
                isDay = current.optInt("is_day", 1) == 1,
                observedAt = System.currentTimeMillis()
            )
        }

    /** Free-text place search, so a city name can stand in for a GPS fix. */
    suspend fun geocode(query: String, limit: Int = 8): List<GeoPlace> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val body = Http.get(
                "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=${URLEncoder.encode(query, "UTF-8")}&count=$limit&language=en&format=json"
            ) ?: return@withContext emptyList()

            val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
                ?: return@withContext emptyList()

            (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                GeoPlace(
                    name = listOfNotNull(
                        name,
                        item.optString("admin1").takeIf { it.isNotBlank() && it != name }
                    ).joinToString(", "),
                    country = item.optString("country").takeIf { it.isNotBlank() },
                    latitude = item.optDouble("latitude", Double.NaN),
                    longitude = item.optDouble("longitude", Double.NaN)
                ).takeIf { !it.latitude.isNaN() && !it.longitude.isNaN() }
            }
        }

    /**
     * City-level location from the public IP.
     *
     * The desktop replacement for GPS. City-level is the right resolution here
     * anyway: the feature buckets weather into six conditions, and a fix good to
     * a few kilometres would not change which bucket the sky is in. Behind a VPN
     * it reports the exit node, which is why the setting stays user-overridable.
     */
    suspend fun locateByIp(): GeoPlace? = withContext(Dispatchers.IO) {
        val body = Http.get("http://ip-api.com/json/?fields=status,city,country,lat,lon")
            ?: return@withContext null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        if (root.optString("status") != "success") return@withContext null

        val latitude = root.optDouble("lat", Double.NaN)
        val longitude = root.optDouble("lon", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return@withContext null

        GeoPlace(
            name = root.optString("city").takeIf { it.isNotBlank() } ?: "Current location",
            country = root.optString("country").takeIf { it.isNotBlank() },
            latitude = latitude,
            longitude = longitude
        )
    }
}
