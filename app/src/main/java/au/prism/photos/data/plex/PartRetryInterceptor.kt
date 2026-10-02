package au.prism.photos.data.plex

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries original file requests (/library/parts/...) that Plex answers with 503.
 *
 * The QNAP server often refuses the first read of a file with 503 Service Unavailable and
 * serves it normally a moment later, which left the viewer blank. Only idempotent GETs for
 * part files are retried, after short [delaysMs] pauses.
 */
class PartRetryInterceptor(
    private val delaysMs: List<Long> = listOf(400L, 1000L, 2000L),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        if (request.method != "GET" || !request.url.encodedPath.startsWith("/library/parts/")) return response
        for (delay in delaysMs) {
            if (response.code != 503) break
            response.close()
            sleep(delay)
            response = chain.proceed(request)
        }
        return response
    }
}
