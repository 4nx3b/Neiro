package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.deezer.DeezerAccountStore
import com.music.bitchord.data.deezer.DeezerService
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.addon.AddonQualityOption

private const val TAG = "BitChord"

class DeezerSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.DEEZER
    override val displayName: String get() = config.label.ifBlank { SourceKind.DEEZER.label }

    override suspend fun health(): SourceHealth {
        val account = DeezerAccountStore.account.value
            ?: return SourceHealth.Rejected(SIGN_IN_PROMPT)
        return SourceHealth.Ok(
            listOfNotNull(
                account.name,
                if (account.lossless) "Lossless" else "Standard",
            ).joinToString(" · "),
        )
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        DeezerAccountStore.account.value ?: return emptyList()
        val rows = DeezerService.searchTracks(query, limit)
        return rows.map { row -> row.toSong() }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        DeezerAccountStore.account.value ?: return null
        val format = formatFor(request)
        val stream = DeezerService.resolveStream(trackId, format) ?: return null
        TrackLog.d(TAG, "Deezer stream for $trackId at $format: ${stream.codec}")
        return SourceStream(
            url = stream.uri,
            format = StreamFormat(
                codec = stream.codec,
                kbps = stream.kbps,
                sampleRateHz = stream.sampleRateHz,
                bitDepth = stream.bitDepth,
            ),
        )
    }

    internal fun formatFor(request: StreamRequest): String = when (request) {
        is StreamRequest.Lossless ->
            config.pickedQuality
                ?.let { pick -> config.qualityOptions.firstOrNull { it.value == pick }?.value }
                ?.takeIf { it != FORMAT_MP3_320 && it != FORMAT_MP3_128 }
                ?: FORMAT_FLAC
        is StreamRequest.Best -> FORMAT_MP3_320
        is StreamRequest.Capped ->
            if (request.maxKbps < 160) FORMAT_MP3_128 else FORMAT_MP3_320
    }

    private fun DeezerService.Track.toSong(): Song {
        return Song(
            videoId = SourceRegistry.trackKey(config.id, trackId),
            title = title,
            artist = artist ?: "Unknown Artist",
            albumName = album,
            thumbnailUrl = coverUrl,
            durationText = durationSec?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            },
            sourceQuality = "HIGH",
        )
    }

    companion object {
        const val FORMAT_FLAC = DeezerService.FORMAT_FLAC
        const val FORMAT_MP3_320 = DeezerService.FORMAT_MP3_320
        const val FORMAT_MP3_128 = DeezerService.FORMAT_MP3_128

        val QUALITY_OPTIONS = listOf(
            AddonQualityOption(value = FORMAT_FLAC, label = "FLAC 1411 kbps"),
            AddonQualityOption(value = FORMAT_MP3_320, label = "320 kbps MP3"),
            AddonQualityOption(value = FORMAT_MP3_128, label = "128 kbps MP3"),
        )

        private const val SIGN_IN_PROMPT = "Sign in to Deezer to use this source"
    }
}
