package au.prism.photos.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val awkwardIds = listOf(
        "12345",
        "content://media/external/images/media/42",
        "content://com.android.externalstorage.documents/document/primary%3ADCIM%2Fa+b c.jpg",
        "file:///storage/emulated/0/Movies/clip (1).mp4",
        "ünïcode/✓?x=1&y=2#frag",
    )

    @Test
    fun `encoded args round trip`() {
        awkwardIds.forEach { assertEquals(it, Routes.decodeArg(Routes.encodeArg(it))) }
    }

    @Test
    fun `encoded args contain only path safe characters`() {
        val safe = Regex("^[A-Za-z0-9_-]*$")
        awkwardIds.forEach { assertTrue(it, safe.matches(Routes.encodeArg(it))) }
    }

    @Test
    fun `editor routes keep the id in one path segment`() {
        val route = Routes.editor("content://media/external/images/media/42")
        assertFalse(route.removePrefix("editor/").substringBefore('?').contains('/'))
        assertTrue(route.endsWith("?locked=false"))
        assertTrue(Routes.videoEditor("x", locked = true).endsWith("?locked=true"))
    }
}
