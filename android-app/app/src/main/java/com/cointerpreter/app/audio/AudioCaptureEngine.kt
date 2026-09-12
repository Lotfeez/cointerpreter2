package com.cointerpreter.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Captures microphone audio as 24kHz mono PCM16 -- the exact format
 * documented for OpenAI Realtime `input_audio_buffer.append` frames
 * (`audio/pcm`, rate 24000). Using this sample rate at capture time avoids
 * an unnecessary resample step (spec §11: "avoid unnecessary audio
 * transcoding").
 */
class AudioCaptureEngine {

    companion object {
        const val SAMPLE_RATE_HZ = 24_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** ~20ms frames: small enough for low latency, large enough to avoid excessive WebSocket overhead. */
        private const val FRAME_DURATION_MS = 20
        private const val BYTES_PER_SAMPLE = 2
        val FRAME_SIZE_BYTES = (SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000) * BYTES_PER_SAMPLE
    }

    @Volatile
    private var muted = false

    fun setMuted(value: Boolean) {
        muted = value
    }

    /**
     * Emits raw PCM16 frames while collected. Cancelling the flow releases
     * the [AudioRecord] instance -- callers (the session controller) must
     * not hold a second concurrent capture flow, which is enforced by the
     * state machine only allowing capture while [com.cointerpreter.app.state.SessionStateMachine.isSessionActive].
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun capture(): Flow<ByteArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, ENCODING)
        if (minBufferSize <= 0) {
            close(IllegalStateException("Device does not support 24kHz mono PCM16 capture"))
            return@callbackFlow
        }

        val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            ENCODING,
            bufferSize,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        audioRecord.startRecording()

        val readBuffer = ByteArray(FRAME_SIZE_BYTES)
        var running = true
        val thread = Thread {
            while (running) {
                val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (read > 0 && !muted) {
                    trySend(readBuffer.copyOf(read))
                }
            }
        }
        thread.name = "CoInterpreter-AudioCapture"
        thread.start()

        awaitClose {
            running = false
            thread.join(500)
            audioRecord.stop()
            audioRecord.release()
        }
    }.flowOn(Dispatchers.Default)
}
