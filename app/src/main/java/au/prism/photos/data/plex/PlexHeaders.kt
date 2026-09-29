package au.prism.photos.data.plex

import android.os.Build
import au.prism.photos.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the X-Plex-* headers Plex expects on every request (plex.tv and server).
 * Never adds X-Plex-Token; callers add that explicitly per request (see PlexTvApi /
 * PlexServerApi) so a token is never accidentally sent to the wrong host.
 */
class PlexHeaders(private val clientIdProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val withHeaders = original.newBuilder()
            .header("Accept", "application/json")
            .header("X-Plex-Product", "Plex Gallery")
            .header("X-Plex-Version", BuildConfig.VERSION_NAME)
            .header("X-Plex-Client-Identifier", clientIdProvider())
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString())
            .header("X-Plex-Device", Build.MODEL ?: "Android")
            .header("X-Plex-Device-Name", "Plex Gallery")
            .build()
        return chain.proceed(withHeaders)
    }
}

/**
 * Rewrites the scheme/host/port of every request to the currently active Plex server
 * connection. Retrofit still needs a fixed, well formed base URL at build time, so
 * [PlexServerApi] is built against a dummy placeholder host and this interceptor swaps
 * it out for the real, dynamically chosen server URI on every call.
 */
class DynamicBaseUrlInterceptor(private val activeBaseUrlProvider: () -> String?) : Interceptor {
    companion object {
        /** Retrofit base host that gets swapped for the active Plex server. Other hosts pass through untouched. */
        const val PLACEHOLDER_HOST = "prism-server.invalid"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != PLACEHOLDER_HOST) return chain.proceed(original)
        val activeBase = activeBaseUrlProvider()?.toHttpUrlOrNull() ?: return chain.proceed(original)
        val newUrl = original.url.newBuilder()
            .scheme(activeBase.scheme)
            .host(activeBase.host)
            .port(activeBase.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
