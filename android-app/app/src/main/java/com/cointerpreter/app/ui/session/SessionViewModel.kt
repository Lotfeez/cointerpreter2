package com.cointerpreter.app.ui.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointerpreter.app.audio.AudioCaptureEngine
import com.cointerpreter.app.audio.AudioPlaybackEngine
import com.cointerpreter.app.audio.AudioRouteManager
import com.cointerpreter.app.data.glossary.GlossaryRepository
import com.cointerpreter.app.data.settings.SettingsRepository
import com.cointerpreter.app.data.transcript.TranscriptRepository
import com.cointerpreter.app.engine.EngineEvent
import com.cointerpreter.app.engine.EngineSessionConfig
import com.cointerpreter.app.engine.InterpreterEngineFactory
import com.cointerpreter.app.engine.RealtimeInterpreterEngine
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import com.cointerpreter.app.model.TranscriptEntry
import com.cointerpreter.app.net.BackendAuthClient
import com.cointerpreter.app.state.SessionError
import com.cointerpreter.app.state.SessionEvent
import com.cointerpreter.app.state.SessionState
import com.cointerpreter.app.state.SessionStateMachine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Central session controller. Bridges pure [SessionStateMachine] transitions
 * to real I/O (mic, engine, backend) and exposes everything the Compose UI
 * needs as [StateFlow]s. Kept free of Android View/Activity references so
 * the interesting logic stays testable; the parts that require Context
 * (audio engines, repositories) are constructed once and injected here in
 * lieu of a full DI framework (spec §18: "introduce DI only if it improves
 * clarity" -- for a single-screen-session app it did not).
 */
class SessionViewModel(
    context: Context,
    private val backendAuthClient: BackendAuthClient = BackendAuthClient(),
) : ViewModel() {

    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val glossaryRepository = GlossaryRepository(appContext)
    val transcriptRepository = TranscriptRepository(appContext)

    private val audioCapture = AudioCaptureEngine()
    private val audioPlayback = AudioPlaybackEngine()
    private val audioRoute = AudioRouteManager(appContext)

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0)
    val sessionDurationSeconds: StateFlow<Int> = _sessionDurationSeconds.asStateFlow()

    private val _sourceLanguage = MutableStateFlow(Language.ARABIC)
    val sourceLanguage: StateFlow<Language> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(Language.ENGLISH)
    val targetLanguage: StateFlow<Language> = _targetLanguage.asStateFlow()

    private val _mode = MutableStateFlow(InterpreterMode.CONVERSATION)
    val mode: StateFlow<InterpreterMode> = _mode.asStateFlow()

    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _voiceMuted = MutableStateFlow(false)
    val voiceMuted: StateFlow<Boolean> = _voiceMuted.asStateFlow()

    private var engine: RealtimeInterpreterEngine? = null
    private var captureJob: Job? = null
    private var durationJob: Job? = null
    private var currentPartialSourceId: String? = null

    init {
        viewModelScope.launch {
            glossaryRepository.load()
            val settings = settingsRepository.settings
            settings.collectIndexedOnce { s ->
                _sourceLanguage.value = s.defaultLanguageA
                _targetLanguage.value = s.defaultLanguageB
                _mode.value = if (s.professionalModeDefault) InterpreterMode.PROFESSIONAL else s.defaultMode
            }
        }
    }

    fun setMode(mode: InterpreterMode) {
        if (!SessionStateMachine.isSessionActive(_sessionState.value)) _mode.value = mode
    }

    fun swapLanguages() {
        val a = _sourceLanguage.value
        _sourceLanguage.value = _targetLanguage.value
        _targetLanguage.value = a
    }

    fun setMicMuted(muted: Boolean) {
        _micMuted.value = muted
        audioCapture.setMuted(muted)
    }

    fun setVoiceMuted(muted: Boolean) {
        _voiceMuted.value = muted
        audioPlayback.setMuted(muted)
    }

    fun start() {
        dispatch(SessionEvent.StartRequested)
    }

    /** Called by the UI once RECORD_AUDIO has actually been granted/denied by the system. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            dispatch(SessionEvent.PermissionGranted)
            beginAuthAndConnect()
        } else {
            dispatch(SessionEvent.PermissionDenied)
        }
    }

    private fun beginAuthAndConnect() {
        viewModelScope.launch {
            val engineForMode = InterpreterEngineFactory.create(_mode.value)
            engine = engineForMode

            val tokenResult = backendAuthClient.requestSessionToken(
                mode = _mode.value,
                engineId = engineForMode.engineId,
                targetLanguageCode = _targetLanguage.value.code,
            )

            when (tokenResult) {
                is BackendAuthClient.Result.Failure -> {
                    val mapped = when (tokenResult.kind) {
                        BackendAuthClient.FailureKind.BACKEND_UNREACHABLE -> SessionError.BACKEND_UNREACHABLE
                        BackendAuthClient.FailureKind.NETWORK -> SessionError.NO_INTERNET
                        BackendAuthClient.FailureKind.HTTP_ERROR -> SessionError.AUTH_FAILED
                        BackendAuthClient.FailureKind.MALFORMED_RESPONSE -> SessionError.MALFORMED_SERVER_RESPONSE
                    }
                    dispatch(SessionEvent.AuthFailed(mapped))
                    return@launch
                }
                is BackendAuthClient.Result.Success -> {
                    dispatch(SessionEvent.AuthSucceeded)
                    connectEngine(engineForMode, tokenResult.response.ephemeralToken)
                }
            }
        }
    }

    private fun connectEngine(engineForMode: RealtimeInterpreterEngine, ephemeralToken: String) {
        viewModelScope.launch {
            engineForMode.events.onEach(::onEngineEvent).launchIn(viewModelScope)

            runCatching {
                engineForMode.start(
                    EngineSessionConfig(
                        ephemeralToken = ephemeralToken,
                        mode = _mode.value,
                        sourceLanguage = _sourceLanguage.value,
                        targetLanguage = _targetLanguage.value,
                        glossary = glossaryRepository.entries.value,
                        voice = "alloy",
                    )
                )
            }.onFailure {
                dispatch(SessionEvent.TransportFailed(SessionError.OPENAI_SESSION_FAILED))
                return@launch
            }

            dispatch(SessionEvent.TransportConnected)
            audioRoute.acquireFocusAndRoute()
            audioPlayback.start()
            startCapture()
            startDurationTimer()
        }
    }

    private fun startCapture() {
        captureJob = audioCapture.capture()
            .onEach { chunk -> engine?.sendAudioChunk(chunk) }
            .launchIn(viewModelScope)
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        _sessionDurationSeconds.value = 0
        durationJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _sessionDurationSeconds.value += 1
            }
        }
    }

    private fun onEngineEvent(event: EngineEvent) {
        when (event) {
            EngineEvent.Connected -> { /* already transitioned on TransportConnected */ }
            EngineEvent.SpeechStarted -> {
                // Barge-in: if we were speaking a translation, cancel it now.
                if (_sessionState.value is SessionState.Speaking) {
                    viewModelScope.launch {
                        engine?.cancelResponse()
                        audioPlayback.truncateAndFlush()
                    }
                }
                dispatch(SessionEvent.SpeechDetected)
            }
            EngineEvent.SpeechStopped -> dispatch(SessionEvent.SpeechEnded)
            EngineEvent.OutputAudioStarted -> dispatch(SessionEvent.ResponseAudioStarted)
            EngineEvent.OutputAudioFinished -> dispatch(SessionEvent.ResponseAudioFinished)
            EngineEvent.ResponseCancelled -> { /* state already moved on SpeechDetected above */ }
            is EngineEvent.OutputAudioChunk -> audioPlayback.write(event.pcm16)
            is EngineEvent.PartialTranscript -> updateTranscript(event.text, isSource = event.isSource, isFinal = false)
            is EngineEvent.FinalTranscript -> updateTranscript(event.text, isSource = event.isSource, isFinal = true)
            is EngineEvent.Disconnected -> dispatch(SessionEvent.ConnectionLost)
            is EngineEvent.Error -> dispatch(SessionEvent.FatalError(mapEngineError(event.exception.kind)))
        }
    }

    private fun mapEngineError(kind: com.cointerpreter.app.engine.EngineException.Kind): SessionError = when (kind) {
        com.cointerpreter.app.engine.EngineException.Kind.AUTH -> SessionError.AUTH_FAILED
        com.cointerpreter.app.engine.EngineException.Kind.TRANSPORT -> SessionError.NETWORK_DROPPED
        com.cointerpreter.app.engine.EngineException.Kind.SESSION_EXPIRED -> SessionError.SESSION_EXPIRED
        com.cointerpreter.app.engine.EngineException.Kind.RATE_LIMITED -> SessionError.RATE_LIMITED
        com.cointerpreter.app.engine.EngineException.Kind.QUOTA -> SessionError.QUOTA_EXCEEDED
        com.cointerpreter.app.engine.EngineException.Kind.SERVER_OUTAGE -> SessionError.TEMPORARY_OUTAGE
        com.cointerpreter.app.engine.EngineException.Kind.MALFORMED_RESPONSE -> SessionError.MALFORMED_SERVER_RESPONSE
        com.cointerpreter.app.engine.EngineException.Kind.UNSUPPORTED_FEATURE -> SessionError.UNSUPPORTED_FEATURE
        com.cointerpreter.app.engine.EngineException.Kind.UNKNOWN -> SessionError.UNKNOWN
    }

    private fun updateTranscript(textDelta: String, isSource: Boolean, isFinal: Boolean) {
        val id = currentPartialSourceId ?: UUID.randomUUID().toString().also { currentPartialSourceId = it }
        val existing = transcriptRepository.live.value.firstOrNull { it.id == id }
        val entry = TranscriptEntry(
            id = id,
            timestampMillis = System.currentTimeMillis(),
            sourceLanguage = _sourceLanguage.value,
            targetLanguage = _targetLanguage.value,
            originalText = if (isSource) textDelta else existing?.originalText ?: "",
            translatedText = if (!isSource) textDelta else existing?.translatedText ?: "",
            isFinal = isFinal,
        )
        transcriptRepository.appendOrUpdate(entry)
        if (isFinal && !isSource) currentPartialSourceId = null
    }

    fun stop() {
        dispatch(SessionEvent.StopRequested)
        viewModelScope.launch {
            captureJob?.cancel()
            durationJob?.cancel()
            engine?.stop()
            engine = null
            audioPlayback.stop()
            audioRoute.release()
            currentPartialSourceId = null
            dispatch(SessionEvent.StopCompleted)
        }
    }

    fun retryAfterError() {
        dispatch(SessionEvent.StartRequested)
    }

    private fun dispatch(event: SessionEvent) {
        val next = SessionStateMachine.transition(_sessionState.value, event) ?: return
        _sessionState.value = next
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        durationJob?.cancel()
        audioPlayback.stop()
        audioRoute.release()
    }
}

/** Small helper: collects the first emission of a Flow without pulling in a full DI/repository test double. */
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collectIndexedOnce(action: (T) -> Unit) {
    kotlinx.coroutines.flow.first(this).let(action)
}
