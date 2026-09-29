package au.prism.photos.ui.signin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.ActiveConnection
import au.prism.photos.domain.ConnectionKind
import au.prism.photos.domain.PlexConnection
import au.prism.photos.domain.PlexServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SignInUiState {
    data object Idle : SignInUiState()
    data class Waiting(val code: String) : SignInUiState()
    data object Signing : SignInUiState()
    data class Error(val message: String) : SignInUiState()
}

class SignInViewModel(private val graph: AppGraph) : ViewModel() {
    var state by mutableStateOf<SignInUiState>(SignInUiState.Idle)
        private set
    var manualError by mutableStateOf<String?>(null)
        private set
    var manualLoading by mutableStateOf(false)
        private set

    private var pollJob: Job? = null
    private var signedInCallback: (() -> Unit)? = null

    fun startPlexSignIn(onUrlReady: (String) -> Unit, onSignedIn: () -> Unit) {
        signedInCallback = onSignedIn
        viewModelScope.launch {
            state = SignInUiState.Idle
            graph.auth.createPin()
                .onSuccess { pin ->
                    val url = graph.auth.authUrl(pin)
                    state = SignInUiState.Waiting(pin.code)
                    onUrlReady(url)
                    poll(pin.id)
                }
                .onFailure { state = SignInUiState.Error(it.message ?: "Could not start sign in") }
        }
    }

    private fun poll(pinId: Long) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                val result = graph.auth.pollPin(pinId)
                val token = result.getOrNull()
                if (result.isFailure) {
                    state = SignInUiState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
                    return@launch
                }
                if (token != null) {
                    finishSignIn(token)
                    return@launch
                }
            }
        }
    }

    private suspend fun finishSignIn(token: String) {
        state = SignInUiState.Signing
        val userResult = graph.auth.user(token)
        val user = userResult.getOrNull()
        if (user == null) {
            state = SignInUiState.Error(userResult.exceptionOrNull()?.message ?: "Could not fetch your Plex account")
            return
        }
        graph.session.update { it.copy(accountToken = token, user = user) }
        state = SignInUiState.Idle
        signedInCallback?.invoke()
    }

    fun cancelWaiting() {
        pollJob?.cancel()
        state = SignInUiState.Idle
    }

    fun verifyManual(url: String, token: String, onConnected: () -> Unit) {
        viewModelScope.launch {
            manualLoading = true
            manualError = null
            val trimmedUrl = url.trim().trimEnd('/')
            graph.auth.verifyManual(trimmedUrl, token.trim())
                .onSuccess { name ->
                    val protocol = if (trimmedUrl.startsWith("https")) "https" else "http"
                    val server = PlexServer(
                        name = name,
                        clientIdentifier = "manual",
                        accessToken = token.trim(),
                        connections = listOf(PlexConnection(trimmedUrl, local = true, relay = false, protocol = protocol)),
                    )
                    graph.session.update {
                        it.copy(
                            serverToken = token.trim(),
                            active = ActiveConnection(trimmedUrl, ConnectionKind.MANUAL),
                            server = server,
                        )
                    }
                    manualLoading = false
                    onConnected()
                }
                .onFailure {
                    manualLoading = false
                    manualError = it.message ?: "Could not connect to that server"
                }
        }
    }
}
