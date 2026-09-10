package com.exo.musicplayer.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThemeState(
    val palette: AppPalette = AppPalette.MIDNIGHT,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val stars: Boolean = true
)

/**
 * Appearance preferences.
 *
 * Kept in SharedPreferences and mirrored into a StateFlow so the whole UI
 * recomposes the moment a palette is tapped — a theme picker that only takes
 * effect after a restart is a bad theme picker.
 */
class ThemeSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ThemeState(
            palette = AppPalette.fromName(prefs.getString(KEY_PALETTE, null)),
            mode = ThemeMode.fromName(prefs.getString(KEY_MODE, null)),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
            stars = prefs.getBoolean(
                KEY_STARS,
                AppPalette.fromName(prefs.getString(KEY_PALETTE, null)).starsByDefault
            )
        )
    )
    val state: StateFlow<ThemeState> = _state.asStateFlow()

    fun setPalette(palette: AppPalette) {
        // Switching to a palette the user has never tuned adopts that palette's
        // own recommendation for stars; an explicit choice is respected below.
        val starsTouched = prefs.contains(KEY_STARS)
        val stars = if (starsTouched) _state.value.stars else palette.starsByDefault
        prefs.edit().putString(KEY_PALETTE, palette.name).apply()
        _state.value = _state.value.copy(palette = palette, stars = stars, dynamicColor = false)
        prefs.edit().putBoolean(KEY_DYNAMIC, false).apply()
    }

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _state.value = _state.value.copy(mode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _state.value = _state.value.copy(dynamicColor = enabled)
    }

    fun setStars(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STARS, enabled).apply()
        _state.value = _state.value.copy(stars = enabled)
    }

    private companion object {
        const val KEY_PALETTE = "palette"
        const val KEY_MODE = "mode"
        const val KEY_DYNAMIC = "dynamic"
        const val KEY_STARS = "stars"
    }
}
