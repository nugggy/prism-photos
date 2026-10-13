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
    const val ALBUM = "album/{albumId}?locked={locked}"
    const val MY_ALBUM = "myalbum/{albumId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{source}/{index}?locked={locked}"
    const val EDITOR = "editor/{itemId}?locked={locked}"
    const val VIDEO_EDITOR = "videoeditor/{itemId}?locked={locked}"
    const val UPLOAD = "upload/{uris}"

    /*
     * Ids can be Plex rating keys or content:// URIs (which contain '/', ':' and '%'), so path
     * arguments are carried as unpadded base64url via [encodeArg] and read back with [decodeArg].
     * That alphabet has nothing Navigation will percent-decode, so no double decoding surprises.
     *
     * [locked] marks a destination opened from locked content (the Locked tab, a locked album, or
     * something opened from those), so PrismNavHost keeps it behind the LockGate.
     */
    fun album(albumId: String, locked: Boolean = false) = "album/${encodeArg(albumId)}?locked=$locked"
    fun myAlbum(albumId: String) = "myalbum/${encodeArg(albumId)}"
    fun viewer(source: String, index: Int, locked: Boolean = false) = "viewer/$source/$index?locked=$locked"
    fun editor(itemId: String, locked: Boolean = false) = "editor/${encodeArg(itemId)}?locked=$locked"
    fun videoEditor(itemId: String, locked: Boolean = false) = "videoeditor/${encodeArg(itemId)}?locked=$locked"
    /** [uris] are content URIs of device items, joined with |. */
    fun upload(uris: List<String>) = "upload/${encodeArg(uris.joinToString("|"))}"

    fun encodeArg(value: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    /** Inverse of [encodeArg]. Falls back to the raw value if it is not valid base64url. */
    fun decodeArg(raw: String): String =
        runCatching { String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8) }.getOrDefault(raw)
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
        is au.prism.photos.domain.ViewerSource.MyAlbum -> "myalbum:${source.albumId}"
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
            s.startsWith("myalbum:") -> au.prism.photos.domain.ViewerSource.MyAlbum(s.removePrefix("myalbum:"))
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
