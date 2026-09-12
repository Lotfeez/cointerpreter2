package com.cointerpreter.app.engine

import com.cointerpreter.app.engine.openai.OpenAiRealtimeTranslateEngine
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Implements automatic bidirectional switching for Conversation Mode
 * (spec §5A, TEST 7) on top of [OpenAiRealtimeTranslateEngine], which only
 * translates towards a single configured target language per session.
 *
 * Approach: open two engine instances fed the same microphone audio, one
 * targeting language A and one targeting language B. OpenAI's documented
 * behavior is that gpt-realtime-translate "may not translate audio that is
 * already in the listener's selected output language" -- in practice, the
 * session whose target matches the speaker's language stays silent, and the
 * session whose target differs produces the translation. CoInterpreter
 * therefore plays whichever engine actually emits [EngineEvent.OutputAudioChunk]
 * for a given utterance, which is by construction the correct direction, and
 * shows the matching transcript pair. No separate language-identification
 * step or "Speaker A / Speaker B" button is needed.
 *
 * Trade-off (documented for BUILD_REPORT.md): this doubles the number of
 * concurrent realtime sessions/audio bandwidth for Conversation Mode versus
 * One-Way Mode. That cost is accepted in exchange for reliable,
 * OpenAI-native automatic direction detection instead of a bespoke and
 * failure-prone client-side language classifier.
 */
class ConversationDirectionCoordinator(
    private val languageA: Language,
    private val languageB: Language,
    private val engineFactory: () -> RealtimeInterpreterEngine = { OpenAiRealtimeTranslateEngine() },
) {
    private var engineToA: RealtimeInterpreterEngine? = null
    private var engineToB: RealtimeInterpreterEngine? = null

    val events: Flow<Pair<Language, EngineEvent>>
        get() {
            val a = engineToA ?: error("start() must be called before observing events")
            val b = engineToB ?: error("start() must be called before observing events")
            return merge(
                a.events.map { languageA to it },
                b.events.map { languageB to it },
            )
        }

    suspend fun start(ephemeralTokenA: String, ephemeralTokenB: String, voice: String) {
        val a = engineFactory()
        val b = engineFactory()
        engineToA = a
        engineToB = b
        a.start(
            EngineSessionConfig(
                ephemeralToken = ephemeralTokenA,
                mode = InterpreterMode.CONVERSATION,
                sourceLanguage = languageB,
                targetLanguage = languageA,
                glossary = emptyList(),
                voice = voice,
            )
        )
        b.start(
            EngineSessionConfig(
                ephemeralToken = ephemeralTokenB,
                mode = InterpreterMode.CONVERSATION,
                sourceLanguage = languageA,
                targetLanguage = languageB,
                glossary = emptyList(),
                voice = voice,
            )
        )
    }

    suspend fun sendAudioChunk(pcm16: ByteArray) {
        engineToA?.sendAudioChunk(pcm16)
        engineToB?.sendAudioChunk(pcm16)
    }

    suspend fun cancelActiveResponse() {
        engineToA?.cancelResponse()
        engineToB?.cancelResponse()
    }

    suspend fun stop() {
        engineToA?.stop()
        engineToB?.stop()
        engineToA = null
        engineToB = null
    }
}
