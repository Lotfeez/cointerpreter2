package com.cointerpreter.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Streams received PCM16 24kHz mono audio to the speaker/earpiece/Bluetooth
 * route via a single long-lived [AudioTrack] in streaming mode, so response
 * audio starts playing as soon as the first chunk arrives rather than
 * waiting for a complete utterance (spec §11 low-latency playback).
 *
 * [truncateAndFlush] backs barge-in (spec §12): when the user starts
 * speaking again, the session controller calls this to stop stale audio
 * immediately, in the same beat that it sends `response.cancel` /
 * `conversation.item.truncate` to the engine, keeping local playback state
 * and server conversation state synchronized.
 */
class AudioPlaybackEngine {

    companion object {
        private const val SAMPLE_RATE_HZ = 24_000
    }

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var muted = false

    fun setMuted(value: Boolean) {
        muted = value
        if (value) truncateAndFlush()
    }

    fun start() {
        if (audioTrack != null) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufferSize, 4096) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()
    }

    fun write(pcm16: ByteArray) {
        if (muted) return
        audioTrack?.write(pcm16, 0, pcm16.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    /** Immediately discards any buffered-but-unplayed audio (barge-in / stop). */
    fun truncateAndFlush() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun stop() {
        audioTrack?.let {
            it.pause()
            it.flush()
            it.stop()
            it.release()
        }
        audioTrack = null
    }
}
