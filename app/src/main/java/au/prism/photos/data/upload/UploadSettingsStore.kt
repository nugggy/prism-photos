package au.prism.photos.data.upload

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.data.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Credential storage: the whole settings JSON (which includes the SMB/WebDAV username and
// password) is encrypted with an Android Keystore AES-GCM key by SecretCipher before it is written,
// and the file is excluded from cloud backup and device transfer (res/xml/backup_rules.xml and
// data_extraction_rules.xml). Plaintext settings from older versions are re-saved encrypted on
// first read.
private val Context.uploadSettingsDataStore by preferencesDataStore(name = "prism_upload_settings")
private val SETTINGS_KEY = stringPreferencesKey("sync_settings_json")

class UploadSettingsStore(private val app: Application) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val state: MutableStateFlow<SyncSettings>

    init {
        var needsSave = false
        val loaded = runBlocking {
            runCatching {
                val prefs = app.uploadSettingsDataStore.data.first()
                prefs[SETTINGS_KEY]?.let { stored ->
                    val (text, wasPlain) = SecretCipher.read(stored) ?: return@let null
                    needsSave = wasPlain
                    json.decodeFromString<SyncSettings>(text)
                }
            }.getOrNull()
        }
        state = MutableStateFlow(loaded ?: SyncSettings())
        if (loaded != null && needsSave) runBlocking { persist(loaded) }
    }

    val settings: StateFlow<SyncSettings> = state.asStateFlow()

    /** Serialises read-modify-write: the Settings screen and the sync worker both update this. */
    private val mutex = Mutex()

    suspend fun update(transform: (SyncSettings) -> SyncSettings) {
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            persist(next)
        }
    }

    private suspend fun persist(settings: SyncSettings) {
        runCatching {
            val stored = SecretCipher.encryptOrPlain(json.encodeToString(settings))
            app.uploadSettingsDataStore.edit { it[SETTINGS_KEY] = stored }
        }
    }
}
