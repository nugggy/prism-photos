package au.prism.photos.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import au.prism.photos.ui.theme.PlexGold

/**
 * Photo editor for the Plex item or device item [itemId] (device items are content:// URIs
 * passed through as the id). Saves a JPEG copy to Pictures/Prism and calls [onDone] with the
 * saved content URI, or [onCancel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    itemId: String,
    onDone: (savedUri: String) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory(itemId))
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val bitmap by vm.sourceBitmap.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()

    var selectedTool by remember { mutableStateOf(EditorTool.CROP) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hasChanges = state != EditState()

    fun requestClose() {
        if (hasChanges) showDiscardConfirm = true else onCancel()
    }

    BackHandler { requestClose() }

    LaunchedEffect(error) { error?.let { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { vm.reset() }, enabled = hasChanges) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset")
                    }
                    TextButton(onClick = { vm.save(onDone) }, enabled = !saving && bitmap != null) {
                        Text("Save", color = PlexGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White),
            )
        },
        bottomBar = {
            NavigationBar {
                EditorTool.entries.forEach { tool ->
                    NavigationBarItem(
                        selected = tool == selectedTool,
                        onClick = { selectedTool = tool },
                        icon = {
                            Icon(
                                when (tool) {
                                    EditorTool.CROP -> Icons.Filled.Crop
                                    EditorTool.ROTATE -> Icons.Filled.RotateRight
                                    EditorTool.ADJUST -> Icons.Filled.Tune
                                    EditorTool.FILTERS -> Icons.Filled.FilterVintage
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(tool.label) },
                    )
                }
            }
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            val bmp = bitmap
            if (loading || bmp == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = PlexGold)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        EditorPreview(
                            bitmap = bmp,
                            state = state,
                            tool = selectedTool,
                            onCropChange = { vm.setCrop(it) },
                        )
                    }
                    val bitmapAspect = bmp.width.toFloat() / bmp.height.toFloat()
                    when (selectedTool) {
                        EditorTool.CROP -> CropControls(
                            state = state,
                            bitmapAspect = bitmapAspect,
                            onRatioSelected = { ratio, rect -> vm.setCropRatio(ratio, rect) },
                            onReset = { vm.setCropRatio(null, CropRect()) },
                        )
                        EditorTool.ROTATE -> RotateControls(
                            onRotate = { vm.rotate90() },
                            onFlipHorizontal = { vm.flipHorizontal() },
                            onFlipVertical = { vm.flipVertical() },
                        )
                        EditorTool.ADJUST -> AdjustControls(state.adjustments) { vm.setAdjustments(it) }
                        EditorTool.FILTERS -> FilterControls(bmp, state.filter) { vm.setFilter(it) }
                    }
                }
            }

            if (saving) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PlexGold)
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onCancel() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}
