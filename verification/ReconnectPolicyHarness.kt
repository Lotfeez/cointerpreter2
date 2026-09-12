import com.cointerpreter.app.state.ReconnectPolicy

fun main() {
    var pass = 0; var fail = 0
    fun check(name: String, cond: Boolean) { println((if (cond) "PASS" else "FAIL") + " - " + name); if (cond) pass++ else fail++ }

    val policy = ReconnectPolicy(baseDelayMillis = 500, maxDelayMillis = 4000, maxAttempts = 10)
    val delays = generateSequence { policy.nextDelayMillisOrNull() }.toList()
    check("delays exactly match expected capped exponential sequence", delays == listOf(500L,1000L,2000L,4000L,4000L,4000L,4000L,4000L,4000L,4000L))

    val p2 = ReconnectPolicy(maxAttempts = 3)
    repeat(3) { p2.nextDelayMillisOrNull() }
    check("exhausted after maxAttempts", p2.isExhausted && p2.nextDelayMillisOrNull() == null)

    p2.reset()
    check("reset clears exhaustion", !p2.isExhausted)

    println()
    println("TOTAL: ${pass+fail} PASSED: $pass FAILED: $fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
