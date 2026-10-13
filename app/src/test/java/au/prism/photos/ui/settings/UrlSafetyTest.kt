package au.prism.photos.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {
    @Test
    fun `https is never flagged`() {
        assertFalse(isInsecureRemoteWebDavUrl("https://example.com/dav"))
        assertFalse(isInsecureRemoteWebDavUrl("HTTPS://203.0.113.5/dav"))
    }

    @Test
    fun `http to a public host is flagged`() {
        assertTrue(isInsecureRemoteWebDavUrl("http://example.com/dav"))
        assertTrue(isInsecureRemoteWebDavUrl("http://203.0.113.5:5005/photos"))
        assertTrue(isInsecureRemoteWebDavUrl("  HTTP://user@mynas.example.org/dav "))
        assertTrue(isInsecureRemoteWebDavUrl("http://172.32.0.1/"))
    }

    @Test
    fun `http to a local network host is not flagged`() {
        listOf(
            "http://192.168.1.20:5005/photos",
            "http://10.0.0.5/dav",
            "http://172.16.4.2/",
            "http://127.0.0.1:8080",
            "http://169.254.1.1/",
            "http://100.101.102.103/",
            "http://nas.local/dav",
            "http://nas/dav",
            "http://localhost:8080/",
            "http://[fd12:3456::1]/dav",
            "http://[fe80::1]/",
        ).forEach { assertFalse(it, isInsecureRemoteWebDavUrl(it)) }
    }

    @Test
    fun `blank or non http input is not flagged`() {
        assertFalse(isInsecureRemoteWebDavUrl(""))
        assertFalse(isInsecureRemoteWebDavUrl("http://"))
        assertFalse(isInsecureRemoteWebDavUrl("smb://example.com/share"))
    }
}
