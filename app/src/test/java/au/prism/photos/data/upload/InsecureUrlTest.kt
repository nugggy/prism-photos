package au.prism.photos.data.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsecureUrlTest {
    @Test
    fun `https is never insecure`() {
        assertFalse(isInsecureRemoteUrl("https://dav.example.com/photos"))
    }

    @Test
    fun `http to a public host is insecure`() {
        assertTrue(isInsecureRemoteUrl("http://dav.example.com/photos"))
        assertTrue(isInsecureRemoteUrl("http://203.0.113.5:5005/"))
        assertTrue(isInsecureRemoteUrl("http://user@8.8.8.8/"))
    }

    @Test
    fun `http to the local network is allowed`() {
        assertFalse(isInsecureRemoteUrl("http://192.168.1.20:5005/Photos"))
        assertFalse(isInsecureRemoteUrl("http://10.0.0.4/"))
        assertFalse(isInsecureRemoteUrl("http://172.20.1.1/"))
        assertFalse(isInsecureRemoteUrl("http://nas:5005/"))
        assertFalse(isInsecureRemoteUrl("http://nas.local/"))
        assertFalse(isInsecureRemoteUrl("http://[fd00::1]:8080/"))
        assertFalse(isInsecureRemoteUrl("http://localhost/"))
    }

    @Test
    fun `172 outside the private block is public`() {
        assertTrue(isInsecureRemoteUrl("http://172.32.0.1/"))
    }
}
