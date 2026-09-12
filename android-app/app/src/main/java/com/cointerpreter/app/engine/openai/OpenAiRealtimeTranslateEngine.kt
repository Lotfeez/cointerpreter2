package com.cointerpreter.app.engine.openai

import android.util.Base64
import com.cointerpreter.app.engine.EngineEvent
import com.cointerpreter.app.engine.EngineException
import com.cointerpreter.app.engine.EngineSessionConfig
import com.cointerpreter.app.engine.RealtimeInterpreterEngine
import com.cointerpreter.app.model.GlossaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wraps `gpt-realtime-translate` on the dedicated `/v1/realtime/translations`
 * endpoint (GA, documented at developers.openai.com/api/docs/guides/
 * realtime-translation as of Sep 2026). This is CoInterpreter's default
 * engine for Conversation Mode and One-Way Mode.
 *
 * Structural properties documented by OpenAI that this class relies on:
 *  - The model translates continuous source audio into ONE configured
 *    target language (`session.audio.output.language`), auto-detecting the
 *    source language across 70+ supported input languages.
 *  - It does not accept custom prompting, tools, or voice selection: output
 *    voice dynamically follows the source speaker's tone. This is exactly
 *    why it structurally cannot "join the conversation" or answer a
 *    question -- it has no capacity to generate anything other than a
 *    translation of what it hears.
 *  - OpenAI's own guidance notes it "may not translate audio that is
 *    already in the listener's selected output language" -- CoInterpreter
 *    uses this in Conversation Mode by running two lightweight sessions
 *    (see [ConversationDirectionCoordinator]) targeted at each of the two
 *    session languages; whichever session actually emits audio for a given
 *    utterance is, by construction, the correct direction, giving automatic
 *    bidirectional switching (spec §5A) without a separate language-ID step.
 *
 * Because this engine has no instruction channel, [supportsCustomInstructions]
 * is false and [updateGlossary] is a documented no-op: Professional Mode
 * (where glossary precedence matters most) always uses
 * [OpenAiGptRealtimeEngine] instead, per [com.cointerpreter.app.engine.InterpreterEngineFactory].
 */
internal class OpenAiRealtimeTranslateEngine : RealtimeInterpreterEngine {

    override val engineId: String = "gpt-realtime-translate"
    override val supportsCustomInstructions: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events

    private var transport: RealtimeWebSocketTransport? = null
    private var playedAudioMsSinceItemStart = 0

    override suspend fun start(config: EngineSessionConfig) {
        val url = "wss://api.openai.com/v1/realtime/translations?model=$engineId"
        val t = RealtimeWebSocketTransport(url = url, ephemeralToken = config.ephemeralToken)
        transport = t

        t.connect()
            .onEach { raw -> handleServerMessage(raw) }
            .launchIn(scope)

        // Target output language for THIS session direction. Conversation
        // Mode opens a second instance of this engine targeted at the other
        // language; see ConversationDirectionCoordinator.
        val sessionUpdate = SessionUpdateEvent(
            session = SessionUpdatePayload(
                audio = AudioConfig(
                    input = AudioIoConfig(
                        format = AudioFormat(rate = 24_000),
                        transcription = TranscriptionConfig(model = "gpt-realtime-whisper"),
                    ),
                    output = AudioIoConfig(
                        format = AudioFormat(rate = 24_000),
                        language = config.targetLanguage.code,
                    ),
                ),
            ),
        )
        t.send(json.encodeToString(SessionUpdateEvent.serializer(), sessionUpdate))
    }

    private suspend fun handleServerMessage(raw: String) {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val type = obj["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "session.created", "session.updated", "translation_session.created" -> _events.emit(EngineEvent.Connected)

            "input_audio_buffer.speech_started" -> _events.emit(EngineEvent.SpeechStarted)
            "input_audio_buffer.speech_stopped" -> _events.emit(EngineEvent.SpeechStopped)

            "conversation.item.input_audio_transcription.delta" -> {
                val d = decode<ConversationItemInputAudioTranscriptionDeltaEvent>(raw)
                _events.emit(EngineEvent.PartialTranscript(d.delta, isSource = true))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val d = decode<ConversationItemInputAudioTranscriptionCompletedEvent>(raw)
                _events.emit(EngineEvent.FinalTranscript(d.transcript, isSource = true))
            }

            "response.output_audio_transcript.delta" -> {
                val d = decode<ResponseOutputAudioTranscriptDeltaEvent>(raw)
                _events.emit(EngineEvent.PartialTranscript(d.delta, isSource = false))
            }
            "response.output_audio_transcript.done" -> {
                val d = decode<ResponseOutputAudioTranscriptDoneEvent>(raw)
                _events.emit(EngineEvent.FinalTranscript(d.transcript, isSource = false))
            }

            "response.output_audio.delta" -> {
                val d = decode<ResponseOutputAudioDeltaEvent>(raw)
                if (playedAudioMsSinceItemStart == 0) _events.emit(EngineEvent.OutputAudioStarted)
                val bytes = Base64.decode(d.delta, Base64.NO_WRAP)
                playedAudioMsSinceItemStart += (bytes.size * 1000) / 48_000
                _events.emit(EngineEvent.OutputAudioChunk(bytes))
            }
            "response.output_audio.done" -> {
                playedAudioMsSinceItemStart = 0
                _events.emit(EngineEvent.OutputAudioFinished)
            }

            "error" -> {
                val err = decode<ServerErrorEvent>(raw)
                _events.emit(EngineEvent.Error(EngineException(err.error.message ?: "Translation session error", EngineException.Kind.UNKNOWN)))
            }

            else -> { /* forward-compatible no-op */ }
        }
    }

    override suspend fun sendAudioChunk(pcm16: ByteArray) {
        transport?.sendAudio(pcm16)
    }

    override suspend fun commitAudioTurn() {
        transport?.send(json.encodeToString(InputAudioBufferCommitEvent.serializer(), InputAudioBufferCommitEvent()))
    }

    override suspend fun cancelResponse() {
        transport?.send(json.encodeToString(ResponseCancelEvent.serializer(), ResponseCancelEvent()))
        playedAudioMsSinceItemStart = 0
        _events.emit(EngineEvent.ResponseCancelled)
    }

    /** No-op: gpt-realtime-translate has no instruction channel (see class doc). */
    override suspend fun updateGlossary(glossary: List<GlossaryEntry>) { /* intentionally unsupported */ }

    override suspend fun stop() {
        transport?.close()
        transport = null
    }

    private inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)
}
