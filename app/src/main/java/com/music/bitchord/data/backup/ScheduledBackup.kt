package com.music.bitchord.data.backup

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class ScheduledBackupFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM,
}

/**
 * The engine behind the Scheduled auto backup and Cloud storage sync cards:
 * the settings they run on, the WorkManager work that carries them, and the
 * SAF document each run writes into.
 *
 * Ported from ArchiveTune's backup package, whose scheduler re-armed a
 * one-time job after every run and whose settings lived in DataStore. Neiro
 * has no DataStore and no dependency injection — the settings are
 * [AppSettings] flows and the schedule is re-armed from the rows that change
 * them, exactly the way the reference's use cases re-armed it from its
 * viewmodel. WorkManager persists the queued work itself, so a restart or a
 * process death never loses a schedule that was already set.
 *
 * One frequency serves both channels — the phone folder and the cloud folder
 * — because that is how the page presents it: one schedule row whose dialog
 * both cards open.
 */
object ScheduledBackup {

    const val DEVICE_WORK = "scheduled_auto_backup"
    const val CLOUD_WORK = "scheduled_auto_backup_cloud"

    private const val RUN_HOUR = 2
    private const val MIN_DELAY_MS = 1_000L
    private const val BACKUP_MIME_TYPE = "application/octet-stream"
    private val DATED_NAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val CONSTRAINTS = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /** One channel's settings as the scheduler and the worker read them. */
    data class Settings(
        val enabled: Boolean,
        val frequency: ScheduledBackupFrequency,
        val customDateEpochDay: Long?,
        val directoryUri: String,
        val directoryName: String,
        val overwriteExisting: Boolean,
    )

    fun deviceSettings(): Settings = Settings(
        enabled = AppSettings.scheduledBackupEnabled.value,
        frequency = AppSettings.scheduledBackupFrequency.value,
        customDateEpochDay = AppSettings.scheduledBackupCustomDateEpochDay.value,
        directoryUri = AppSettings.scheduledBackupDirectoryUri.value,
        directoryName = AppSettings.scheduledBackupDirectoryName.value,
        overwriteExisting = AppSettings.scheduledBackupOverwriteExisting.value,
    )

    fun cloudSettings(): Settings = Settings(
        enabled = AppSettings.cloudSyncEnabled.value,
        frequency = AppSettings.scheduledBackupFrequency.value,
        customDateEpochDay = AppSettings.scheduledBackupCustomDateEpochDay.value,
        directoryUri = AppSettings.cloudSyncFolderUri.value,
        directoryName = AppSettings.cloudSyncFolderName.value,
        overwriteExisting = AppSettings.cloudSyncOverwriteExisting.value,
    )

    /**
     * Re-arms both channels from the settings as they stand. Called whenever a
     * row on the page changes one, so the work on record is never staler than
     * the last tap.
     */
    fun reschedule(context: Context) {
        scheduleChannel(context, deviceSettings(), DEVICE_WORK)
        scheduleChannel(context, cloudSettings(), CLOUD_WORK)
    }

    /**
     * Re-arms one channel after its work ran. A periodic schedule repeats on
     * its own and must not be touched — re-arming one resets its initial delay,
     * which would push every run out by a period. A custom date needs this:
     * its work is one-time, so after it fires the next one is scheduled here,
     * a month past the date that just ran and every month after that until one
     * is in the future again.
     */
    internal fun rescheduleAfterRun(context: Context, cloud: Boolean) {
        if (AppSettings.scheduledBackupFrequency.value != ScheduledBackupFrequency.CUSTOM) return
        AppSettings.scheduledBackupCustomDateEpochDay.value?.let(::advanceCustomDate)
        if (cloud) {
            scheduleChannel(context, cloudSettings(), CLOUD_WORK)
        } else {
            scheduleChannel(context, deviceSettings(), DEVICE_WORK)
        }
    }

    private fun advanceCustomDate(epochDay: Long) {
        val now = ZonedDateTime.now()
        var next = LocalDate.ofEpochDay(epochDay).atTime(RUN_HOUR, 0).atZone(now.zone)
        while (!next.isAfter(now)) next = next.plusMonths(1)
        if (next.toLocalDate().toEpochDay() != epochDay) {
            AppSettings.setScheduledBackupCustomDate(next.toLocalDate().toEpochDay())
        }
    }

    private fun scheduleChannel(context: Context, settings: Settings, workName: String) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled || settings.directoryUri.isEmpty()) {
            workManager.cancelUniqueWork(workName)
            return
        }
        if (settings.frequency == ScheduledBackupFrequency.CUSTOM) {
            val epochDay = settings.customDateEpochDay
            if (epochDay == null || LocalDate.ofEpochDay(epochDay).isBefore(LocalDate.now())) {
                workManager.cancelUniqueWork(workName)
                return
            }
            val request = OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(delayUntilCustomDate(epochDay), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ScheduledBackupWorker.KEY_TARGET_CLOUD to (workName == CLOUD_WORK)))
                .build()
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
            periodDays(settings.frequency),
            TimeUnit.DAYS,
        )
            .setConstraints(CONSTRAINTS)
            .setInitialDelay(delayUntilNextRun(settings.frequency), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ScheduledBackupWorker.KEY_TARGET_CLOUD to (workName == CLOUD_WORK)))
            .build()
        workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A folder picked for the scheduled backups, kept for the worker's use. */
    fun setDirectory(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        AppSettings.setScheduledBackupDirectory(uri.toString(), resolveDirectoryName(context, uri))
        reschedule(context)
    }

    /** A Drive folder picked for the cloud sync, kept the same way. */
    fun setCloudFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        AppSettings.setCloudSyncFolder(uri.toString(), resolveDirectoryName(context, uri))
        reschedule(context)
    }

    /** The Sync now row: one backup into the Drive folder, immediately. */
    suspend fun syncNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = AppSettings.cloudSyncFolderUri.value
            if (folder.isEmpty()) error("No Drive folder chosen")
            val destination = createDestination(context, Uri.parse(folder), AppSettings.cloudSyncOverwriteExisting.value)
            BackupArchive.create(context, destination).getOrThrow()
            AppSettings.setCloudSyncLastRunAt(System.currentTimeMillis())
        }
    }

    /**
     * The document a scheduled run writes into: a dated name each time, or
     * — with overwrite on — the one fixed child document, found so the write
     * replaces it rather than accumulating " (1)" copies a dated history was
     * explicitly turned off for.
     */
    internal fun createDestination(context: Context, treeUri: Uri, overwriteExisting: Boolean): Uri {
        val appName = context.getString(R.string.app_name)
        val fileName =
            if (overwriteExisting) "$appName.backup"
            else "${appName}_${LocalDateTime.now().format(DATED_NAME_TIMESTAMP)}.backup"
        if (overwriteExisting) {
            findChildDocument(context, treeUri, fileName)?.let { return it }
        }
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentDocumentUri,
            BACKUP_MIME_TYPE,
            fileName,
        ) ?: error("Failed to create the backup file")
    }

    private fun findChildDocument(context: Context, treeUri: Uri, displayName: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return context.contentResolver
            .query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == displayName) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                    }
                }
                null
            }
    }

    private fun resolveDirectoryName(context: Context, uri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        return context.contentResolver
            .query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> cursor.readDisplayName() }
            ?.takeIf(String::isNotBlank)
            ?: documentId.substringAfterLast(':').ifBlank { documentId }
    }

    private fun Cursor.readDisplayName(): String? =
        if (moveToFirst()) {
            getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        } else {
            null
        }

    private fun delayUntilNextRun(frequency: ScheduledBackupFrequency): Long {
        val now = ZonedDateTime.now()
        val nextDate = when (frequency) {
            ScheduledBackupFrequency.DAILY -> now.toLocalDate().plusDays(1)
            ScheduledBackupFrequency.WEEKLY -> now.toLocalDate().plusWeeks(1)
            ScheduledBackupFrequency.MONTHLY -> now.toLocalDate().plusMonths(1)
            ScheduledBackupFrequency.CUSTOM -> now.toLocalDate().plusDays(1)
        }
        val runAt = nextDate.atTime(RUN_HOUR, 0).atZone(now.zone)
        return Duration.between(now, runAt).toMillis().coerceAtLeast(MIN_DELAY_MS)
    }

    private fun delayUntilCustomDate(epochDay: Long): Long {
        val now = ZonedDateTime.now()
        val runAt = LocalDate.ofEpochDay(epochDay).atTime(RUN_HOUR, 0).atZone(now.zone)
        return Duration.between(now, runAt).toMillis().coerceAtLeast(MIN_DELAY_MS)
    }

    private fun periodDays(frequency: ScheduledBackupFrequency): Long =
        when (frequency) {
            ScheduledBackupFrequency.DAILY -> 1L
            ScheduledBackupFrequency.WEEKLY -> 7L
            ScheduledBackupFrequency.MONTHLY -> 30L
            ScheduledBackupFrequency.CUSTOM -> 30L
        }
}
