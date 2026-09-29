package au.prism.photos.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.domain.Session
import au.prism.photos.domain.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "prism_session")
private val SESSION_KEY = stringPreferencesKey("session_json")

/**
 * Session persisted to DataStore as JSON. Reads synchronously (runBlocking) on init so
 * [session] is already correct - including a stable, persisted client identifier - the
 * moment the app graph is built, before any network call needs it.
 */
class SessionStoreImpl(private val app: Application) : SessionStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val state: MutableStateFlow<Session>

    init {
        val loaded = runBlocking {
            runCatching {
                val prefs = app.sessionDataStore.data.first()
                prefs[SESSION_KEY]?.let { json.decodeFromString<Session>(it) }
            }.getOrNull()
        }
        val initial = when {
            loaded == null -> Session(clientId = UUID.randomUUID().toString())
            loaded.clientId.isBlank() -> loaded.copy(clientId = UUID.randomUUID().toString())
            else -> loaded
        }
        state = MutableStateFlow(initial)
        if (loaded == null || loaded.clientId.isBlank()) {
            runBlocking { persist(initial) }
        }
    }

    override val session: StateFlow<Session> = state.asStateFlow()

    override suspend fun update(transform: (Session) -> Session) {
        val next = transform(state.value)
        state.value = next
        persist(next)
    }

    override suspend fun clear() {
        val next = Session(clientId = state.value.clientId)
        state.value = next
        persist(next)
    }

    private suspend fun persist(session: Session) {
        runCatching {
            app.sessionDataStore.edit { it[SESSION_KEY] = json.encodeToString(session) }
        }
    }
}
