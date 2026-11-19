package com.music.bitchord.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.music.bitchord.R
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.io.Writer

/**
 * Playlists across the app's edge, both directions: a "m3u" or "csv" file in,
 * one playlist of Neiro's own out; and a playlist out again as CSV.
 *
 * Ported from ArchiveTune's BackupRestoreViewModel, whose import matched
 * songs online before they landed anywhere — a step that needs its own
 * progress screen. Here the parsed rows go straight into
 * [LocalPlaylistStore] as the songs the file says they are: titles and
 * artists survive whole, and the ids a playlist file never carries stay
 * empty until the songs are played or re-matched.
 */
object PlaylistTransfer {

    val CSV_MIME_TYPES = arrayOf(
        "text/csv",
        "text/x-csv",
        "text/comma-separated-values",
        "text/x-comma-separated-values",
        "application/csv",
        "application/x-csv",
        "application/vnd.ms-excel",
        "text/plain",
        "text/*",
        "application/octet-stream",
    )

    data class Imported(
        val songCount: Int,
        val playlistTitle: String,
    )

    suspend fun importM3u(context: Context, source: Uri): Result<Imported> = withContext(Dispatchers.IO) {
        runCatching { createPlaylist(context, source, parseM3u(context, source)) }
    }

    suspend fun importCsv(context: Context, source: Uri): Result<Imported> = withContext(Dispatchers.IO) {
        runCatching { createPlaylist(context, source, parseCsv(context, source)) }
    }

    suspend fun exportCsv(context: Context, destination: Uri, playlist: com.music.bitchord.data.spotify.LocalPlaylist): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val songs = playlist.songs
                if (songs.isEmpty()) error("Playlist has no songs")
                val writer = context.contentResolver.openOutputStream(destination, "wt")
                    ?.bufferedWriter(Charsets.UTF_8)
                    ?: error("Unable to open CSV destination")
                writer.use { output ->
                    output.append('\uFEFF')
                    writeCsvRecord(output, CSV_HEADER)
                    songs.forEach { song ->
                        writeCsvRecord(
                            output,
                            listOf(
                                song.title,
                                song.artist,
                                song.albumName.orEmpty(),
                                song.durationText.orEmpty(),
                            ),
                        )
                    }
                }
            }
        }

    fun suggestedCsvName(title: String): String {
        val safeBaseName = title
            .trim()
            .replace(CSV_FILE_NAME_FORBIDDEN_CHARACTERS, "_")
            .trimEnd('.', ' ')
            .takeCodePoints(MAX_CSV_FILE_NAME_LENGTH)
            .ifBlank { DEFAULT_CSV_FILE_NAME }
        return "$safeBaseName.csv"
    }

    private fun createPlaylist(context: Context, source: Uri, songs: List<Song>): Imported {
        if (songs.isEmpty()) error(context.getString(R.string.backup_import_empty))
        val playlist = LocalPlaylistStore.savePlaylist(playlistTitle(context, source), songs)
        return Imported(songs.size, playlist.title)
    }

    private fun playlistTitle(context: Context, source: Uri): String {
        val displayName = runCatching { queryDisplayName(context, source) }.getOrNull().orEmpty()
        return displayName.substringBeforeLast('.')
            .trim()
            .ifBlank { context.getString(R.string.backup_import_default_title) }
    }

    private fun queryDisplayName(context: Context, source: Uri): String? {
        context.contentResolver
            .query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        return source.lastPathSegment
    }

    private fun parseM3u(context: Context, source: Uri): List<Song> {
        val songs = mutableListOf<Song>()
        runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                if (lines.firstOrNull()?.startsWith("#EXTM3U") == true) {
                    lines.forEach { rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            val info = rawLine.substringAfter("#EXTINF:").substringAfter(',')
                            val artistNames = info.substringBefore(" - ")
                                .split(';')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            val title = info.substringAfter(" - ")
                            songs.add(
                                Song(
                                    videoId = "",
                                    title = title,
                                    artist = artistNames.firstOrNull() ?: "",
                                    thumbnailUrl = null,
                                    artists = artistNames.map { ArtistRef(name = it) },
                                ),
                            )
                        }
                    }
                }
            }
        }
        return songs
    }

    private fun parseCsv(context: Context, source: Uri): List<Song> {
        val out = mutableListOf<Song>()
        runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                val reader = PushbackReader(InputStreamReader(stream, Charsets.UTF_8), 1)
                val iterator = readCsvRecords(reader).iterator()
                if (!iterator.hasNext()) return@use

                val firstRecord = iterator.next()
                val normalizedHeader = firstRecord.map(::normalizeCsvHeaderCell)

                val titleIndex =
                    normalizedHeader.indexOfFirst { it == "title" || it == "tracktitle" || it == "songtitle" }
                val artistIndex =
                    normalizedHeader.indexOfFirst { it == "artist" || it == "artists" || it == "artistname" }

                val hasHeader = titleIndex >= 0 && artistIndex >= 0
                val resolvedTitleIndex = if (hasHeader) titleIndex else 0
                val resolvedArtistIndex = if (hasHeader) artistIndex else 1

                fun addFromRecord(record: List<String>) {
                    val titleRaw = record.getOrNull(resolvedTitleIndex).orEmpty()
                    val artistRaw = record.getOrNull(resolvedArtistIndex).orEmpty()

                    val title = titleRaw.trim().trimStart('\uFEFF')
                    if (title.isBlank()) return

                    val artistNames = artistRaw
                        .trim()
                        .split(';', '|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    out.add(
                        Song(
                            videoId = "",
                            title = title,
                            artist = artistNames.firstOrNull() ?: "",
                            thumbnailUrl = null,
                            artists = artistNames.map { ArtistRef(name = it) },
                        ),
                    )
                }

                if (!hasHeader) {
                    addFromRecord(firstRecord)
                }
                while (iterator.hasNext()) {
                    addFromRecord(iterator.next())
                }
            }
        }
        return out
    }

    internal fun readCsvRecords(reader: Reader): Sequence<List<String>> =
        sequence {
            val pushbackReader = if (reader is PushbackReader) reader else PushbackReader(reader, 1)
            val record = ArrayList<String>(8)
            val field = StringBuilder(64)
            var inQuotes = false

            fun endField() {
                record.add(field.toString())
                field.setLength(0)
            }

            suspend fun SequenceScope<List<String>>.endRecord() {
                endField()
                val anyContent = record.any { it.isNotBlank() }
                if (anyContent) {
                    yield(record.toList())
                }
                record.clear()
            }

            while (true) {
                val value = pushbackReader.read()
                if (value == -1) {
                    if (field.isNotEmpty() || record.isNotEmpty()) {
                        endRecord()
                    }
                    break
                }

                val ch = value.toChar()
                when (ch) {
                    '"' -> {
                        if (inQuotes) {
                            val next = pushbackReader.read()
                            if (next == '"'.code) {
                                field.append('"')
                            } else {
                                inQuotes = false
                                if (next != -1) pushbackReader.unread(next)
                            }
                        } else {
                            if (field.isEmpty()) {
                                inQuotes = true
                            } else {
                                field.append('"')
                            }
                        }
                    }

                    ',' -> {
                        if (inQuotes) {
                            field.append(',')
                        } else {
                            endField()
                        }
                    }

                    '\n' -> {
                        if (inQuotes) {
                            field.append('\n')
                        } else {
                            endRecord()
                        }
                    }

                    '\r' -> {
                        if (inQuotes) {
                            field.append('\r')
                        } else {
                            val next = pushbackReader.read()
                            if (next != '\n'.code && next != -1) pushbackReader.unread(next)
                            endRecord()
                        }
                    }

                    else -> {
                        field.append(ch)
                    }
                }
            }
        }

    private fun normalizeCsvHeaderCell(value: String): String =
        value
            .trim()
            .trimStart('\uFEFF')
            .lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")

    private fun writeCsvRecord(output: Writer, record: List<String>) {
        record.forEachIndexed { index, value ->
            if (index > 0) output.append(',')
            output.writeCsvField(value)
        }
        output.append("\r\n")
    }

    private fun Writer.writeCsvField(value: String) {
        val safeValue = value.escapeSpreadsheetFormula()
        append('"')
        safeValue.forEach { character ->
            if (character == '"') append('"')
            append(character)
        }
        append('"')
    }

    private fun String.escapeSpreadsheetFormula(): String {
        val firstMeaningfulCharacter = firstOrNull { character -> !character.isWhitespace() }
        return if (firstMeaningfulCharacter != null && firstMeaningfulCharacter in CSV_FORMULA_PREFIXES) {
            "'$this"
        } else {
            this
        }
    }

    private fun String.takeCodePoints(maxCodePoints: Int): String {
        val count = codePointCount(0, length)
        if (count <= maxCodePoints) return this
        return substring(0, offsetByCodePoints(0, maxCodePoints))
    }

    private val CSV_HEADER = listOf("Title", "Artist", "Album", "Duration")
    private val CSV_FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    private val CSV_FILE_NAME_FORBIDDEN_CHARACTERS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private const val MAX_CSV_FILE_NAME_LENGTH = 96
    private const val DEFAULT_CSV_FILE_NAME = "playlist"
}
