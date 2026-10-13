package au.prism.photos.data.upload

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashNamesTest {

    private fun probeOf(vararg states: Pair<String, RemoteState>): suspend (String) -> RemoteState {
        val map = states.toMap()
        return { path -> map[path] ?: RemoteState.Absent }
    }

    @Test
    fun `free name is used and must not replace anything`() = runTest {
        val result = ClashNames.resolve("Uploads", "IMG_1.jpg", 100, probeOf())
        assertEquals(ClashResolution.Use("IMG_1.jpg", replaces = false), result)
    }

    @Test
    fun `same sized file is treated as an earlier upload and replaced`() = runTest {
        val result = ClashNames.resolve("Uploads", "IMG_1.jpg", 100, probeOf("Uploads/IMG_1.jpg" to RemoteState.Present(100)))
        assertEquals(ClashResolution.Use("IMG_1.jpg", replaces = true), result)
    }

    @Test
    fun `different file gets a suffix`() = runTest {
        val result = ClashNames.resolve(
            "Uploads",
            "IMG_1.jpg",
            100,
            probeOf("Uploads/IMG_1.jpg" to RemoteState.Present(999), "Uploads/IMG_1_1.jpg" to RemoteState.Present(5)),
        )
        assertEquals(ClashResolution.Use("IMG_1_2.jpg", replaces = false), result)
    }

    @Test
    fun `folder or unknown size at the name is never overwritten`() = runTest {
        val result = ClashNames.resolve("", "IMG_1.jpg", 100, probeOf("IMG_1.jpg" to RemoteState.Present(-1)))
        assertEquals(ClashResolution.Use("IMG_1_1.jpg", replaces = false), result)
    }

    @Test
    fun `failed check refuses instead of overwriting`() = runTest {
        val result = ClashNames.resolve("Uploads", "IMG_1.jpg", 100, probeOf("Uploads/IMG_1.jpg" to RemoteState.Unknown("timeout")))
        assertTrue(result is ClashResolution.CannotCheck)
    }

    @Test
    fun `failed check on a later suffix also refuses`() = runTest {
        val result = ClashNames.resolve(
            "Uploads",
            "IMG_1.jpg",
            100,
            probeOf("Uploads/IMG_1.jpg" to RemoteState.Present(7), "Uploads/IMG_1_1.jpg" to RemoteState.Unknown("HTTP 401")),
        )
        assertEquals(ClashResolution.CannotCheck("HTTP 401"), result)
    }

    @Test
    fun `gives up after fifty clashes rather than looping`() = runTest {
        val result = ClashNames.resolve("", "a.jpg", 100) { RemoteState.Present(1) }
        assertTrue(result is ClashResolution.CannotCheck)
    }

    @Test
    fun `temp path sits hidden in the same folder`() {
        assertEquals("Uploads/2026/.IMG_1.jpg.part", ClashNames.tempPathFor("Uploads/2026/IMG_1.jpg"))
        assertEquals(".IMG_1.jpg.part", ClashNames.tempPathFor("IMG_1.jpg"))
    }
}
