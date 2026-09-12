package com.cointerpreter.app.engine

import com.cointerpreter.app.model.GlossaryEntry
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic contract for "whatever engine is currently doing the
 * live speech-to-speech interpreting". The rest of the app (ViewModel, audio
 * layer, UI) only ever talks to this interface, per spec §1: CoInterpreter
 * must not be tightly coupled to one OpenAI model identifier.
 *
 * Two implementations exist today, chosen per [InterpreterMode] by
 * [InterpreterEngineFactory]:
 *  - [OpenAiRealtimeTranslateEngine]: wraps `gpt-realtime-translate` on the
 *    dedicated `/v1/realtime/translations` endpoint. Purpose-built for
 *    speech-to-speech translation, cannot answer questions because it has no
 *    conversational/tool-use capability at all, but does not accept custom
 *    prompting or glossary injection.
 *  - [OpenAiGptRealtimeEngine]: wraps the general-purpose `gpt-realtime`
 *    model on the standard realtime session endpoint, driven by
 *    [InterpreterConstitution]. Accepts full custom instructions and
 *    glossary injection, used for Professional Mode where terminology
 *    control outweighs the extra naturalness of the dedicated model.
 *
 * A future engine (a different vendor, a newer OpenAI model, an on-device
 * fallback) only needs to implement this interface; nothing else in the app
 * changes.
 */
interface RealtimeInterpreterEngine {

    /** Human-readable identifier for logs/diagnostics, e.g. "gpt-realtime-translate". */
    val engineId: String

    /** True if this engine accepts [InterpreterConstitution] + glossary text as a system prompt. */
    val supportsCustomInstructions: Boolean

    /**
     * Opens a realtime session for the given configuration. Suspends until
     * the transport is connected and the session is configured, or throws an
     * [EngineException] mapped to a [com.cointerpreter.app.state.SessionError].
     */
    suspend fun start(config: EngineSessionConfig)

    /** Streams raw little-endian PCM16 mono audio captured from the microphone into the session. */
    suspend fun sendAudioChunk(pcm16: ByteArray)

    /** Signals that the current utterance is complete (used only when server VAD is disabled). */
    suspend fun commitAudioTurn()

    /**
     * Cancels any in-flight or currently-playing model response immediately.
     * Called on barge-in (spec §12) so stale translated audio is not spoken
     * over a new utterance from the other speaker.
     */
    suspend fun cancelResponse()

    /** Updates the glossary/instructions for an already-open session without a full reconnect. */
    suspend fun updateGlossary(glossary: List<GlossaryEntry>)

    /** Closes the session and releases all network resources. Safe to call multiple times. */
    suspend fun stop()

    /** Stream of engine events the session layer maps onto [com.cointerpreter.app.state.SessionEvent]. */
    val events: Flow<EngineEvent>
}

data class EngineSessionConfig(
    val ephemeralToken: String,
    val mode: InterpreterMode,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val glossary: List<GlossaryEntry>,
    val voice: String,
)

sealed class EngineEvent {
    data object Connected : EngineEvent()
    data object SpeechStarted : EngineEvent()
    data object SpeechStopped : EngineEvent()
    data class PartialTranscript(val text: String, val isSource: Boolean) : EngineEvent()
    data class FinalTranscript(val text: String, val isSource: Boolean) : EngineEvent()
    data class OutputAudioChunk(val pcm16: ByteArray) : EngineEvent()
    data object OutputAudioStarted : EngineEvent()
    data object OutputAudioFinished : EngineEvent()
    data object ResponseCancelled : EngineEvent()
    data class Disconnected(val reason: String?) : EngineEvent()
    data class Error(val exception: EngineException) : EngineEvent()
}

/** Engine-level error, mapped to [com.cointerpreter.app.state.SessionError] by the session layer. */
class EngineException(
    message: String,
    val kind: Kind,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        AUTH,
        TRANSPORT,
        SESSION_EXPIRED,
        RATE_LIMITED,
        QUOTA,
        SERVER_OUTAGE,
        MALFORMED_RESPONSE,
        UNSUPPORTED_FEATURE,
        UNKNOWN,
    }
}
