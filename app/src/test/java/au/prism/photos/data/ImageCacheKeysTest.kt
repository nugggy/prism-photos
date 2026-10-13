package au.prism.photos.data

import au.prism.photos.data.plex.PlexUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageCacheKeysTest {
    private val lan = "http://192.168.1.20:32400"
    private val remote = "https://203-0-113-5.abcdef.plex.direct:32400"

    @Test
    fun `token and host are stripped, path and size kept`() {
        val url = PlexUrls.thumb(lan, "/library/metadata/1/thumb/170", "secret-token", 300)
        assertEquals(
            "/photo/:/transcode?width=300&height=300&minSize=1&upscale=1&url=%2Flibrary%2Fmetadata%2F1%2Fthumb%2F170",
            ImageCacheKeys.stripHostAndToken(url),
        )
    }

    @Test
    fun `same thumbnail over LAN and remote with different tokens shares one key`() {
        val viaLan = PlexUrls.thumb(lan, "/library/metadata/1/thumb/170", "token-a", 300)
        val viaRemote = PlexUrls.thumb(remote, "/library/metadata/1/thumb/170", "token-b", 300)
        val a = ImageCacheKeys.stableKey(viaLan, "server1", lan)
        val b = ImageCacheKeys.stableKey(viaRemote, "server1", remote)
        assertEquals(a, b)
    }

    @Test
    fun `different sizes keep different keys`() {
        val small = ImageCacheKeys.stableKey(PlexUrls.thumb(lan, "/t", "x", 200), "s", lan)
        val large = ImageCacheKeys.stableKey(PlexUrls.thumb(lan, "/t", "x", 800), "s", lan)
        assertEquals(false, small == large)
    }

    @Test
    fun `token in the middle of the query is removed`() {
        assertEquals(
            "/library/parts/5/file.jpg?download=1",
            ImageCacheKeys.stripHostAndToken("$lan/library/parts/5/file.jpg?X-Plex-Token=abc&download=1"),
        )
        assertEquals("/library/parts/5/file.jpg", ImageCacheKeys.stripHostAndToken("$lan/library/parts/5/file.jpg?X-Plex-Token=abc"))
    }

    @Test
    fun `urls for another host or without a server keep the default key`() {
        assertNull(ImageCacheKeys.stableKey("https://plex.tv/users/avatar.png", "server1", lan))
        assertNull(ImageCacheKeys.stableKey("$lan/x?X-Plex-Token=a", null, lan))
        assertNull(ImageCacheKeys.stableKey("content://media/external/images/media/1", "server1", lan))
    }

    @Test
    fun `server id keeps servers apart`() {
        val url = "$lan/library/metadata/1/thumb/1?X-Plex-Token=a"
        assertEquals(false, ImageCacheKeys.stableKey(url, "server1", lan) == ImageCacheKeys.stableKey(url, "server2", lan))
    }
}
