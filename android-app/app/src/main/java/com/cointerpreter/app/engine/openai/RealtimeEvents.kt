package com.cointerpreter.app.engine.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire types for the OpenAI Realtime GA protocol, as documented at
 * developers.openai.com in September 2026 (see ARCHITECTURE.md
 * "Documentation assumptions" for the exact pages consulted). Event names
 * and field shapes here are taken directly from official docs; nothing is
 * invented. Unknown/future event types are tolerated via [RealtimeEnvelope]
 * falling back to a raw [JsonElement] payload rather than failing to parse.
 */

/** Minimal envelope used to read `type` before dispatching to a typed event. */
@Serializable
data class RealtimeEnvelopeProbe(val type: String)

// ---------------------------------------------------------------------
// Client -> server events
// ---------------------------------------------------------------------

@Serializable
data class SessionUpdateEvent(
    val type: String = "session.update",
    val session: SessionUpdatePayload,
)

@Serializable
data class SessionUpdatePayload(
    /** "realtime" for a standard gpt-realtime session; omitted for the dedicated translation endpoint. */
    val type: String? = null,
    val model: String? = null,
    val instructions: String? = null,
    val voice: String? = null,
    val audio: AudioConfig? = null,
    @SerialName("turn_detection") val turnDetection: TurnDetectionConfig? = null,
)

@Serializable
data class AudioConfig(
    val input: AudioIoConfig? = null,
    val output: AudioIoConfig? = null,
)

@Serializable
data class AudioIoConfig(
    val format: AudioFormat? = null,
    /** ISO-639-1 target language code, used by gpt-realtime-translate's `audio.output.language`. */
    val language: String? = null,
    val transcription: TranscriptionConfig? = null,
)

@Serializable
data class AudioFormat(
    val type: String = "audio/pcm",
    val rate: Int = 24_000,
)

@Serializable
data class TranscriptionConfig(
    val model: String = "gpt-realtime-whisper",
    val language: String? = null,
)

@Serializable
data class TurnDetectionConfig(
    val type: String = "server_vad",
    @SerialName("prefix_padding_ms") val prefixPaddingMs: Int = 300,
    @SerialName("silence_duration_ms") val silenceDurationMs: Int = 500,
    val threshold: Double = 0.5,
)

@Serializable
data class InputAudioBufferAppendEvent(
    val type: String = "input_audio_buffer.append",
    val audio: String, // base64-encoded PCM16
)

@Serializable
data class InputAudioBufferCommitEvent(
    val type: String = "input_audio_buffer.commit",
)

@Serializable
data class ResponseCreateEvent(
    val type: String = "response.create",
)

@Serializable
data class ResponseCancelEvent(
    val type: String = "response.cancel",
)

/** Truncates an in-progress or already-played assistant audio item on barge-in. */
@Serializable
data class ConversationItemTruncateEvent(
    val type: String = "conversation.item.truncate",
    @SerialName("item_id") val itemId: String,
    @SerialName("content_index") val contentIndex: Int = 0,
    @SerialName("audio_end_ms") val audioEndMs: Int,
)

// ---------------------------------------------------------------------
// Server -> client events (only the subset CoInterpreter consumes)
// ---------------------------------------------------------------------

@Serializable
data class ServerErrorEvent(
    val type: String = "error",
    val error: ServerErrorDetail,
)

@Serializable
data class ServerErrorDetail(
    val type: String? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class InputAudioBufferSpeechStartedEvent(
    val type: String = "input_audio_buffer.speech_started",
)

@Serializable
data class InputAudioBufferSpeechStoppedEvent(
    val type: String = "input_audio_buffer.speech_stopped",
)

@Serializable
data class ResponseOutputAudioDeltaEvent(
    val type: String = "response.output_audio.delta",
    val delta: String, // base64 PCM16
    @SerialName("item_id") val itemId: String? = null,
)

@Serializable
data class ResponseOutputAudioDoneEvent(
    val type: String = "response.output_audio.done",
    @SerialName("item_id") val itemId: String? = null,
)

@Serializable
data class ResponseOutputAudioTranscriptDeltaEvent(
    val type: String = "response.output_audio_transcript.delta",
    val delta: String,
)

@Serializable
data class ResponseOutputAudioTranscriptDoneEvent(
    val type: String = "response.output_audio_transcript.done",
    val transcript: String,
)

@Serializable
data class ConversationItemInputAudioTranscriptionDeltaEvent(
    val type: String = "conversation.item.input_audio_transcription.delta",
    val delta: String,
)

@Serializable
data class ConversationItemInputAudioTranscriptionCompletedEvent(
    val type: String = "conversation.item.input_audio_transcription.completed",
    val transcript: String,
)

@Serializable
data class ResponseDoneEvent(
    val type: String = "response.done",
)

@Serializable
data class SessionCreatedEvent(
    val type: String = "session.created",
)
