package au.prism.photos.data

import android.app.Application
import au.prism.photos.BuildConfig
import au.prism.photos.data.plex.DynamicBaseUrlInterceptor
import au.prism.photos.data.plex.PlexHeaders
import au.prism.photos.data.plex.PlexServerApi
import au.prism.photos.data.plex.PlexTvApi
import au.prism.photos.domain.DeviceMediaSource
import au.prism.photos.domain.LocalStore
import au.prism.photos.domain.MediaRepository
import au.prism.photos.domain.PlexAuth
import au.prism.photos.domain.SessionStore
import au.prism.photos.domain.SettingsStore
import au.prism.photos.domain.UpdateChecker
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manual dependency graph. The data layer owns this file and replaces the fakes
 * with real implementations. UI code reaches it through PrismApp.graph.
 */
class AppGraph(val app: Application) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val settings: SettingsStore = SettingsStoreImpl(app)
    val session: SessionStore = SessionStoreImpl(app)
    val local: LocalStore = LocalStoreImpl(app)

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }

    private val plexHeaders = PlexHeaders(clientIdProvider = { session.session.value.clientId })

    /** Plain client with no Plex headers - used for plex.tv host probing and GitHub update checks. */
    private val plainHttpClient: OkHttpClient = baseClientBuilder().build()

    /** Fixed host plex.tv client. */
    private val plexTvHttpClient: OkHttpClient = baseClientBuilder().addInterceptor(plexHeaders).build()

    /** Dynamic host client for the currently active Plex server connection. */
    private val plexServerHttpClient: OkHttpClient = baseClientBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor { session.session.value.active?.uri })
        .addInterceptor(plexHeaders)
        .build()

    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    private val plexTvApi: PlexTvApi = Retrofit.Builder()
        .baseUrl("https://plex.tv/")
        .client(plexTvHttpClient)
        .addConverterFactory(converterFactory)
        .build()
        .create(PlexTvApi::class.java)

    private val plexServerApi: PlexServerApi = Retrofit.Builder()
        .baseUrl("http://prism-server.invalid/")
        .client(plexServerHttpClient)
        .addConverterFactory(converterFactory)
        .build()
        .create(PlexServerApi::class.java)

    val connectionChooser = ConnectionChooser(plainHttpClient)

    val auth: PlexAuth = PlexAuthImpl(
        plexTvApi = plexTvApi,
        connectionChooser = connectionChooser,
        plainHttpClient = plainHttpClient,
        json = json,
        clientIdProvider = { session.session.value.clientId },
    )

    val media: MediaRepository = MediaRepositoryImpl(
        api = plexServerApi,
        session = session,
        settings = settings,
        local = local,
        connectionChooser = connectionChooser,
        json = json,
        filesDir = app.filesDir,
        scope = scope,
    )

    val updates: UpdateChecker = UpdateCheckerImpl(app, plainHttpClient, json)

    val device: DeviceMediaSource = DeviceMediaSourceImpl(app)

    /** Shared Coil 3 image loader. The UI sets this as the SingletonImageLoader in MainActivity. */
    val imageLoader: ImageLoader = ImageLoader.Builder(app)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { plexServerHttpClient }))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(app, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(File(app.cacheDir, "images").toOkioPath())
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
