package com.music.bitchord.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.session.MediaController
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.beginRadioQueue
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.bridge.ArchiveTuneHostBridge
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * The app's answers to the ported ArchiveTune player: BitChord's controller
 * for playback, the view model for lyrics and likes, the app's settings for
 * every preference both players share.
 */
class ArchiveTuneBridgeImpl(
    private val controller: MediaController,
    private val viewModel: MainViewModel,
    @Suppress("unused") scope: CoroutineScope,
    private val context: Context,
    private val onOpenArtist: (String) -> Unit,
    private val onOpenAlbum: (String) -> Unit,
    private val onOpenSearch: () -> Unit,
) : ArchiveTuneHostBridge {
    /*
     * THREADING. A MediaController may only be called on its application
     * looper (main, for this app's controller). The vendored player stack
     * calls into this bridge from wherever it happens to be — composition,
     * its own Dispatchers.IO lookups (MusicDatabase.song → song()), menu
     * callbacks — so every controller read or command here goes through
     * [onMain] / [main], and the synchronous answers read [currentItem], a
     * snapshot the controller's own listener keeps current on its looper.
     *
     * The lyrics collector used to run on Dispatchers.Default and read
     * controller.currentMediaItem: StateFlow.collect hands over the current
     * value at once, so the very first emission threw "MediaController
     * method is called from a wrong thread" on every launch with a ported
     * style saved. (R8 merges lambda classes, so release traces named
     * PartySync / PlaylistSync for it.)
     */
    private val looper: Looper = controller.applicationLooper
    private val handler = Handler(looper)

    /** Runs [block] on the controller's looper: inline when already there, else posted. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() === looper) block() else handler.post(block)
    }

    /** Coroutines that command the controller run on its looper. */
    private val main = CoroutineScope(SupervisorJob() + LooperDispatcher(handler))

    /** The current item, as last seen on the controller's looper. */
    @Volatile
    private var currentItem: MediaItem? = null

    private val itemListener =
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentItem = controller.currentMediaItem
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                currentItem = controller.currentMediaItem
            }

            override fun onMediaMetadataChanged(mediaMetadata: Media3Metadata) {
                currentItem = controller.currentMediaItem
            }
        }

    @Volatile
    private var released = false

    /** The lyrics translated into ArchiveTune's LRC-shaped store format. */
    private val bridgedLyrics = MutableStateFlow<LyricsEntity?>(null)

    init {
        onMain {
            if (!released) {
                currentItem = controller.currentMediaItem
                controller.addListener(itemListener)
            }
        }

        // Follow the app's lyrics and re-emit them in ArchiveTune's format.
        // Collected on the controller's looper; the id comes from the
        // snapshot and the LRC is rendered off main, with no controller in
        // sight of the background block. lyricsChecked is watched too so a
        // lookup that came back with nothing reads as the module's own
        // "not found" marker instead of an endless shimmer.
        main.launch {
            combine(viewModel.lyrics, viewModel.lyricsChecked) { lines, checked -> lines to checked }
                .collect { (lines, checked) ->
                    val id = currentItem?.mediaId
                    bridgedLyrics.value =
                        if (id == null) {
                            null
                        } else if (lines != null) {
                            val providerName = viewModel.lyricsSource.value?.label ?: ""
                            val lrc = withContext(Dispatchers.Default) { lines.toArchiveTuneLrc() }
                            LyricsEntity(
                                id = id,
                                lyrics = lrc,
                                source = LyricsEntity.Source.REMOTE.value,
                                providerName = providerName,
                            )
                        } else if (checked) {
                            LyricsEntity(
                                id = id,
                                lyrics = LyricsEntity.LYRICS_NOT_FOUND,
                                source = LyricsEntity.Source.REMOTE.value,
                                providerName = "",
                            )
                        } else {
                            null
                        }
                }
        }
    }

    /** Detaches from the controller and stops the collectors. Called when the host unmounts. */
    fun release() {
        released = true
        main.cancel()
        onMain { controller.removeListener(itemListener) }
    }

    override fun player(): androidx.media3.common.Player = controller

    override fun pauseFromSleepTimer() {
        onMain { controller.pause() }
    }

    override fun playItems(items: List<MediaItem>, startIndex: Int) {
        main.launch {
            controller.playSongs(items.map { it.toNeiroSong() }, startIndex)
        }
    }

    override fun playNext(items: List<MediaItem>) {
        enqueue(items) { at -> at }
    }

    override fun addToQueue(items: List<MediaItem>) {
        enqueue(items) { controller.mediaItemCount }
    }

    override fun addAfterQueueIndex(index: Int, items: List<MediaItem>) {
        enqueue(items) { at -> (at + index + 1).coerceIn(0, controller.mediaItemCount) }
    }

    /** [where] runs inside the launched block, on the controller's looper. */
    private fun enqueue(items: List<MediaItem>, where: (Int) -> Int) {
        val songs = items.map { it.toNeiroSong() }
        if (songs.isEmpty()) return
        main.launch {
            val base = controller.currentMediaItemIndex
            controller.addMediaItems(where(base), songs.map { it.toMediaItem() })
        }
    }

    override fun startRadio(seed: MediaMetadata) {
        main.launch {
            if (controller.currentMediaItem?.mediaId != seed.id) {
                controller.playSongs(listOf(seed.toNeiroSong()), 0)
            }
            controller.beginRadioQueue()
        }
    }

    override fun toggleLike() {
        val item = currentItem ?: return
        // The auto download on like setting answers here too — this heart is
        // the ported player's, and a like through it is as much a like as one
        // through the native player's (whose funnel is MainActivity's
        // toggleLikeOf). Only a like that landed queues anything.
        if (viewModel.toggleLike(item.mediaId) == LikeStatus.LIKE) {
            Downloads.autoDownloadOnLike(context, item.toNeiroSong(), true)
        }
    }

    override fun likeSong(mediaId: String, liked: Boolean) {
        if (viewModel.toggleLike(mediaId) == LikeStatus.LIKE) {
            currentItem
                ?.takeIf { it.mediaId == mediaId }
                ?.toNeiroSong()
                ?.let { Downloads.autoDownloadOnLike(context, it, true) }
        }
    }

    override fun stopAndClearPlayback() {
        onMain {
            controller.clearMediaItems()
            controller.stop()
        }
    }

    override fun song(mediaId: String): SongEntity? {
        val item = currentItem ?: return null
        if (item.mediaId != mediaId) return null
        val meta = item.mediaMetadata
        return SongEntity(
            id = mediaId,
            title = meta.title?.toString() ?: "",
            duration = meta.durationMs?.div(1000L)?.toInt() ?: -1,
            thumbnailUrl = meta.artworkUri?.toString(),
            liked = viewModel.likeStatuses.value[mediaId] == LikeStatus.LIKE,
        )
    }

    override fun currentLyrics(): StateFlow<LyricsEntity?> = bridgedLyrics

    override fun playlists(): List<PlaylistEntity> =
        viewModel.playlists.value.map { playlist ->
            PlaylistEntity(
                id = playlist.playlistId,
                name = playlist.title,
                browseId = playlist.browseId,
                thumbnailUrl = playlist.thumbnailUrl,
            )
        }

    override fun addToPlaylist(playlistId: String, mediaId: String) {
        val playlist = viewModel.playlists.value.firstOrNull { it.playlistId == playlistId } ?: return
        val song =
            currentItem?.takeIf { it.mediaId == mediaId }?.toNeiroSong()
                ?: Song(videoId = mediaId, title = "", artist = "", thumbnailUrl = null)
        viewModel.addToPlaylists(listOf(playlist), song)
    }

    override fun download(metadata: MediaMetadata) {
        // Downloads stay this app's own flow; nothing to hand over yet.
    }

    override fun openArtist(browseId: String) = onOpenArtist(browseId)

    override fun openAlbum(browseId: String) = onOpenAlbum(browseId)

    override fun openSearch() = onOpenSearch()
}

/**
 * Dispatches onto one Handler's looper, inline when already on it — the
 * controller's own thread, for the bridge's coroutines.
 */
private class LooperDispatcher(private val handler: Handler) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean =
        Looper.myLooper() !== handler.looper

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        handler.post(block)
    }
}

/**
 * Media items the ported player resolved back into app songs. The full
 * fidelity path is the queue (the controller's own items round-trip through
 * its own metadata); what the player hands over carries at least the id,
 * title, artist and artwork, which is what queuing needs.
 */
private fun MediaItem.toNeiroSong(): Song {
    val meta: Media3Metadata = mediaMetadata ?: Media3Metadata.Builder().build()
    return Song(
        videoId = mediaId,
        title = meta.title?.toString() ?: "",
        artist = meta.artist?.toString() ?: "",
        thumbnailUrl = meta.artworkUri?.toString(),
    )
}

/** Radio seeds the player hands over as ArchiveTune's own metadata shape. */
private fun MediaMetadata.toNeiroSong(): Song =
    Song(
        videoId = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        thumbnailUrl = thumbnailUrl,
    )

/**
 * The app's lyric lines rendered into the LRC dialect ArchiveTune's player
 * parses: `[mm:ss.xx]` per line, `<mm:ss.xx>` per word where the app has
 * word timings.
 *
 * The A2 convention the receiving parser keeps verbatim: a word's run
 * carries its own trailing space (`<00:27.39>I `), so the reconstructed
 * text keeps its inter-word gaps — a run without one is a syllable of the
 * word before it, and serialising the app's trimmed words without them is
 * what fused every line into "Ibeentrynacall". The last run is closed by a
 * bare terminator stamp at the line's end, which the parser reads as the
 * final word's end and nothing else, so the sweep stops where the line
 * stops instead of drifting into the next one.
 */
internal fun List<LyricLine>.toArchiveTuneLrc(): String =
    joinToString("\n") { line ->
        val stamp = formatLrcTime(line.timeMs)
        if (line.words.isEmpty()) {
            "$stamp ${line.text}".trimEnd()
        } else {
            val runs = line.words.joinToString("") { word ->
                "<${formatLrcTime(word.startMs)}>${word.text} "
            }
            "$stamp $runs".trimEnd() + "<${formatLrcTime(line.endMs)}>"
        }
    }

private fun formatLrcTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "[%d:%02d.%02d]".format(minutes, seconds, hundredths)
}
