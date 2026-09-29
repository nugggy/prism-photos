package au.prism.photos.ui.nav

/**
 * Navigation routes shared by all UI packages. Owned by the UI shell.
 * Viewer and editor routes carry a ViewerSource encoded as a string (see [encodeSource]).
 */
object Routes {
    const val SIGN_IN = "signin"
    const val SERVERS = "servers"
    const val LIBRARIES = "libraries"
    const val HOME = "home"                 // bottom nav host: photos, albums, favourites, device, locked
    const val ALBUM = "album/{albumId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{source}/{index}"
    const val EDITOR = "editor/{itemId}"

    fun album(albumId: String) = "album/$albumId"
    fun viewer(source: String, index: Int) = "viewer/$source/$index"
    fun editor(itemId: String) = "editor/$itemId"
}

/**
 * ViewerSource <-> route string. Kept simple and URL safe:
 *  timeline | favourites | locked | album:ID | search:QUERY | device:BUCKET | items:ID,ID | uris:URI|URI
 * Values are URL encoded by the caller with [java.net.URLEncoder].
 */
object SourceCodec {
    fun encode(source: au.prism.photos.domain.ViewerSource): String = when (source) {
        au.prism.photos.domain.ViewerSource.Timeline -> "timeline"
        au.prism.photos.domain.ViewerSource.Favourites -> "favourites"
        au.prism.photos.domain.ViewerSource.Locked -> "locked"
        is au.prism.photos.domain.ViewerSource.Album -> "album:${source.albumId}"
        is au.prism.photos.domain.ViewerSource.Search -> "search:${enc(source.query)}"
        is au.prism.photos.domain.ViewerSource.Device -> "device:${source.bucketId ?: ""}"
        is au.prism.photos.domain.ViewerSource.Items -> "items:${source.ids.joinToString(",")}"
        is au.prism.photos.domain.ViewerSource.ExternalUris -> "uris:${source.uris.joinToString("|") { enc(it) }}"
    }.let { enc(it) }

    fun decode(raw: String): au.prism.photos.domain.ViewerSource {
        val s = dec(raw)
        return when {
            s == "timeline" -> au.prism.photos.domain.ViewerSource.Timeline
            s == "favourites" -> au.prism.photos.domain.ViewerSource.Favourites
            s == "locked" -> au.prism.photos.domain.ViewerSource.Locked
            s.startsWith("album:") -> au.prism.photos.domain.ViewerSource.Album(s.removePrefix("album:"))
            s.startsWith("search:") -> au.prism.photos.domain.ViewerSource.Search(dec(s.removePrefix("search:")))
            s.startsWith("device:") -> au.prism.photos.domain.ViewerSource.Device(s.removePrefix("device:").ifEmpty { null })
            s.startsWith("items:") -> au.prism.photos.domain.ViewerSource.Items(s.removePrefix("items:").split(",").filter { it.isNotEmpty() })
            s.startsWith("uris:") -> au.prism.photos.domain.ViewerSource.ExternalUris(s.removePrefix("uris:").split("|").filter { it.isNotEmpty() }.map { dec(it) })
            else -> au.prism.photos.domain.ViewerSource.Timeline
        }
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    private fun dec(v: String) = java.net.URLDecoder.decode(v, "UTF-8")
}
