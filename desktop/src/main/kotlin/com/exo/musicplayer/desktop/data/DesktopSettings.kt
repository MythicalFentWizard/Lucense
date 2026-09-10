package com.exo.musicplayer.desktop.data

import java.io.File
import java.util.prefs.Preferences

/**
 * Where Resonate keeps its own files on Windows.
 *
 * `%LOCALAPPDATA%\Resonate` — the correct home for a per-user cache and
 * database on this platform, and specifically not next to the executable, which
 * is normally under Program Files and not writable.
 */
object AppDirs {
    val root: File by lazy {
        val local = System.getenv("LOCALAPPDATA")
            ?: System.getProperty("user.home") + File.separator + ".resonate"
        File(local, "Resonate").also { it.mkdirs() }
    }

    val covers: File by lazy { File(root, "covers").also { it.mkdirs() } }
    val tools: File by lazy { File(root, "tools").also { it.mkdirs() } }
    val downloads: File by lazy { File(root, "downloads").also { it.mkdirs() } }
    val database: File by lazy { File(root, "resonate.db") }
}

/** Small key/value settings, in the Windows registry via java.util.prefs. */
class DesktopSettings {

    private val prefs: Preferences = Preferences.userRoot().node("com/exo/musicplayer")

    var folders: List<String>
        get() = prefs.get(KEY_FOLDERS, "").split('|').filter { it.isNotBlank() }
        set(value) = prefs.put(KEY_FOLDERS, value.joinToString("|"))

    var outputs: List<String>
        get() = prefs.get(KEY_OUTPUTS, "").split('|').filter { it.isNotBlank() }
        set(value) = prefs.put(KEY_OUTPUTS, value.joinToString("|"))

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, 0.85f)
        set(value) = prefs.putFloat(KEY_VOLUME, value)

    var accentName: String
        get() = prefs.get(KEY_ACCENT, "Amethyst")
        set(value) = prefs.put(KEY_ACCENT, value)

    var downloadDir: String
        get() = prefs.get(KEY_DOWNLOAD_DIR, AppDirs.downloads.absolutePath)
        set(value) = prefs.put(KEY_DOWNLOAD_DIR, value)

    /** Blank means "work it out from the IP address". */
    var weatherPlace: String
        get() = prefs.get(KEY_WEATHER_PLACE, "")
        set(value) = prefs.put(KEY_WEATHER_PLACE, value)

    var weatherLatitude: Double
        get() = prefs.getDouble(KEY_WEATHER_LAT, Double.NaN)
        set(value) = prefs.putDouble(KEY_WEATHER_LAT, value)

    var weatherLongitude: Double
        get() = prefs.getDouble(KEY_WEATHER_LON, Double.NaN)
        set(value) = prefs.putDouble(KEY_WEATHER_LON, value)

    var downloadQualityName: String
        get() = prefs.get(KEY_DOWNLOAD_QUALITY, "MEDIUM")
        set(value) = prefs.put(KEY_DOWNLOAD_QUALITY, value)

    var duckEnabled: Boolean
        get() = prefs.getBoolean(KEY_DUCK_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_DUCK_ENABLED, value)

    var duckKeyName: String
        get() = prefs.get(KEY_DUCK_KEY, "F9")
        set(value) = prefs.put(KEY_DUCK_KEY, value)

    /** How far the volume drops while ducked, as a percentage. */
    var duckPercent: Int
        get() = prefs.getInt(KEY_DUCK_PERCENT, 70)
        set(value) = prefs.putInt(KEY_DUCK_PERCENT, value.coerceIn(10, 100))

    var writeTagsOnIdentify: Boolean
        get() = prefs.getBoolean(KEY_WRITE_TAGS, true)
        set(value) = prefs.putBoolean(KEY_WRITE_TAGS, value)

    private companion object {
        const val KEY_FOLDERS = "library_folders"
        const val KEY_OUTPUTS = "audio_outputs"
        const val KEY_VOLUME = "volume"
        const val KEY_ACCENT = "accent"
        const val KEY_DOWNLOAD_DIR = "download_dir"
        const val KEY_WEATHER_PLACE = "weather_place"
        const val KEY_WEATHER_LAT = "weather_lat"
        const val KEY_WEATHER_LON = "weather_lon"
        const val KEY_WRITE_TAGS = "write_tags_on_identify"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_DUCK_ENABLED = "duck_enabled"
        const val KEY_DUCK_KEY = "duck_key"
        const val KEY_DUCK_PERCENT = "duck_percent"
    }
}
