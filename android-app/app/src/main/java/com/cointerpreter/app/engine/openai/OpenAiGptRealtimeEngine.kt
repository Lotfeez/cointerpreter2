package com.cointerpreter.app.engine.openai

import android.util.Base64
import com.cointerpreter.app.engine.EngineEvent
import com.cointerpreter.app.engine.EngineException
import com.cointerpreter.app.engine.EngineSessionConfig
import com.cointerpreter.app.engine.InterpreterConstitution
import com.cointerpreter.app.engine.RealtimeInterpreterEngine
import com.cointerpreter.app.model.GlossaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wraps the general-purpose `gpt-realtime` model on the standard Realtime
 * session endpoint (`wss://api.openai.com/v1/realtime?model=...`).
 *
 * This is the engine CoInterpreter uses for Professional Mode: it accepts
 * full custom `session.instructions`, which is where [InterpreterConstitution]
 * and the session glossary (spec §6, §25) are injected. It costs a little
 * naturalness and latency relative to [OpenAiRealtimeTranslateEngine] but is
 * the only current OpenAI realtime surface that lets CoInterpreter enforce
 * "never answer, only translate" and terminology preservation as an explicit,
 * editable instruction rather than a fixed model behavior.
 */
internal class OpenAiGptRealtimeEngine(
    private val modelId: String = "gpt-realtime",
) : RealtimeInterpreterEngine {

    override val engineId: String = modelId
    override val supportsCustomInstructions: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events

    private var transport: RealtimeWebSocketTransport? = null
    private var currentGlossary: List<GlossaryEntry> = emptyList()
    private var currentConfig: EngineSessionConfig? = null
    private var lastAssistantItemId: String? = null
    private var playedAudioMsSinceItemStart: Int = 0

    override suspend fun start(config: EngineSessionConfig) {
        currentConfig = config
        currentGlossary = config.glossary

        val url = "wss://api.openai.com/v1/realtime?model=$modelId"
        val t = RealtimeWebSocketTransport(url = url, ephemeralToken = config.ephemeralToken)
        transport = t

        t.connect()
            .onEach { raw -> handleServerMessage(raw, config) }
            .launchIn(scope)

        val instructions = InterpreterConstitution.buildInstructions(
            mode = config.mode,
            sourceLanguage = config.sourceLanguage,
            targetLanguage = config.targetLanguage,
            glossary = config.glossary,
        )

        val sessionUpdate = SessionUpdateEvent(
            session = SessionUpdatePayload(
                type = "realtime",
                model = modelId,
                instructions = instructions,
                voice = config.voice,
                audio = AudioConfig(
                    input = AudioIoConfig(format = AudioFormat(rate = 24_000)),
                    output = AudioIoConfig(format = AudioFormat(rate = 24_000)),
                ),
                turnDetection = TurnDetectionConfig(),
            ),
        )
        t.send(json.encodeToString(SessionUpdateEvent.serializer(), sessionUpdate))
    }

    private suspend fun handleServerMessage(raw: String, config: EngineSessionConfig) {
        val type = runCatching {
            json.parseToJsonElement(raw).jsonObjectOrNull()?.get("type")?.jsonPrimitive?.content
        }.getOrNull() ?: return

        when (type) {
            "session.created", "session.updated" -> _events.emit(EngineEvent.Connected)

            "input_audio_buffer.speech_started" -> _events.emit(EngineEvent.SpeechStarted)
            "input_audio_buffer.speech_stopped" -> _events.emit(EngineEvent.SpeechStopped)

            "conversation.item.input_audio_transcription.delta" -> {
                val delta = decode<ConversationItemInputAudioTranscriptionDeltaEvent>(raw)
                _events.emit(EngineEvent.PartialTranscript(delta.delta, isSource = true))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val done = decode<ConversationItemInputAudioTranscriptionCompletedEvent>(raw)
                _events.emit(EngineEvent.FinalTranscript(done.transcript, isSource = true))
            }

            "response.output_audio_transcript.delta" -> {
                val delta = decode<ResponseOutputAudioTranscriptDeltaEvent>(raw)
                _events.emit(EngineEvent.PartialTranscript(delta.delta, isSource = false))
            }
            "response.output_audio_transcript.done" -> {
                val done = decode<ResponseOutputAudioTranscriptDoneEvent>(raw)
                _events.emit(EngineEvent.FinalTranscript(done.transcript, isSource = false))
            }

            "response.output_audio.delta" -> {
                val delta = decode<ResponseOutputAudioDeltaEvent>(raw)
                lastAssistantItemId = delta.itemId ?: lastAssistantItemId
                if (playedAudioMsSinceItemStart == 0) _events.emit(EngineEvent.OutputAudioStarted)
                val bytes = Base64.decode(delta.delta, Base64.NO_WRAP)
                // 24kHz, 16-bit mono => 48,000 bytes/sec => track elapsed ms for truncate-on-barge-in.
                playedAudioMsSinceItemStart += (bytes.size * 1000) / 48_000
                _events.emit(EngineEvent.OutputAudioChunk(bytes))
            }
            "response.output_audio.done" -> {
                playedAudioMsSinceItemStart = 0
                _events.emit(EngineEvent.OutputAudioFinished)
            }
            "response.done" -> { /* no-op: output_audio.done already signaled end of playback */ }

            "error" -> {
                val err = decode<ServerErrorEvent>(raw)
                _events.emit(EngineEvent.Error(mapServerError(err)))
            }

            else -> { /* forward-compatible: ignore unrecognized GA event types */ }
        }
    }

    override suspend fun sendAudioChunk(pcm16: ByteArray) {
        transport?.sendAudio(pcm16)
    }

    override suspend fun commitAudioTurn() {
        transport?.send(json.encodeToString(InputAudioBufferCommitEvent.serializer(), InputAudioBufferCommitEvent()))
    }

    override suspend fun cancelResponse() {
        val t = transport ?: return
        t.send(json.encodeToString(ResponseCancelEvent.serializer(), ResponseCancelEvent()))
        val itemId = lastAssistantItemId
        if (itemId != null) {
            t.send(
                json.encodeToString(
                    ConversationItemTruncateEvent.serializer(),
                    ConversationItemTruncateEvent(itemId = itemId, audioEndMs = playedAudioMsSinceItemStart),
                )
            )
        }
        playedAudioMsSinceItemStart = 0
        _events.emit(EngineEvent.ResponseCancelled)
    }

    override suspend fun updateGlossary(glossary: List<GlossaryEntry>) {
        currentGlossary = glossary
        val config = currentConfig ?: return
        val instructions = InterpreterConstitution.buildInstructions(
            mode = config.mode,
            sourceLanguage = config.sourceLanguage,
            targetLanguage = config.targetLanguage,
            glossary = glossary,
        )
        transport?.send(
            json.encodeToString(
                SessionUpdateEvent.serializer(),
                SessionUpdateEvent(session = SessionUpdatePayload(instructions = instructions)),
            )
        )
    }

    override suspend fun stop() {
        transport?.close()
        transport = null
    }

    private inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)

    private fun mapServerError(err: ServerErrorEvent): EngineException {
        val kind = when (err.error.code) {
            "rate_limit_exceeded" -> EngineException.Kind.RATE_LIMITED
            "insufficient_quota" -> EngineException.Kind.QUOTA
            "session_expired" -> EngineException.Kind.SESSION_EXPIRED
            "invalid_request_error" -> EngineException.Kind.MALFORMED_RESPONSE
            else -> EngineException.Kind.UNKNOWN
        }
        return EngineException(err.error.message ?: "Realtime error", kind)
    }
}

// Small helper kept file-local to avoid a hard dependency on a specific
// kotlinx.serialization JsonObject extension across versions.
private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
    this as? kotlinx.serialization.json.JsonObject
