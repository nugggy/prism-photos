package au.prism.photos.data.plex

import java.net.URLEncoder

/**
 * Pure URL builders for Plex image/video endpoints. No Android dependencies so these
 * can be unit tested on the plain JVM. See docs/plex-api.md "Images" and "Video".
 */
object PlexUrls {
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun base(serverUri: String): String = serverUri.trimEnd('/')

    /** Thumbnail through the server transcoder. [thumbPath] is the item's thumb, e.g. /library/metadata/1234/thumb/1700000000. */
    fun thumb(serverUri: String, thumbPath: String, token: String, size: Int): String =
        "${base(serverUri)}/photo/:/transcode?width=$size&height=$size&minSize=1&upscale=1&url=${enc(thumbPath)}&X-Plex-Token=${enc(token)}"

    /** Full resolution original, e.g. {server}/library/parts/5678/1700000000/file.jpg?X-Plex-Token=... */
    fun original(serverUri: String, partKey: String, token: String): String =
        "${base(serverUri)}$partKey?X-Plex-Token=${enc(token)}"

    fun download(serverUri: String, partKey: String, token: String): String =
        "${original(serverUri, partKey, token)}&download=1"

    /** Direct play video URL - identical to [original], kept separate for clarity at call sites. */
    fun directPlay(serverUri: String, partKey: String, token: String): String =
        original(serverUri, partKey, token)

    /** HLS transcode start URL for the universal transcoder. */
    fun hlsTranscode(
        serverUri: String,
        ratingKey: String,
        token: String,
        clientId: String,
        platform: String,
        sessionId: String,
    ): String {
        val path = enc("/library/metadata/$ratingKey")
        return "${base(serverUri)}/video/:/transcode/universal/start.m3u8" +
            "?path=$path&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1&directPlay=0&directStream=1" +
            "&videoQuality=100&maxVideoBitrate=20000&videoResolution=1920x1080" +
            "&session=${enc(sessionId)}&X-Plex-Client-Identifier=${enc(clientId)}" +
            "&X-Plex-Platform=${enc(platform)}&X-Plex-Product=Plex%20Gallery&X-Plex-Token=${enc(token)}"
    }

    fun stopTranscode(serverUri: String, sessionId: String, token: String): String =
        "${base(serverUri)}/video/:/transcode/universal/stop?session=${enc(sessionId)}&X-Plex-Token=${enc(token)}"
}
