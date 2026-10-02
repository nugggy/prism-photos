package au.prism.photos.data

import au.prism.photos.data.plex.PartRetryInterceptor
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class PartRetryInterceptorTest {

    /** Minimal chain that answers each proceed() with the next queued status code. */
    private class FakeChain(private val request: Request, private val codes: ArrayDeque<Int>) : Interceptor.Chain {
        var calls = 0
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            calls++
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(codes.removeFirst())
                .message("")
                .body("".toResponseBody())
                .build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private val part = Request.Builder().url("http://192.168.0.162:32400/library/parts/1/2/file.jpg?X-Plex-Token=t").build()
    private val noSleep = PartRetryInterceptor(sleep = {})

    @Test
    fun `retries a part 503 until it succeeds`() {
        val chain = FakeChain(part, ArrayDeque(listOf(503, 503, 200)))
        assertEquals(200, noSleep.intercept(chain).code)
        assertEquals(3, chain.calls)
    }

    @Test
    fun `gives up after the configured retries`() {
        val chain = FakeChain(part, ArrayDeque(listOf(503, 503, 503, 503)))
        assertEquals(503, noSleep.intercept(chain).code)
        assertEquals(4, chain.calls)
    }

    @Test
    fun `does not retry other paths`() {
        val other = Request.Builder().url("http://192.168.0.162:32400/library/sections/13/all").build()
        val chain = FakeChain(other, ArrayDeque(listOf(503)))
        assertEquals(503, noSleep.intercept(chain).code)
        assertEquals(1, chain.calls)
    }
}
