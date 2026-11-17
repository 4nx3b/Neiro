package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.addon.AddonQualityOption
import com.music.bitchord.data.tidal.TidalAccountStore
import com.music.bitchord.data.tidal.TidalService

private const val TAG = "BitChord"

/**
 * Tidal as [MusicSource] — the ArchiveTune fork's flagship source, ported onto
 * the registry contract. An account the listener signs into (Sources screen →
 * Tidal) rather than a server they configure: [config.baseUrl] stays empty and
 * the credentials live in [TidalAccountStore].
 *
 * Everything the source answers is account-bound, which is why [health] speaks
 * in session terms rather than reachability terms — a signed-out Tidal is not
 * "unreachable" (retrying cannot help) but "rejected" (sign in first), so the
 * sources screen shows the action the situation calls for.
 *
 * Tiers ride the config's quality list like an addon's: `qualityOptions` is
 * seeded at registry init and [activeQuality][SourceConfig.activeQuality] picks
 * the tier a stream is asked at. The request's own tier (lossless vs best
 * lossy) always wins over the pick where they disagree — a listener whose
 * connection ceiling said "no lossless today" is not served FLAC because the
 * picker still says so.
 */
class TidalSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.TIDAL
    override val displayName: String get() = config.label.ifBlank { SourceKind.TIDAL.label }

    override suspend fun health(): SourceHealth {
        val session = TidalAccountStore.session.value ?: return SourceHealth.Rejected(SIGN_IN_PROMPT)
        val subscription = session.userId?.let { userId ->
            runCatching { TidalService.fetchSubscription(session.accessToken, userId) }
                .getOrNull()
        } ?: TidalAccountStore.Subscription.UNKNOWN
        return SourceHealth.Ok(
            listOfNotNull(
                session.username,
                when (subscription) {
                    TidalAccountStore.Subscription.PREMIUM -> "Premium"
                    TidalAccountStore.Subscription.FREE -> "Free"
                    TidalAccountStore.Subscription.UNKNOWN -> null
                },
                session.countryCode,
            ).joinToString(" · "),
        )
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val session = TidalAccountStore.session.value ?: return emptyList()
        val token = TidalAccountStore.validAccessToken() ?: return emptyList()
        val rows = TidalService.searchTracks(token, query, limit, session.countryCode)
        return rows.map { row -> row.toSong() }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val session = TidalAccountStore.session.value ?: return null
        val token = TidalAccountStore.validAccessToken() ?: return null
        val tier = tierFor(request)
        val decoded = TidalService.playbackInfo(
            accessToken = token,
            trackId = trackId,
            audioQuality = tier,
            durationSec = null,
            countryCode = session.countryCode,
        ) ?: return null
        TrackLog.d(TAG, "Tidal stream for $trackId at $tier: ${decoded.codec} ${decoded.url.take(72)}")
        return SourceStream(
            url = decoded.url,
            format = StreamFormat(
                codec = decoded.codec,
                kbps = decoded.kbps,
                sampleRateHz = decoded.sampleRateHz,
                bitDepth = decoded.bitDepth,
                kbpsInferred = decoded.kbpsInferred,
            ),
        )
    }

    /**
     * The API tier to ask at: the pick for a lossless request — an explicit
     * Hi-Res or 16-bit choice, with a lossy pick read as "no lossless tier
     * chosen" and no pick at all defaulting to 16-bit LOSSLESS rather than
     * the list's first entry, because Hi-Res bandwidth is something to opt
     * into, not to land in — and the best lossy tier for everything else,
     * because a `Best` request means best *lossy*: the rungs below Lossless
     * exist to cap what a metered connection pays for, and a FLAC is the one
     * thing they never meant to buy.
     */
    internal fun tierFor(request: StreamRequest): String = when (request) {
        is StreamRequest.Lossless ->
            config.pickedQuality
                ?.let { pick -> config.qualityOptions.firstOrNull { it.value == pick }?.value }
                ?.takeIf { it != QUALITY_HIGH && it != QUALITY_LOW }
                ?: QUALITY_LOSSLESS
        is StreamRequest.Best -> QUALITY_HIGH
        is StreamRequest.Capped -> QUALITY_HIGH
    }

    private fun TidalService.Track.toSong(): Song {
        val lossless = audioQuality.equals("LOSSLESS", true) ||
            audioQuality.equals("HI_RES", true) ||
            audioQuality.equals("HI_RES_LOSSLESS", true)
        return Song(
            videoId = SourceRegistry.trackKey(config.id, id),
            title = title,
            artist = artistNames.joinToString(", ").ifBlank { "Unknown Artist" },
            albumName = album,
            thumbnailUrl = albumCoverId?.let { TidalService.artworkUrl(it) },
            durationText = durationSec?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            },
            sourceQuality = if (lossless) "LOSSLESS" else "HIGH",
            sourceHiRes = audioQuality.equals("HI_RES_LOSSLESS", true),
            isExplicit = isExplicit,
        )
    }

    companion object {
        const val QUALITY_LOW = "LOW"
        const val QUALITY_HIGH = "HIGH"
        const val QUALITY_LOSSLESS = "LOSSLESS"
        const val QUALITY_HI_RES = "HI_RES_LOSSLESS"

        /**
         * The tiers the picker offers, in the order they are listed. Seeded
         * into the config by [SourceRegistry.sourcesForInit] so the picker and
         * the resolve agree on the vocabulary; a stored config from before the
         * seeding keeps working because [tierFor] falls back to LOSSLESS.
         */
        val QUALITY_OPTIONS = listOf(
            AddonQualityOption(value = QUALITY_HI_RES, label = "Hi-Res FLAC"),
            AddonQualityOption(value = QUALITY_LOSSLESS, label = "Lossless 16-bit"),
            AddonQualityOption(value = QUALITY_HIGH, label = "320 kbps AAC"),
            AddonQualityOption(value = QUALITY_LOW, label = "96 kbps AAC"),
        )

        private const val SIGN_IN_PROMPT = "Sign in to Tidal to use this source"
    }
}
