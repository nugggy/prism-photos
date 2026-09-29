package au.prism.photos.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import au.prism.photos.ui.theme.PlexGold

/**
 * Photo editor for the Plex item or device item [itemId] (device items are content:// URIs
 * passed through as the id). Saves a JPEG (or PNG/WebP) copy to Pictures/Plex Gallery via
 * MediaStore, or shares a copy through the system share sheet, then calls [onDone] with the
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
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val exportSettings by vm.exportSettings.collectAsStateWithLifecycle()
    val compareOriginal by vm.compareOriginal.collectAsStateWithLifecycle()

    var selectedTool by remember { mutableStateOf(EditorTool.ADJUST) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hasChanges = state != EditState()
    val context = LocalContext.current

    fun requestClose() {
        if (hasChanges) showDiscardConfirm = true else onCancel()
    }

    fun shareResult(result: ExportResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share photo"))
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
                    IconButton(onClick = { vm.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { vm.toggleCompare() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Compare with original",
                            tint = if (compareOriginal) PlexGold else Color.White,
                        )
                    }
                    IconButton(onClick = { showRevertConfirm = true }, enabled = hasChanges) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Revert to original")
                    }
                    TextButton(onClick = { vm.save(onDone) }, enabled = !saving && bitmap != null) {
                        Text("Save", color = PlexGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                EditorTool.entries.forEach { tool ->
                    NavigationBarItem(
                        selected = tool == selectedTool,
                        onClick = { selectedTool = tool },
                        icon = {
                            Icon(
                                when (tool) {
                                    EditorTool.ADJUST -> Icons.Filled.Tune
                                    EditorTool.FILTERS -> Icons.Filled.FilterVintage
                                    EditorTool.CROP -> Icons.Filled.Crop
                                    EditorTool.MARKUP -> Icons.Filled.Draw
                                    EditorTool.FRAMES -> Icons.Filled.PictureInPicture
                                    EditorTool.EXPORT -> Icons.Filled.IosShare
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
                        EditorPreviewView(vm = vm, tool = selectedTool)
                    }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        when (selectedTool) {
                            EditorTool.ADJUST -> AdjustTab(vm, state.adjustments)
                            EditorTool.FILTERS -> FiltersTab(vm, bmp, state.filter, state.filterStrength)
                            EditorTool.CROP -> CropTab(vm, state)
                            EditorTool.MARKUP -> MarkupTab(vm, state.markup)
                            EditorTool.FRAMES -> FramesTab(vm, state.frame, vm.hasTakenAt)
                            EditorTool.EXPORT -> ExportTab(
                                vm = vm,
                                settings = exportSettings,
                                onSaveCopy = { vm.save(onDone) },
                                onShare = { vm.exportForShare { result -> shareResult(result) } },
                            )
                        }
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

    if (showRevertConfirm) {
        AlertDialog(
            onDismissRequest = { showRevertConfirm = false },
            title = { Text("Revert to original?") },
            text = { Text("This clears every adjustment, filter, crop, markup and frame you've applied.") },
            confirmButton = {
                TextButton(onClick = { showRevertConfirm = false; vm.revertToOriginal() }) { Text("Revert") }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
