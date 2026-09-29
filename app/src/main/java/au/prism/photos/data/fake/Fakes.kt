package au.prism.photos.data.fake

import au.prism.photos.domain.ActiveConnection
import au.prism.photos.domain.Album
import au.prism.photos.domain.AlbumContents
import au.prism.photos.domain.AppSettings
import au.prism.photos.domain.ConnectionKind
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.DeviceAlbum
import au.prism.photos.domain.DeviceMediaSource
import au.prism.photos.domain.DownloadProgress
import au.prism.photos.domain.ExifInfo
import au.prism.photos.domain.LoadState
import au.prism.photos.domain.LocalStore
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import au.prism.photos.domain.MediaRepository
import au.prism.photos.domain.PlexAuth
import au.prism.photos.domain.PlexLibrary
import au.prism.photos.domain.PlexPin
import au.prism.photos.domain.PlexServer
import au.prism.photos.domain.PlexUser
import au.prism.photos.domain.Session
import au.prism.photos.domain.SessionStore
import au.prism.photos.domain.SettingsStore
import au.prism.photos.domain.SortOrder
import au.prism.photos.domain.UpdateChecker
import au.prism.photos.domain.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory fakes so the UI compiles and can be previewed before the real data layer lands.
 * Sample images come from picsum.photos so the fake grid renders on an emulator with internet.
 */

fun sampleItems(count: Int = 120): List<MediaItem> {
    val now = System.currentTimeMillis()
    return (0 until count).map { i ->
        val video = i % 7 == 3
        MediaItem(
            id = "fake-$i",
            kind = if (video) MediaKind.VIDEO else MediaKind.PHOTO,
            title = if (video) "VID_${1000 + i}.mp4" else "IMG_${1000 + i}.jpg",
            takenAt = now - i * 26L * 60 * 60 * 1000,
            width = if (i % 3 == 0) 3024 else 4032,
            height = if (i % 3 == 0) 4032 else 3024,
            thumbPath = "/fake/$i",
            partKey = "/fake/part/$i",
            durationMs = if (video) 12_000L + i * 1000 else 0,
            favourite = i % 9 == 0,
            albumId = "album-${i % 4}",
            albumTitle = "Album ${i % 4}",
            exif = ExifInfo(make = "OnePlus", model = "OnePlus 15", aperture = "f/1.8", exposure = "1/120", iso = 50, lens = "23mm"),
            fileSize = 3_456_789L + i,
            filePath = "/media/photos/IMG_${1000 + i}.jpg",
            tags = if (i % 5 == 0) listOf("Beach", "Family") else emptyList(),
            place = if (i % 4 == 0) "Port Macquarie" else null,
            country = "Australia",
            sectionKey = "3",
        )
    }
}

fun sampleAlbums(): List<Album> = (0 until 4).map { i ->
    Album(id = "album-$i", title = "Album $i", thumbPath = "/fake/${i * 5}", itemCount = 30, addedAt = System.currentTimeMillis(), sectionKey = "3")
}

class FakeSettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

class FakeSessionStore : SessionStore {
    private val state = MutableStateFlow(
        Session(
            accountToken = "fake",
            user = PlexUser(username = "demo"),
            server = PlexServer("Demo Server", "demo", "token", emptyList()),
            serverToken = "token",
            active = ActiveConnection("http://demo:32400", ConnectionKind.LOCAL),
            libraryKey = "3",
            libraryTitle = "Photos",
            clientId = UUID.randomUUID().toString(),
        )
    )
    override val session: StateFlow<Session> = state.asStateFlow()
    override suspend fun update(transform: (Session) -> Session) = state.update(transform)
    override suspend fun clear() = state.update { Session(clientId = it.clientId) }
}

class FakeLocalStore : LocalStore {
    private val locked = MutableStateFlow<Set<String>>(emptySet())
    private val lockedAlbums = MutableStateFlow<Set<String>>(emptySet())
    private val covers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val accents = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val sorts = MutableStateFlow<Map<String, SortOrder>>(emptyMap())
    override val lockedItemIds: StateFlow<Set<String>> = locked.asStateFlow()
    override val lockedAlbumIds: StateFlow<Set<String>> = lockedAlbums.asStateFlow()
    override val albumCovers: StateFlow<Map<String, String>> = covers.asStateFlow()
    override val albumAccents: StateFlow<Map<String, Long>> = accents.asStateFlow()
    override val albumSort: StateFlow<Map<String, SortOrder>> = sorts.asStateFlow()
    override suspend fun setItemLocked(id: String, locked: Boolean) = this.locked.update { if (locked) it + id else it - id }
    override suspend fun setItemsLocked(ids: Collection<String>, locked: Boolean) = this.locked.update { if (locked) it + ids else it - ids.toSet() }
    override suspend fun setAlbumLocked(id: String, locked: Boolean) = lockedAlbums.update { if (locked) it + id else it - id }
    override suspend fun setAlbumCover(albumId: String, itemId: String?) = covers.update { if (itemId == null) it - albumId else it + (albumId to itemId) }
    override suspend fun setAlbumAccent(albumId: String, argb: Long?) = accents.update { if (argb == null) it - albumId else it + (albumId to argb) }
    override suspend fun setAlbumSort(albumId: String, order: SortOrder?) = sorts.update { if (order == null) it - albumId else it + (albumId to order) }
}

class FakePlexAuth : PlexAuth {
    override suspend fun createPin(): Result<PlexPin> = Result.success(PlexPin(1, "ABCD"))
    override fun authUrl(pin: PlexPin): String = "https://app.plex.tv/auth#?code=${pin.code}"
    override suspend fun pollPin(pinId: Long): Result<String?> = Result.success("fake-token")
    override suspend fun user(accountToken: String): Result<PlexUser> = Result.success(PlexUser(username = "demo"))
    override suspend fun servers(accountToken: String): Result<List<PlexServer>> =
        Result.success(listOf(PlexServer("Demo Server", "demo", "token", emptyList())))
    override suspend fun connect(server: PlexServer, mode: ConnectionMode, manualUrl: String?): Result<ActiveConnection> =
        Result.success(ActiveConnection("http://demo:32400", ConnectionKind.LOCAL))
    override suspend fun verifyManual(url: String, token: String): Result<String> = Result.success("Demo Server")
    override suspend fun signOut(accountToken: String?) {}
}

class FakeMediaRepository(private val local: LocalStore, scope: CoroutineScope) : MediaRepository {
    private val all = MutableStateFlow(sampleItems())
    private val visible = MutableStateFlow<List<MediaItem>>(emptyList())
    private val lockedFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    private val state = MutableStateFlow<LoadState>(LoadState.Loaded)

    init {
        all.onEach { recompute() }.launchIn(scope)
        local.lockedItemIds.onEach { recompute() }.launchIn(scope)
    }

    private fun recompute() {
        val locked = local.lockedItemIds.value
        visible.value = all.value.filter { it.id !in locked }
        lockedFlow.value = all.value.filter { it.id in locked }
    }

    override val timeline: StateFlow<List<MediaItem>> = visible.asStateFlow()
    override val timelineState: StateFlow<LoadState> = state.asStateFlow()
    override val lockedItems: StateFlow<List<MediaItem>> = lockedFlow.asStateFlow()

    override suspend fun refreshTimeline(force: Boolean) { state.value = LoadState.Loaded }
    override suspend fun libraries(): Result<List<PlexLibrary>> = Result.success(listOf(PlexLibrary("3", "Photos")))
    override suspend fun rootAlbums(): Result<AlbumContents> = Result.success(AlbumContents(sampleAlbums(), emptyList()))
    override suspend fun albumContents(albumId: String): Result<AlbumContents> =
        Result.success(AlbumContents(emptyList(), all.value.filter { it.albumId == albumId }))
    override suspend fun album(albumId: String): Album? = sampleAlbums().firstOrNull { it.id == albumId }
    override suspend fun favourites(): Result<List<MediaItem>> = Result.success(visible.value.filter { it.favourite })
    override suspend fun search(query: String): Result<List<MediaItem>> =
        Result.success(visible.value.filter { it.title.contains(query, true) || it.tags.any { t -> t.contains(query, true) } })
    override suspend fun item(id: String): MediaItem? = all.value.firstOrNull { it.id == id }
    override suspend fun items(ids: List<String>): List<MediaItem> = ids.mapNotNull { id -> all.value.firstOrNull { it.id == id } }

    override suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit> {
        all.update { list -> list.map { if (it.id == id) it.copy(favourite = favourite) else it } }
        return Result.success(Unit)
    }
    override suspend fun rename(id: String, kind: MediaKind?, title: String): Result<Unit> {
        all.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        return Result.success(Unit)
    }
    override suspend fun renameAlbum(albumId: String, title: String): Result<Unit> = Result.success(Unit)
    override suspend fun setSummary(id: String, kind: MediaKind?, summary: String): Result<Unit> {
        all.update { list -> list.map { if (it.id == id) it.copy(summary = summary) else it } }
        return Result.success(Unit)
    }
    override suspend fun addTag(id: String, kind: MediaKind?, tag: String): Result<Unit> {
        all.update { list -> list.map { if (it.id == id) it.copy(tags = (it.tags + tag).distinct()) else it } }
        return Result.success(Unit)
    }
    override suspend fun removeTag(id: String, kind: MediaKind?, tag: String): Result<Unit> {
        all.update { list -> list.map { if (it.id == id) it.copy(tags = it.tags - tag) else it } }
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        all.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }
    override suspend fun clearCache() {}

    private fun seed(item: MediaItem) = item.id.removePrefix("fake-").toIntOrNull() ?: 1
    override fun thumbUrl(item: MediaItem, size: Int): String = "https://picsum.photos/seed/${seed(item)}/$size/$size"
    override fun albumCoverUrl(album: Album, size: Int): String = "https://picsum.photos/seed/${album.id}/$size/$size"
    override fun originalUrl(item: MediaItem): String = "https://picsum.photos/seed/${seed(item)}/${item.width / 2}/${item.height / 2}"
    override fun downloadUrl(item: MediaItem): String = originalUrl(item)
    override fun videoUrl(item: MediaItem, transcode: Boolean): String =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    override suspend fun stopTranscode() {}
}

class FakeUpdateChecker : UpdateChecker {
    override val installedVersion: String = "1.0.0"
    override suspend fun check(repo: String): Result<UpdateInfo?> = Result.success(null)
    override fun download(info: UpdateInfo): Flow<DownloadProgress> = flow { emit(DownloadProgress.Failed("Not available in fake mode")) }
    override fun install(filePath: String) {}
}

class FakeDeviceMediaSource : DeviceMediaSource {
    override suspend fun hasPermission(): Boolean = false
    override suspend fun albums(): List<DeviceAlbum> = emptyList()
    override suspend fun items(bucketId: String?): List<MediaItem> = emptyList()
    override suspend fun itemsForUris(uris: List<String>): List<MediaItem> = emptyList()
}
