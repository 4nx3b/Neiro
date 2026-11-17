package com.music.bitchord.data.settings

/**
 * Stream bitrate ceiling on the YouTube fallback path — MEDIUM, HIGH and
 * LOSSLESS all mean "whatever the best available Opus format is" there; what
 * actually tells them apart is which other sources are allowed to answer
 * *before* YouTube gets asked. That part is [permits], and the rungs read:
 *
 * - [LOSSLESS] — the user's own addons and JioSaavn both asked.
 * - [HIGH] — the addons skipped, JioSaavn asked.
 * - [MEDIUM] and [LOW] — both skipped; YouTube's own Opus ladder is all there
 *   is, capped at [maxKbps].
 *
 * [hourly] is what the ceiling costs in data over an hour of listening, which
 * is the only part of this a user actually cares about on a metered plan.
 */
enum class AudioQuality(
    val maxKbps: Int,
    val label: String,
    val detail: String,
    val hourly: String,
) {
    LOW(64, "Low", "~64 kbps · smallest download", "29 MB/hr"),
    MEDIUM(Int.MAX_VALUE, "Medium", "Best available · ~171 kbps Opus", "77 MB/hr"),
    HIGH(Int.MAX_VALUE, "High", "JioSaavn up to 320kbps, YouTube fallback", "144 MB/hr"),
    LOSSLESS(Int.MAX_VALUE, "Lossless", "Your addons + JioSaavn, bit-exact where available", "300+ MB/hr"),
    ;
}

/**
 * The full player's own design, ported from ArchiveTune's PlayerDesignStyle:
 * each value is a whole layout of the now-playing screen — its artwork
 * treatment, typography, slider shape and transport. [BITCHORD] is this app's
 * own player and stays exactly as it has always been; the other two are
 * ArchiveTune's, ported with their behaviors.
 */
enum class PlayerDesignStyle {
    /** This app's own player — the sleeve, the deck, the mesh. Untouched. */
    BITCHORD,

    /** ArchiveTune "Apple Music": the shrink-on-pause album, the floating light. */
    APPLE_MUSIC,

    /** ArchiveTune "TikTok": the queue as a vertical feed, the right rail, the caption. */
    TIKTOK,
}

/** What the phone's main player stands on, below the artwork. */
enum class PlayerTheme {
    /** The artwork's own colours hung off its bottom edge, as a mesh. */
    DEFAULT,

    /** v1.5's four quantised blobs, drifting behind the whole screen. */
    LEGACY,

    /** The cover blurred across the whole screen, as the lyrics and queue are. */
    SIMPLE,

    /**
     * The theme's own background, flat — the "follow theme" background. The
     * player's chrome stays white over it, as it does over the blur.
     */
    FLAT,

    /**
     * A radial glow grown from the artwork's palette behind the sleeve, fading
     * to the theme background at the screen's edges.
     */
    GLOW,
}

/** Which tab the app opens on. Ported from ArchiveTune's NavigationTab. */
enum class DefaultOpenTab { HOME, SEARCH, LIBRARY }

/**
 * How the app's own back navigations animate. Ported from ArchiveTune's
 * BackAnimationStyle: DEFAULT is the instant pop; PREDICTIVE follows the
 * system's predictive back gesture, scaling the page away under the finger.
 */
enum class BackAnimationStyle { DEFAULT, PREDICTIVE }

/** The surface that was last open inside the expanded player. */
enum class LastPlayerScreen {
    MAIN,
    LYRICS,
    QUEUE,
}
