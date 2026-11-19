/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DownloadsExport {

    data class Outcome(val copied: Int, val failed: Int)

    suspend fun exportTo(
        context: Context,
        treeUri: Uri,
        onProgress: (copied: Int, failed: Int, total: Int) -> Unit = { _, _, _ -> },
    ): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val songs = Downloads.getDownloadedSongs(context)
            var copied = 0
            var failed = 0
            for (song in songs) {
                val source = song.localUri?.let(Uri::parse)
                if (source == null) {
                    failed++
                    onProgress(copied, failed, songs.size)
                    continue
                }
                val name = fileNameFor(song, context, source)
                val target = runCatching {
                    DocumentsContract.createDocument(resolver, parent, mimeTypeFor(name), name)
                }.getOrNull()
                val copiedOne = target != null && copyInto(resolver, source, target)
                if (copiedOne) copied++ else {
                    failed++
                    target?.let { runCatching { resolver.delete(it, null, null) } }
                }
                onProgress(copied, failed, songs.size)
            }
            Outcome(copied, failed)
        }
    }

    private fun copyInto(
        resolver: android.content.ContentResolver,
        source: Uri,
        target: Uri,
    ): Boolean = runCatching {
        resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(target)?.use { output ->
                input.copyTo(output, COPY_BUFFER_BYTES)
            }
        } != null
    }.getOrDefault(false)

    private fun fileNameFor(song: Song, context: Context, source: Uri): String {
        val artist = sanitise(song.artist)
        val title = sanitise(song.title)
        val stem = when {
            artist.isEmpty() -> title
            title.isEmpty() -> artist
            else -> "$title - $artist"
        }.ifBlank { song.videoId }
        val extension = sourceExtension(context, source)
        val capped = stem.take(MAX_STEM_CHARS).trimEnd()
        return if (extension.isNullOrBlank()) capped else "$capped.$extension"
    }

    private fun sourceExtension(context: Context, source: Uri): String? = runCatching {
        when (source.scheme) {
            "file" -> source.path?.let(::File)?.extension
            else -> displayNameOf(context, source)?.substringAfterLast('.', "")
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun displayNameOf(context: Context, uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"
        "wav" -> "audio/x-wav"
        "mp3" -> "audio/mpeg"
        "ogg", "opus" -> "audio/opus"
        else -> "application/octet-stream"
    }

    private fun sanitise(raw: String): String = raw
        .replace(ILLEGAL, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .trim('.')

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    private val WHITESPACE = Regex("""\s+""")
    private const val MAX_STEM_CHARS = 120
    private const val COPY_BUFFER_BYTES = 64 * 1024
}
