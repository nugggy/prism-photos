package au.prism.photos.data.upload

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// NOTE ON CREDENTIAL STORAGE: androidx.security's EncryptedSharedPreferences would be the
// preferred place for the SMB/WebDAV username and password, but the security-crypto artifact is
// not in this project's pinned version catalog (gradle/libs.versions.toml) and the brief for this
// change asked not to bump or add dependencies unless essential. Falling back to a plain DataStore
// file, which Android still keeps in the app's private, sandboxed storage (mode 0600, not
// readable by other apps without root) - it is not full at-rest encryption though. If
// androidx.security-crypto is added to the catalog later, swap this DataStore for
// EncryptedSharedPreferences without changing the public surface of this class.
private val Context.uploadSettingsDataStore by preferencesDataStore(name = "prism_upload_settings")
private val SETTINGS_KEY = stringPreferencesKey("sync_settings_json")

class UploadSettingsStore(private val app: Application) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val state: MutableStateFlow<SyncSettings>

    init {
        val loaded = runBlocking {
            runCatching {
                val prefs = app.uploadSettingsDataStore.data.first()
                prefs[SETTINGS_KEY]?.let { json.decodeFromString<SyncSettings>(it) }
            }.getOrNull()
        }
        state = MutableStateFlow(loaded ?: SyncSettings())
    }

    val settings: StateFlow<SyncSettings> = state.asStateFlow()

    suspend fun update(transform: (SyncSettings) -> SyncSettings) {
        val next = transform(state.value)
        state.value = next
        runCatching { app.uploadSettingsDataStore.edit { it[SETTINGS_KEY] = json.encodeToString(next) } }
    }
}
