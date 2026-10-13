package au.prism.photos.data.upload

/**
 * Plain http:// to a host on the local network is tolerated (NAS boxes rarely have certificates),
 * but Basic auth over http:// to anything else would send the password readable to every hop on
 * the internet. Pure string checks, no DNS lookups, so it is unit testable on the JVM.
 */
object InsecureUrl {
    const val MESSAGE =
        "This WebDAV address uses http:// to a server outside your local network, which would send your password unencrypted. Use an https:// address or a LAN address."

    /** True when [url] is http:// and its host is not a private, loopback or link local address. */
    fun isInsecureRemoteUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return false
        // No host yet (for example "http://" while it is being typed): nothing can be sent anywhere.
        val host = hostOf(trimmed) ?: return false
        return !isLocalHost(host)
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        if (authority.isBlank()) return null
        return if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']').lowercase()
        } else {
            authority.substringBefore(':').lowercase()
        }.takeIf { it.isNotBlank() }
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost") return true
        if (h.contains(':')) return isLocalIpv6(h)
        val octets = h.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val (a, b) = octets.map { it.toInt() }
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127) // carrier grade NAT / Tailscale
        }
        // Single label names ("nas") and the usual home network suffixes only resolve on the LAN.
        if (!h.contains('.')) return true
        return listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain").any { h.endsWith(it) }
    }

    private fun isLocalIpv6(h: String): Boolean {
        if (h == "::1") return true
        val first = h.substringBefore(':').ifBlank { return false }
        val value = first.toIntOrNull(16) ?: return false
        return (value and 0xfe00) == 0xfc00 || (value and 0xffc0) == 0xfe80
    }
}

/** Shorthand for [InsecureUrl.isInsecureRemoteUrl], used by the Settings screen WebDAV warning. */
fun isInsecureRemoteUrl(url: String): Boolean = InsecureUrl.isInsecureRemoteUrl(url)
