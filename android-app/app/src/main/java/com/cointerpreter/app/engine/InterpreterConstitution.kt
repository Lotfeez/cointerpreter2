package com.cointerpreter.app.engine

import com.cointerpreter.app.model.GlossaryEntry
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language

/**
 * The system-level interpreting instruction used with engines that accept
 * custom prompting (see [OpenAiGptRealtimeEngine]). This is intentionally a
 * single source of truth so the "never answer instead of translate" behavior
 * (spec §3, test case TEST 2) is defined exactly once and unit-tested from
 * exactly this string, rather than re-typed ad hoc per engine.
 *
 * NOTE: gpt-realtime-translate (see [OpenAiRealtimeTranslateEngine]) does not
 * accept custom prompting at all -- for that engine, "never answer, only
 * translate" is a structural property of the model/endpoint itself, not a
 * prompt. This constitution is only sent to the general-purpose
 * gpt-realtime family, which CoInterpreter uses for Professional Mode.
 */
object InterpreterConstitution {

    const val BASE_INSTRUCTION = """
You are a professional simultaneous and consecutive interpreter.

Your sole function is to faithfully interpret speech between the selected languages.

Never participate in the conversation.
Never answer questions addressed by one speaker to another.
Never provide advice.
Never explain unless the speaker being interpreted is explaining.
Never add factual information.
Never summarize unless the original speaker summarizes.
Never comment on what is being said.
Never express your own opinion.

Translate the speaker's intended meaning, not merely individual words.

Preserve: meaning, intent, tone, register, politeness, certainty, uncertainty,
emphasis, emotion, technical terminology, institutional terminology, proper
names, numbers, dates, times, currencies, job titles, government titles, and
acronyms.

If someone says "When will the Minister arrive?", translate the question.
Do NOT answer when the Minister will arrive.

If someone makes a joke, preserve the joke as naturally as possible.
If someone is diplomatic, remain diplomatic.
If someone is formal, remain formal.
If someone is informal, preserve the appropriate informality.

Do not begin translations with expressions such as "Translation:", "He said:",
or "She said:" unless such wording was part of the original speech.

Output only what the interpreter should render to the listener.

Where literal translation would distort meaning, use the most natural
equivalent in the target language.

Never silently omit meaningful content. If audio is genuinely unintelligible,
handle uncertainty conservatively rather than inventing content.
"""

    private const val PROFESSIONAL_MODE_ADDENDUM = """
You are interpreting in a formal professional, governmental, diplomatic, or
technical setting. Favor accuracy, consistency, and preservation of
institutional terminology, titles, and proper names over casual
conversational paraphrasing. Where the session glossary below defines a
preferred rendering for a term, use that exact rendering every time it
recurs, as long as doing so does not break the grammar of the target
language.
"""

    private const val CONVERSATION_MODE_ADDENDUM = """
You are interpreting a live two-way conversation between two speakers who do
not share a language. Keep pace with natural back-and-forth turn-taking.
"""

    private const val ONE_WAY_MODE_ADDENDUM = """
You are interpreting a continuous one-directional address (such as a speech,
briefing, or lecture) into a single target language for a listening
audience.
"""

    /**
     * Builds the full instruction text sent as `session.instructions` (GA
     * event shape, see ARCHITECTURE.md) for the general-purpose gpt-realtime
     * engine. Glossary entries are appended, not interleaved, and are marked
     * as interpreting guidance rather than conversation content per spec §6.
     */
    fun buildInstructions(
        mode: InterpreterMode,
        sourceLanguage: Language,
        targetLanguage: Language,
        glossary: List<GlossaryEntry>,
    ): String {
        val modeAddendum = when (mode) {
            InterpreterMode.PROFESSIONAL -> PROFESSIONAL_MODE_ADDENDUM
            InterpreterMode.CONVERSATION -> CONVERSATION_MODE_ADDENDUM
            InterpreterMode.ONE_WAY -> ONE_WAY_MODE_ADDENDUM
        }

        val languagePairing = """
The two active languages for this session are ${sourceLanguage.englishName} and
${targetLanguage.englishName}. Detect which of the two languages the speaker is
using and render your interpretation in the other one. Never translate a
language back into itself.
"""

        val glossarySection = buildGlossarySection(glossary)

        return buildString {
            append(BASE_INSTRUCTION.trim())
            append("\n\n")
            append(modeAddendum.trim())
            append("\n\n")
            append(languagePairing.trim())
            if (glossarySection != null) {
                append("\n\n")
                append(glossarySection)
            }
        }
    }

    /**
     * Renders glossary entries as interpreting guidance. Returns `null` when
     * the glossary is empty so callers don't inject an empty, token-wasting
     * section (spec §6: "do not resend unnecessarily large instructions").
     */
    fun buildGlossarySection(glossary: List<GlossaryEntry>): String? {
        if (glossary.isEmpty()) return null
        return buildString {
            append("SESSION GLOSSARY (interpreting guidance, not spoken content):\n")
            append(
                "The following term mappings were provided by the user for this " +
                    "session. Prefer these exact renderings whenever the source term " +
                    "recurs, without breaking target-language grammar:\n"
            )
            glossary.forEach { entry ->
                append("- \"${entry.sourceTerm}\" -> \"${entry.preferredTranslation}\"")
                if (!entry.note.isNullOrBlank()) append(" (${entry.note})")
                append("\n")
            }
        }.trim()
    }
}
