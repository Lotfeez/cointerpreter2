package com.cointerpreter.app.data.transcript

import android.content.Context
import com.cointerpreter.app.model.Language
import com.cointerpreter.app.model.TranscriptEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Local-only transcript storage (spec §10, §17). CoInterpreter never uploads
 * saved transcripts to a cloud service, never retains them unless the user
 * opts to save, and offers explicit clear/delete actions.
 */
class TranscriptRepository(context: Context) {

    private val savedDir = File(context.filesDir, "transcripts").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _live = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val live: StateFlow<List<TranscriptEntry>> = _live.asStateFlow()

    fun appendOrUpdate(entry: TranscriptEntry) {
        val existingIndex = _live.value.indexOfFirst { it.id == entry.id }
        _live.value = if (existingIndex >= 0) {
            _live.value.toMutableList().also { it[existingIndex] = entry }
        } else {
            _live.value + entry
        }
    }

    fun clearLive() {
        _live.value = emptyList()
    }

    /** Returns the saved file name on success, so the UI can confirm what was written. */
    suspend fun saveCurrentTranscript(): String? = withContext(Dispatchers.IO) {
        val current = _live.value
        if (current.isEmpty()) return@withContext null
        val fileName = "transcript_${System.currentTimeMillis()}.json"
        val file = File(savedDir, fileName)
        file.writeText(
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TranscriptEntrySerializable.serializer()),
                current.map { TranscriptEntrySerializable.fromDomain(it) },
            )
        )
        fileName
    }

    suspend fun listSavedTranscripts(): List<String> = withContext(Dispatchers.IO) {
        savedDir.listFiles()?.map { it.name }?.sortedDescending() ?: emptyList()
    }

    suspend fun deleteSavedTranscript(fileName: String) = withContext(Dispatchers.IO) {
        File(savedDir, fileName).delete()
        Unit
    }
}

@kotlinx.serialization.Serializable
private data class TranscriptEntrySerializable(
    val id: String,
    val timestampMillis: Long,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val originalText: String,
    val translatedText: String,
    val isFinal: Boolean,
) {
    companion object {
        fun fromDomain(entry: TranscriptEntry) = TranscriptEntrySerializable(
            id = entry.id,
            timestampMillis = entry.timestampMillis,
            sourceLanguageCode = entry.sourceLanguage.code,
            targetLanguageCode = entry.targetLanguage.code,
            originalText = entry.originalText,
            translatedText = entry.translatedText,
            isFinal = entry.isFinal,
        )
    }

    fun toDomain() = TranscriptEntry(
        id = id,
        timestampMillis = timestampMillis,
        sourceLanguage = Language.byCode(sourceLanguageCode),
        targetLanguage = Language.byCode(targetLanguageCode),
        originalText = originalText,
        translatedText = translatedText,
        isFinal = isFinal,
    )
}
