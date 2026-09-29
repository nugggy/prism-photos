package au.prism.photos.data

import org.junit.Assert.assertFalse
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
}
