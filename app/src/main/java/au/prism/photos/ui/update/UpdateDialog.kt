package au.prism.photos.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.prism.photos.domain.DownloadProgress
import au.prism.photos.ui.components.formatDateShort
import java.time.OffsetDateTime

@Composable
fun UpdateDialog(vm: UpdateViewModel, onDismiss: () -> Unit) {
    val info = vm.available ?: return
    val progress = vm.downloadProgress
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available – ${info.versionName}") },
        text = {
            Column {
                Text(formatPublished(info.publishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (progress is DownloadProgress.InProgress) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                } else if (progress is DownloadProgress.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(progress.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.download() }, enabled = progress !is DownloadProgress.InProgress) {
                Text("Download and install")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private fun formatPublished(raw: String): String = try {
    formatDateShort(OffsetDateTime.parse(raw).toInstant().toEpochMilli())
} catch (e: Exception) {
    raw
}
