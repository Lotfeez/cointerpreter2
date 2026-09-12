package com.cointerpreter.app.data.glossary

import android.content.Context
import com.cointerpreter.app.model.GlossaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manages the session glossary described in spec §6: add / edit / delete /
 * clear, persisted locally (only if the user chooses to keep it across
 * sessions) and never sent anywhere except as interpreting guidance inside
 * an OpenAI Realtime session's instructions (see [com.cointerpreter.app.engine.InterpreterConstitution]).
 */
class GlossaryRepository(context: Context) {

    private val storageFile = File(context.filesDir, "glossary.json")
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<GlossaryEntry>>(emptyList())
    val entries: StateFlow<List<GlossaryEntry>> = _entries.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext
        runCatching {
            val text = storageFile.readText()
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(GlossaryEntrySerializable.serializer()),
                text,
            )
            _entries.value = list.map { it.toDomain() }
        }
    }

    suspend fun add(sourceTerm: String, preferredTranslation: String, note: String? = null) {
        val entry = GlossaryEntry(id = UUID.randomUUID().toString(), sourceTerm = sourceTerm, preferredTranslation = preferredTranslation, note = note)
        _entries.value = _entries.value + entry
        persist()
    }

    suspend fun edit(id: String, sourceTerm: String, preferredTranslation: String, note: String?) {
        _entries.value = _entries.value.map {
            if (it.id == id) it.copy(sourceTerm = sourceTerm, preferredTranslation = preferredTranslation, note = note) else it
        }
        persist()
    }

    suspend fun delete(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        persist()
    }

    suspend fun clear() {
        _entries.value = emptyList()
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val serializable = _entries.value.map { GlossaryEntrySerializable.fromDomain(it) }
        val text = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(GlossaryEntrySerializable.serializer()),
            serializable,
        )
        storageFile.writeText(text)
    }
}

@kotlinx.serialization.Serializable
private data class GlossaryEntrySerializable(
    val id: String,
    val sourceTerm: String,
    val preferredTranslation: String,
    val note: String? = null,
) {
    fun toDomain() = GlossaryEntry(id, sourceTerm, preferredTranslation, note)

    companion object {
        fun fromDomain(entry: GlossaryEntry) =
            GlossaryEntrySerializable(entry.id, entry.sourceTerm, entry.preferredTranslation, entry.note)
    }
}
