package au.prism.photos.data

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ApiRetryPolicyTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `reads are retried after a connection failure`() {
        assertEquals(ApiFailureAction.RECONNECT_AND_RETRY, ApiRetryPolicy.onFailure(IOException("reset"), idempotent = true))
        assertEquals(ApiFailureAction.RECONNECT_AND_RETRY, ApiRetryPolicy.onFailure(SocketTimeoutException(), idempotent = true))
    }

    @Test
    fun `writes are never repeated after a connection failure`() {
        assertEquals(ApiFailureAction.RECONNECT_ONLY, ApiRetryPolicy.onFailure(IOException("reset"), idempotent = false))
        assertEquals(ApiFailureAction.RECONNECT_ONLY, ApiRetryPolicy.onFailure(SocketTimeoutException(), idempotent = false))
    }

    @Test
    fun `401 revalidates the token rather than signing out`() {
        assertEquals(ApiFailureAction.REVALIDATE_TOKEN, ApiRetryPolicy.onFailure(http(401), idempotent = true))
        assertEquals(ApiFailureAction.REVALIDATE_TOKEN, ApiRetryPolicy.onFailure(http(401), idempotent = false))
    }

    @Test
    fun `other HTTP errors are passed through untouched`() {
        assertEquals(ApiFailureAction.RETHROW, ApiRetryPolicy.onFailure(http(500), idempotent = true))
        assertEquals(ApiFailureAction.RETHROW, ApiRetryPolicy.onFailure(http(403), idempotent = false))
        assertEquals(ApiFailureAction.RETHROW, ApiRetryPolicy.onFailure(IllegalStateException(), idempotent = true))
    }
}
