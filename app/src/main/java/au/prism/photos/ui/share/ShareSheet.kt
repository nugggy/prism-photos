package au.prism.photos.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import au.prism.photos.BuildConfig
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class ShareTargetDef(val label: String, val packageName: String)

private val knownTargets = listOf(
    ShareTargetDef("WhatsApp", "com.whatsapp"),
    ShareTargetDef("Messages", "com.google.android.apps.messaging"),
    ShareTargetDef("Gmail", "com.google.android.gm"),
    ShareTargetDef("Instagram", "com.instagram.android"),
    ShareTargetDef("Messenger", "com.facebook.orca"),
    ShareTargetDef("Telegram", "org.telegram.messenger"),
)

/**
 * Bottom sheet with direct share targets (WhatsApp, Messages, Gmail, Instagram, Messenger,
 * Telegram), a copy-link action and a More button for the system chooser. Plex items are
 * downloaded to cache first and shared through the FileProvider; device items share their
 * content URI directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var preparing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var preparedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(items) {
        preparing = true
        error = null
        try {
            preparedUris = withContext(Dispatchers.IO) {
                items.mapIndexed { index, item ->
                    prepareUri(context, item) { bytes, total ->
                        if (total > 0) progress = (index + bytes.toFloat() / total) / items.size
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't prepare the files to share"
        } finally {
            preparing = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Share", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            when {
                preparing -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = PlexGold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Preparing", style = MaterialTheme.typography.bodySmall)
                }
                error != null -> {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val mimeType = remember(items) { mimeTypeFor(items) }
                    val pm = context.packageManager
                    val direct = remember { knownTargets.filter { isInstalled(pm, it.packageName) } }
                    val smsPackage = remember { if (!direct.any { it.packageName == "com.google.android.apps.messaging" }) resolveSmsPackage(pm) else null }
                    val canCopyLink = items.size == 1 && !items[0].isLocal

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(direct) { target ->
                            ShareTargetButton(label = target.label, icon = appIcon(pm, target.packageName)) {
                                launchDirect(context, preparedUris, mimeType, target.packageName)
                                onDismiss()
                            }
                        }
                        if (smsPackage != null) {
                            item {
                                ShareTargetButton(label = "Messages", icon = appIcon(pm, smsPackage)) {
                                    launchDirect(context, preparedUris, mimeType, smsPackage)
                                    onDismiss()
                                }
                            }
                        }
                        if (canCopyLink) {
                            item {
                                ShareTargetButton(label = "Copy link", icon = null, fallback = Icons.Filled.ContentCopy) {
                                    copyLink(context, items[0])
                                    onDismiss()
                                }
                            }
                        }
                        item {
                            ShareTargetButton(label = "More", icon = null, fallback = Icons.Filled.MoreHoriz) {
                                launchChooser(context, preparedUris, mimeType)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetButton(
    label: String,
    icon: Drawable?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap(128, 128).asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    fallback ?: Icons.Filled.MoreHoriz,
                    contentDescription = label,
                    modifier = Modifier.size(52.dp).clip(CircleShape).padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun prepareUri(context: Context, item: MediaItem, onProgress: (Long, Long) -> Unit): Uri {
    if (item.isLocal) return Uri.parse(item.localUri)
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val dest = File(dir, fileNameFor(item))
    Downloader.download(PrismApp.graph.media.downloadUrl(item), dest, onProgress)
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", dest)
}

private fun fileNameFor(item: MediaItem): String {
    val existing = item.filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    if (existing != null) return existing
    val ext = if (item.isVideo) "mp4" else "jpg"
    val base = item.title.ifBlank { item.id }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return if (base.contains('.')) base else "$base.$ext"
}

private fun mimeTypeFor(items: List<MediaItem>): String {
    if (items.size == 1) {
        val item = items[0]
        return item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
    }
    val allVideo = items.all { it.isVideo }
    val allPhoto = items.all { !it.isVideo }
    return when {
        allVideo -> "video/*"
        allPhoto -> "image/*"
        else -> "*/*"
    }
}

private fun buildShareIntent(uris: List<Uri>, mimeType: String): Intent = if (uris.size == 1) {
    Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uris[0])
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
} else {
    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun launchDirect(context: Context, uris: List<Uri>, mimeType: String, packageName: String) {
    if (uris.isEmpty()) return
    try {
        val intent = buildShareIntent(uris, mimeType).apply { setPackage(packageName) }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
    }
}

private fun launchChooser(context: Context, uris: List<Uri>, mimeType: String) {
    if (uris.isEmpty()) return
    val intent = buildShareIntent(uris, mimeType)
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun copyLink(context: Context, item: MediaItem) {
    val session = PrismApp.graph.session.session.value
    val serverId = session.server?.clientIdentifier
    val url = "https://app.plex.tv/desktop/#!/server/$serverId/details?key=/library/metadata/${item.id}"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Plex link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
    pm.getPackageInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

private fun appIcon(pm: PackageManager, packageName: String): Drawable? = try {
    pm.getApplicationIcon(packageName)
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private fun resolveSmsPackage(pm: PackageManager): String? = try {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
    pm.resolveActivity(intent, 0)?.activityInfo?.packageName
} catch (e: Exception) {
    null
}
