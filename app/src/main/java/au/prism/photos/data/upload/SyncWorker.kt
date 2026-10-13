package au.prism.photos.data.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import au.prism.photos.PrismApp
import au.prism.photos.data.Diagnostics
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val NOTIFICATION_CHANNEL_ID = "sync"
private const val NOTIFICATION_ID = 4821

/**
 * Backs up new photos and videos from the selected device albums to the Plex library folder.
 * Runs as a foreground service so Android does not kill it mid transfer.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = PrismApp.graph
        val settings = graph.uploadSettings.settings.value
        if (!settings.autoSyncEnabled || settings.selectedBuckets.isEmpty()) return Result.success()
        if (!graph.device.hasPermission()) return Result.failure()

        var foregroundAllowed = tryForeground(foregroundInfo(0, 0, ""))

        // No date cutoff: the ledger alone decides what is already synced, so items that failed
        // last time or carry an EXIF date older than the previous run are still picked up.
        // lastSyncAt is only a "last ran" stat for Settings.
        val deviceItems = settings.selectedBuckets.flatMap { bucketId -> graph.device.items(bucketId) }
        val candidates = LedgerMatching.selectSyncCandidates(deviceItems, graph.syncLedger.index())

        val startedAt = System.currentTimeMillis()
        if (candidates.isEmpty()) {
            graph.uploadSettings.update { it.copy(lastSyncAt = startedAt) }
            return Result.success()
        }

        Diagnostics.log("Sync: ${candidates.size} new item(s) to upload from ${settings.selectedBuckets.size} album(s)")
        val uris = candidates.mapNotNull { it.localUri }
        var lastNotify = 0L
        val report = graph.uploadService.uploadItems(uris, folder = null) { done, total, name, fraction ->
            val now = System.currentTimeMillis()
            if (foregroundAllowed && (now - lastNotify > 500 || done == total)) {
                lastNotify = now
                foregroundAllowed = tryForegroundAsync(foregroundInfo(done, total, name, fraction))
            }
        }

        graph.uploadSettings.update {
            it.copy(lastSyncAt = startedAt, lastSyncUploaded = report.uploaded, lastSyncFailed = report.failed)
        }
        Diagnostics.log("Sync finished: ${report.uploaded} uploaded, ${report.skipped} already synced, ${report.failed} failed")
        return if (report.failed > 0 && report.uploaded == 0) Result.retry() else Result.success()
    }

    /**
     * Android 12 and later refuse to start a foreground service from the background in many
     * situations (ForegroundServiceStartNotAllowedException), which a periodic worker often is.
     * The sync still works without the notification, it is just more likely to be stopped early.
     */
    private suspend fun tryForeground(info: ForegroundInfo): Boolean = try {
        setForeground(info)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Diagnostics.log("Sync: couldn't show the progress notification, carrying on without it (${e::class.java.simpleName})")
        false
    }

    /** Returns false once the system has refused, so the caller stops asking for every progress tick. */
    private fun tryForegroundAsync(info: ForegroundInfo): Boolean = try {
        setForegroundAsync(info)
        true
    } catch (e: Exception) {
        Diagnostics.log("Sync: progress notification refused (${e::class.java.simpleName})")
        false
    }

    private fun foregroundInfo(done: Int, total: Int, name: String, fraction: Float = 0f): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Syncing to Plex")
            .setContentText(if (total > 0) "$done of $total  ·  $name" else "Preparing…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while photos and videos are backed up to Plex"
            },
        )
    }
}

/** Schedules and cancels [SyncWorker] runs. */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "prism_sync_periodic"
    private const val ONE_OFF_WORK_NAME = "prism_sync_now"

    private fun constraints(wifiOnly: Boolean, chargingOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(chargingOnly)
        .build()

    fun schedulePeriodic(context: Context, wifiOnly: Boolean, chargingOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /** Called whenever the auto sync toggle or its constraints change, and once on app start. */
    fun ensureScheduled(context: Context, settings: SyncSettings) {
        if (settings.autoSyncEnabled) schedulePeriodic(context, settings.wifiOnly, settings.chargingOnly) else cancelPeriodic(context)
    }

    fun syncNow(context: Context, wifiOnly: Boolean, chargingOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints(wifiOnly, chargingOnly)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
