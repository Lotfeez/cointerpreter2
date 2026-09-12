package com.cointerpreter.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Requests audio focus, selects the best available communication device,
 * and reacts to device changes (spec §11, §13). Prefers a wired or
 * Bluetooth headset when present, since a shared mic+speaker on the same
 * phone is the hardest case for echo cancellation; falls back to the
 * earpiece/speaker with `MODE_IN_COMMUNICATION`, which enables the
 * platform's built-in acoustic echo cancellation (AEC) and noise
 * suppression on devices that support it. CoInterpreter does not claim
 * perfect echo cancellation on speakerphone (see PRIVACY.md / README
 * troubleshooting): documented as a real hardware/API limitation rather
 * than pretended away.
 */
class AudioRouteManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: android.media.AudioFocusRequest? = null

    fun acquireFocusAndRoute() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val preferred = preferredCommunicationDevice()
            if (preferred != null) audioManager.setCommunicationDevice(preferred)
        } else {
            @Suppress("DEPRECATION")
            if (hasBluetoothScoDevice()) {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = !hasWiredHeadset()
            }
        }

        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    fun release() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun preferredCommunicationDevice(): AudioDeviceInfo? {
        val devices = audioManager.availableCommunicationDevices
        val priority = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
        for (type in priority) {
            devices.firstOrNull { it.type == type }?.let { return it }
        }
        return devices.firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun hasBluetoothScoDevice(): Boolean = audioManager.isBluetoothScoAvailableOffCall

    @Suppress("DEPRECATION")
    private fun hasWiredHeadset(): Boolean = audioManager.isWiredHeadsetOn
}
