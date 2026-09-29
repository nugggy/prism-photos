package au.prism.photos.data.plex

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** plex.tv account API. Base URL https://plex.tv/. Common X-Plex-* headers are added by PlexHeaders. */
interface PlexTvApi {
    @POST("api/v2/pins")
    suspend fun createPin(@Query("strong") strong: Boolean = true): PinDto

    @GET("api/v2/pins/{id}")
    suspend fun getPin(@Path("id") id: Long): PinDto

    @GET("api/v2/user")
    suspend fun user(@Header("X-Plex-Token") token: String): PlexTvUserDto

    @GET("api/v2/resources")
    suspend fun resources(
        @Header("X-Plex-Token") token: String,
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Query("includeIPv6") includeIPv6: Int = 1,
    ): List<ResourceDto>

    @DELETE("api/v2/users/signout")
    suspend fun signout(@Header("X-Plex-Token") token: String)
}
