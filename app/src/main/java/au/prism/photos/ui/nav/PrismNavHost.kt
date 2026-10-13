package au.prism.photos.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.ViewerSource
import au.prism.photos.ui.albums.AlbumDetailScreen
import au.prism.photos.ui.albums.MyAlbumDetailScreen
import au.prism.photos.ui.components.LocalPickHandler
import au.prism.photos.ui.editor.EditorScreen
import au.prism.photos.ui.home.HomeScreen
import au.prism.photos.ui.lock.LockGate
import au.prism.photos.ui.search.SearchScreen
import au.prism.photos.ui.servers.LibraryPickerScreen
import au.prism.photos.ui.servers.ServerPickerScreen
import au.prism.photos.ui.settings.SettingsScreen
import au.prism.photos.ui.signin.SignInScreen
import au.prism.photos.ui.viewer.ViewerScreen

private const val ANIM_MS = 260

/**
 * Root navigation host. [pendingViewUris] is set by MainActivity when an external intent
 * (VIEW, REVIEW, SEND) hands Prism a list of content URIs to show in the viewer straight away.
 * [pickHandler], when non null, means the activity was launched in ACTION_PICK / GET_CONTENT
 * mode: taps on media cells across the app resolve the pick instead of opening the viewer.
 */
@Composable
fun PrismNavHost(
    navController: NavHostController = rememberNavController(),
    pendingViewUris: List<String>? = null,
    onViewUrisConsumed: () -> Unit = {},
    pickHandler: ((MediaItem) -> Unit)? = null,
) {
    val graph = PrismApp.graph
    val session by graph.session.session.collectAsStateWithLifecycle()
    val start = remember(session.isSignedIn, session.hasLibrary) {
        when {
            !session.isSignedIn -> Routes.SIGN_IN
            !session.hasLibrary -> Routes.LIBRARIES
            else -> Routes.HOME
        }
    }

    CompositionLocalProvider(LocalPickHandler provides pickHandler) {
        NavHost(
            navController = navController,
            startDestination = start,
            enterTransition = { slideInHorizontally(tween(ANIM_MS)) { it / 4 } + fadeIn(tween(ANIM_MS)) },
            exitTransition = { fadeOut(tween(ANIM_MS)) },
            popEnterTransition = { fadeIn(tween(ANIM_MS)) },
            popExitTransition = { slideOutHorizontally(tween(ANIM_MS)) { it / 4 } + fadeOut(tween(ANIM_MS)) },
        ) {
            composable(Routes.SIGN_IN) {
                SignInScreen(
                    onSignedIn = { navController.navigate(Routes.SERVERS) { popUpTo(Routes.SIGN_IN) { inclusive = true } } },
                    onManualConnected = { navController.navigate(Routes.LIBRARIES) { popUpTo(Routes.SIGN_IN) { inclusive = true } } },
                )
            }
            composable(Routes.SERVERS) {
                ServerPickerScreen(onSelected = { navController.navigate(Routes.LIBRARIES) })
            }
            composable(Routes.LIBRARIES) {
                LibraryPickerScreen(onSelected = { navController.navigate(Routes.HOME) { popUpTo(0) } })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAlbum = { albumId -> navController.navigate(Routes.album(albumId)) },
                    onOpenMyAlbum = { albumId -> navController.navigate(Routes.myAlbum(albumId)) },
                    onOpenViewer = { source, index -> navController.navigate(Routes.viewer(SourceCodec.encode(source), index)) },
                )
            }
            composable(
                Routes.ALBUM,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType },
                    lockedArgument(),
                ),
            ) { entry ->
                val albumId = Routes.decodeArg(entry.arguments?.getString("albumId").orEmpty())
                // Decided on entry: locking the album from inside pops back anyway, and this keeps the
                // gate in place through the exit animation and after process death restores the entry.
                val gated = remember(albumId) {
                    entry.arguments?.getBoolean("locked") == true || albumId in graph.local.lockedAlbumIds.value
                }
                Gated(gated) {
                    AlbumDetailScreen(
                        albumId = albumId,
                        showLocked = gated,
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id, locked = gated)) },
                        onOpenViewer = { index ->
                            navController.navigate(Routes.viewer(SourceCodec.encode(ViewerSource.Album(albumId)), index, locked = gated))
                        },
                    )
                }
            }
            composable(
                Routes.MY_ALBUM,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { entry ->
                val albumId = Routes.decodeArg(entry.arguments?.getString("albumId").orEmpty())
                MyAlbumDetailScreen(
                    albumId = albumId,
                    onBack = { navController.popBackStack() },
                    onOpenViewer = { index -> navController.navigate(Routes.viewer(SourceCodec.encode(ViewerSource.MyAlbum(albumId)), index)) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenViewer = { query, index -> navController.navigate(Routes.viewer(SourceCodec.encode(ViewerSource.Search(query)), index)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSwitchServer = { navController.navigate(Routes.SERVERS) },
                    onSwitchLibrary = { navController.navigate(Routes.LIBRARIES) },
                    onSignedOut = { navController.navigate(Routes.SIGN_IN) { popUpTo(0) } },
                )
            }
            composable(
                Routes.VIEWER,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType },
                    lockedArgument(),
                ),
            ) { entry ->
                val sourceArg = entry.arguments?.getString("source").orEmpty()
                val index = entry.arguments?.getInt("index") ?: 0
                val source = remember(sourceArg) { SourceCodec.decode(sourceArg) }
                val gated = remember(sourceArg) {
                    entry.arguments?.getBoolean("locked") == true ||
                        source == ViewerSource.Locked ||
                        (source is ViewerSource.Album && source.albumId in graph.local.lockedAlbumIds.value)
                }
                Gated(gated) {
                    ViewerScreen(
                        source = source,
                        showLocked = gated,
                        startIndex = index,
                        onClose = { navController.popBackStack() },
                        onEdit = { itemId -> navController.navigate(Routes.editor(itemId, locked = gated)) },
                        onEditVideo = { itemId -> navController.navigate(Routes.videoEditor(itemId, locked = gated)) },
                    )
                }
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }, lockedArgument()),
            ) { entry ->
                val itemId = Routes.decodeArg(entry.arguments?.getString("itemId").orEmpty())
                Gated(entry.arguments?.getBoolean("locked") == true) {
                    EditorScreen(
                        itemId = itemId,
                        onDone = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
            composable(
                Routes.VIDEO_EDITOR,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }, lockedArgument()),
            ) { entry ->
                val itemId = Routes.decodeArg(entry.arguments?.getString("itemId").orEmpty())
                Gated(entry.arguments?.getBoolean("locked") == true) {
                    au.prism.photos.ui.videoeditor.VideoEditorScreen(
                        itemId = itemId,
                        onDone = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
            composable(
                Routes.UPLOAD,
                arguments = listOf(navArgument("uris") { type = NavType.StringType }),
            ) { entry ->
                val uris = Routes.decodeArg(entry.arguments?.getString("uris").orEmpty())
                    .split("|").filter { it.isNotBlank() }
                au.prism.photos.ui.upload.UploadScreen(
                    uris = uris,
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
        }
    }

    LaunchedEffect(pendingViewUris) {
        val uris = pendingViewUris
        if (!uris.isNullOrEmpty()) {
            navController.navigate(Routes.viewer(SourceCodec.encode(ViewerSource.ExternalUris(uris)), 0))
            onViewUrisConsumed()
        }
    }
}

/** Optional `locked` query argument shared by destinations that can show locked content. */
private fun lockedArgument() = navArgument("locked") {
    type = NavType.BoolType
    defaultValue = false
}

/** Wraps [content] in a [LockGate] when [gated]; otherwise shows it as is. */
@Composable
private fun Gated(gated: Boolean, content: @Composable () -> Unit) {
    if (gated) LockGate(content) else content()
}
