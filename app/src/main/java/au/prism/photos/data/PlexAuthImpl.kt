package au.prism.photos.data

import au.prism.photos.data.plex.PlexTvApi
import au.prism.photos.data.plex.RootContainerDto
import au.prism.photos.domain.ActiveConnection
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.PlexAuth
import au.prism.photos.domain.PlexConnection
import au.prism.photos.domain.PlexPin
import au.prism.photos.domain.PlexServer
import au.prism.photos.domain.PlexUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class PlexAuthImpl(
    private val plexTvApi: PlexTvApi,
    private val connectionChooser: ConnectionChooser,
    private val plainHttpClient: OkHttpClient,
    private val json: Json,
    private val clientIdProvider: () -> String,
) : PlexAuth {

    override suspend fun createPin(): Result<PlexPin> = runCatching {
        val dto = plexTvApi.createPin(strong = true)
        PlexPin(id = dto.id, code = dto.code)
    }

    override fun authUrl(pin: PlexPin): String =
        "https://app.plex.tv/auth#?clientID=${clientIdProvider()}&code=${pin.code}&context%5Bdevice%5D%5Bproduct%5D=Plex%20Gallery"

    override suspend fun pollPin(pinId: Long): Result<String?> = runCatching {
        plexTvApi.getPin(pinId).authToken
    }

    override suspend fun user(accountToken: String): Result<PlexUser> = runCatching {
        val dto = plexTvApi.user(accountToken)
        PlexUser(id = dto.id, username = dto.username, email = dto.email, thumb = dto.thumb, title = dto.title?.ifBlank { null } ?: dto.username)
    }

    override suspend fun servers(accountToken: String): Result<List<PlexServer>> = runCatching {
        plexTvApi.resources(accountToken)
            .filter { it.provides.split(",").map { p -> p.trim() }.contains("server") }
            .map { r ->
                PlexServer(
                    name = r.name,
                    clientIdentifier = r.clientIdentifier,
                    accessToken = r.accessToken.orEmpty(),
                    connections = r.connections.map { c -> PlexConnection(c.uri, c.local, c.relay, c.protocol, c.address, c.port) },
                    owned = r.owned,
                    version = r.productVersion,
                    platform = r.platform,
                )
            }
    }

    override suspend fun connect(server: PlexServer, mode: ConnectionMode, manualUrl: String?): Result<ActiveConnection> = runCatching {
        connectionChooser.choose(server.connections, mode, manualUrl)
            ?: throw IOException("Couldn't reach ${server.name}. Check the server is online and reachable.")
    }

    override suspend fun verifyManual(url: String, token: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val clean = url.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$clean/?X-Plex-Token=$token")
                .header("Accept", "application/json")
                .build()
            plainHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server responded with HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<RootContainerDto>(body)
                parsed.mediaContainer.friendlyName?.takeIf { it.isNotBlank() } ?: "Plex Server"
            }
        }
    }

    override suspend fun signOut(accountToken: String?) {
        if (accountToken.isNullOrBlank()) return
        runCatching { plexTvApi.signout(accountToken) }
    }
}
