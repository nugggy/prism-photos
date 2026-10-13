package au.prism.photos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("1.2.3", "1.2.2"))
    }

    @Test
    fun `newer minor version is detected`() {
        assertTrue(isNewerVersion("1.3.0", "1.2.9"))
    }

    @Test
    fun `newer major version is detected`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(isNewerVersion("1.2.3", "1.2.3"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.2.3"))
    }

    @Test
    fun `debug suffix on installed version does not confuse comparison`() {
        // installedVersion is expected to already have the -debug suffix stripped before calling this.
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `differing segment counts compare missing segments as zero`() {
        assertTrue(isNewerVersion("1.2.1", "1.2"))
        assertFalse(isNewerVersion("1.2.0", "1.2"))
    }

    @Test
    fun `pre-release suffix keeps the component number`() {
        // Used to parse "10-rc" as 0, making 1.2.10-rc look like 1.2.0.
        assertTrue(isNewerVersion("1.2.10-rc", "1.2.9"))
        assertTrue(isNewerVersion("1.2.10-rc1", "1.2.3"))
    }

    @Test
    fun `pre-release is older than the same release`() {
        assertFalse(isNewerVersion("1.3.0-rc1", "1.3.0"))
        assertTrue(isNewerVersion("1.3.0", "1.3.0-rc1"))
        assertTrue(isNewerVersion("1.3.0-rc2", "1.3.0-rc1"))
        assertFalse(isNewerVersion("1.3.0-beta", "1.3.0-rc1"))
    }

    @Test
    fun `glued pre-release label counts as pre-release`() {
        assertFalse(isNewerVersion("1.3.0rc1", "1.3.0"))
        assertTrue(isNewerVersion("1.3.1rc1", "1.3.0"))
    }

    @Test
    fun `github sha256 digest is parsed`() {
        val hex = "a".repeat(64)
        assertEquals(hex, parseSha256Digest("sha256:$hex"))
        assertEquals(hex, parseSha256Digest("SHA256:${hex.uppercase()}"))
        assertNull(parseSha256Digest(null))
        assertNull(parseSha256Digest("sha512:$hex"))
        assertNull(parseSha256Digest("sha256:xyz"))
    }
}
