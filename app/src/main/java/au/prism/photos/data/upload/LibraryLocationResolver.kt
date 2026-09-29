package au.prism.photos.data.upload

import au.prism.photos.data.plex.PlexServerApi
import au.prism.photos.domain.SessionStore

/**
 * Looks up the photo library's folder path on the server (Directory[].Location[].path from
 * `GET /library/sections`), used to prefill the sync destination's server folder path in
 * Settings. See docs/plex-api.md "Uploading to the server".
 */
class LibraryLocationResolver(
    private val api: PlexServerApi,
    private val session: SessionStore,
) {
    suspend fun currentLibraryPath(): String? {
        val sess = session.session.value
        val token = sess.serverToken ?: return null
        val libKey = sess.libraryKey ?: return null
        return try {
            val container = api.sections(token)
            container.mediaContainer.directory
                .firstOrNull { it.key == libKey }
                ?.location
                ?.firstOrNull()
                ?.path
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
