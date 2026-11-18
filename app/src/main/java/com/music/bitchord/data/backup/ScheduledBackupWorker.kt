package com.music.bitchord.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException

/**
 * One scheduled run: the zip [BackupArchive] writes, into the folder its
 * channel was given, on the schedule [ScheduledBackup] armed it with.
 *
 * A lost grant (the folder was unmounted, the cloud app removed) fails the run
 * outright rather than retrying forever; anything else is worth another
 * attempt, which WorkManager backs off on its own.
 */
class ScheduledBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val cloud = inputData.getBoolean(KEY_TARGET_CLOUD, false)
        val settings = if (cloud) ScheduledBackup.cloudSettings() else ScheduledBackup.deviceSettings()
        if (!settings.enabled || settings.directoryUri.isEmpty()) return Result.success()

        return try {
            val destination = ScheduledBackup.createDestination(
                applicationContext,
                Uri.parse(settings.directoryUri),
                settings.overwriteExisting,
            )
            BackupArchive.create(applicationContext, destination).getOrThrow()
            if (cloud) AppSettings.setCloudSyncLastRunAt(System.currentTimeMillis())
            ScheduledBackup.rescheduleAfterRun(applicationContext, cloud)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            Result.failure()
        } catch (exception: Exception) {
            Result.retry()
        }
    }

    companion object {
        internal const val KEY_TARGET_CLOUD = "cloud"
    }
}
