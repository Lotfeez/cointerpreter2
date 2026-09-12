package com.cointerpreter.app.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionStateMachineTest {

    @Test
    fun `happy path from Idle to Speaking and back to Ready`() {
        var state: SessionState = SessionState.Idle
        state = SessionStateMachine.transition(state, SessionEvent.StartRequested)!!
        assertThat(state).isEqualTo(SessionState.RequestingPermission)

        state = SessionStateMachine.transition(state, SessionEvent.PermissionGranted)!!
        assertThat(state).isEqualTo(SessionState.Authenticating)

        state = SessionStateMachine.transition(state, SessionEvent.AuthSucceeded)!!
        assertThat(state).isEqualTo(SessionState.Connecting)

        state = SessionStateMachine.transition(state, SessionEvent.TransportConnected)!!
        assertThat(state).isEqualTo(SessionState.Ready)

        state = SessionStateMachine.transition(state, SessionEvent.SpeechDetected)!!
        assertThat(state).isEqualTo(SessionState.Listening)

        state = SessionStateMachine.transition(state, SessionEvent.SpeechEnded)!!
        assertThat(state).isEqualTo(SessionState.Interpreting)

        state = SessionStateMachine.transition(state, SessionEvent.ResponseAudioStarted)!!
        assertThat(state).isEqualTo(SessionState.Speaking)

        state = SessionStateMachine.transition(state, SessionEvent.ResponseAudioFinished)!!
        assertThat(state).isEqualTo(SessionState.Ready)
    }

    @Test
    fun `barge-in interrupts Speaking directly into Listening`() {
        val next = SessionStateMachine.transition(SessionState.Speaking, SessionEvent.SpeechDetected)
        assertThat(next).isEqualTo(SessionState.Listening)
    }

    @Test
    fun `illegal transitions return null instead of throwing`() {
        assertThat(SessionStateMachine.transition(SessionState.Idle, SessionEvent.SpeechDetected)).isNull()
        assertThat(SessionStateMachine.transition(SessionState.Stopped, SessionEvent.SpeechEnded)).isNull()
        assertThat(SessionStateMachine.transition(SessionState.Ready, SessionEvent.ResponseAudioStarted)).isNull()
    }

    @Test
    fun `starting twice while already active is rejected, preventing duplicate sessions`() {
        assertThat(SessionStateMachine.transition(SessionState.Ready, SessionEvent.StartRequested)).isNull()
        assertThat(SessionStateMachine.transition(SessionState.Listening, SessionEvent.StartRequested)).isNull()
    }

    @Test
    fun `fatal error interrupts active states and is recoverable`() {
        val errorState = SessionStateMachine.transition(SessionState.Listening, SessionEvent.FatalError(SessionError.NETWORK_DROPPED))
        assertThat(errorState).isInstanceOf(SessionState.Error::class.java)

        val restarted = SessionStateMachine.transition(errorState!!, SessionEvent.StartRequested)
        assertThat(restarted).isEqualTo(SessionState.RequestingPermission)
    }

    @Test
    fun `fatal error is ignored while already stopping or stopped`() {
        assertThat(SessionStateMachine.transition(SessionState.Stopping, SessionEvent.FatalError(SessionError.UNKNOWN))).isNull()
    }

    @Test
    fun `reconnect flow restores Ready or escalates to Error when exhausted`() {
        val reconnecting = SessionStateMachine.transition(SessionState.Ready, SessionEvent.ConnectionLost)
        assertThat(reconnecting).isEqualTo(SessionState.Reconnecting)

        val restored = SessionStateMachine.transition(reconnecting!!, SessionEvent.ConnectionRestored)
        assertThat(restored).isEqualTo(SessionState.Ready)

        val exhausted = SessionStateMachine.transition(SessionState.Reconnecting, SessionEvent.ReconnectExhausted)
        assertThat(exhausted).isInstanceOf(SessionState.Error::class.java)
    }

    @Test
    fun `stopping always requires StopCompleted before returning to Stopped`() {
        assertThat(SessionStateMachine.transition(SessionState.Stopping, SessionEvent.StopCompleted))
            .isEqualTo(SessionState.Stopped)
        assertThat(SessionStateMachine.transition(SessionState.Stopping, SessionEvent.SpeechDetected)).isNull()
    }

    @Test
    fun `isSessionActive is true only for states that should keep audio capture running`() {
        assertThat(SessionStateMachine.isSessionActive(SessionState.Idle)).isFalse()
        assertThat(SessionStateMachine.isSessionActive(SessionState.RequestingPermission)).isFalse()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Ready)).isTrue()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Listening)).isTrue()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Interpreting)).isTrue()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Speaking)).isTrue()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Reconnecting)).isTrue()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Stopped)).isFalse()
        assertThat(SessionStateMachine.isSessionActive(SessionState.Error(SessionError.UNKNOWN))).isFalse()
    }
}
