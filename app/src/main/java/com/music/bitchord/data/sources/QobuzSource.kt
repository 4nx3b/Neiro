package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.qobuz.QobuzAccountStore
import com.music.bitchord.data.qobuz.QobuzService
import com.music.bitchord.data.sources.addon.AddonQualityOption

private const val TAG = "BitChord"

class QobuzSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.QOBUZ
    override val displayName: String get() = config.label.ifBlank { SourceKind.QOBUZ.label }

    override suspend fun health(): SourceHealth {
        val account = QobuzAccountStore.account.value
            ?: return SourceHealth.Rejected(SIGN_IN_PROMPT)
        return SourceHealth.Ok(
            listOfNotNull(
                account.label,
                if (account.premium) "Lossless" else "Standard",
            ).joinToString(" · "),
        )
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val account = QobuzAccountStore.account.value ?: return emptyList()
        val rows = QobuzService.searchTracks(account, query, limit)
        return rows.map { row -> row.toSong() }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val account = QobuzAccountStore.account.value ?: return null
        val formatId = formatIdFor(request)
        val stream = QobuzService.resolveStream(account, trackId, formatId) ?: return null
        TrackLog.d(TAG, "Qobuz stream for $trackId at format $formatId: ${stream.codec}")
        return SourceStream(
            url = stream.url,
            format = StreamFormat(
                codec = stream.codec,
                kbps = stream.kbps,
                sampleRateHz = stream.sampleRateHz,
                bitDepth = stream.bitDepth,
            ),
        )
    }

    internal fun formatIdFor(request: StreamRequest): Int = when (request) {
        is StreamRequest.Lossless ->
            config.pickedQuality
                ?.let { pick -> config.qualityOptions.firstOrNull { it.value == pick }?.value }
                ?.toIntOrNull()
                ?.takeIf { it != QobuzService.FORMAT_ID_MP3_320 }
                ?: QobuzService.FORMAT_ID_FLAC
        is StreamRequest.Best -> QobuzService.FORMAT_ID_MP3_320
        is StreamRequest.Capped -> QobuzService.FORMAT_ID_MP3_320
    }

    private fun QobuzService.Track.toSong(): Song {
        return Song(
            videoId = SourceRegistry.trackKey(config.id, trackId),
            title = title,
            artist = artist ?: "Unknown Artist",
            albumName = album,
            thumbnailUrl = thumbnailUrl,
            durationText = durationSec?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            },
            sourceQuality = "LOSSLESS",
        )
    }

    companion object {
        val QUALITY_OPTIONS = listOf(
            AddonQualityOption(value = QobuzService.FORMAT_ID_MAX.toString(), label = "MAX 24-bit"),
            AddonQualityOption(value = QobuzService.FORMAT_ID_HI_RES.toString(), label = "Hi-Res"),
            AddonQualityOption(value = QobuzService.FORMAT_ID_FLAC.toString(), label = "FLAC 16-bit"),
            AddonQualityOption(value = QobuzService.FORMAT_ID_MP3_320.toString(), label = "320 kbps MP3"),
        )

        private const val SIGN_IN_PROMPT = "Sign in to Qobuz to use this source"
    }
}
