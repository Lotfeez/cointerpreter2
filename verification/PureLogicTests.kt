import com.cointerpreter.app.engine.InterpreterConstitution
import com.cointerpreter.app.model.GlossaryEntry
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import com.cointerpreter.app.state.SessionError
import com.cointerpreter.app.state.SessionEvent
import com.cointerpreter.app.state.SessionState
import com.cointerpreter.app.state.SessionStateMachine

/**
 * Standalone assertion harness for the pure-Kotlin (Android-free) parts of
 * CoInterpreter: the session state machine, the interpreter constitution /
 * glossary prompt builder, and the language model. Compiled and executed
 * directly with the Kotlin compiler in the sandbox since the Android SDK
 * and Gradle/AGP distribution servers are not reachable here (see
 * BUILD_REPORT.md). The full Android/JUnit unit + instrumentation test
 * suite under app/src/test and app/src/androidTest runs in CI via Gradle
 * (.github/workflows/android-build.yml) where the SDK is available.
 */

var passed = 0
var failed = 0
val failures = mutableListOf<String>()

fun check(name: String, condition: Boolean) {
    if (condition) {
        passed++
    } else {
        failed++
        failures += name
    }
    println((if (condition) "PASS" else "FAIL") + " - " + name)
}

fun <T> checkEquals(name: String, expected: T, actual: T) {
    check("$name (expected=$expected actual=$actual)", expected == actual)
}

fun main() {
    // ---------------------------------------------------------------
    // Session state machine: valid transitions
    // ---------------------------------------------------------------
    run {
        var s: SessionState = SessionState.Idle
        s = SessionStateMachine.transition(s, SessionEvent.StartRequested)!!
        checkEquals("Idle -> StartRequested -> RequestingPermission", SessionState.RequestingPermission, s)

        s = SessionStateMachine.transition(s, SessionEvent.PermissionGranted)!!
        checkEquals("RequestingPermission -> PermissionGranted -> Authenticating", SessionState.Authenticating, s)

        s = SessionStateMachine.transition(s, SessionEvent.AuthSucceeded)!!
        checkEquals("Authenticating -> AuthSucceeded -> Connecting", SessionState.Connecting, s)

        s = SessionStateMachine.transition(s, SessionEvent.TransportConnected)!!
        checkEquals("Connecting -> TransportConnected -> Ready", SessionState.Ready, s)

        s = SessionStateMachine.transition(s, SessionEvent.SpeechDetected)!!
        checkEquals("Ready -> SpeechDetected -> Listening", SessionState.Listening, s)

        s = SessionStateMachine.transition(s, SessionEvent.SpeechEnded)!!
        checkEquals("Listening -> SpeechEnded -> Interpreting", SessionState.Interpreting, s)

        s = SessionStateMachine.transition(s, SessionEvent.ResponseAudioStarted)!!
        checkEquals("Interpreting -> ResponseAudioStarted -> Speaking", SessionState.Speaking, s)

        // Barge-in: new speech while Speaking goes straight back to Listening.
        s = SessionStateMachine.transition(s, SessionEvent.SpeechDetected)!!
        checkEquals("Speaking -> SpeechDetected (barge-in) -> Listening", SessionState.Listening, s)
    }

    // ---------------------------------------------------------------
    // Session state machine: illegal transitions are rejected (return null)
    // ---------------------------------------------------------------
    run {
        val illegal1 = SessionStateMachine.transition(SessionState.Idle, SessionEvent.SpeechDetected)
        check("Idle + SpeechDetected is illegal (null)", illegal1 == null)

        val illegal2 = SessionStateMachine.transition(SessionState.Stopped, SessionEvent.SpeechEnded)
        check("Stopped + SpeechEnded is illegal (null)", illegal2 == null)

        val illegal3 = SessionStateMachine.transition(SessionState.Ready, SessionEvent.ResponseAudioStarted)
        check("Ready + ResponseAudioStarted is illegal (null)", illegal3 == null)
    }

    // ---------------------------------------------------------------
    // Session state machine: fatal error interrupts active states
    // ---------------------------------------------------------------
    run {
        val errorState = SessionStateMachine.transition(SessionState.Listening, SessionEvent.FatalError(SessionError.NETWORK_DROPPED))
        check("Listening + FatalError -> Error state", errorState is SessionState.Error)

        val restart = SessionStateMachine.transition(errorState!!, SessionEvent.StartRequested)
        checkEquals("Error -> StartRequested -> RequestingPermission (recoverable)", SessionState.RequestingPermission, restart)
    }

    // ---------------------------------------------------------------
    // Session state machine: no duplicate sessions (Ready cannot be
    // re-entered via a second StartRequested while already active)
    // ---------------------------------------------------------------
    run {
        val duringActiveSession = SessionStateMachine.transition(SessionState.Ready, SessionEvent.StartRequested)
        check("Ready + StartRequested is illegal (prevents duplicate session)", duringActiveSession == null)
    }

    // ---------------------------------------------------------------
    // Reconnect flow
    // ---------------------------------------------------------------
    run {
        val reconnecting = SessionStateMachine.transition(SessionState.Ready, SessionEvent.ConnectionLost)
        checkEquals("Ready -> ConnectionLost -> Reconnecting", SessionState.Reconnecting, reconnecting)
        val restored = SessionStateMachine.transition(reconnecting!!, SessionEvent.ConnectionRestored)
        checkEquals("Reconnecting -> ConnectionRestored -> Ready", SessionState.Ready, restored)
        val exhausted = SessionStateMachine.transition(SessionState.Reconnecting, SessionEvent.ReconnectExhausted)
        check("Reconnecting -> ReconnectExhausted -> Error", exhausted is SessionState.Error)
    }

    // ---------------------------------------------------------------
    // isSessionActive helper used to gate mic capture
    // ---------------------------------------------------------------
    run {
        check("isSessionActive(Idle) == false", !SessionStateMachine.isSessionActive(SessionState.Idle))
        check("isSessionActive(Listening) == true", SessionStateMachine.isSessionActive(SessionState.Listening))
        check("isSessionActive(Speaking) == true", SessionStateMachine.isSessionActive(SessionState.Speaking))
        check("isSessionActive(Stopped) == false", !SessionStateMachine.isSessionActive(SessionState.Stopped))
    }

    // ---------------------------------------------------------------
    // Interpreter constitution: critical "translate, don't answer" instruction
    // ---------------------------------------------------------------
    run {
        val instructions = InterpreterConstitution.buildInstructions(
            mode = InterpreterMode.CONVERSATION,
            sourceLanguage = Language.ARABIC,
            targetLanguage = Language.ENGLISH,
            glossary = emptyList(),
        )
        check(
            "Constitution explicitly forbids answering instead of translating",
            instructions.contains("Do NOT answer when the Minister will arrive") &&
                instructions.contains("Never answer questions addressed by one speaker to another"),
        )
        check("Constitution forbids joining the conversation", instructions.contains("Never participate in the conversation"))
        check("Constitution forbids adding facts", instructions.contains("Never add factual information"))
        check("Constitution requires preserving numbers/dates/names", instructions.contains("proper") && instructions.contains("numbers"))
        check("Constitution mentions both active languages", instructions.contains("Arabic") && instructions.contains("English"))
    }

    // ---------------------------------------------------------------
    // Professional Mode addendum favors terminology precedence
    // ---------------------------------------------------------------
    run {
        val instructions = InterpreterConstitution.buildInstructions(
            mode = InterpreterMode.PROFESSIONAL,
            sourceLanguage = Language.ARABIC,
            targetLanguage = Language.FRENCH,
            glossary = emptyList(),
        )
        check("Professional Mode addendum present", instructions.contains("formal professional, governmental"))
    }

    // ---------------------------------------------------------------
    // Glossary injection: entries appear as guidance, and an empty
    // glossary must not add a wasted section (cost-awareness, spec §6/§28)
    // ---------------------------------------------------------------
    run {
        val emptySection = InterpreterConstitution.buildGlossarySection(emptyList())
        check("Empty glossary produces no section (avoids wasted tokens)", emptySection == null)

        val glossary = listOf(
            GlossaryEntry(id = "1", sourceTerm = "القطاع غير الربحي", preferredTranslation = "non-profit sector"),
            GlossaryEntry(id = "2", sourceTerm = "FATF", preferredTranslation = "Financial Action Task Force"),
        )
        val section = InterpreterConstitution.buildGlossarySection(glossary)!!
        check("Glossary section contains first entry", section.contains("non-profit sector"))
        check("Glossary section contains second entry", section.contains("Financial Action Task Force"))
        check("Glossary section is marked as guidance, not spoken content", section.contains("interpreting guidance"))

        val fullInstructions = InterpreterConstitution.buildInstructions(
            mode = InterpreterMode.PROFESSIONAL,
            sourceLanguage = Language.ARABIC,
            targetLanguage = Language.ENGLISH,
            glossary = glossary,
        )
        check("Full instructions include glossary terms when present", fullInstructions.contains("FATF"))
    }

    // ---------------------------------------------------------------
    // Language model: RTL flags and lookups (spec §4)
    // ---------------------------------------------------------------
    run {
        check("Arabic is RTL", Language.ARABIC.isRtl)
        check("English is LTR", !Language.ENGLISH.isRtl)
        check("French is LTR", !Language.FRENCH.isRtl)
        checkEquals("byCode(ar) resolves to Arabic", Language.ARABIC, Language.byCode("ar"))
        checkEquals("byCode(fr) resolves to French", Language.FRENCH, Language.byCode("fr"))
        check("First-class languages include Arabic, English, French", Language.FIRST_CLASS.containsAll(listOf(Language.ARABIC, Language.ENGLISH, Language.FRENCH)))
    }

    println()
    println("TOTAL: ${passed + failed}, PASSED: $passed, FAILED: $failed")
    if (failed > 0) {
        println("FAILING CHECKS:")
        failures.forEach { println(" - $it") }
        kotlin.system.exitProcess(1)
    }
}
