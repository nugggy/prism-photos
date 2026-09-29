package au.prism.photos.data

import au.prism.photos.domain.ConnectionKind
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.PlexConnection
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionChooserTest {

    private val localHttps = PlexConnection(uri = "https://192-168-1-20.abcdef.plex.direct:32400", local = true, relay = false, protocol = "https")
    private val localHttp = PlexConnection(uri = "http://192.168.1.20:32400", local = true, relay = false, protocol = "http")
    private val remote = PlexConnection(uri = "https://203-0-113-5.abcdef.plex.direct:32400", local = false, relay = false, protocol = "https")
    private val relay = PlexConnection(uri = "https://abcdef.plex.direct:443", local = false, relay = true, protocol = "https")

    private val allConnections = listOf(localHttp, localHttps, remote, relay)

    @Test
    fun `AUTO ranks local non-relay first then remote then relay, https wins ties`() {
        val ranked = rankConnections(allConnections, ConnectionMode.AUTO, manualUrl = null)
        assertEquals(
            listOf(localHttps.uri, localHttp.uri, remote.uri, relay.uri),
            ranked.map { it.uri },
        )
        assertEquals(ConnectionKind.LOCAL, ranked[0].kind)
        assertEquals(ConnectionKind.REMOTE, ranked[2].kind)
        assertEquals(ConnectionKind.RELAY, ranked[3].kind)
    }

    @Test
    fun `LAN ranks all connections but keeps local ahead of remote and relay`() {
        val ranked = rankConnections(allConnections, ConnectionMode.LAN, manualUrl = null)
        assertEquals(
            listOf(localHttps.uri, localHttp.uri, remote.uri, relay.uri),
            ranked.map { it.uri },
        )
    }

    @Test
    fun `REMOTE skips local connections entirely`() {
        val ranked = rankConnections(allConnections, ConnectionMode.REMOTE, manualUrl = null)
        assertEquals(listOf(remote.uri, relay.uri), ranked.map { it.uri })
        assertEquals(ConnectionKind.REMOTE, ranked[0].kind)
        assertEquals(ConnectionKind.RELAY, ranked[1].kind)
    }

    @Test
    fun `manual URL is always ranked first in every mode`() {
        val manual = "http://192.168.1.99:32400"
        for (mode in ConnectionMode.entries) {
            val ranked = rankConnections(allConnections, mode, manualUrl = manual)
            assertEquals("mode=$mode", manual, ranked.first().uri)
            assertEquals("mode=$mode", ConnectionKind.MANUAL, ranked.first().kind)
        }
    }

    @Test
    fun `blank manual url is ignored`() {
        val ranked = rankConnections(allConnections, ConnectionMode.AUTO, manualUrl = "  ")
        assertEquals(localHttps.uri, ranked.first().uri)
    }

    @Test
    fun `no connections yields empty ranking`() {
        val ranked = rankConnections(emptyList(), ConnectionMode.AUTO, manualUrl = null)
        assertEquals(emptyList<String>(), ranked.map { it.uri })
    }
}
