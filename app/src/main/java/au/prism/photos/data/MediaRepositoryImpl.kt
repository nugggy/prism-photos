package au.prism.photos.data

import au.prism.photos.data.plex.DirectoryDto
import au.prism.photos.data.plex.MetadataDto
import au.prism.photos.data.plex.MetadataMediaContainerDto
import au.prism.photos.data.plex.PlexServerApi
import au.prism.photos.data.plex.PlexUrls
import au.prism.photos.domain.Album
import au.prism.photos.domain.AlbumContents
import au.prism.photos.domain.ExifInfo
import au.prism.photos.domain.LoadState
import au.prism.photos.domain.LocalStore
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import au.prism.photos.domain.MediaRepository
import au.prism.photos.domain.PlexLibrary
import au.prism.photos.domain.SessionStore
import au.prism.photos.domain.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/** Newest first merge of one or more already-mapped lists, by [MediaItem.takenAt]. */
fun mergeTimelines(vararg lists: List<MediaItem>): List<MediaItem> =
    lists.toList().flatten().sortedByDescending { it.takenAt }

private fun parseTakenAt(originallyAvailableAt: String?, addedAtEpochSeconds: Long?): Long {
    val fromDate = originallyAvailableAt?.takeIf { it.isNotBlank() }?.let {
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(it)?.time }.getOrNull()
    }
    return fromDate ?: ((addedAtEpochSeconds ?: 0L) * 1000L)
}

private fun extractRatingKey(key: String?): String? {
    if (key.isNullOrBlank()) return null
    return Regex("""/library/metadata/(\d+)""").find(key)?.groupValues?.get(1)
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

class MediaRepositoryImpl(
    private val api: PlexServerApi,
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val local: LocalStore,
    private val connectionChooser: ConnectionChooser,
    private val json: Json,
    private val filesDir: File,
    private val scope: CoroutineScope,
) : MediaRepository {

    /** Every item (photo/clip) currently known for the selected library, including locked ones. */
    private val allItems = MutableStateFlow<List<MediaItem>>(emptyList())
    private val timelineStateFlow = MutableStateFlow<LoadState>(LoadState.Idle)
    private val visibleFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    private val lockedFlow = MutableStateFlow<List<MediaItem>>(emptyList())

    private var transcodeSessionId: String? = null

    init {
        combine(allItems, local.lockedItemIds, local.lockedAlbumIds) { all, lockedIds, lockedAlbums ->
            Triple(all, lockedIds, lockedAlbums)
        }.onEach { (all, lockedIds, lockedAlbums) ->
            fun isLocked(item: MediaItem) = item.id in lockedIds || (item.albumId != null && item.albumId in lockedAlbums)
            visibleFlow.value = all.filterNot { isLocked(it) }
            lockedFlow.value = all.filter { isLocked(it) }
        }.launchIn(scope)

        session.session
            .map { it.server?.clientIdentifier to it.libraryKey }
            .distinctUntilChanged()
            .onEach { (serverId, libraryKey) -> if (serverId != null && libraryKey != null) loadCacheThenRefresh() }
            .launchIn(scope)
    }

    override val timeline: StateFlow<List<MediaItem>> = visibleFlow.asStateFlow()
    override val timelineState: StateFlow<LoadState> = timelineStateFlow.asStateFlow()
    override val lockedItems: StateFlow<List<MediaItem>> = lockedFlow.asStateFlow()

    // ---- loading & caching ----

    private fun cacheFile(): File? {
        val sess = session.session.value
        val serverId = sess.server?.clientIdentifier ?: return null
        val libKey = sess.libraryKey ?: return null
        val dir = File(filesDir, "cache").apply { mkdirs() }
        return File(dir, "timeline-$serverId-$libKey.json")
    }

    private suspend fun loadCacheThenRefresh() {
        val file = cacheFile()
        if (file != null && file.exists()) {
            runCatching {
                val text = withContext(Dispatchers.IO) { file.readText() }
                val items = json.decodeFromString<List<MediaItem>>(text)
                allItems.value = items
                timelineStateFlow.value = LoadState.Loaded
            }
        }
        refreshTimeline(force = true)
    }

    override suspend fun refreshTimeline(force: Boolean) {
        val sess = session.session.value
        if (!sess.hasLibrary) return
        if (timelineStateFlow.value == LoadState.Loading && !force) return
        val libKey = sess.libraryKey ?: return
        timelineStateFlow.value = LoadState.Loading
        Diagnostics.log("Refreshing library $libKey via ${sess.active?.uri} (${sess.active?.kind})")
        val photos = runCatching { fetchAllPages(libKey, type = 13) }
        val clips = runCatching { fetchAllPages(libKey, type = 12) }
        val photosErr = photos.exceptionOrNull()
        val clipsErr = clips.exceptionOrNull()
        if (photosErr != null && clipsErr != null) {
            val message = describe(photosErr)
            Diagnostics.log("Library refresh failed: $message")
            timelineStateFlow.value = LoadState.Error(message)
            return
        }
        val merged = mergeTimelines(photos.getOrDefault(emptyList()), clips.getOrDefault(emptyList()))
        Diagnostics.log("Timeline ready: ${merged.size} items (${photos.getOrNull()?.size ?: 0} photos, ${clips.getOrNull()?.size ?: 0} videos)")
        allItems.value = merged
        persistCache(merged)
        timelineStateFlow.value = LoadState.Loaded
    }

    /** Human readable description of a failed request, including the HTTP status and body when available. */
    private fun describe(e: Throwable): String = when (e) {
        is HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string()?.take(200) }.getOrNull().orEmpty()
            "HTTP ${e.code()} ${e.message()}".trim() + if (body.isNotBlank()) " $body" else ""
        }
        else -> "${e::class.java.simpleName}: ${e.message ?: "no message"}"
    }

    private suspend fun persistCache(items: List<MediaItem>) {
        val file = cacheFile() ?: return
        runCatching { withContext(Dispatchers.IO) { file.writeText(json.encodeToString(items)) } }
    }

    private suspend fun fetchAllPages(libraryKey: String, type: Int): List<MediaItem> {
        val label = if (type == 13) "photos" else "videos"
        val started = System.currentTimeMillis()
        // Try the server side sort first, then fall back to the server's default order (we sort locally anyway).
        return try {
            fetchPages(libraryKey, type, sort = "originallyAvailableAt:desc").also {
                Diagnostics.log("Fetched ${it.size} $label (type $type, sorted) in ${System.currentTimeMillis() - started} ms")
            }
        } catch (e: Exception) {
            Diagnostics.log("Sorted $label request failed: ${describe(e)}. Retrying without sort")
            fetchPages(libraryKey, type, sort = null).also {
                Diagnostics.log("Fetched ${it.size} $label (type $type, unsorted) in ${System.currentTimeMillis() - started} ms")
            }
        }
    }

    private suspend fun fetchPages(libraryKey: String, type: Int, sort: String?): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        var start = 0
        val pageSize = 500
        while (true) {
            val container = apiCall { token -> sectionAll(libraryKey, token, type, sort, start, pageSize) }
            val mc = container.mediaContainer
            val page = mc.metadata.map { mapItem(it, libraryKey) }
            out += page
            val total = mc.totalSize ?: mc.size ?: page.size
            if (start == 0) Diagnostics.log("Type $type page 1: ${page.size} items, size=${mc.size}, totalSize=${mc.totalSize}")
            start += pageSize
            if (page.isEmpty() || page.size < pageSize || start >= total) break
        }
        return out
    }

    // ---- libraries & albums ----

    override suspend fun libraries(): Result<List<PlexLibrary>> = runCatching {
        val container = apiCall { token -> sections(token) }
        container.mediaContainer.directory.filter { it.type == "photo" }.map { d ->
            PlexLibrary(key = d.key.orEmpty(), title = d.title.orEmpty(), uuid = d.uuid.orEmpty(), thumbPath = d.thumb)
        }
    }

    override suspend fun rootAlbums(): Result<AlbumContents> = runCatching {
        val libKey = session.session.value.libraryKey ?: throw IllegalStateException("No library selected")
        val container = apiCall { token -> sectionAll(libKey, token, null, null, null, null) }
        buildAlbumContents(container.mediaContainer, libKey)
    }

    override suspend fun albumContents(albumId: String): Result<AlbumContents> = runCatching {
        val libKey = session.session.value.libraryKey ?: throw IllegalStateException("No library selected")
        val container = apiCall { token -> children(albumId, token) }
        buildAlbumContents(container.mediaContainer, libKey)
    }

    override suspend fun album(albumId: String): Album? {
        val libKey = session.session.value.libraryKey ?: return null
        return runCatching {
            val container = apiCall { token -> metadata(albumId, token) }
            container.mediaContainer.metadata.firstOrNull()?.let { mapAlbumFromMetadata(it, libKey) }
        }.getOrNull()
    }

    private fun buildAlbumContents(mc: MetadataMediaContainerDto, libraryKey: String): AlbumContents {
        val lockedAlbumIds = local.lockedAlbumIds.value
        val lockedItemIds = local.lockedItemIds.value
        val albums = (mc.directory.filter { it.type == "photoalbum" }.map { mapAlbumFromDirectory(it, libraryKey) } +
            mc.metadata.filter { it.type == "photoalbum" }.map { mapAlbumFromMetadata(it, libraryKey) })
            .filter { it.id.isNotBlank() && it.id !in lockedAlbumIds }
        val items = mc.metadata.filter { it.type == "photo" || it.type == "clip" }
            .map { mapItem(it, libraryKey) }
            .filter { it.id !in lockedItemIds }
        return AlbumContents(albums = albums, items = items)
    }

    private fun mapAlbumFromDirectory(d: DirectoryDto, sectionKey: String): Album = Album(
        id = d.ratingKey ?: extractRatingKey(d.key) ?: d.key.orEmpty(),
        title = d.title.orEmpty(),
        thumbPath = d.thumb,
        compositePath = d.composite,
        itemCount = d.leafCount ?: 0,
        addedAt = (d.addedAt ?: 0L) * 1000L,
        parentId = d.parentRatingKey,
        sectionKey = sectionKey,
        summary = d.summary.orEmpty(),
    )

    private fun mapAlbumFromMetadata(m: MetadataDto, sectionKey: String): Album = Album(
        id = m.ratingKey ?: extractRatingKey(m.key).orEmpty(),
        title = m.title.orEmpty(),
        thumbPath = m.thumb,
        compositePath = m.composite,
        itemCount = m.leafCount ?: 0,
        addedAt = (m.addedAt ?: 0L) * 1000L,
        parentId = m.parentRatingKey,
        sectionKey = sectionKey,
        summary = m.summary.orEmpty(),
    )

    // ---- favourites & search ----

    override suspend fun favourites(): Result<List<MediaItem>> = runCatching {
        val libKey = session.session.value.libraryKey ?: throw IllegalStateException("No library selected")
        val extra = mapOf("userRating>" to "10")
        val photos = apiCall { token -> sectionAll(libKey, token, 13, null, null, null, extra) }.mediaContainer.metadata.map { mapItem(it, libKey) }
        val clips = apiCall { token -> sectionAll(libKey, token, 12, null, null, null, extra) }.mediaContainer.metadata.map { mapItem(it, libKey) }
        filterLocked(photos + clips).sortedByDescending { it.takenAt }
    }

    override suspend fun search(query: String): Result<List<MediaItem>> = runCatching {
        val libKey = session.session.value.libraryKey ?: throw IllegalStateException("No library selected")
        val photos = apiCall { token -> this.search(libKey, 13, query, token) }.mediaContainer.metadata.map { mapItem(it, libKey) }
        val clips = apiCall { token -> this.search(libKey, 12, query, token) }.mediaContainer.metadata.map { mapItem(it, libKey) }
        filterLocked(photos + clips).sortedByDescending { it.takenAt }
    }

    private fun filterLocked(items: List<MediaItem>): List<MediaItem> {
        val lockedItemIds = local.lockedItemIds.value
        val lockedAlbumIds = local.lockedAlbumIds.value
        return items.filter { it.id !in lockedItemIds && (it.albumId == null || it.albumId !in lockedAlbumIds) }
    }

    // ---- single items ----

    override suspend fun item(id: String): MediaItem? {
        allItems.value.firstOrNull { it.id == id }?.let { return it }
        val libKey = session.session.value.libraryKey ?: return null
        return runCatching {
            val container = apiCall { token -> metadata(id, token) }
            container.mediaContainer.metadata.firstOrNull()?.let { mapItem(it, libKey) }
        }.getOrNull()
    }

    override suspend fun items(ids: List<String>): List<MediaItem> = ids.mapNotNull { item(it) }

    // ---- mutations ----

    override suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit> {
        val previous = allItems.value
        allItems.update { list -> list.map { if (it.id == id) it.copy(favourite = favourite) else it } }
        return try {
            apiCall { token -> rate(id, if (favourite) 10 else -1, token) }
            Result.success(Unit)
        } catch (e: Exception) {
            allItems.value = previous
            Result.failure(e)
        }
    }

    override suspend fun rename(id: String, kind: MediaKind?, title: String): Result<Unit> {
        val previous = allItems.value
        allItems.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        return editField(id, typeCodeFor(kind, id), mapOf("title.value" to enc(title), "title.locked" to "1"))
            .onFailure { allItems.value = previous }
    }

    override suspend fun renameAlbum(albumId: String, title: String): Result<Unit> =
        editField(albumId, 14, mapOf("title.value" to enc(title), "title.locked" to "1"))

    override suspend fun setSummary(id: String, kind: MediaKind?, summary: String): Result<Unit> {
        val previous = allItems.value
        allItems.update { list -> list.map { if (it.id == id) it.copy(summary = summary) else it } }
        return editField(id, typeCodeFor(kind, id), mapOf("summary.value" to enc(summary), "summary.locked" to "1"))
            .onFailure { allItems.value = previous }
    }

    override suspend fun addTag(id: String, kind: MediaKind?, tag: String): Result<Unit> {
        val previous = allItems.value
        allItems.update { list -> list.map { if (it.id == id) it.copy(tags = (it.tags + tag).distinct()) else it } }
        return editField(id, typeCodeFor(kind, id), mapOf("tag[0].tag.tag" to enc(tag), "tag.locked" to "1"))
            .onFailure { allItems.value = previous }
    }

    override suspend fun removeTag(id: String, kind: MediaKind?, tag: String): Result<Unit> {
        val previous = allItems.value
        allItems.update { list -> list.map { if (it.id == id) it.copy(tags = it.tags - tag) else it } }
        return editField(id, typeCodeFor(kind, id), mapOf("tag[].tag.tag-" to enc(tag)))
            .onFailure { allItems.value = previous }
    }

    override suspend fun delete(id: String): Result<Unit> = try {
        apiCall { token -> deleteMetadata(id, token) }
        allItems.update { list -> list.filterNot { it.id == id } }
        Result.success(Unit)
    } catch (e: HttpException) {
        if (e.code() == 403) {
            Result.failure(IOException("Media deletion is disabled on the server. Enable it in Plex settings under Library."))
        } else {
            Result.failure(e)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) { cacheFile()?.delete() }
    }

    private fun typeCodeFor(kind: MediaKind?, id: String): Int = when (kind) {
        MediaKind.VIDEO -> 12
        MediaKind.PHOTO -> 13
        null -> if (allItems.value.firstOrNull { it.id == id }?.isVideo == true) 12 else 13
    }

    private suspend fun editField(id: String, type: Int, fields: Map<String, String>): Result<Unit> {
        val libKey = session.session.value.libraryKey ?: return Result.failure(IllegalStateException("No library selected"))
        val allFields = linkedMapOf("type" to type.toString(), "id" to id)
        allFields.putAll(fields)
        return try {
            apiCall { token -> edit(libKey, allFields, token) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- URLs ----

    override fun thumbUrl(item: MediaItem, size: Int): String {
        val sess = session.session.value
        val uri = sess.active?.uri ?: return ""
        val token = sess.serverToken ?: return ""
        val path = item.thumbPath ?: return ""
        return PlexUrls.thumb(uri, path, token, size)
    }

    override fun albumCoverUrl(album: Album, size: Int): String {
        val sess = session.session.value
        val uri = sess.active?.uri ?: return ""
        val token = sess.serverToken ?: return ""
        val overrideItemId = local.albumCovers.value[album.id]
        val overridePath = overrideItemId?.let { oid -> allItems.value.firstOrNull { it.id == oid }?.thumbPath }
        val path = overridePath ?: album.compositePath ?: album.thumbPath ?: return ""
        return PlexUrls.thumb(uri, path, token, size)
    }

    override fun originalUrl(item: MediaItem): String {
        val sess = session.session.value
        val uri = sess.active?.uri ?: return ""
        val token = sess.serverToken ?: return ""
        val part = item.partKey ?: return ""
        return PlexUrls.original(uri, part, token)
    }

    override fun downloadUrl(item: MediaItem): String {
        val sess = session.session.value
        val uri = sess.active?.uri ?: return ""
        val token = sess.serverToken ?: return ""
        val part = item.partKey ?: return ""
        return PlexUrls.download(uri, part, token)
    }

    override fun videoUrl(item: MediaItem, transcode: Boolean): String {
        val sess = session.session.value
        val uri = sess.active?.uri ?: return ""
        val token = sess.serverToken ?: return ""
        return if (!transcode) {
            val part = item.partKey ?: return ""
            PlexUrls.directPlay(uri, part, token)
        } else {
            val sid = transcodeSessionId ?: UUID.randomUUID().toString().also { transcodeSessionId = it }
            PlexUrls.hlsTranscode(uri, item.id, token, sess.clientId, "Android", sid)
        }
    }

    override suspend fun stopTranscode() {
        val sid = transcodeSessionId ?: return
        transcodeSessionId = null
        runCatching { apiCall { token -> this.stopTranscode(sid, token) } }
    }

    // ---- network plumbing ----

    /**
     * Runs a Plex server call, clearing the session on 401 and, on a connection
     * failure, re-running the connection chooser once and retrying.
     */
    private suspend fun <T> apiCall(block: suspend PlexServerApi.(token: String) -> T): T {
        val sess = session.session.value
        val token = sess.serverToken ?: throw IOException("Not signed in")
        return try {
            api.block(token)
        } catch (e: HttpException) {
            if (e.code() == 401) session.clear()
            throw e
        } catch (e: IOException) {
            val server = sess.server
            if (server != null) {
                val mode = settings.settings.value.connectionMode
                val manualUrl = settings.settings.value.manualServerUrl
                val newActive = connectionChooser.choose(server.connections, mode, manualUrl)
                if (newActive != null) {
                    session.update { it.copy(active = newActive) }
                    return api.block(token)
                }
            }
            throw e
        }
    }

    private fun mapItem(dto: MetadataDto, sectionKey: String): MediaItem {
        val id = dto.ratingKey ?: extractRatingKey(dto.key).orEmpty()
        val isVideo = dto.type == "clip"
        val media = dto.media.firstOrNull()
        val part = media?.part?.firstOrNull()
        return MediaItem(
            id = id,
            kind = if (isVideo) MediaKind.VIDEO else MediaKind.PHOTO,
            title = dto.title?.takeIf { it.isNotBlank() } ?: id,
            summary = dto.summary.orEmpty(),
            takenAt = parseTakenAt(dto.originallyAvailableAt, dto.addedAt),
            addedAt = (dto.addedAt ?: 0L) * 1000L,
            width = media?.width ?: 0,
            height = media?.height ?: 0,
            thumbPath = dto.thumb,
            partKey = part?.key,
            durationMs = dto.duration ?: media?.duration ?: 0L,
            favourite = (dto.userRating ?: 0.0) >= 10.0,
            albumId = dto.parentRatingKey,
            albumTitle = dto.parentTitle,
            exif = ExifInfo(
                make = media?.make,
                model = media?.model,
                lens = media?.lens,
                aperture = media?.aperture,
                exposure = media?.exposure,
                iso = media?.iso,
                container = media?.container,
                videoCodec = media?.videoCodec,
                audioCodec = media?.audioCodec,
                videoResolution = media?.videoResolution,
            ),
            fileSize = part?.size ?: 0L,
            filePath = part?.file,
            tags = dto.tag.mapNotNull { it.tag.takeIf { t -> t.isNotBlank() } },
            place = dto.place.firstOrNull()?.tag?.takeIf { it.isNotBlank() },
            country = dto.country.firstOrNull()?.tag?.takeIf { it.isNotBlank() },
            sectionKey = sectionKey,
        )
    }
}
