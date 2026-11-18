package com.music.bitchord.data.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.stats.Backup
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StoredBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The portable form of everything Neiro can back up, as one zip the user owns.
 *
 * Ported from ArchiveTune's BackupArchiveRepository, whose engine zipped a
 * SQLite database plus a settings XML — a shape that assumes the library *is*
 * a database. Neiro keeps no such database: settings live in
 * [AppSettings][com.music.bitchord.data.settings.AppSettings] and the library
 * is the listening record, so the archive is three JSON members instead —
 * a manifest, the settings, and every month of listening — and restore is
 * the existing import paths rather than a file swap.
 *
 * A zip's first two bytes are "PK"; the single-file JSON export this app has
 * always written does not start with them, which is the whole of the format
 * check: an old export restores through [Backup.importFrom] untouched.
 */
object BackupArchive {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The suggested name for a backup the Internal service row writes. */
    fun suggestedName(context: Context): String =
        "${context.getString(R.string.app_name)}_${LocalDateTime.now().format(NAME_TIMESTAMP)}.backup"

    suspend fun create(context: Context, target: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = AppSettings.exportPrefs().mapNotNull { (key, value) ->
                Backup.PrefValue.of(value)?.let { key to it }
            }.toMap()
            val history = ListeningStats.exportAll()
            val output = context.contentResolver.openOutputStream(target, "wt")
                ?: error("Couldn't open that file for writing")
            ZipOutputStream(output.buffered()).use { zip ->
                writeEntry(zip, MANIFEST_ENTRY, json.encodeToString(
                    Manifest.serializer(),
                    Manifest(createdAt = Instant.now().toString(), appVersion = BuildConfig.VERSION_NAME),
                ).toByteArray())
                writeEntry(zip, SETTINGS_ENTRY, json.encodeToString(
                    MapSerializer(String.serializer(), Backup.PrefValue.serializer()),
                    settings,
                ).toByteArray())
                writeEntry(zip, HISTORY_ENTRY, json.encodeToString(
                    ListSerializer(StoredBucket.serializer()),
                    history,
                ).toByteArray())
            }
        }
    }

    /**
     * What a picked file turned out to be, decided before anything is written.
     *
     * [legacy] marks one of the single-file JSON exports, which [Backup] owns
     * end to end and validates itself at restore time.
     */
    data class Validity(
        val isValid: Boolean = false,
        val legacy: Boolean = false,
        val madeAt: String? = null,
        val appVersion: String? = null,
        @StringRes val errorRes: Int? = null,
    )

    suspend fun validate(context: Context, source: Uri): Validity = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(source)
            ?: return@withContext Validity(errorRes = R.string.backup_restore_invalid)
        val isZip = stream.use { it.readZipHead() }
        if (!isZip) return@withContext Validity(isValid = true, legacy = true)
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext Validity(errorRes = R.string.backup_restore_invalid)
            ZipInputStream(input.buffered()).use { zip ->
                var manifest: Manifest? = null
                var hasPayload = false
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifest =
                            json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        SETTINGS_ENTRY, HISTORY_ENTRY -> hasPayload = true
                    }
                    entry = zip.nextEntry
                }
                val checked = manifest
                    ?: return@withContext Validity(errorRes = R.string.backup_restore_corrupted)
                if (checked.app != APP_TAG) return@withContext Validity(errorRes = R.string.backup_restore_invalid)
                if (checked.format > FORMAT_VERSION) return@withContext Validity(errorRes = R.string.backup_restore_newer)
                if (!hasPayload) return@withContext Validity(errorRes = R.string.backup_restore_missing)
                Validity(isValid = true, madeAt = checked.createdAt, appVersion = checked.appVersion)
            }
        } catch (_: Exception) {
            Validity(errorRes = R.string.backup_restore_corrupted)
        }
    }

    /** What a restore turned out to carry, for the line shown afterwards. */
    data class Restored(
        val months: Int,
        val settings: Int,
        val versionName: String,
    )

    suspend fun restore(context: Context, source: Uri): Result<Restored> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(source)
                ?: error("Couldn't open that file")
            val isZip = stream.use { it.readZipHead() }
            if (!isZip) {
                val summary = Backup.importFrom(context, source).getOrElse { throw it }
                return@runCatching Restored(summary.months, summary.settings, summary.from)
            }
            var manifest: Manifest? = null
            var settings: Map<String, Backup.PrefValue>? = null
            var history: List<StoredBucket>? = null
            val input = context.contentResolver.openInputStream(source) ?: error("Couldn't open that file")
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifest =
                            json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        SETTINGS_ENTRY -> settings = json.decodeFromString(
                            MapSerializer(String.serializer(), Backup.PrefValue.serializer()),
                            zip.readBytes().decodeToString(),
                        )
                        HISTORY_ENTRY -> history = json.decodeFromString(
                            ListSerializer(StoredBucket.serializer()),
                            zip.readBytes().decodeToString(),
                        )
                    }
                    entry = zip.nextEntry
                }
            }
            val checked = manifest ?: error("The selected file is not a valid backup archive.")
            require(checked.app == APP_TAG) { "That backup is from another app" }
            require(checked.format <= FORMAT_VERSION) {
                "That backup was written by a newer version of ${context.getString(R.string.app_name)}"
            }
            val restoredSettings = settings ?: error("The backup archive does not contain any restorable data.")
            val restoredHistory = history ?: emptyList()
            AppSettings.importPrefs(restoredSettings.mapValues { it.value.decoded() })
            ListeningStats.importAll(restoredHistory)
            SearchHistory.reload()
            Restored(restoredHistory.size, restoredSettings.size, checked.appVersion)
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun java.io.InputStream.readZipHead(): Boolean {
        val head = ByteArray(2)
        var read = 0
        while (read < head.size) {
            val count = read(head, read, head.size - read)
            if (count <= 0) return false
            read += count
        }
        return head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    }

    @Serializable
    private data class Manifest(
        val app: String = APP_TAG,
        val format: Int = FORMAT_VERSION,
        val createdAt: String = "",
        val appVersion: String = "",
    )

    private const val APP_TAG = "bitchord"
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val SETTINGS_ENTRY = "settings.json"
    private const val HISTORY_ENTRY = "history.json"
    private val NAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
}
