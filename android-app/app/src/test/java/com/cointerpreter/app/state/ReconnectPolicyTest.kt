package com.cointerpreter.app.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun `delays grow exponentially and are capped at maxDelayMillis`() {
        val policy = ReconnectPolicy(baseDelayMillis = 500, maxDelayMillis = 4000, maxAttempts = 10)
        val delays = generateSequence { policy.nextDelayMillisOrNull() }.toList()
        assertThat(delays).containsExactly(500L, 1000L, 2000L, 4000L, 4000L, 4000L, 4000L, 4000L, 4000L, 4000L).inOrder()
    }

    @Test
    fun `returns null once maxAttempts is exhausted, preventing endless retry loops`() {
        val policy = ReconnectPolicy(maxAttempts = 3)
        repeat(3) { assertThat(policy.nextDelayMillisOrNull()).isNotNull() }
        assertThat(policy.nextDelayMillisOrNull()).isNull()
        assertThat(policy.isExhausted).isTrue()
    }

    @Test
    fun `reset allows a fresh sequence of attempts after a successful reconnect`() {
        val policy = ReconnectPolicy(maxAttempts = 2)
        policy.nextDelayMillisOrNull()
        policy.nextDelayMillisOrNull()
        assertThat(policy.isExhausted).isTrue()
        policy.reset()
        assertThat(policy.isExhausted).isFalse()
        assertThat(policy.nextDelayMillisOrNull()).isNotNull()
    }
}
