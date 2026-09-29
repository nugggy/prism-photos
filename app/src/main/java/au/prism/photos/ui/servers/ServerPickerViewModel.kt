package au.prism.photos.ui.servers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.ActiveConnection
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.PlexServer
import kotlinx.coroutines.launch

sealed class ProbeStatus {
    data object Checking : ProbeStatus()
    data class Ok(val active: ActiveConnection) : ProbeStatus()
    data class Failed(val message: String) : ProbeStatus()
}

class ServerPickerViewModel(private val graph: AppGraph) : ViewModel() {
    var servers by mutableStateOf<List<PlexServer>>(emptyList())
        private set
    var statuses by mutableStateOf<Map<String, ProbeStatus>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var connectionMode by mutableStateOf(graph.settings.settings.value.connectionMode)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            val token = graph.session.session.value.accountToken
            if (token == null) {
                error = "Not signed in"
                loading = false
                return@launch
            }
            graph.auth.servers(token)
                .onSuccess { list ->
                    servers = list
                    loading = false
                    probeAll()
                }
                .onFailure {
                    error = it.message ?: "Could not load servers"
                    loading = false
                }
        }
    }

    private fun probeAll() {
        val mode = connectionMode
        val manual = graph.settings.settings.value.manualServerUrl.ifBlank { null }
        servers.forEach { server ->
            statuses = statuses + (server.clientIdentifier to ProbeStatus.Checking)
            viewModelScope.launch {
                graph.auth.connect(server, mode, manual)
                    .onSuccess { active -> statuses = statuses + (server.clientIdentifier to ProbeStatus.Ok(active)) }
                    .onFailure { statuses = statuses + (server.clientIdentifier to ProbeStatus.Failed(it.message ?: "Unreachable")) }
            }
        }
    }

    fun changeConnectionMode(mode: ConnectionMode) {
        connectionMode = mode
        viewModelScope.launch { graph.settings.update { it.copy(connectionMode = mode) } }
        statuses = emptyMap()
        probeAll()
    }

    fun select(server: PlexServer, onDone: () -> Unit) {
        viewModelScope.launch {
            val known = statuses[server.clientIdentifier] as? ProbeStatus.Ok
            val active = known?.active
                ?: graph.auth.connect(server, connectionMode, graph.settings.settings.value.manualServerUrl.ifBlank { null }).getOrNull()
            if (active == null) {
                statuses = statuses + (server.clientIdentifier to ProbeStatus.Failed("Could not reach this server"))
                return@launch
            }
            graph.session.update { it.copy(server = server, serverToken = server.accessToken, active = active) }
            onDone()
        }
    }
}
