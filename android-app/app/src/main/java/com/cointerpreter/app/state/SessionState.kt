package com.cointerpreter.app.state

/**
 * Explicit state model for a CoInterpreter interpreting session (spec §19).
 * Using a sealed hierarchy instead of booleans makes illegal states
 * unrepresentable and makes [SessionStateMachine] exhaustively testable.
 */
sealed class SessionState {
    data object Idle : SessionState()
    data object RequestingPermission : SessionState()
    data object Authenticating : SessionState()
    data object Connecting : SessionState()
    data object Ready : SessionState()
    data object Listening : SessionState()
    data object Interpreting : SessionState()
    data object Speaking : SessionState()
    data object Reconnecting : SessionState()
    data object Stopping : SessionState()
    data object Stopped : SessionState()
    data class Error(val error: SessionError) : SessionState()
}

enum class SessionError {
    NO_INTERNET,
    BACKEND_UNREACHABLE,
    AUTH_FAILED,
    OPENAI_SESSION_FAILED,
    SESSION_EXPIRED,
    MIC_PERMISSION_DENIED,
    MIC_UNAVAILABLE,
    AUDIO_INIT_FAILED,
    BLUETOOTH_ROUTE_FAILED,
    UNSUPPORTED_FEATURE,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    TEMPORARY_OUTAGE,
    MALFORMED_SERVER_RESPONSE,
    NETWORK_DROPPED,
    UNKNOWN,
}

/** Events that can legally drive transitions between [SessionState]s. */
sealed class SessionEvent {
    data object StartRequested : SessionEvent()
    data object PermissionGranted : SessionEvent()
    data object PermissionDenied : SessionEvent()
    data object AuthSucceeded : SessionEvent()
    data class AuthFailed(val error: SessionError) : SessionEvent()
    data object TransportConnected : SessionEvent()
    data class TransportFailed(val error: SessionError) : SessionEvent()
    data object SpeechDetected : SessionEvent()
    data object SpeechEnded : SessionEvent()
    data object ResponseAudioStarted : SessionEvent()
    data object ResponseAudioFinished : SessionEvent()
    data object ConnectionLost : SessionEvent()
    data object ConnectionRestored : SessionEvent()
    data object ReconnectExhausted : SessionEvent()
    data object StopRequested : SessionEvent()
    data object StopCompleted : SessionEvent()
    data class FatalError(val error: SessionError) : SessionEvent()
}

/**
 * Pure, deterministic state transition function. No I/O, no coroutines here
 * by design: this is what makes [SessionStateMachineTest] able to exhaustively
 * check every (state, event) pair without touching Android or the network.
 *
 * Returns `null` for an event that is not a legal transition from the given
 * state (e.g. [SessionEvent.SpeechDetected] while [SessionState.Idle]); the
 * caller should treat a `null` result as "ignore, do not crash".
 */
object SessionStateMachine {

    fun transition(current: SessionState, event: SessionEvent): SessionState? {
        // A fatal error can interrupt almost any state except when we're
        // already tearing down, so handle it first.
        if (event is SessionEvent.FatalError &&
            current !is SessionState.Stopping &&
            current !is SessionState.Stopped
        ) {
            return SessionState.Error(event.error)
        }

        return when (current) {
            SessionState.Idle -> when (event) {
                SessionEvent.StartRequested -> SessionState.RequestingPermission
                else -> null
            }

            SessionState.RequestingPermission -> when (event) {
                SessionEvent.PermissionGranted -> SessionState.Authenticating
                SessionEvent.PermissionDenied -> SessionState.Error(SessionError.MIC_PERMISSION_DENIED)
                SessionEvent.StopRequested -> SessionState.Idle
                else -> null
            }

            SessionState.Authenticating -> when (event) {
                SessionEvent.AuthSucceeded -> SessionState.Connecting
                is SessionEvent.AuthFailed -> SessionState.Error(event.error)
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Connecting -> when (event) {
                SessionEvent.TransportConnected -> SessionState.Ready
                is SessionEvent.TransportFailed -> SessionState.Error(event.error)
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Ready -> when (event) {
                SessionEvent.SpeechDetected -> SessionState.Listening
                SessionEvent.ConnectionLost -> SessionState.Reconnecting
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Listening -> when (event) {
                SessionEvent.SpeechEnded -> SessionState.Interpreting
                SessionEvent.ConnectionLost -> SessionState.Reconnecting
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Interpreting -> when (event) {
                SessionEvent.ResponseAudioStarted -> SessionState.Speaking
                SessionEvent.ConnectionLost -> SessionState.Reconnecting
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Speaking -> when (event) {
                SessionEvent.ResponseAudioFinished -> SessionState.Ready
                // Barge-in: new speech interrupts playback and immediately
                // starts a new listening turn (spec §12).
                SessionEvent.SpeechDetected -> SessionState.Listening
                SessionEvent.ConnectionLost -> SessionState.Reconnecting
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Reconnecting -> when (event) {
                SessionEvent.ConnectionRestored -> SessionState.Ready
                SessionEvent.ReconnectExhausted -> SessionState.Error(SessionError.NETWORK_DROPPED)
                SessionEvent.StopRequested -> SessionState.Stopping
                else -> null
            }

            SessionState.Stopping -> when (event) {
                SessionEvent.StopCompleted -> SessionState.Stopped
                else -> null
            }

            SessionState.Stopped -> when (event) {
                SessionEvent.StartRequested -> SessionState.RequestingPermission
                else -> null
            }

            is SessionState.Error -> when (event) {
                SessionEvent.StartRequested -> SessionState.RequestingPermission
                SessionEvent.StopRequested -> SessionState.Stopped
                else -> null
            }
        }
    }

    /** True if audio capture + a live realtime connection should be active in this state. */
    fun isSessionActive(state: SessionState): Boolean = state is SessionState.Ready ||
        state is SessionState.Listening ||
        state is SessionState.Interpreting ||
        state is SessionState.Speaking ||
        state is SessionState.Reconnecting
}
