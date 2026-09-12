package com.cointerpreter.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cointerpreter_settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val defaultLanguageA: Language = Language.ARABIC,
    val defaultLanguageB: Language = Language.ENGLISH,
    val defaultMode: InterpreterMode = InterpreterMode.CONVERSATION,
    val voice: String = "alloy",
    val professionalModeDefault: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val autoSaveTranscripts: Boolean = true,
    val transcriptTextScale: Float = 1.0f,
    val theme: AppTheme = AppTheme.SYSTEM,
)

/**
 * Settings persist only non-sensitive preferences (spec §21, §22): never an
 * API key, never raw audio. Backed by Jetpack DataStore rather than Room
 * since there is no relational data here (spec §22: "avoid adding databases
 * unnecessarily").
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANG_A = stringPreferencesKey("lang_a")
        val LANG_B = stringPreferencesKey("lang_b")
        val MODE = stringPreferencesKey("mode")
        val VOICE = stringPreferencesKey("voice")
        val PROFESSIONAL_DEFAULT = booleanPreferencesKey("professional_default")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_transcripts")
        val TEXT_SCALE = intPreferencesKey("transcript_text_scale_pct")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultLanguageA = prefs[Keys.LANG_A]?.let(Language::byCode) ?: Language.ARABIC,
            defaultLanguageB = prefs[Keys.LANG_B]?.let(Language::byCode) ?: Language.ENGLISH,
            defaultMode = prefs[Keys.MODE]?.let { runCatching { InterpreterMode.valueOf(it) }.getOrNull() }
                ?: InterpreterMode.CONVERSATION,
            voice = prefs[Keys.VOICE] ?: "alloy",
            professionalModeDefault = prefs[Keys.PROFESSIONAL_DEFAULT] ?: false,
            keepScreenAwake = prefs[Keys.KEEP_AWAKE] ?: true,
            autoSaveTranscripts = prefs[Keys.AUTO_SAVE] ?: true,
            transcriptTextScale = (prefs[Keys.TEXT_SCALE] ?: 100) / 100f,
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.LANG_A] = next.defaultLanguageA.code
            prefs[Keys.LANG_B] = next.defaultLanguageB.code
            prefs[Keys.MODE] = next.defaultMode.name
            prefs[Keys.VOICE] = next.voice
            prefs[Keys.PROFESSIONAL_DEFAULT] = next.professionalModeDefault
            prefs[Keys.KEEP_AWAKE] = next.keepScreenAwake
            prefs[Keys.AUTO_SAVE] = next.autoSaveTranscripts
            prefs[Keys.TEXT_SCALE] = (next.transcriptTextScale * 100).toInt()
            prefs[Keys.THEME] = next.theme.name
        }
    }
}
