package au.prism.photos.data.plex

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Plex Media Server API. Built against a dummy placeholder base URL; the real scheme,
 * host and port of the currently active server connection are swapped in at request
 * time by [DynamicBaseUrlInterceptor]. See docs/plex-api.md for every endpoint's shape.
 */
interface PlexServerApi {
    /** GET {server}/identity - reachability probe, needs no token. */
    @GET("identity")
    suspend fun identity(): IdentityContainerDto

    /** GET {server}/?X-Plex-Token=... - used to verify a manual server URL + token. */
    @GET(".")
    suspend fun root(@Header("X-Plex-Token") token: String): RootContainerDto

    @GET("library/sections")
    suspend fun sections(@Header("X-Plex-Token") token: String): SectionsContainerDto

    @GET("library/sections/{key}/all")
    suspend fun sectionAll(
        @Path("key") sectionKey: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int? = null,
        @Query("sort") sort: String? = null,
        @Header("X-Plex-Container-Start") start: Int? = null,
        @Header("X-Plex-Container-Size") size: Int? = null,
        @QueryMap(encoded = true) extra: Map<String, String> = emptyMap(),
    ): MetadataContainerDto

    // ---- photo playlists (My albums) ----

    @GET("playlists")
    suspend fun playlists(
        @Header("X-Plex-Token") token: String,
        @Query("playlistType") playlistType: String = "photo",
    ): MetadataContainerDto

    @GET("playlists/{id}/items")
    suspend fun playlistItems(
        @Path("id") playlistId: String,
        @Header("X-Plex-Token") token: String,
    ): MetadataContainerDto

    @POST("playlists")
    suspend fun createPlaylist(
        @Header("X-Plex-Token") token: String,
        @Query("title") title: String,
        @Query("uri") uri: String,
        @Query("type") type: String = "photo",
        @Query("smart") smart: Int = 0,
    ): MetadataContainerDto

    @PUT("playlists/{id}/items")
    suspend fun addPlaylistItems(
        @Path("id") playlistId: String,
        @Header("X-Plex-Token") token: String,
        @Query("uri") uri: String,
    ): Response<Unit>

    @DELETE("playlists/{id}/items/{playlistItemId}")
    suspend fun removePlaylistItem(
        @Path("id") playlistId: String,
        @Path("playlistItemId") playlistItemId: String,
        @Header("X-Plex-Token") token: String,
    ): Response<Unit>

    @PUT("playlists/{id}")
    suspend fun renamePlaylist(
        @Path("id") playlistId: String,
        @Header("X-Plex-Token") token: String,
        @Query("title") title: String,
    ): Response<Unit>

    @DELETE("playlists/{id}")
    suspend fun deletePlaylist(
        @Path("id") playlistId: String,
        @Header("X-Plex-Token") token: String,
    ): Response<Unit>

    @GET("library/metadata/{ratingKey}/children")
    suspend fun children(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
    ): MetadataContainerDto

    @GET("library/metadata/{ratingKey}")
    suspend fun metadata(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
    ): MetadataContainerDto

    @GET("library/sections/{key}/search")
    suspend fun search(
        @Path("key") sectionKey: String,
        @Query("type") type: Int,
        @Query("query") query: String,
        @Header("X-Plex-Token") token: String,
    ): MetadataContainerDto

    @PUT(":/rate")
    suspend fun rate(
        @Query("key") ratingKey: String,
        @Query("rating") rating: Int,
        @Header("X-Plex-Token") token: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
    ): retrofit2.Response<Unit>

    @PUT("library/sections/{key}/all")
    suspend fun edit(
        @Path("key") sectionKey: String,
        @QueryMap(encoded = true) fields: Map<String, String>,
        @Header("X-Plex-Token") token: String,
    ): retrofit2.Response<Unit>

    @DELETE("library/metadata/{ratingKey}")
    suspend fun deleteMetadata(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
    ): retrofit2.Response<Unit>

    @GET("video/:/transcode/universal/stop")
    suspend fun stopTranscode(
        @Query("session") session: String,
        @Header("X-Plex-Token") token: String,
    ): retrofit2.Response<Unit>
}
