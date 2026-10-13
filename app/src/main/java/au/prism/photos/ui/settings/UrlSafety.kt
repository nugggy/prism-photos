package au.prism.photos.ui.settings

import java.net.URI

/**
 * True when [url] is plain http:// to a host that is not on the local network, so the WebDAV
 * password and every uploaded photo would cross the internet unencrypted. LAN addresses (private
 * IPv4 ranges, link local, loopback, IPv6 unique local, and .local / .lan / .home.arpa names or
 * bare hostnames) are fine over http. A local stand in until a shared helper exists in data/.
 */
fun isInsecureRemoteWebDavUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true)) return false
    val host = runCatching { URI(trimmed).host }.getOrNull()
        ?: trimmed.substring(7).substringBefore('/').substringBefore('?').substringAfterLast('@').substringBefore(':')
    if (host.isBlank()) return false
    return !isLanHost(host.lowercase().removePrefix("[").removeSuffix("]"))
}

internal fun isLanHost(host: String): Boolean {
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || host.endsWith(".internal")) return true
    val ipv4 = host.split('.').mapNotNull { it.toIntOrNull() }
    if (ipv4.size == 4 && host.count { it == '.' } == 3 && ipv4.all { it in 0..255 }) {
        val (a, b) = ipv4
        return a == 10 || a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // carrier grade NAT, also used by Tailscale
    }
    if (':' in host) {
        return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")
    }
    // A bare name with no dots (for example "nas") only resolves on the local network.
    return '.' !in host
}
