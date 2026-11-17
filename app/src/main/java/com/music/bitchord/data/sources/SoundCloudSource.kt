package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.soundcloud.SoundCloudAccountStore
import com.music.bitchord.data.soundcloud.SoundCloudService
import com.music.bitchord.data.sources.addon.AddonQualityOption
import com.music.bitchord.playback.StreamContainer

private const val TAG = "BitChord"

class SoundCloudSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.SOUNDCLOUD
    override val displayName: String get() = config.label.ifBlank { SourceKind.SOUNDCLOUD.label }

    override suspend fun health(): SourceHealth {
        val account = SoundCloudAccountStore.account.value
        return SourceHealth.Ok(account?.username)
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val token = SoundCloudAccountStore.account.value?.token
        val rows = SoundCloudService.searchTracks(query, limit, token)
        return rows.map { row -> row.toSong() }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val token = SoundCloudAccountStore.account.value?.token
        val tier = tierFor(request)
        val stream = SoundCloudService.resolveStream(trackId, tier, token) ?: return null
        TrackLog.d(TAG, "SoundCloud stream for $trackId at $tier: ${stream.codec} hls=${stream.isHls}")
        if (stream.isHls) {
            StreamContainer.declare(stream.url, "hls")
        }
        return SourceStream(
            url = stream.url,
            format = StreamFormat(
                codec = stream.codec,
                kbps = stream.kbps,
                sampleRateHz = stream.sampleRateHz,
            ),
            headers = SoundCloudService.mediaHeaders(),
            durationSec = stream.durationSec,
        )
    }

    internal fun tierFor(request: StreamRequest): String {
        val picked = config.pickedQuality
            ?.let { pick -> config.qualityOptions.firstOrNull { it.value == pick }?.value }
        return when (request) {
            is StreamRequest.Lossless -> picked ?: TIER_DEFAULT
            is StreamRequest.Best -> picked ?: TIER_DEFAULT
            is StreamRequest.Capped -> when {
                request.maxKbps >= 160 -> picked?.takeIf { kbpsOf(it) ?: 0 <= request.maxKbps } ?: TIER_AAC_160
                request.maxKbps >= 128 -> TIER_MP3_128
                else -> TIER_AAC_96
            }
        }
    }

    private fun kbpsOf(tier: String): Int? = when (tier) {
        TIER_AAC_160 -> 160
        TIER_MP3_128 -> 128
        TIER_AAC_96 -> 96
        else -> null
    }

    private fun SoundCloudService.Track.toSong(): Song {
        return Song(
            videoId = SourceRegistry.trackKey(config.id, trackId),
            title = title,
            artist = artist,
            thumbnailUrl = artworkUrl,
            durationText = durationSec?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            },
            sourceQuality = "HIGH",
        )
    }

    companion object {
        const val TIER_AAC_160 = SoundCloudService.TIER_AAC_160
        const val TIER_MP3_128 = SoundCloudService.TIER_MP3_128
        const val TIER_AAC_96 = SoundCloudService.TIER_AAC_96
        private const val TIER_DEFAULT = TIER_AAC_160

        val QUALITY_OPTIONS = listOf(
            AddonQualityOption(value = TIER_AAC_160, label = "160 kbps AAC"),
            AddonQualityOption(value = TIER_MP3_128, label = "128 kbps MP3"),
            AddonQualityOption(value = TIER_AAC_96, label = "96 kbps AAC"),
        )
    }
}
