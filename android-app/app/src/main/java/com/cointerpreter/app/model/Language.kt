package com.cointerpreter.app.model

/**
 * A language supported by CoInterpreter's UI. [code] is the ISO-639-1 (or
 * BCP-47-ish) tag CoInterpreter sends to the realtime engine, e.g. in
 * `session.audio.output.language` for gpt-realtime-translate.
 *
 * [isRtl] drives Compose `LayoutDirection` and bidi handling for transcript
 * rendering; it is never used to fake RTL by only right-aligning text.
 */
data class Language(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val isRtl: Boolean,
) {
    companion object {
        val ARABIC = Language("ar", "Arabic", "العربية", isRtl = true)
        val ENGLISH = Language("en", "English", "English", isRtl = false)
        val FRENCH = Language("fr", "French", "Français", isRtl = false)

        /** First-class languages shown at the top of every language picker. */
        val FIRST_CLASS = listOf(ARABIC, ENGLISH, FRENCH)

        /**
         * Broader catalog. gpt-realtime-translate documents 70+ recognized
         * input languages and 13 output languages; this list intentionally
         * stays conservative and only includes languages the interpreter
         * constitution and glossary UX have been reviewed against. Extend
         * once additional languages are UX/QA reviewed (see UX review notes
         * in BUILD_REPORT.md).
         */
        val EXTENDED = FIRST_CLASS + listOf(
            Language("es", "Spanish", "Español", isRtl = false),
            Language("de", "German", "Deutsch", isRtl = false),
            Language("it", "Italian", "Italiano", isRtl = false),
            Language("pt", "Portuguese", "Português", isRtl = false),
            Language("tr", "Turkish", "Türkçe", isRtl = false),
            Language("ur", "Urdu", "اردو", isRtl = true),
            Language("fa", "Persian", "فارسی", isRtl = true),
            Language("hi", "Hindi", "हिन्दी", isRtl = false),
            Language("zh", "Chinese", "中文", isRtl = false),
            Language("ja", "Japanese", "日本語", isRtl = false),
            Language("ko", "Korean", "한국어", isRtl = false),
            Language("ru", "Russian", "Русский", isRtl = false),
        )

        fun byCode(code: String): Language =
            EXTENDED.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/** The three core interpreting modes described in the product spec. */
enum class InterpreterMode {
    CONVERSATION,
    ONE_WAY,
    PROFESSIONAL,
}

/** A single glossary entry the user supplies before or during a session. */
data class GlossaryEntry(
    val id: String,
    val sourceTerm: String,
    val preferredTranslation: String,
    val note: String? = null,
)

/** One line of interpreted speech, shown in both original and translated form. */
data class TranscriptEntry(
    val id: String,
    val timestampMillis: Long,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val originalText: String,
    val translatedText: String,
    val isFinal: Boolean,
)
