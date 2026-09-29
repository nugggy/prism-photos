package au.prism.photos.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.BuildConfig
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.DownloadProgress
import au.prism.photos.domain.UpdateInfo
import kotlinx.coroutines.launch

private const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L

class UpdateViewModel(private val graph: AppGraph) : ViewModel() {
    var available by mutableStateOf<UpdateInfo?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloadProgress by mutableStateOf<DownloadProgress?>(null)
        private set
    var manualMessage by mutableStateOf<String?>(null)
        private set
    var dialogOpen by mutableStateOf(false)
        private set

    init {
        val settings = graph.settings.settings.value
        if (settings.autoCheckUpdates && System.currentTimeMillis() - settings.lastUpdateCheckAt > SIX_HOURS_MS) {
            checkNow(manual = false)
        }
    }

    fun checkNow(manual: Boolean) {
        viewModelScope.launch {
            checking = true
            manualMessage = null
            val repo = graph.settings.settings.value.updateRepo.ifBlank { BuildConfig.UPDATE_REPO }
            graph.updates.check(repo)
                .onSuccess { info ->
                    available = info
                    if (info != null) {
                        dialogOpen = true
                    } else if (manual) {
                        manualMessage = "You are on the latest version"
                    }
                }
                .onFailure { if (manual) manualMessage = it.message ?: "Could not check for updates" }
            checking = false
            graph.settings.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
        }
    }

    fun openDialog() {
        if (available != null) dialogOpen = true
    }

    fun dismissDialog() {
        dialogOpen = false
    }

    fun download() {
        val info = available ?: return
        viewModelScope.launch {
            graph.updates.download(info).collect { progress ->
                downloadProgress = progress
                if (progress is DownloadProgress.Done) {
                    graph.updates.install(progress.filePath)
                }
            }
        }
    }
}
