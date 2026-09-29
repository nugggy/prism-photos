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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
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

    /** Locked items that are not part of the loaded timeline, fetched by id so the Locked tab always shows them. */
    private val extraLocked = MutableStateFlow<Map<String, MediaItem>>(emptyMap())
    private val extraLockedInFlight = java.util.Collections.synchronizedSet(HashSet<String>())

    init {
        combine(allItems, local.lockedItemIds, local.lockedAlbumIds, extraLocked) { all, lockedIds, lockedAlbums, extras ->
            listOf(all, lockedIds, lockedAlbums, extras)
        }.onEach { parts ->
            @Suppress("UNCHECKED_CAST")
            val all = parts[0] as List<MediaItem>
            @Suppress("UNCHECKED_CAST")
            val lockedIds = parts[1] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val lockedAlbums = parts[2] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val extras = parts[3] as Map<String, MediaItem>
            fun isLocked(item: MediaItem) = item.id in lockedIds || (item.albumId != null && item.albumId in lockedAlbums)
            visibleFlow.value = all.filterNot { isLocked(it) }
            val known = all.filter { isLocked(it) }
            val knownIds = known.map { it.id }.toSet()
            val missing = lockedIds.filter { it !in knownIds }
            lockedFlow.value = (known + missing.mapNotNull { extras[it] }).sortedByDescending { it.takenAt }
            // Fetch locked items the timeline does not contain (for example when the timeline is empty).
            val toFetch = missing.filter { it !in extras && extraLockedInFlight.add(it) }
            if (toFetch.isNotEmpty()) scope.launch {
                toFetch.forEach { id ->
                    val fetched = attempt { item(id) }.getOrNull()
                    if (fetched != null) extraLocked.update { it + (id to fetched) }
                    extraLockedInFlight.remove(id)
                }
            }
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

    private var refreshJob: Deferred<Unit>? = null

    /**
     * Runs the refresh on the repository's own application scope so that navigating away from the
     * screen that asked for it cannot cancel the network requests. Callers that are cancelled simply
     * stop waiting; the refresh itself carries on and updates [timeline] when done.
     */
    override suspend fun refreshTimeline(force: Boolean) {
        val sess = session.session.value
        if (!sess.hasLibrary) return
        val libKey = sess.libraryKey ?: return
        val job = synchronized(this) {
            val existing = refreshJob
            if (existing != null && existing.isActive) {
                existing
            } else {
                scope.async { doRefresh(libKey, sess.active?.uri, sess.active?.kind?.name) }.also { refreshJob = it }
            }
        }
        job.await()
    }

    private suspend fun doRefresh(libKey: String, uri: String?, kind: String?) {
        timelineStateFlow.value = LoadState.Loading
        Diagnostics.log("Refreshing library $libKey via $uri ($kind)")
        val photos = attempt { fetchAllPages(libKey, type = 13) }
        val clips = attempt { fetchAllPages(libKey, type = 12) }
        val photosErr = photos.exceptionOrNull()
        val clipsErr = clips.exceptionOrNull()
        if (photosErr != null && clipsErr != null) {
            val message = describe(photosErr)
            Diagnostics.log("Library refresh failed: $message")
            timelineStateFlow.value = LoadState.Error(message)
            return
        }
        var merged = mergeTimelines(photos.getOrDefault(emptyList()), clips.getOrDefault(emptyList()))
        // Second source, only when the type filters found nothing: walk the album folders the way
        // Plex organises photo libraries. This does not depend on the numeric type filters.
        if (merged.isEmpty()) {
            val walked = attempt { walkAlbums(libKey) }
            walked.exceptionOrNull()?.let { Diagnostics.log("Album walk failed: ${describe(it)}") }
            merged = mergeTimelines(merged, walked.getOrDefault(emptyList()))
        }
        merged = merged.distinctBy { it.id }
        Diagnostics.log("Timeline ready: ${merged.size} items (${merged.count { !it.isVideo }} photos, ${merged.count { it.isVideo }} videos)")
        allItems.value = merged
        persistCache(merged)
        timelineStateFlow.value = LoadState.Loaded
    }

    /** Like runCatching, but never swallows coroutine cancellation. */
    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Recursively lists every photo and video by walking albums from the library root. */
    private suspend fun walkAlbums(libraryKey: String): List<MediaItem> {
        val started = System.currentTimeMillis()
        val items = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
        val seenAlbums = java.util.Collections.synchronizedSet(HashSet<String>())
        val root = apiCall { token -> sectionAll(libraryKey, token, null, null, null, null) }.mediaContainer
        val queue = java.util.concurrent.ConcurrentLinkedQueue<String>()
        fun absorb(mc: MetadataMediaContainerDto) {
            mc.metadata.filter { isMediaItem(it) }.map { mapItem(it, libraryKey) }.forEach { if (it.id.isNotBlank()) items[it.id] = it }
            val albumIds = mc.directory.filter { it.type == "photoalbum" }.mapNotNull { it.ratingKey ?: extractRatingKey(it.key) } +
                mc.metadata.filter { it.type == "photoalbum" }.mapNotNull { it.ratingKey ?: extractRatingKey(it.key) }
            albumIds.forEach { if (seenAlbums.add(it)) queue.add(it) }
        }
        absorb(root)
        var albumsWalked = 0
        val semaphore = Semaphore(4)
        while (queue.isNotEmpty() && albumsWalked < 5000) {
            val batch = generateSequence { queue.poll() }.take(32).toList()
            kotlinx.coroutines.coroutineScope {
                batch.map { albumId ->
                    async {
                        semaphore.withPermit {
                            attempt { apiCall { token -> children(albumId, token) }.mediaContainer }
                                .onFailure { Diagnostics.log("Album $albumId failed: ${describe(it)}") }
                                .getOrNull()?.let { absorb(it) }
                        }
                    }
                }.awaitAll()
            }
            albumsWalked += batch.size
        }
        Diagnostics.log("Album walk: $albumsWalked albums, ${items.size} items in ${System.currentTimeMillis() - started} ms")
        return items.values.toList()
    }

    /** True for anything that is a photo or a video, whatever the server calls it. */
    private fun isMediaItem(dto: MetadataDto): Boolean {
        if (dto.type == "photoalbum") return false
        if (dto.type == "photo" || dto.type == "clip" || dto.type == "video") return true
        return dto.media.any { it.part.isNotEmpty() }
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
        // Some servers answer a sorted or paged request with zero items and no error, so step down
        // through sorted+paged, unsorted+paged and unsorted+unpaged until something comes back.
        val attempts = listOf(
            Triple("sorted", "originallyAvailableAt:desc", true),
            Triple("unsorted", null, true),
            Triple("unpaged", null, false),
        )
        var lastError: Exception? = null
        for ((name, sort, paged) in attempts) {
            try {
                val (items, total) = fetchPages(libraryKey, type, sort, paged)
                if (items.isNotEmpty()) {
                    Diagnostics.log("Fetched ${items.size} $label (type $type, $name) in ${System.currentTimeMillis() - started} ms")
                    return items
                }
                Diagnostics.log("Type $type $name request returned 0 items (server total $total), trying the next approach")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Diagnostics.log("Type $type $name request failed: ${describe(e)}")
            }
        }
        lastError?.let { throw it }
        Diagnostics.log("No $label found by type $type")
        return emptyList()
    }

    private suspend fun fetchPages(libraryKey: String, type: Int, sort: String?, paged: Boolean): Pair<List<MediaItem>, Int> {
        val out = mutableListOf<MediaItem>()
        var start = 0
        val pageSize = 500
        var total = 0
        while (true) {
            val container = apiCall { token ->
                if (paged) sectionAll(libraryKey, token, type, sort, start, pageSize) else sectionAll(libraryKey, token, type, sort, null, null)
            }
            val mc = container.mediaContainer
            val page = mc.metadata.map { mapItem(it, libraryKey) }
            out += page
            total = mc.totalSize ?: mc.size ?: page.size
            if (start == 0) Diagnostics.log("Type $type page 1 ($sort, paged=$paged): ${page.size} items, size=${mc.size}, totalSize=${mc.totalSize}")
            start += pageSize
            if (!paged || page.isEmpty() || page.size < pageSize || start >= total) break
        }
        return out to total
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
            Diagnostics.log("Request failed (${e::class.java.simpleName}: ${e.message}); checking connections")
            val server = sess.server
            if (server != null) {
                val mode = settings.settings.value.connectionMode
                val manualUrl = settings.settings.value.manualServerUrl
                val newActive = connectionChooser.choose(server.connections, mode, manualUrl)
                if (newActive != null) {
                    Diagnostics.log("Reconnected via ${newActive.uri} (${newActive.kind}); retrying once")
                    session.update { it.copy(active = newActive) }
                    return api.block(token)
                }
            }
            throw e
        }
    }

    private fun mapItem(dto: MetadataDto, sectionKey: String): MediaItem {
        val id = dto.ratingKey ?: extractRatingKey(dto.key).orEmpty()
        val isVideo = dto.type == "clip" || dto.type == "video" || dto.type == "movie" || dto.type == "episode" ||
            dto.media.firstOrNull()?.videoCodec != null || (dto.duration ?: 0L) > 0L
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
