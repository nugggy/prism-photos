package au.prism.photos.data

import au.prism.photos.data.plex.PlexTvApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** What [MediaRepositoryImpl]'s apiCall does after a Plex server request fails. */
enum class ApiFailureAction {
    /** 401: ask plex.tv whether the token is still valid before deciding to sign out. */
    REVALIDATE_TOKEN,

    /** Connection failure on a read: re-run the connection chooser and retry once. */
    RECONNECT_AND_RETRY,

    /**
     * Connection failure on a write: re-run the connection chooser for the next request but do
     * not repeat this one, since the server may already have applied it (a timeout after the
     * request was sent) and repeating could, for example, create a second album.
     */
    RECONNECT_ONLY,

    RETHROW,
}

/** Pure decision table for apiCall, unit tested without a server. */
object ApiRetryPolicy {
    fun onFailure(error: Throwable, idempotent: Boolean): ApiFailureAction = when (error) {
        is HttpException -> if (error.code() == 401) ApiFailureAction.REVALIDATE_TOKEN else ApiFailureAction.RETHROW
        is IOException -> if (idempotent) ApiFailureAction.RECONNECT_AND_RETRY else ApiFailureAction.RECONNECT_ONLY
        else -> ApiFailureAction.RETHROW
    }
}

enum class TokenStatus { VALID, INVALID, UNKNOWN }

/** Checks a Plex account token against plex.tv (GET /api/v2/user). */
class PlexTokenValidator(private val plexTvApi: PlexTvApi) {
    suspend fun check(token: String): TokenStatus = try {
        plexTvApi.user(token)
        TokenStatus.VALID
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 401 || e.code() == 403) TokenStatus.INVALID else TokenStatus.UNKNOWN
    } catch (e: Exception) {
        TokenStatus.UNKNOWN
    }
}
