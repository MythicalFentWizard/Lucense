package com.exo.musicplayer.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Current conditions from Open-Meteo.
 *
 * Uses HttpURLConnection and org.json rather than pulling in Retrofit/OkHttp:
 * it is one request with four fields, and the app is built on a connection where
 * every extra artifact costs real minutes.
 *
 * Open-Meteo needs no API key. Location comes from the platform LocationManager's
 * last known fix, which avoids a dependency on Google Play Services and never
 * powers up the GPS on its own.
 */
class WeatherRepository(private val context: Context) {

    private val _current = MutableStateFlow<WeatherSnapshot?>(null)
    val current: StateFlow<WeatherSnapshot?> = _current.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var lastFetchAt = 0L

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Cached reading, refreshed at most every [CACHE_MS]. */
    suspend fun currentWeather(force: Boolean = false): WeatherSnapshot? {
        val cached = _current.value
        val fresh = cached != null &&
            System.currentTimeMillis() - lastFetchAt < CACHE_MS
        if (fresh && !force) return cached
        return refresh()
    }

    suspend fun refresh(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            _available.value = false
            return@withContext null
        }
        val location = lastKnownLocation()
        if (location == null) {
            _available.value = false
            return@withContext null
        }
        val snapshot = runCatching { fetch(location.latitude, location.longitude) }
            .onFailure { Log.w(TAG, "Weather fetch failed", it) }
            .getOrNull()

        if (snapshot != null) {
            _current.value = snapshot
            lastFetchAt = System.currentTimeMillis()
            _available.value = true
        }
        snapshot
    }

    private fun lastKnownLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            val fix = runCatching {
                @Suppress("MissingPermission")
                manager.getLastKnownLocation(provider)
            }.getOrNull() ?: continue
            if (best == null || fix.time > best.time) best = fix
        }
        return best
    }

    private fun fetch(latitude: Double, longitude: Double): WeatherSnapshot? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,is_day"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Weather HTTP ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val code = current.optInt("weather_code", -1)
            if (code < 0) return null
            WeatherSnapshot(
                condition = WeatherCondition.fromWmoCode(code),
                wmoCode = code,
                temperatureC = current.optDouble("temperature_2m", Double.NaN),
                isDay = current.optInt("is_day", 1) == 1,
                observedAt = System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val CACHE_MS = 30 * 60 * 1000L
    }
}
