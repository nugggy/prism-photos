package au.prism.photos.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import au.prism.photos.domain.ExifInfo
import au.prism.photos.domain.MediaItem
import au.prism.photos.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bottom sheet with photo/video metadata, description editing and tag management. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InfoSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onSummaryChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val context = LocalContext.current
    var deviceExif by remember(item.id) { mutableStateOf<ExifInfo?>(null) }
    LaunchedEffect(item.id) {
        if (item.isLocal) {
            deviceExif = withContext(Dispatchers.IO) { readDeviceExif(context, item) }
        }
    }
    val exif = item.exif ?: deviceExif

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Text(Format.dateTime(item.takenAt), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            InfoRow("Dimensions", "${item.width} × ${item.height}  ·  ${Format.megapixels(item.width, item.height)}")
            if (item.fileSize > 0) InfoRow("File size", Format.bytes(item.fileSize))
            item.filePath?.let { InfoRow("File path", it) }
            exif?.make?.let { InfoRow("Camera make", it) }
            exif?.model?.let { InfoRow("Camera model", it) }
            exif?.lens?.let { InfoRow("Lens", it) }
            exif?.aperture?.let { InfoRow("Aperture", it) }
            exif?.exposure?.let { InfoRow("Exposure", it) }
            exif?.iso?.let { InfoRow("ISO", it.toString()) }
            if (item.isVideo) {
                exif?.videoCodec?.let { InfoRow("Video codec", it) }
                exif?.videoResolution?.let { InfoRow("Video resolution", it) }
                if (item.durationMs > 0) InfoRow("Duration", Format.duration(item.durationMs))
            }
            item.place?.let { InfoRow("Place", it) }
            item.country?.let { InfoRow("Country", it) }
            item.albumTitle?.let { InfoRow("Album", it) }
            if (!item.isLocal) InfoRow("Plex rating key", item.id)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Description", style = MaterialTheme.typography.titleSmall)
            var summaryText by remember(item.id) { mutableStateOf(item.summary) }
            OutlinedTextField(
                value = summaryText,
                onValueChange = { summaryText = it },
                placeholder = { Text("Add a description") },
                trailingIcon = {
                    if (summaryText != item.summary) {
                        IconButton(onClick = { onSummaryChange(summaryText) }) {
                            Icon(Icons.Filled.Check, contentDescription = "Save description")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Tags", style = MaterialTheme.typography.titleSmall)
            FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                item.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveTag(tag) }, modifier = Modifier.height(18.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove tag $tag")
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                }
            }
            var newTag by remember(item.id) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    placeholder = { Text("Add tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (newTag.isNotBlank()) {
                        onAddTag(newTag.trim())
                        newTag = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add tag")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

private fun readDeviceExif(context: Context, item: MediaItem): ExifInfo? {
    val uriString = item.localUri ?: return null
    return try {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            val exif = ExifInterface(stream)
            ExifInfo(
                make = exif.getAttribute(ExifInterface.TAG_MAKE),
                model = exif.getAttribute(ExifInterface.TAG_MODEL),
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
                exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "$it s" },
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.toIntOrNull(),
            )
        }
    } catch (e: Exception) {
        null
    }
}
