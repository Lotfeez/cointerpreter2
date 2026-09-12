package com.cointerpreter.app.engine

import com.cointerpreter.app.engine.openai.OpenAiGptRealtimeEngine
import com.cointerpreter.app.engine.openai.OpenAiRealtimeTranslateEngine
import com.cointerpreter.app.model.InterpreterMode

/**
 * Chooses a [RealtimeInterpreterEngine] implementation for a given mode.
 * This is the single place that knows both engines exist, which is what
 * keeps the rest of the app decoupled from any one OpenAI model id
 * (spec §1).
 */
object InterpreterEngineFactory {

    fun create(mode: InterpreterMode): RealtimeInterpreterEngine = when (mode) {
        // Professional Mode needs the session glossary and the full
        // interpreter constitution enforced as an editable instruction, so
        // it always uses the general-purpose model.
        InterpreterMode.PROFESSIONAL -> OpenAiGptRealtimeEngine(modelId = "gpt-realtime")

        // Conversation and One-Way Mode default to the purpose-built
        // translation model: lower latency, naturally tone-matched voice,
        // and structurally incapable of answering instead of translating.
        InterpreterMode.CONVERSATION,
        InterpreterMode.ONE_WAY,
        -> OpenAiRealtimeTranslateEngine()
    }
}
