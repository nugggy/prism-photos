package au.prism.photos.data

import au.prism.photos.domain.ActiveConnection
import au.prism.photos.domain.ConnectionKind
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.PlexConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A ranked connection candidate to probe. */
data class ConnectionCandidate(val uri: String, val kind: ConnectionKind)

private const val PROBE_TIMEOUT_MS = 2_500L

/**
 * Pure ranking function per docs/plex-api.md "Connection choosing rules". Given every
 * connection Plex reported for a server, returns the ordered list of candidates to try,
 * highest priority first. A manual URL, when set, always comes first regardless of mode.
 */
fun rankConnections(
    connections: List<PlexConnection>,
    mode: ConnectionMode,
    manualUrl: String?,
): List<ConnectionCandidate> {
    val manual = manualUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        ?.let { listOf(ConnectionCandidate(it, ConnectionKind.MANUAL)) }
        ?: emptyList()

    val candidates = if (mode == ConnectionMode.REMOTE) connections.filterNot { it.local } else connections
    val local = candidates.filter { it.local && !it.relay }
    val remote = candidates.filter { !it.local && !it.relay }
    val relay = candidates.filter { it.relay }

    // Tie-break: https before http, stable order otherwise.
    fun order(list: List<PlexConnection>) = list.sortedByDescending { it.protocol.equals("https", ignoreCase = true) }

    val ordered = when (mode) {
        ConnectionMode.AUTO, ConnectionMode.LAN -> order(local) + order(remote) + order(relay)
        ConnectionMode.REMOTE -> order(remote) + order(relay)
    }

    val kindOf: (PlexConnection) -> ConnectionKind = { c -> if (c.relay) ConnectionKind.RELAY else if (c.local) ConnectionKind.LOCAL else ConnectionKind.REMOTE }
    return manual + ordered.map { ConnectionCandidate(it.uri, kindOf(it)) }
}

/** Probes candidates and picks a reachable one, honouring the staged LAN behaviour. */
class ConnectionChooser(private val httpClient: OkHttpClient) {

    suspend fun choose(
        connections: List<PlexConnection>,
        mode: ConnectionMode,
        manualUrl: String?,
    ): ActiveConnection? {
        val all = rankConnections(connections, mode, manualUrl)
        return if (mode == ConnectionMode.LAN) {
            val stage1 = all.filter { it.kind == ConnectionKind.MANUAL || it.kind == ConnectionKind.LOCAL }
            val stage2 = all.filter { it.kind == ConnectionKind.REMOTE || it.kind == ConnectionKind.RELAY }
            probeAndPick(stage1) ?: probeAndPick(stage2)
        } else {
            probeAndPick(all)
        }
    }

    private suspend fun probeAndPick(candidates: List<ConnectionCandidate>): ActiveConnection? {
        if (candidates.isEmpty()) return null
        val reachable: List<Boolean> = coroutineScope {
            candidates.map { candidate ->
                async { withTimeoutOrNull(PROBE_TIMEOUT_MS) { probe(candidate.uri) } == true }
            }.awaitAll()
        }
        val chosen = candidates.indices.firstOrNull { reachable[it] }?.let { candidates[it] }
        return chosen?.let { ActiveConnection(it.uri, it.kind) }
    }

    private suspend fun probe(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("${uri.trimEnd('/')}/identity").header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
