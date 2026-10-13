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

    @Test
    fun `scheme matching ignores case and surrounding spaces`() {
        assertFalse(isInsecureRemoteUrl("HTTPS://203.0.113.5/dav"))
        assertTrue(isInsecureRemoteUrl("  HTTP://user@mynas.example.org/dav "))
    }

    @Test
    fun `loopback, link local, carrier grade NAT and home suffixes are local`() {
        listOf(
            "http://127.0.0.1:8080",
            "http://169.254.1.1/",
            "http://100.101.102.103/",
            "http://localhost:8080/",
            "http://[::1]/",
            "http://[fe80::1]/",
            "http://[febf::1]/",
            "http://[fc00::1]/",
            "http://nas.lan/",
            "http://nas.home/",
            "http://nas.home.arpa/",
            "http://nas.internal/",
            "http://nas.localdomain/",
            "http://nas.local./",
        ).forEach { assertFalse(it, isInsecureRemoteUrl(it)) }
    }

    @Test
    fun `addresses just outside the local ranges are public`() {
        assertTrue(isInsecureRemoteUrl("http://100.128.0.1/"))
        assertTrue(isInsecureRemoteUrl("http://169.255.0.1/"))
        assertTrue(isInsecureRemoteUrl("http://[fec0::1]/"))
        assertTrue(isInsecureRemoteUrl("http://[2001:db8::1]/"))
    }

    @Test
    fun `blank, hostless or non http input is not flagged`() {
        assertFalse(isInsecureRemoteUrl(""))
        assertFalse(isInsecureRemoteUrl("http://"))
        assertFalse(isInsecureRemoteUrl("smb://example.com/share"))
    }
}
