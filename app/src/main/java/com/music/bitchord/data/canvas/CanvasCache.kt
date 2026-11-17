package com.music.bitchord.data.canvas

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeSet

/**
 * Disk cache for canvas clips — the looping video some releases publish
 * alongside a track, played over the cover art by
 * [CanvasArtworkPlayer][com.music.bitchord.ui.player.CanvasArtworkPlayer].
 *
 * Without this, that player fetched a clip straight off the network through
 * a bare OkHttp data source, repeating for as long as the track playing over
 * it does ([androidx.media3.common.Player.REPEAT_MODE_ONE]). ExoPlayer frees
 * a sample's buffer as soon as the renderer has consumed it — there is no
 * back buffer configured, nor should there be one sized for an entire clip —
 * so once a loop finishes, nothing of it is left in memory to loop back to,
 * and reaching position zero again means the data source is asked for those
 * bytes again. A five-second clip behind a four-minute track loops around
 * fifty times, which without this cache was fifty downloads of the same few
 * seconds of video rather than one: the "40MB for one song" and multi-
 * gigabyte-day reports trace to exactly this repeat, not to audio bitrate.
 *
 * Wrapping the upstream in [CacheDataSource] means only the first loop of a
 * clip ever reaches the network; every loop after it, and every replay of
 * the same track later in the session, is served from disk instead.
 *
 * The ceiling is [AppSettings.maxCanvasCacheSizeMb] rather than a constant:
 * [MutableLruCacheEvictor] is what lets it move without tearing the cache
 * down and reopening it, which would orphan the [CacheDataSource] the
 * canvas player already holds a reference to — see [AudioCache][com.music.bitchord.playback.AudioCache],
 * whose own dynamic evictor is the pattern this follows.
 */
@UnstableApi
object CanvasCache {

    private lateinit var cache: SimpleCache
    private val evictor = MutableLruCacheEvictor(limitBytesOf(AppSettings.DEFAULT_CANVAS_CACHE_SIZE_MB))
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Opened once per process, alongside [com.music.bitchord.playback.AudioCache.init]. */
    fun init(context: Context) {
        cache = SimpleCache(
            File(context.cacheDir, "canvas"),
            evictor,
            StandaloneDatabaseProvider(context),
        )
        // May run before AppSettings.readAll on a cold start — the default
        // stands in until then, and the collector below takes the stored
        // value over as soon as readAll publishes it.
        evictor.maxBytes = limitBytesOf(AppSettings.maxCanvasCacheSizeMb.value)
        evictor.applyNow(cache)
        scope.launch {
            AppSettings.maxCanvasCacheSizeMb.collect { mb ->
                evictor.maxBytes = limitBytesOf(mb)
                evictor.applyNow(cache)
            }
        }
    }

    /**
     * [upstream] wrapped so a clip already on disk never touches the
     * network again — see the class doc for why that is the whole point.
     * A cache write that fails (full disk, evicted mid-write) drops back to
     * plain streaming rather than surfacing as a playback error, the same
     * choice [com.music.bitchord.playback.AudioCache] makes for audio.
     */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** How much of the disk the clips are holding right now, in bytes. */
    fun sizeBytes(): Long = cache.cacheSize

    /** Drops every cached clip. The listener asked; no grace period. */
    fun clear(onComplete: () -> Unit = {}) {
        scope.launch {
            cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    /**
     * The stored MB as bytes: off is zero (everything written is evicted as
     * it lands, so a clip still plays but never from disk), unlimited is no
     * ceiling of this app's own.
     */
    private fun limitBytesOf(mb: Int): Long = when (mb) {
        AppSettings.UNLIMITED_CANVAS_CACHE_MB -> Long.MAX_VALUE
        else -> mb.toLong() * 1024 * 1024
    }
}

/**
 * [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor], except the
 * ceiling is a `var` rather than baked into the constructor — the same shape
 * as [DynamicLruCacheEvictor][com.music.bitchord.playback.DynamicLruCacheEvictor],
 * without the audio cache's head-span exemption, which exists for Automix's
 * analyzer and has no meaning for a video clip. Needed here so a lowered
 * ceiling reclaims space at once rather than at the next write, and so a
 * ceiling of zero can actually empty the cache.
 */
@UnstableApi
private class MutableLruCacheEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Reclaims space right away when [maxBytes] drops, rather than waiting for
     * the next write to notice — otherwise a lowered limit only takes effect
     * whenever the listener next happens to play something.
     */
    fun applyNow(cache: Cache) = evictCache(cache, 0)

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes) {
            val span = leastRecentlyUsed.pollFirst() ?: return
            cache.removeSpan(span)
        }
    }

    private companion object {
        fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
            val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
            return when {
                delta == 0L -> lhs.compareTo(rhs)
                delta < 0L -> -1
                else -> 1
            }
        }
    }
}
