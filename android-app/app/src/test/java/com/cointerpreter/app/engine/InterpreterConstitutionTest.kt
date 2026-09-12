package com.cointerpreter.app.engine

import com.cointerpreter.app.model.GlossaryEntry
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Directly tests the behavioral requirement the whole product spec calls
 * "the critical test": an interpreter must translate a question, never
 * answer it (TEST 2). Also covers glossary precedence (TEST 8) and mode
 * addenda.
 */
class InterpreterConstitutionTest {

    @Test
    fun `constitution forbids answering instead of translating`() {
        val instructions = InterpreterConstitution.buildInstructions(
            mode = InterpreterMode.CONVERSATION,
            sourceLanguage = Language.ENGLISH,
            targetLanguage = Language.ARABIC,
            glossary = emptyList(),
        )
        assertThat(instructions).contains("Never answer questions addressed by one speaker to another")
        assertThat(instructions).contains("Do NOT answer when the Minister will arrive")
    }

    @Test
    fun `constitution forbids the model becoming a conversation participant`() {
        val instructions = InterpreterConstitution.buildInstructions(
            InterpreterMode.CONVERSATION, Language.ARABIC, Language.ENGLISH, emptyList(),
        )
        assertThat(instructions).contains("Never participate in the conversation")
        assertThat(instructions).contains("Never provide advice")
        assertThat(instructions).contains("Never add factual information")
        assertThat(instructions).contains("Never express your own opinion")
    }

    @Test
    fun `professional mode addendum favors terminology precedence`() {
        val professional = InterpreterConstitution.buildInstructions(
            InterpreterMode.PROFESSIONAL, Language.ARABIC, Language.FRENCH, emptyList(),
        )
        val conversation = InterpreterConstitution.buildInstructions(
            InterpreterMode.CONVERSATION, Language.ARABIC, Language.FRENCH, emptyList(),
        )
        assertThat(professional).contains("formal professional, governmental")
        assertThat(conversation).doesNotContain("formal professional, governmental")
    }

    @Test
    fun `empty glossary produces no section to avoid wasting tokens`() {
        assertThat(InterpreterConstitution.buildGlossarySection(emptyList())).isNull()
    }

    @Test
    fun `glossary entries are injected as interpreting guidance and preserved verbatim`() {
        val glossary = listOf(
            GlossaryEntry(id = "1", sourceTerm = "وزارة العمل", preferredTranslation = "Ministry of Labour"),
            GlossaryEntry(id = "2", sourceTerm = "المنافتف", preferredTranslation = "MENAFATF", note = "regional FATF body"),
        )
        val section = InterpreterConstitution.buildGlossarySection(glossary)
        assertThat(section).isNotNull()
        assertThat(section).contains("Ministry of Labour")
        assertThat(section).contains("MENAFATF")
        assertThat(section).contains("regional FATF body")
        assertThat(section).contains("interpreting guidance")

        val fullInstructions = InterpreterConstitution.buildInstructions(
            InterpreterMode.PROFESSIONAL, Language.ARABIC, Language.ENGLISH, glossary,
        )
        assertThat(fullInstructions).contains("Ministry of Labour")
    }

    @Test
    fun `instructions state both active languages and forbid translating a language into itself`() {
        val instructions = InterpreterConstitution.buildInstructions(
            InterpreterMode.ONE_WAY, Language.FRENCH, Language.ARABIC, emptyList(),
        )
        assertThat(instructions).contains("French")
        assertThat(instructions).contains("Arabic")
        assertThat(instructions).contains("Never translate a")
    }
}
