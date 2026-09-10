package com.exo.musicplayer.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One thing the phone can play sound out of. */
data class AudioOutput(
    /** Stable across reconnects; the raw device id is not. */
    val key: String,
    val deviceId: Int,
    val name: String,
    val type: Int
) {
    val isSpeaker: Boolean get() = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}

/**
 * Enumerates audio outputs and remembers which the user picked.
 *
 * Device ids are reassigned every time something reconnects, so selections are
 * stored against a type+name key instead — otherwise unplugging headphones once
 * would silently drop them from the selection.
 */
class AudioOutputRepository(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs =
        appContext.getSharedPreferences("audio_output", Context.MODE_PRIVATE)

    private val _outputs = MutableStateFlow(currentOutputs())
    val outputs: StateFlow<List<AudioOutput>> = _outputs.asStateFlow()

    private val _selectedKeys = MutableStateFlow(
        prefs.getStringSet(KEY_SELECTED, emptySet())!!.toSet()
    )
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _mirrorEnabled = MutableStateFlow(prefs.getBoolean(KEY_MIRROR, false))
    val mirrorEnabled: StateFlow<Boolean> = _mirrorEnabled.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                    _outputs.value = currentOutputs()
                }

                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                    _outputs.value = currentOutputs()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    fun refresh() { _outputs.value = currentOutputs() }

    /** Picks one output, replacing any previous choice. */
    fun select(key: String) {
        val next = if (_selectedKeys.value == setOf(key)) emptySet() else setOf(key)
        _selectedKeys.value = next
        prefs.edit().putStringSet(KEY_SELECTED, next).apply()
    }

    fun toggle(key: String) {
        val next = _selectedKeys.value.toMutableSet()
        if (!next.remove(key)) next += key
        _selectedKeys.value = next
        prefs.edit().putStringSet(KEY_SELECTED, next).apply()
    }

    /** Empty selection means "whatever Android would do anyway". */
    fun clearSelection() {
        _selectedKeys.value = emptySet()
        prefs.edit().putStringSet(KEY_SELECTED, emptySet()).apply()
    }

    fun setMirrorEnabled(enabled: Boolean) {
        _mirrorEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MIRROR, enabled).apply()
    }

    /** Resolves stored keys back to live devices, dropping any not connected. */
    fun selectedDevices(): List<AudioDeviceInfo> {
        val keys = _selectedKeys.value
        if (keys.isEmpty()) return emptyList()
        return rawOutputs().filter { keyOf(it) in keys }
    }

    fun deviceFor(key: String): AudioDeviceInfo? = rawOutputs().firstOrNull { keyOf(it) == key }

    private fun currentOutputs(): List<AudioOutput> = rawOutputs()
        .map { AudioOutput(keyOf(it), it.id, nameOf(it), it.type) }
        .distinctBy { it.key }

    private fun rawOutputs(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in USABLE_TYPES }

    private fun keyOf(device: AudioDeviceInfo): String =
        "${device.type}|${device.productName?.toString().orEmpty()}"

    private fun nameOf(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.trim().orEmpty()
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> product.ifBlank { "USB audio" }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> product.ifBlank { "Bluetooth" }
            AudioDeviceInfo.TYPE_HEARING_AID -> product.ifBlank { "Hearing aid" }
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
            AudioDeviceInfo.TYPE_DOCK -> "Dock"
            else -> product.ifBlank { "Audio output" }
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_keys"
        const val KEY_MIRROR = "mirror_enabled"

        /** Earpiece and telephony sinks are deliberately excluded. */
        val USABLE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            add(AudioDeviceInfo.TYPE_HDMI)
            add(AudioDeviceInfo.TYPE_HDMI_ARC)
            add(AudioDeviceInfo.TYPE_DOCK)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
    }
}
