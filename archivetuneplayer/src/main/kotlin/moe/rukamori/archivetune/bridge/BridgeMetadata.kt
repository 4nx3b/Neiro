/*
 * BRIDGE — ArchiveTune's MediaMetadata for items that never carried one.
 *
 * ArchiveTune's own MusicService builds every MediaItem with its
 * models.MediaMetadata as localConfiguration.tag, and the copied player
 * reads it back through MediaItem.metadata. A MediaController never sees a
 * tag (it does not cross the session), so every item the hosting app's
 * controller hands the ported stack read as "no metadata": no title, no
 * artist, no artwork in the mini player, the full player or the queue.
 *
 * This rebuilds the same shape from what does cross the session — Media3's
 * own MediaMetadata and the extras the host puts on it. The extras keys are
 * the hosting app's (com.music.bitchord.playback.PlayerConnection's
 * `bitchord.*` constants); an app that sets none still gets title, artists,
 * album and artwork.
 */

package moe.rukamori.archivetune.bridge

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.models.MediaMetadata

object BridgeMetadata {
    /** The host's extras keys (see the file comment). */
    const val EXTRA_IS_VIDEO = "bitchord.isVideo"
    const val EXTRA_ARTIST_ID = "bitchord.artistId"
    const val EXTRA_ALBUM_ID = "bitchord.albumId"
    const val EXTRA_DURATION_TEXT = "bitchord.durationText"
    const val EXTRA_EXPLICIT = "bitchord.explicit"
    const val EXTRA_SET_VIDEO_ID = "bitchord.setVideoId"

    private const val CACHE_SIZE = 256

    /** mediaId + title + artwork → metadata. LRU, access-ordered. */
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean =
                size > CACHE_SIZE
        }

    /** ArchiveTune's metadata for [item], or null for an item with no id. */
    fun of(item: MediaItem): MediaMetadata? {
        val id = item.mediaId
        if (id.isEmpty()) return null
        val m = item.mediaMetadata
        val extras = m.extras
        val title = m.title?.toString() ?: m.displayTitle?.toString()
        val artwork = m.artworkUri?.toString()
        val key = "$id\u0000$title\u0000$artwork"
        synchronized(cache) { cache[key]?.let { return it } }
        val built =
            build(
                mediaId = id,
                title = title,
                artist = m.artist?.toString() ?: m.albumArtist?.toString(),
                artistId = extras?.getString(EXTRA_ARTIST_ID),
                artworkUrl = artwork,
                albumTitle = m.albumTitle?.toString(),
                albumId = extras?.getString(EXTRA_ALBUM_ID),
                durationMs = m.durationMs,
                durationText = extras?.getString(EXTRA_DURATION_TEXT),
                isMusicVideo = extras?.getBoolean(EXTRA_IS_VIDEO) == true,
                explicit = extras?.getBoolean(EXTRA_EXPLICIT) == true,
                setVideoId = extras?.getString(EXTRA_SET_VIDEO_ID),
            )
        synchronized(cache) { cache[key] = built }
        return built
    }

    /** The pure mapping behind [of]; JVM-testable (no android.* types). */
    fun build(
        mediaId: String,
        title: String?,
        artist: String?,
        artistId: String? = null,
        artworkUrl: String? = null,
        albumTitle: String? = null,
        albumId: String? = null,
        durationMs: Long? = null,
        durationText: String? = null,
        isMusicVideo: Boolean = false,
        explicit: Boolean = false,
        setVideoId: String? = null,
    ): MediaMetadata {
        val names =
            artist
                ?.split(", ", " & ", " • ")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val artists =
            names.mapIndexed { i, name ->
                // The host knows the id of its first credited artist only.
                MediaMetadata.Artist(id = if (i == 0) artistId?.takeIf { it.isNotBlank() } else null, name = name)
            }
        val seconds =
            when {
                durationMs != null && durationMs > 0 -> (durationMs / 1000).toInt()
                else -> parseDuration(durationText) ?: -1
            }
        val album =
            albumTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaMetadata.Album(id = albumId?.takeIf { id -> id.isNotBlank() } ?: "", title = it) }
        return MediaMetadata(
            id = mediaId,
            title = title.orEmpty(),
            artists = artists,
            duration = seconds,
            thumbnailUrl = artworkUrl?.takeIf { it.isNotBlank() },
            album = album,
            setVideoId = setVideoId,
            explicit = explicit,
            isMusicVideo = isMusicVideo,
        )
    }

    /** "3:45" / "1:02:03" → seconds. */
    fun parseDuration(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(':')
        if (parts.size !in 1..3) return null
        var total = 0
        for (p in parts) {
            val n = p.trim().toIntOrNull() ?: return null
            total = total * 60 + n
        }
        return total
    }
}
