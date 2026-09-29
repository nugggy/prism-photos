package au.prism.photos.data

import au.prism.photos.data.plex.GithubReleaseDto
import au.prism.photos.data.plex.MetadataContainerDto
import au.prism.photos.data.plex.PinDto
import au.prism.photos.data.plex.PlexTvUserDto
import au.prism.photos.data.plex.ResourceDto
import au.prism.photos.data.plex.SectionsContainerDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexDtosTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `parses pin response`() {
        val text = """{ "id": 123, "code": "abcd1234", "expiresAt": "2026-09-29T12:00:00Z", "authToken": null }"""
        val pin = json.decodeFromString<PinDto>(text)
        assertEquals(123L, pin.id)
        assertEquals("abcd1234", pin.code)
        assertNull(pin.authToken)
    }

    @Test
    fun `parses user response`() {
        val text = """{ "id": 42, "uuid": "u-1", "username": "demo", "email": "demo@example.com", "thumb": null, "title": "Demo" }"""
        val user = json.decodeFromString<PlexTvUserDto>(text)
        assertEquals(42L, user.id)
        assertEquals("demo", user.username)
    }

    @Test
    fun `parses resources with connections`() {
        val text = """
        [{
          "name": "Home Server", "product": "Plex Media Server", "productVersion": "1.41.0",
          "platform": "Linux", "clientIdentifier": "abc123", "provides": "server", "owned": true,
          "accessToken": "server-scoped-token", "publicAddress": "203.0.113.5", "httpsRequired": false,
          "connections": [
            { "protocol": "https", "address": "192-168-1-20.abcdef.plex.direct", "port": 32400,
              "uri": "https://192-168-1-20.abcdef.plex.direct:32400", "local": true, "relay": false, "IPv6": false },
            { "protocol": "http", "address": "192.168.1.20", "port": 32400,
              "uri": "http://192.168.1.20:32400", "local": true, "relay": false, "IPv6": false },
            { "protocol": "https", "address": "abcdef.plex.direct", "port": 443,
              "uri": "https://abcdef.plex.direct:443", "local": false, "relay": true, "IPv6": false }
          ]
        }]
        """.trimIndent()
        val resources = json.decodeFromString<List<ResourceDto>>(text)
        assertEquals(1, resources.size)
        val server = resources[0]
        assertEquals("abc123", server.clientIdentifier)
        assertEquals(3, server.connections.size)
        assertTrue(server.connections[0].local)
        assertTrue(server.connections[2].relay)
    }

    @Test
    fun `parses library sections`() {
        val text = """
        { "MediaContainer": { "Directory": [
          { "key": "3", "type": "photo", "title": "Photos", "agent": "com.plexapp.agents.none",
            "scanner": "Plex Photo Scanner", "uuid": "u-3", "thumb": "/:/resources/photo.png",
            "updatedAt": 1700000000, "Location": [{ "id": 3, "path": "/media/photos" }] }
        ] } }
        """.trimIndent()
        val sections = json.decodeFromString<SectionsContainerDto>(text)
        val dir = sections.mediaContainer.directory.single()
        assertEquals("3", dir.key)
        assertEquals("photo", dir.type)
        assertEquals(1, dir.location?.size)
    }

    @Test
    fun `parses photo metadata with tolerant numeric fields as strings`() {
        val text = """
        { "MediaContainer": { "Metadata": [
          {
            "ratingKey": "1234", "key": "/library/metadata/1234", "guid": "g-1",
            "type": "photo", "title": "IMG_0001.jpg", "summary": "", "index": 1, "year": 2024,
            "thumb": "/library/metadata/1234/thumb/1700000000",
            "originallyAvailableAt": "2024-03-14",
            "addedAt": "1710403200", "updatedAt": 1710403200,
            "userRating": "10.0",
            "createdAtAccuracy": "local", "createdAtTZOffset": "36000",
            "parentRatingKey": "1200", "parentKey": "/library/metadata/1200", "parentTitle": "2024-03",
            "Media": [{
              "id": 5678, "width": "4032", "height": 3024, "aspectRatio": 1.33, "container": "jpeg",
              "aperture": "f/1.8", "exposure": "1/120", "iso": 50, "lens": "23mm", "make": "OnePlus", "model": "OnePlus 15",
              "Part": [{ "id": 5678, "key": "/library/parts/5678/1700000000/file.jpg", "file": "/media/photos/2024/IMG_0001.jpg",
                         "size": 3456789, "container": "jpeg" }]
            }],
            "Tag": [{ "tag": "Beach" }],
            "Country": [{ "tag": "Australia" }],
            "Place": [{ "tag": "Port Macquarie" }]
          }
        ] } }
        """.trimIndent()
        val container = json.decodeFromString<MetadataContainerDto>(text)
        val item = container.mediaContainer.metadata.single()
        assertEquals("1234", item.ratingKey)
        assertEquals("photo", item.type)
        assertEquals(1710403200L, item.addedAt)
        assertEquals(10.0, item.userRating)
        val media = item.media.single()
        assertEquals(4032, media.width)
        assertEquals(3024, media.height)
        val part = media.part.single()
        assertEquals(3456789L, part.size)
        assertEquals("Beach", item.tag.single().tag)
        assertEquals("Port Macquarie", item.place.single().tag)
    }

    @Test
    fun `parses clip metadata with duration`() {
        val text = """
        { "MediaContainer": { "Metadata": [
          {
            "ratingKey": "999", "type": "clip", "title": "VID_0001.mp4",
            "addedAt": 1700000000, "duration": 12345,
            "Media": [{ "width": 1920, "height": 1080, "videoCodec": "hevc", "audioCodec": "aac",
                        "duration": 12345, "videoResolution": "4k",
                        "Part": [{ "key": "/library/parts/1/1/file.mp4", "size": 1000, "duration": 12345 }] }]
          }
        ] } }
        """.trimIndent()
        val container = json.decodeFromString<MetadataContainerDto>(text)
        val item = container.mediaContainer.metadata.single()
        assertEquals("clip", item.type)
        assertEquals(12345L, item.duration)
        assertEquals("hevc", item.media.single().videoCodec)
    }

    @Test
    fun `parses github release with apk asset`() {
        val text = """
        {
          "tag_name": "v1.2.3",
          "body": "Bug fixes",
          "assets": [
            { "name": "prism-debug.apk", "browser_download_url": "https://example.com/debug.apk", "size": 111 },
            { "name": "prism-release.apk", "browser_download_url": "https://example.com/release.apk", "size": 222 }
          ],
          "published_at": "2026-09-29T00:00:00Z",
          "html_url": "https://example.com/releases/v1.2.3"
        }
        """.trimIndent()
        val release = json.decodeFromString<GithubReleaseDto>(text)
        assertEquals("v1.2.3", release.tagName)
        assertEquals(2, release.assets.size)
        val preferred = release.assets.sortedByDescending { it.name.contains("release") }.first()
        assertEquals("prism-release.apk", preferred.name)
    }
}
