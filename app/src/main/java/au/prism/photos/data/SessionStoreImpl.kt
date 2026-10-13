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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "prism_session")
private val SESSION_KEY = stringPreferencesKey("session_json")

/**
 * Session persisted to DataStore as JSON, encrypted with [SecretCipher] because it holds the Plex
 * account and server tokens. Reads synchronously (runBlocking) on init so [session] is already
 * correct - including a stable, persisted client identifier - the moment the app graph is built,
 * before any network call needs it. A plaintext session from an older version is re-saved
 * encrypted on first read.
 */
class SessionStoreImpl(private val app: Application) : SessionStore {
    private val json = Json { ignoreUnknownKeys = true }

    /** Serialises read-modify-write so concurrent updates cannot overwrite each other. */
    private val mutex = Mutex()

    private val state: MutableStateFlow<Session>

    init {
        var needsSave = false
        val loaded = runBlocking {
            runCatching {
                val prefs = app.sessionDataStore.data.first()
                prefs[SESSION_KEY]?.let { stored ->
                    val (text, wasPlain) = SecretCipher.read(stored) ?: return@let null
                    needsSave = wasPlain
                    json.decodeFromString<Session>(text)
                }
            }.getOrNull()
        }
        val initial = when {
            loaded == null -> Session(clientId = UUID.randomUUID().toString())
            loaded.clientId.isBlank() -> loaded.copy(clientId = UUID.randomUUID().toString())
            else -> loaded
        }
        state = MutableStateFlow(initial)
        if (loaded == null || loaded.clientId.isBlank() || needsSave) {
            runBlocking { persist(initial) }
        }
    }

    override val session: StateFlow<Session> = state.asStateFlow()

    override suspend fun update(transform: (Session) -> Session) {
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            persist(next)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            val next = Session(clientId = state.value.clientId)
            state.value = next
            persist(next)
        }
    }

    private suspend fun persist(session: Session) {
        runCatching {
            val stored = SecretCipher.encryptOrPlain(json.encodeToString(session))
            app.sessionDataStore.edit { it[SESSION_KEY] = stored }
        }
    }
}
