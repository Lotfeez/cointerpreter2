package com.cointerpreter.app.engine

import com.cointerpreter.app.model.InterpreterMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the mode -> engine mapping documented in ARCHITECTURE.md:
 * Professional Mode gets the instructable general-purpose model (needed
 * for glossary/constitution injection); Conversation and One-Way Mode get
 * the purpose-built translation model.
 */
class InterpreterEngineFactoryTest {

    @Test
    fun `professional mode uses the general-purpose instructable engine`() {
        val engine = InterpreterEngineFactory.create(InterpreterMode.PROFESSIONAL)
        assertThat(engine.supportsCustomInstructions).isTrue()
        assertThat(engine.engineId).isEqualTo("gpt-realtime")
    }

    @Test
    fun `conversation mode uses the dedicated translation engine`() {
        val engine = InterpreterEngineFactory.create(InterpreterMode.CONVERSATION)
        assertThat(engine.supportsCustomInstructions).isFalse()
        assertThat(engine.engineId).isEqualTo("gpt-realtime-translate")
    }

    @Test
    fun `one-way mode uses the dedicated translation engine`() {
        val engine = InterpreterEngineFactory.create(InterpreterMode.ONE_WAY)
        assertThat(engine.supportsCustomInstructions).isFalse()
        assertThat(engine.engineId).isEqualTo("gpt-realtime-translate")
    }
}
