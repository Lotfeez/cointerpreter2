package com.cointerpreter.app.state

/**
 * Bounded exponential backoff for reconnect attempts (spec §27: "use bounded
 * exponential backoff where appropriate", "do not create endless aggressive
 * retry loops"). Pure and deterministic so it can be unit tested without
 * mocking a clock or coroutine delay.
 */
class ReconnectPolicy(
    private val baseDelayMillis: Long = 500,
    private val maxDelayMillis: Long = 15_000,
    private val maxAttempts: Int = 5,
) {
    private var attempt = 0

    fun reset() {
        attempt = 0
    }

    /** Returns the delay for the next attempt, or `null` if [maxAttempts] has been exhausted. */
    fun nextDelayMillisOrNull(): Long? {
        if (attempt >= maxAttempts) return null
        val delay = (baseDelayMillis * (1L shl attempt)).coerceAtMost(maxDelayMillis)
        attempt++
        return delay
    }

    val attemptsMade: Int get() = attempt
    val isExhausted: Boolean get() = attempt >= maxAttempts
}
