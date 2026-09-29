package au.prism.photos.data

import au.prism.photos.data.plex.PlexUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexUrlsTest {
    private val server = "https://192-168-1-20.abcdef.plex.direct:32400"
    private val token = "abc123"

    @Test
    fun `thumb builds transcode url with size and encoded path`() {
        val url = PlexUrls.thumb(server, "/library/metadata/1234/thumb/1700000000", token, 400)
        assertEquals(
            "$server/photo/:/transcode?width=400&height=400&minSize=1&upscale=1&url=%2Flibrary%2Fmetadata%2F1234%2Fthumb%2F1700000000&X-Plex-Token=$token",
            url,
        )
    }

    @Test
    fun `original url appends token to part key`() {
        val url = PlexUrls.original(server, "/library/parts/5678/1700000000/file.jpg", token)
        assertEquals("$server/library/parts/5678/1700000000/file.jpg?X-Plex-Token=$token", url)
    }

    @Test
    fun `download url adds download param`() {
        val url = PlexUrls.download(server, "/library/parts/5678/1700000000/file.jpg", token)
        assertEquals("$server/library/parts/5678/1700000000/file.jpg?X-Plex-Token=$token&download=1", url)
    }

    @Test
    fun `direct play matches original`() {
        val partKey = "/library/parts/5678/1700000000/file.jpg"
        assertEquals(PlexUrls.original(server, partKey, token), PlexUrls.directPlay(server, partKey, token))
    }

    @Test
    fun `hls transcode url contains required params`() {
        val url = PlexUrls.hlsTranscode(server, "1234", token, "client-id", "Android", "session-1")
        assertTrue(url.startsWith("$server/video/:/transcode/universal/start.m3u8?"))
        assertTrue(url.contains("path=%2Flibrary%2Fmetadata%2F1234"))
        assertTrue(url.contains("protocol=hls"))
        assertTrue(url.contains("directPlay=0"))
        assertTrue(url.contains("directStream=1"))
        assertTrue(url.contains("session=session-1"))
        assertTrue(url.contains("X-Plex-Client-Identifier=client-id"))
        assertTrue(url.contains("X-Plex-Platform=Android"))
        assertTrue(url.contains("X-Plex-Product=Prism"))
        assertTrue(url.contains("X-Plex-Token=$token"))
    }

    @Test
    fun `stop transcode url has session and token`() {
        val url = PlexUrls.stopTranscode(server, "session-1", token)
        assertTrue(url.startsWith("$server/video/:/transcode/universal/stop?"))
        assertTrue(url.contains("session=session-1"))
        assertTrue(url.contains("X-Plex-Token=$token"))
    }
}
