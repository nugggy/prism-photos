package au.prism.photos.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.domain.AppSettings
import au.prism.photos.domain.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "prism_settings")
private val SETTINGS_KEY = stringPreferencesKey("settings_json")

class SettingsStoreImpl(private val app: Application) : SettingsStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val state: MutableStateFlow<AppSettings>

    init {
        val loaded = runBlocking {
            runCatching {
                val prefs = app.settingsDataStore.data.first()
                prefs[SETTINGS_KEY]?.let { json.decodeFromString<AppSettings>(it) }
            }.getOrNull()
        }
        state = MutableStateFlow(loaded ?: AppSettings())
    }

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        state.value = next
        runCatching { app.settingsDataStore.edit { it[SETTINGS_KEY] = json.encodeToString(next) } }
    }
}
