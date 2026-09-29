package au.prism.photos.ui.editor

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.editorDataStore by preferencesDataStore(name = "prism_editor_presets")
private val PRESETS_KEY = stringPreferencesKey("presets_json")
private val CLIPBOARD_KEY = stringPreferencesKey("clipboard_look_json")

private const val MAX_PRESETS = 20

/** Named "looks" (adjustments + filter + frame) saved by the user, plus a single "copy edits"
 * clipboard slot, both persisted with DataStore so they survive across editor sessions. */
class EditorPresetsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadPresets(): List<EditorPreset> = runCatching {
        val raw = context.editorDataStore.data.first()[PRESETS_KEY] ?: return emptyList()
        json.decodeFromString(ListSerializer(EditorPreset.serializer()), raw)
    }.getOrDefault(emptyList())

    suspend fun savePreset(name: String, look: EditLook): List<EditorPreset> {
        val current = loadPresets().toMutableList()
        val trimmedName = name.trim().ifBlank { "Preset ${current.size + 1}" }
        val preset = EditorPreset(id = System.currentTimeMillis().toString(), name = trimmedName, look = look)
        current.add(0, preset)
        while (current.size > MAX_PRESETS) current.removeAt(current.size - 1)
        persist(current)
        return current
    }

    suspend fun deletePreset(id: String): List<EditorPreset> {
        val current = loadPresets().filterNot { it.id == id }
        persist(current)
        return current
    }

    private suspend fun persist(presets: List<EditorPreset>) {
        runCatching {
            val encoded = json.encodeToString(ListSerializer(EditorPreset.serializer()), presets)
            context.editorDataStore.edit { it[PRESETS_KEY] = encoded }
        }
    }

    suspend fun copyLook(look: EditLook) {
        runCatching {
            context.editorDataStore.edit { it[CLIPBOARD_KEY] = json.encodeToString(look) }
        }
    }

    suspend fun pasteLook(): EditLook? = runCatching {
        val raw = context.editorDataStore.data.first()[CLIPBOARD_KEY] ?: return null
        json.decodeFromString<EditLook>(raw)
    }.getOrNull()
}
