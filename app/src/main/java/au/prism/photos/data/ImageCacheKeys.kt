package au.prism.photos.data

import coil3.intercept.Interceptor
import coil3.request.ImageResult

/**
 * Stable Coil cache keys for Plex image URLs. Plex URLs carry the connection's host (LAN IP,
 * plex.direct or relay) and the X-Plex-Token, so the same thumbnail gets a different URL after
 * switching LAN/remote or signing in again, which used to re-download every image. Pure string
 * handling so it can be unit tested on the JVM.
 */
object ImageCacheKeys {
    /**
     * "plex:<serverId><path>?<query without X-Plex-Token>" for an http(s) URL on [activeUri]'s host,
     * or null when the URL belongs to some other host (left to Coil's default key) or there is no
     * server yet. Only URLs for the current connection are rewritten, so an image from another
     * server can never be filed under this server's key.
     */
    fun stableKey(url: String, serverId: String?, activeUri: String?): String? {
        if (serverId.isNullOrBlank() || activeUri.isNullOrBlank()) return null
        val origin = originOf(url) ?: return null
        if (!origin.equals(originOf(activeUri), ignoreCase = true)) return null
        return "plex:$serverId${stripHostAndToken(url) ?: return null}"
    }

    /** Path plus query of an http(s) URL with scheme, host, port, fragment and X-Plex-Token removed. */
    fun stripHostAndToken(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val afterAuthority = url.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3)
        val rest = if (afterAuthority < 0) "" else url.substring(afterAuthority).substringBefore('#')
        val path = rest.substringBefore('?').ifEmpty { "/" }
        val query = rest.substringAfter('?', "")
        val kept = query.split('&')
            .filter { it.isNotEmpty() && !it.substringBefore('=').equals("X-Plex-Token", ignoreCase = true) }
        return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
    }

    private fun originOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val end = url.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3).let { if (it < 0) url.length else it }
        return url.substring(0, end).substringAfter("://").substringAfterLast('@')
    }
}

/**
 * Coil interceptor that files Plex images under [ImageCacheKeys.stableKey] in both the memory and
 * disk caches, unless the request already chose its own keys. Installed once in AppGraph so no UI
 * call site needs to know about it.
 */
class StablePlexCacheKeyInterceptor(
    private val serverId: () -> String?,
    private val activeUri: () -> String?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = when (val data = request.data) {
            is String -> data
            is coil3.Uri -> data.toString()
            else -> null
        } ?: return chain.proceed()
        val key = ImageCacheKeys.stableKey(url, serverId(), activeUri()) ?: return chain.proceed()
        if (request.diskCacheKey != null && request.memoryCacheKey != null) return chain.proceed()
        val keyed = request.newBuilder().apply {
            if (request.diskCacheKey == null) diskCacheKey(key)
            if (request.memoryCacheKey == null) memoryCacheKey(key)
        }.build()
        return chain.withRequest(keyed).proceed()
    }
}
