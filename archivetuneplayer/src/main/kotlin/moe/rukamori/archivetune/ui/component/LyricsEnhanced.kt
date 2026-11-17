/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import moe.rukamori.archivetune.bridge.LyricsClock // BRIDGE: lyrics
import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.LocalLyricsScrollListener
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.AiLyricsRomanization
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.LyricsUtils.hasWordSyncedLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedWordsForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeLyricsLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeWordsForLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.lastfm.CatalogueCoverProvider
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val LYRIC_SYNC_LEAD_MS = 120L
private const val LRC_LEAD_MS = LYRIC_SYNC_LEAD_MS

private const val LYRICS_POSITION_PUBLISH_MIN_DELTA_MS = 10L

private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 0L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FIRST_FOCUS_SETTLE_FRAMES = 42
private const val LYRIC_FIRST_FOCUS_EMPTY_FRAMES = 6

private const val LYRIC_FOCUS_TARGET_MANUAL = -2
private const val LYRIC_FOCUS_TARGET_PENDING = -1

private const val LYRIC_FIRST_FOCUS_FADE_MS = 200

private const val LYRIC_FIRST_FOCUS_TIMEOUT_MS = 900L

private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

private const val LINE_SYNCED_TRAILING_LINE_DURATION_MS = 4_000L

private const val POSITION_RESET_BACKWARD_THRESHOLD_MS = 1000L

private const val ROMANIZATION_FIRST_BUILD_GRACE_MS = 700L

// Native word-synced renderer tuning. The alphas and timings mirror the look
// the accompanist library shipped, so the swap is visually familiar.
private const val NATIVE_ACTIVE_ALPHA = 1f
private const val NATIVE_INACTIVE_ALPHA = 0.64f
private const val NATIVE_UNSUNG_ALPHA = 0.45f
private const val NATIVE_LINE_OPACITY_MS = 700
private const val NATIVE_BLUR_FADE_MS = 500
private const val NATIVE_SCROLL_DAMPING = 0.9f
private const val NATIVE_SCROLL_STIFFNESS = 100f

private val NativeLineSpacing = 24.dp

// Blur strength (in em) applied to lines away from the active one: neighbours
// stay readable but clearly out of focus, Apple-Music style.
private fun nativeBlurEm(signedDistance: Int): Float =
    when (abs(signedDistance)) {
        0 -> 0f
        1 -> 0.08f
        2 -> 0.11f
        3 -> 0.13f
        else -> 0.15f
    }

private data class NativeLyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val phonetic: String? = null,
)

@Immutable
private data class NativeLyricLine(
    val startMs: Int,
    val endMs: Int,
    val text: String,
    val words: List<NativeLyricWord> = emptyList(),
    val backgroundWords: List<NativeLyricWord> = emptyList(),
    val rightAlignBackground: Boolean = false,
    val translation: String? = null,
    val isMetadata: Boolean = false,
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()

    // Sub-line assembled from the per-word romanization the build step keeps
    // aligned with `words`; rendered under the main text like the phonetic
    // row the accompanist view drew.
    fun phoneticRow(): String? =
        words
            .mapNotNull { it.phonetic?.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")

    fun backgroundText(): String? =
        backgroundWords
            .takeIf { it.isNotEmpty() }
            ?.joinWordsWithLatinGaps()

    // Character position the sweep has revealed by `positionMs`, interpolating
    // inside the singing word and across the pause after it — the BitChord
    // sweep math, on this module's own line model.
    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= startMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                val through = (positionMs - word.startMs).toFloat() / span
                return start + through * word.text.length
            }
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }
}

private data class NativeLyricsBuild(
    val lines: List<NativeLyricLine>,
    val romanization: Map<Int, List<String?>>,
    val generation: Int,
)

private fun Map<Int, List<String?>>.renderedRomanization(): Map<Int, List<String?>> =
    filterValues { values -> values.any { !it.isNullOrBlank() } }

internal fun extractTtmlWriters(lyrics: String?): String {
    if (lyrics.isNullOrBlank()) return ""
    val writerTagPattern =
        Regex(
            pattern = "<(songwriter|composer|lyricist|writer|ar)(?:\\s[^>]*)?>([^<]+)</\\1>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    val writers = LinkedHashSet<String>()
    writerTagPattern.findAll(lyrics).forEach { match ->
        val name = match.groupValues.getOrNull(2)?.trim()
        if (!name.isNullOrBlank()) writers.add(name)
    }
    return writers.joinToString(", ").trim()
}

@Composable
fun LyricsEnhanced(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,

    textSizeOverride: Float? = null,
    singleActiveLine: Boolean = false,
    translationVisibleOverride: Boolean? = null,
    phoneticVisibleOverride: Boolean? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val animationsDisabled = LocalAnimationsDisabled.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()

    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsTextSizePreference) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val lyricsTextSize = textSizeOverride ?: lyricsTextSizePreference

    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = false)
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)

    val aiRomanizationSettings = AiLyricsRomanization.rememberSettings()

    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
            aiRomanizationSettings.active,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
                aiHandled = aiRomanizationSettings.active,
            )
        }

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)

    val textColor = textColorOverride ?: Color.White
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedLineKeys = remember { mutableStateListOf<String>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 7
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<LyricsSharePayload?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyrics =
        remember(currentLyrics, mediaMetadata?.id) {
            currentLyrics
                ?.takeIf { lyricsEntity -> lyricsEntity.id == mediaMetadata?.id }
                ?.lyrics
        }
    val showTranslations =
        remember(currentLyrics?.source, romanizationPreferences.showsRomanization, translationVisibleOverride) {
            val base = currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value ||
                romanizationPreferences.showsRomanization
            base && (translationVisibleOverride ?: true)
        }
    val showPhoneticLines = phoneticVisibleOverride ?: true

    val playbackState by playerConnection.playbackState.collectAsState()
    var restartTick by remember { mutableIntStateOf(0) }
    var lastPlaybackState by remember { mutableStateOf(Player.STATE_READY) }
    LaunchedEffect(mediaMetadata?.id, playbackState) {
        if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_ENDED &&
            player.currentPosition < 1_000L
        ) {
            restartTick++
        } else if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_BUFFERING &&
            player.currentPosition < 500L &&
            restartTick > 0
        ) {
            restartTick++
        }
        lastPlaybackState = playbackState
    }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, lyrics, restartTick) {
            Triple(mediaMetadata?.id.orEmpty(), lyrics, restartTick)
        }

    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics!!) || isTtml(lyrics!!)) }
    val isWordSyncedFormat = remember(lyrics) { lyrics != null && hasWordSyncedLyrics(lyrics!!) }

    var parsedEntries by remember(lyrics) { mutableStateOf<List<LyricsEntry>?>(null) }
    LaunchedEffect(lyrics) {
        val text = lyrics
        if (text == null || text == LYRICS_NOT_FOUND) {
            parsedEntries = emptyList()
            return@LaunchedEffect
        }
        parsedEntries =
            withContext(Dispatchers.Default) {
                when {
                    isTtml(text) -> parseTtml(text)
                    isLineSyncedLrc(text) -> parseLyrics(text)
                    else ->
                        text
                            .lines()
                            .filter { it.isNotBlank() }
                            .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                }
            }
    }
    val lyricsEntries: List<LyricsEntry> = parsedEntries.orEmpty()

    val lyricsProviderNameRaw = currentLyrics?.providerName.orEmpty()
    val lyricsSourceLabel = stringResource(R.string.lyrics_from_source, lyricsProviderNameRaw)
    val lyricsProviderLabel =
        lyricsSourceLabel.takeIf { lyricsProviderNameRaw.isNotBlank() }

    var resolvedWriters by remember(mediaMetadata?.id) { mutableStateOf<String?>(null) }
    val writersLookupTitle = mediaMetadata?.title.orEmpty()
    val writersLookupArtist =
        mediaMetadata
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?.trim()
            .orEmpty()
    LaunchedEffect(mediaMetadata?.id, writersLookupTitle, writersLookupArtist) {
        if (writersLookupTitle.isBlank()) return@LaunchedEffect

        val writers = CatalogueCoverProvider.resolveSongwriters(writersLookupTitle, writersLookupArtist)
        resolvedWriters = writers?.takeIf { it.isNotEmpty() }?.joinToString()
    }
    val ttmlWriters = lyrics?.let(::extractTtmlWriters).orEmpty()
    val composerWritersRaw =
        resolvedWriters?.takeIf { it.isNotBlank() }
            ?: ttmlWriters.takeIf { it.isNotBlank() }
            ?: mediaMetadata
                ?.artists
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString { it.name }
                ?.trim()
                .orEmpty()
    val composerFooterLabel =
        stringResource(R.string.written_by, composerWritersRaw)
            .takeIf { composerWritersRaw.isNotBlank() }

    var nativeLyricsBuild by remember(lyrics, isWordSyncedFormat) {
        mutableStateOf(NativeLyricsBuild(emptyList(), emptyMap(), generation = 0))
    }
    val syncedLines = nativeLyricsBuild.lines

    val karaokeGeneration = nativeLyricsBuild.generation

    val aiRomanizationSessionKey =
        remember(mediaMetadata?.id, lyrics) { AiLyricsRomanization.sessionKey(mediaMetadata?.id, lyrics) }
    val aiRomanizationResult by AiLyricsRomanization.results.collectAsStateWithLifecycle()

    val aiRomanizedLines: List<String?> =
        remember(aiRomanizationResult, aiRomanizationSessionKey, aiRomanizationSettings.active, aiRomanizationSettings.configKey, lyricsEntries) {
            if (!aiRomanizationSettings.active) {
                emptyList()
            } else {
                AiLyricsRomanization.linesFor(
                    aiRomanizationSessionKey,
                    lyricsEntries.map { it.text },
                    aiRomanizationSettings,
                )
            }
        }
    LaunchedEffect(aiRomanizationSessionKey, lyricsEntries, aiRomanizationSettings) {
        if (!aiRomanizationSettings.active || !aiRomanizationSettings.auto) return@LaunchedEffect
        if (lyricsEntries.isEmpty()) return@LaunchedEffect
        AiLyricsRomanization.request(
            sessionKey = aiRomanizationSessionKey,
            lines = lyricsEntries.map { it.text },
            settings = aiRomanizationSettings,
        )
    }

    LaunchedEffect(lyricsEntries, romanizationPreferences, aiRomanizedLines, lyricsProviderLabel, composerFooterLabel, mediaMetadata?.id, singleActiveLine) {
        withContext(Dispatchers.Default) {
        fun publish(romanization: Map<Int, List<String?>>) {
            val previous = nativeLyricsBuild
            val changesVisibleLines =
                previous.lines.isNotEmpty() &&
                    romanization.renderedRomanization() != previous.romanization.renderedRomanization()
            nativeLyricsBuild =
                NativeLyricsBuild(
                    lines =
                        buildNativeLyricLines(
                            entries = lyricsEntries,
                            isWordSynced = isWordSyncedFormat,
                            romanizationMap = romanization,
                            providerHeader = lyricsProviderLabel,
                            composerFooter = composerFooterLabel,
                            compactTranslation = singleActiveLine,
                        ),
                    romanization = romanization,
                    generation = if (changesVisibleLines) previous.generation + 1 else previous.generation,
                )
        }

        val aiMap = aiRomanizationMap(lyricsEntries, aiRomanizedLines)

        val toRomanize: List<Pair<Int, LyricsEntry>> =
            if (!romanizationPreferences.isEnabled) {
                emptyList()
            } else {
                lyricsEntries.mapIndexedNotNull { index, entry ->
                    val hasProviderRomanization =
                        providedRomanizedTextForEntry(entry, romanizationPreferences) != null
                    if (hasProviderRomanization || shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                        index to entry
                    } else {
                        null
                    }
                }
            }
        if (toRomanize.isEmpty()) {
            publish(aiMap)
            return@withContext
        }

        val romanization =
            async {
                val jobs =
                    toRomanize.map { (index, entry) ->
                        async {
                            val romanized: List<String?> =
                                try {
                                    if (entry.words != null) {
                                        val mainWordCount = entry.words!!.count { !it.isBackground }
                                        providedRomanizedWordsForEntry(entry, mainWordCount, romanizationPreferences)
                                            ?: romanizeWordsForLine(

                                                words = entry.words!!.filter { !it.isBackground }.map { it.text },
                                                lineText = entry.text,
                                                preferences = romanizationPreferences,
                                            )
                                    } else {
                                        listOf(
                                            providedRomanizedTextForEntry(entry, romanizationPreferences)
                                                ?: romanizeLyricsLine(entry.text, romanizationPreferences),
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    reportException(e)
                                    if (entry.words != null) {
                                        List(entry.words!!.count { !it.isBackground }) { null }
                                    } else {
                                        listOf(null)
                                    }
                                }
                            index to romanized
                        }
                    }
                jobs.awaitAll().toMap()
            }

        val quickRomanization = withTimeoutOrNull(ROMANIZATION_FIRST_BUILD_GRACE_MS) { romanization.await() }
        if (quickRomanization != null) {
            publish(quickRomanization)
            return@withContext
        }
        publish(aiMap)
        publish(romanization.await())
        }
    }

    val leadMs = if (isWordSyncedFormat) TTML_LEAD_MS else LRC_LEAD_MS

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(playbackParameters.speed)
    val playbackPositionMs =
        remember(player) {
            mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
        }
    val playbackSyncPosition: () -> Int =
        remember {
            {
                (
                    playbackPositionMs.longValue +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value +
                        LYRIC_VISUAL_TUNING_OFFSET_MS
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    val currentLineIndexState = remember { mutableIntStateOf(-1) }

    val activeNativeLine =
        remember(syncedLines, currentLineIndexState.intValue, singleActiveLine) {
            if (!singleActiveLine) {
                null
            } else {
                val index = currentLineIndexState.intValue
                val line = syncedLines.getOrNull(index)
                if (line != null && line.startMs >= 0 && line.startMs < 86_400_000) line else null
            }
        }

    val latestSyncedLines = rememberUpdatedState(syncedLines)

    var positionResetCounter by remember { mutableIntStateOf(0) }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    val listState = key(lyricsSessionKey, positionResetCounter, karaokeGeneration) { rememberLazyListState() }

    var awaitingFirstFocus by
        remember(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
            mutableStateOf(isSynced)
        }

    LaunchedEffect(awaitingFirstFocus) {
        if (!awaitingFirstFocus) return@LaunchedEffect
        delay(LYRIC_FIRST_FOCUS_TIMEOUT_MS)
        awaitingFirstFocus = false
    }

    val firstFocusAlpha =
        animateFloatAsState(
            targetValue = if (awaitingFirstFocus) 0f else 1f,
            animationSpec =
                tween(
                    durationMillis = if (awaitingFirstFocus) 0 else LYRIC_FIRST_FOCUS_FADE_MS,
                    easing = LinearEasing,
                ),
            label = "lyrics-first-focus-alpha",
        )

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
        isSelectionModeActive = false
        selectedLineKeys.clear()

        currentLineIndexState.intValue = -1
    }

    LaunchedEffect(player, lyricsSessionKey, animationsDisabled, playbackParameters.speed) {
        var wasSliderActive = false
        var anchorFrameNanos = 0L
        var lastRawPositionMs = player.currentPosition.coerceAtLeast(0L)

        var lastLinesRef: List<NativeLyricLine>? = null
        var cachedLineIdx = -1
        var cachedCurrentLineStart = Int.MIN_VALUE
        var cachedNextLineStart = Int.MAX_VALUE
        var lastPublishedPositionMs = playbackPositionMs.longValue
        var lyricsClockMs = playbackPositionMs.longValue.toDouble() // BRIDGE: lyrics

        fun publishPosition(nextPosition: Long) {
            val delta = nextPosition - lastPublishedPositionMs
            if (delta >= LYRICS_POSITION_PUBLISH_MIN_DELTA_MS || delta < 0) { // BRIDGE: lyrics
                lastPublishedPositionMs = nextPosition
                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }

        while (isActive) {
            val sliderPosition = latestSliderPositionProvider.value()
            val isSliderActive = sliderPosition != null
            if (isSliderActive && !wasSliderActive) {
                isManualScrolling = false
            }
            wasSliderActive = isSliderActive

            val rawPlayerPosition = player.currentPosition.coerceAtLeast(0L) // BRIDGE: lyrics read once a frame
            val rawPosition = (sliderPosition ?: rawPlayerPosition).coerceAtLeast(0L) // BRIDGE: lyrics
            if (lastRawPositionMs - rawPlayerPosition > POSITION_RESET_BACKWARD_THRESHOLD_MS) {
                positionResetCounter += 1
                currentLineIndexState.intValue = -1
            }
            lastRawPositionMs = rawPlayerPosition

            val effectivePositionMs: Long
            if (sliderPosition != null || !player.isPlaying || animationsDisabled) {
                anchorFrameNanos = 0L
                publishPosition(rawPosition)
                effectivePositionMs =
                    (rawPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
                if (sliderPosition == null) {
                    delay(if (player.isPlaying) 50L else 100L)
                } else {
                    withFrameNanos { }
                }
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                // A monotonic clock (LyricsClock): the controller corrects the raw
                // player position backwards once a second or so, which would make the
                // word sweep and the auto-scroll stutter through every correction.
                if (anchorFrameNanos == 0L) { // BRIDGE: lyrics
                    anchorFrameNanos = frameNanos // BRIDGE: lyrics
                    lyricsClockMs = maxOf(rawPosition.toDouble(), lastPublishedPositionMs.toDouble()) // BRIDGE: lyrics
                } // BRIDGE: lyrics
                val advanceMs = ((frameNanos - anchorFrameNanos) / 1_000_000.0) * latestPlaybackSpeed.value // BRIDGE: lyrics
                anchorFrameNanos = frameNanos // BRIDGE: lyrics
                lyricsClockMs = LyricsClock.next(lyricsClockMs, rawPosition.toDouble(), advanceMs) // BRIDGE: lyrics
                val nextPosition = lyricsClockMs.roundToLong().coerceAtLeast(0L) // BRIDGE: lyrics

                publishPosition(nextPosition)
                effectivePositionMs =
                    (nextPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
            }

            val linesNow = latestSyncedLines.value
            if (linesNow.isNotEmpty()) {
                val lyricsChanged = linesNow !== lastLinesRef
                if (lyricsChanged) {
                    lastLinesRef = linesNow
                    cachedLineIdx = -1
                }
                val pos = effectivePositionMs.toInt()
                val needsResearch =
                    cachedLineIdx == -1 ||
                        pos >= cachedNextLineStart ||
                        pos < cachedCurrentLineStart
                if (needsResearch) {
                    val newLineIdx = linesNow.findLastStartedLineIndex(pos)
                    cachedLineIdx = newLineIdx
                    cachedCurrentLineStart =
                        if (newLineIdx >= 0) linesNow[newLineIdx].startMs else Int.MIN_VALUE
                    cachedNextLineStart =
                        linesNow.getOrNull(newLineIdx + 1)?.startMs ?: Int.MAX_VALUE
                    if (newLineIdx != currentLineIndexState.intValue) {
                        currentLineIndexState.intValue = newLineIdx
                    }
                }
            }
        }
    }

    val nestedScrollConnection =
        remember {
            var lastUserScrollEventMs = 0L
            object : NestedScrollConnection {
                private fun markManualScroll() {
                    val now = System.currentTimeMillis()
                    if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                        isManualScrolling = true
                        lastManualScrollTime = now
                        lastUserScrollEventMs = now
                    }
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        markManualScroll()
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (!isSelectionModeActive && isManualScrolling) {
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Velocity.Zero
                }
            }
        }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    val onLyricsScroll = LocalLyricsScrollListener.current
    LaunchedEffect(isManualScrolling) {
        onLyricsScroll(isManualScrolling)
    }

    val latestSyncedLinesForScroll = rememberUpdatedState(syncedLines)

    LaunchedEffect(lyricsSessionKey, isSynced, positionResetCounter, karaokeGeneration) {
        if (!isSynced || singleActiveLine) {
            awaitingFirstFocus = false
            return@LaunchedEffect
        }

        snapshotFlow { latestSyncedLinesForScroll.value.isNotEmpty() }.first { it }

        snapshotFlow {
            if (isManualScrolling || isSelectionModeActive) {
                LYRIC_FOCUS_TARGET_MANUAL
            } else {
                currentLineIndexState.intValue
                    .takeIf { index -> index in latestSyncedLinesForScroll.value.indices }
                    ?: LYRIC_FOCUS_TARGET_PENDING
            }
        }.distinctUntilChanged()
            .collectLatest { target ->
                if (target == LYRIC_FOCUS_TARGET_MANUAL || target == LYRIC_FOCUS_TARGET_PENDING) {
                    return@collectLatest
                }
                if (awaitingFirstFocus) {
                    // The native renderer scrolls the same LazyListState this
                    // file owns, so the first-focus wait is always on it.
                    listState.awaitKaraokeFirstFocus(target)
                    awaitingFirstFocus = false
                }
            }
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val typography = MaterialTheme.typography
    val normalTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.headlineMedium.copy(
                fontSize = lyricsTextSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = lyricsFontFamily ?: typography.headlineMedium.fontFamily,
            )
        }
    val accompanimentTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.82f).sp,
                fontFamily = lyricsFontFamily ?: typography.titleLarge.fontFamily,
            )
        }
    val translationTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.72f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = lyricsFontFamily ?: typography.titleLarge.fontFamily,
            )
        }
    val phoneticTextStyle =
        remember(typography, lyricsTextSize) {
            typography.bodyMedium.copy(
                fontSize = (lyricsTextSize * 0.55f).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    val plainLyrics =
        remember(lyricsEntries, isSynced, lyricsProviderLabel, composerFooterLabel) {
            val lyricItems =
                if (isSynced) {
                    emptyList()
                } else {
                    lyricsEntries.mapIndexedNotNull { index, entry ->
                        val text = entry.text.trim()
                        if (text.isBlank()) {
                            null
                        } else {
                            val selectionId = "plain:$index:${text.hashCode()}"
                            PlainLyricLine(
                                itemId = "$selectionId#$index",
                                selectionId = selectionId,
                                text = text,
                            )
                        }
                    }
                }
            val providerHeaderLine =
                lyricsProviderLabel
                    ?.takeIf { lyricItems.isNotEmpty() }
                    ?.let { text ->
                        PlainLyricLine(
                            itemId = "provider_header",
                            selectionId = "plain:provider_header:${text.hashCode()}",
                            text = text,
                            isMetadata = true,
                        )
                    }
            val composerFooterLine =
                composerFooterLabel
                    ?.takeIf { lyricItems.isNotEmpty() }
                    ?.let { text ->
                        PlainLyricLine(
                            itemId = "composer_footer",
                            selectionId = "plain:composer_footer:${text.hashCode()}",
                            text = text,
                            isMetadata = true,
                        )
                    }
            PlainLyrics(
                items =
                    buildList {
                        if (providerHeaderLine != null) add(providerHeaderLine)
                        addAll(lyricItems)
                        if (composerFooterLine != null) add(composerFooterLine)
                    },
            )
        }
    val selectionLines =
        remember(isSynced, syncedLines, plainLyrics) {
            if (isSynced) {
                syncedLines.mapIndexedNotNull { index, line ->
                    val text = line.text
                    if (text.isBlank()) {
                        null
                    } else {
                        val isMetadataLine = line.startMs < 0 || line.startMs >= 86_400_000
                        if (isMetadataLine) {
                            null
                        } else {
                            val selectionId = line.selectionKey()
                            val (translation, romanisation) = line.shareTranslationParts()
                            LyricSelectionLine(
                                itemId = "$selectionId#$index",
                                selectionId = selectionId,
                                text = text,
                                translation = translation,
                                romanisation = romanisation,
                            )
                        }
                    }
                }
            } else {
                plainLyrics.items.mapNotNull { line ->

                    if (line.isMetadata) {
                        null
                    } else {
                        LyricSelectionLine(
                            itemId = line.itemId,
                            selectionId = line.selectionId,
                            text = line.text,
                        )
                    }
                }
            }
        }
    val selectedLineKeySnapshot = selectedLineKeys.toList()
    val selectedLineKeySet = remember(selectedLineKeySnapshot) { selectedLineKeySnapshot.toSet() }
    val dismissSelection = {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }
    val toggleSelectedLine: (String) -> Unit = { lineKey ->
        if (selectedLineKeys.contains(lineKey)) {
            selectedLineKeys.remove(lineKey)
            if (selectedLineKeys.isEmpty()) isSelectionModeActive = false
        } else if (selectedLineKeys.size < maxSelectionLimit) {
            selectedLineKeys.add(lineKey)
        } else {
            showMaxSelectionToast = true
        }
    }
    val shareSelectedLyrics: () -> Unit = {
        val metadata = mediaMetadata
        if (metadata != null) {
            val selectedEntries =
                selectionLines.filter { line -> line.selectionId in selectedLineKeySet }
            val selectedLyricsText =
                selectedEntries.joinToString("\n") { line -> line.text }
            if (selectedLyricsText.isNotBlank()) {
                shareDialogData =
                    LyricsSharePayload(
                        lyricsText = selectedLyricsText,
                        songTitle = metadata.title,
                        artists = metadata.artists.joinToString { it.name },
                        lines = selectedEntries.map { line ->
                            LyricsShareLine(
                                text = line.text,
                                translation = line.translation,
                                romanisation = line.romanisation,
                            )
                        },
                    )
                showShareDialog = true
            }
        }
        dismissSelection()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)

                .graphicsLayer { alpha = firstFocusAlpha.value },
    ) {
        when {
            lyrics == LYRICS_NOT_FOUND -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            lyrics == null || parsedEntries == null -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            isSynced && syncedLines.isEmpty() && lyricsEntries.isNotEmpty() -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            isSynced && syncedLines.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced && plainLyrics.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced -> {
                PlainLyricsView(
                    lines = plainLyrics,
                    listState = listState,
                    selectedLineKeys = selectedLineKeySet,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                    onLineClicked = { lineKey ->
                        if (isSelectionModeActive) toggleSelectedLine(lineKey)
                    },
                    onLinePressed = { lineKey ->
                        if (!isSelectionModeActive) {
                            isSelectionModeActive = true
                            if (!selectedLineKeys.contains(lineKey)) {
                                selectedLineKeys.add(lineKey)
                            }
                        } else if (!selectedLineKeys.contains(lineKey)) {
                            toggleSelectedLine(lineKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            singleActiveLine && isSynced -> {
                key(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides phoneticTextStyle,
                    ) {
                        SingleActiveLyricLine(
                            activeLine = activeNativeLine,
                            currentPosition = playbackSyncPosition,
                            textColor = textColor,
                            normalTextStyle = normalTextStyle,
                            accompanimentTextStyle = accompanimentTextStyle,
                            phoneticTextStyle = phoneticTextStyle,
                            showTranslation = showTranslations,
                            onLineClick = { line ->
                                if (lyricsClick && line.startMs > 0) {
                                    player.seekTo(line.startMs.toLong())
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            else -> {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                ) {
                    val lyricsViewportOffset =
                        remember(maxHeight) {
                            val proportional = maxHeight * 0.16f
                            if (proportional > 112.dp) proportional else 112.dp
                        }

                    key(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalTextStyle provides phoneticTextStyle,
                        ) {
                            // Native replacement for the accompanist karaoke view:
                            // one LazyColumn of dimmed lines whose active entry is
                            // swept word-by-word on a draw-phase clock.
                            NativeLyricsView(
                                lines = syncedLines,
                                listState = listState,
                                activeLineIndex = currentLineIndexState.intValue,
                                position = playbackSyncPosition,
                                userScrolling = isManualScrolling || isSelectionModeActive,
                                onLineClicked = { line ->
                                    if (isSelectionModeActive) {
                                        toggleSelectedLine(line.selectionKey())
                                    } else if (lyricsClick && isSynced && line.startMs > 0) {
                                        player.seekTo(line.startMs.toLong())
                                    }
                                },
                                onLinePressed = { line ->
                                    val lineKey = line.selectionKey()
                                    if (!isSelectionModeActive) {
                                        isSelectionModeActive = true
                                        if (!selectedLineKeys.contains(lineKey)) {
                                            selectedLineKeys.add(lineKey)
                                        }
                                    } else if (!selectedLineKeys.contains(lineKey)) {
                                        toggleSelectedLine(lineKey)
                                    }
                                },
                                textColor = textColor,
                                normalTextStyle = normalTextStyle,
                                accompanimentTextStyle = accompanimentTextStyle,
                                translationTextStyle = translationTextStyle,
                                phoneticTextStyle = phoneticTextStyle,
                                useBlurEffect = lyricsLineBlur && !animationsDisabled,
                                showTranslation = showTranslations,
                                showPhonetic = showPhoneticLines,
                                anchorTopPadding = lyricsViewportOffset,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

    }

    if (isSelectionModeActive && selectionLines.isNotEmpty()) {
        LyricsSelectionBottomSheet(
            lines = selectionLines,
            selectedLineKeys = selectedLineKeySet,
            onToggleLine = toggleSelectedLine,
            onDismissRequest = dismissSelection,
            onShareSelected = shareSelectedLyrics,
        )
    }

    if (showShareDialog && shareDialogData != null) {
        val sharePayload = shareDialogData!!
        val lyricsText = sharePayload.lyricsText
        val songTitle = sharePayload.songTitle
        val artists = sharePayload.artists
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = LyricsSharePayload(lyricsText, songTitle, artists),
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = shareDialogData!!,
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

@Immutable
private data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

@Immutable
private data class PlainLyricLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
    val isMetadata: Boolean = false,
)

@Immutable
private data class LyricSelectionLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
    val translation: String? = null,
    val romanisation: String? = null,
)

private fun NativeLyricLine.shareTranslationParts(): Pair<String?, String?> {
    val raw = translation?.trim()?.takeIf { it.isNotEmpty() } ?: return null to null
    val parts = raw.split("\n\n")
    if (parts.size >= 2) {
        return parts.first().trim() to parts.last().trim()
    }
    val compactParts = raw.split('\n')
    if (compactParts.size >= 2) {
        return compactParts.first().trim() to compactParts.last().trim()
    }
    return null to raw
}

@Composable
private fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedLineKeys: Set<String>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding =
        remember {
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines.items,
            key = { line -> line.itemId },
            contentType = { "plain_lyric_line" },
        ) { line ->
            PlainLyricLineItem(
                line = line,
                selected = line.selectionId in selectedLineKeys,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
            )
        }
    }
}

@Composable
private fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            textColor
        }

    Text(
        text = line.text,
        style =
            if (line.isMetadata) {
                textStyle.copy(
                    fontSize = (textStyle.fontSize.value * 0.38f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            } else {
                textStyle
            },
        color = contentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(
                    onClick = { onLineClicked(line.selectionId) },
                    onLongClick = { onLinePressed(line.selectionId) },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LyricsSelectionBottomSheet(
    lines: List<LyricSelectionLine>,
    selectedLineKeys: Set<String>,
    onToggleLine: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onShareSelected: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstSelectedIndex =
        remember(lines, selectedLineKeys) {
            lines
                .indexOfFirst { line -> line.selectionId in selectedLineKeys }
                .coerceAtLeast(0)
        }
    val sheetListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = firstSelectedIndex,
        )
    val selectedCount = selectedLineKeys.size

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        KeepStatusBarHiddenInDialog()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_element, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.close))
                }
            }

            LazyColumn(
                state = sheetListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = lines,
                    key = { line -> line.itemId },
                    contentType = { "lyric_selection_line" },
                ) { line ->
                    LyricsSelectionLineItem(
                        line = line,
                        selected = line.selectionId in selectedLineKeys,
                        onClick = { onToggleLine(line.selectionId) },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = onShareSelected,
                    enabled = selectedCount > 0,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.share_selected))
                }
            }
        }
    }
}

@Composable
private fun LyricsSelectionLineItem(
    line: LyricSelectionLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun NativeLyricsView(
    lines: List<NativeLyricLine>,
    listState: LazyListState,
    activeLineIndex: Int,
    position: () -> Int,
    userScrolling: Boolean,
    onLineClicked: (NativeLyricLine) -> Unit,
    onLinePressed: (NativeLyricLine) -> Unit,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    translationTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    useBlurEffect: Boolean,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    anchorTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchorTopPx = with(density) { anchorTopPadding.roundToPx() }

    // Auto-scroll: the active line is pinned anchorTopPadding below the top
    // of the viewport, and the pinning pauses while the user scrolls or picks
    // lines for sharing (the outer nested-scroll connection re-enables it).
    LaunchedEffect(activeLineIndex, userScrolling, lines) {
        if (userScrolling || lines.isEmpty()) return@LaunchedEffect
        val target = activeLineIndex.coerceIn(0, lines.lastIndex)
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible != null && abs(visible.offset - anchorTopPx) > 1) {
            listState.animateScrollBy(
                (visible.offset - anchorTopPx).toFloat(),
                spring(dampingRatio = NATIVE_SCROLL_DAMPING, stiffness = NATIVE_SCROLL_STIFFNESS),
            )
        } else if (visible == null) {
            runCatching { listState.animateScrollToItem(target, -anchorTopPx) }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        // Enough bottom padding that the last line can still be pinned to the
        // anchor instead of stopping at the viewport bottom.
        val bottomSafePadding = maxHeight - anchorTopPadding + 48.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = anchorTopPadding,
                    bottom = bottomSafePadding,
                ),
            verticalArrangement = Arrangement.spacedBy(NativeLineSpacing),
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.startMs}-${line.endMs}-$index" },
            ) { index, line ->
                NativeLyricsLineItem(
                    line = line,
                    isActive = index == activeLineIndex,
                    signedDistance = index - activeLineIndex,
                    position = position,
                    textColor = textColor,
                    normalTextStyle = normalTextStyle,
                    accompanimentTextStyle = accompanimentTextStyle,
                    translationTextStyle = translationTextStyle,
                    phoneticTextStyle = phoneticTextStyle,
                    useBlurEffect = useBlurEffect,
                    showTranslation = showTranslation,
                    showPhonetic = showPhonetic,
                    onLineClicked = { onLineClicked(line) },
                    onLinePressed = { onLinePressed(line) },
                )
            }
        }
    }
}

@Composable
private fun NativeLyricsLineItem(
    line: NativeLyricLine,
    isActive: Boolean,
    signedDistance: Int,
    position: () -> Int,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    translationTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    useBlurEffect: Boolean,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
) {
    if (line.isMetadata) {
        // Provider header / composer footer bookends: small centred captions
        // that never take part in the sweep.
        Text(
            text = line.text,
            style =
                normalTextStyle.copy(
                    fontSize = (normalTextStyle.fontSize.value * 0.38f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            color = textColor.copy(alpha = 0.6f),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onLineClicked,
                        onLongClick = onLinePressed,
                    ),
        )
        return
    }

    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) NATIVE_ACTIVE_ALPHA else NATIVE_INACTIVE_ALPHA,
        animationSpec = tween(durationMillis = NATIVE_LINE_OPACITY_MS, easing = FastOutSlowInEasing),
        label = "native-line-alpha",
    )
    val blurRadius by animateDpAsState(
        targetValue =
            if (useBlurEffect && signedDistance != 0) {
                (nativeBlurEm(signedDistance) * normalTextStyle.fontSize.value).dp
            } else {
                0.dp
            },
        animationSpec = tween(durationMillis = NATIVE_BLUR_FADE_MS, easing = FastOutSlowInEasing),
        label = "native-line-blur",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = lineAlpha }
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                .combinedClickable(
                    onClick = onLineClicked,
                    onLongClick = onLinePressed,
                )
                .padding(vertical = 2.dp),
    ) {
        // Backing vocals above the lead line, Apple-Music style; the TTML
        // agent marks "v2" lines, which belong on the opposite edge.
        line.backgroundText()?.let { backgroundText ->
            Text(
                text = backgroundText,
                style = accompanimentTextStyle,
                color = textColor.copy(alpha = 0.45f),
                textAlign = if (line.rightAlignBackground) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (isActive && line.isWordSynced) {
            SweptLyricText(
                line = line,
                position = position,
                style = normalTextStyle,
                brightColor = textColor,
            )
        } else {
            Text(
                text = line.text,
                style = if (isActive) normalTextStyle else normalTextStyle.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
            )
        }

        if (showTranslation) {
            TranslationStack(
                translation = line.translation,
                textColor = textColor,
                subLineStyle = translationTextStyle,
            )
        }

        val phoneticRow = line.phoneticRow()
        if (showPhonetic && !phoneticRow.isNullOrBlank()) {
            Text(
                text = phoneticRow,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private suspend fun LazyListState.awaitKaraokeFirstFocus(index: Int) {
    var framesAwaited = 0
    var emptyFrames = 0
    while (framesAwaited < LYRIC_FIRST_FOCUS_SETTLE_FRAMES) {
        withFrameNanos { }
        framesAwaited++
        if (layoutInfo.totalItemsCount == 0) {
            emptyFrames++
            if (emptyFrames >= LYRIC_FIRST_FOCUS_EMPTY_FRAMES) return
        } else {
            emptyFrames = 0
            val itemInfo =
                layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == index }
            if (itemInfo != null && !isScrollInProgress) return
        }
    }
}

private fun NativeLyricLine.selectionKey(text: String = this.text): String = "$startMs:$endMs:${text.hashCode()}"

private fun List<NativeLyricLine>.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (this[mid].startMs < 0) {
            low = mid + 1
        } else if (this[mid].startMs <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

private fun List<WordTimestamp>.toNativeWords(phonetics: List<String?>): List<NativeLyricWord> =
    mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val nextStart = getOrNull(index + 1)?.startTime?.toMilliseconds()
        val rawEnd = word.endTime.toMilliseconds()
        val end =
            nextStart
                ?.let { minOf(rawEnd, it) }
                ?: rawEnd

        // Word text is trimmed so the sweep can locate each word inside the
        // line text by substring, the way BitChordLyrics maps its words.
        NativeLyricWord(
            startMs = start.toLong(),
            endMs = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS).toLong(),
            text = word.text.trim(),
            phonetic = phonetics.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

private fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

private fun aiRomanizationMap(
    entries: List<LyricsEntry>,
    aiLines: List<String?>,
): Map<Int, List<String?>> {
    if (aiLines.isEmpty() || entries.isEmpty()) return emptyMap()
    val map = mutableMapOf<Int, List<String?>>()
    entries.forEachIndexed { index, entry ->
        val romanized = aiLines.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
        val words = entry.words?.filter { !it.isBackground }
        map[index] =
            if (!words.isNullOrEmpty()) {
                distributePhonetics(words.map { it.text }, romanized)
            } else {
                listOf(romanized)
            }
    }
    return map
}

private fun distributePhonetics(
    words: List<String>,
    romanized: String,
): List<String?> {
    if (words.isEmpty()) return emptyList()
    val tokens = romanized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return List(words.size) { null }
    if (words.size == 1) return listOf(tokens.joinToString(" "))

    val out = MutableList<String?>(words.size) { null }
    tokens.forEachIndexed { tokenIndex, token ->
        val anchor = (tokenIndex * words.size / tokens.size).coerceIn(0, words.size - 1)
        out[anchor] = listOfNotNull(out[anchor], token).joinToString(" ")
    }
    return out
}

private fun buildNativeLyricLines(
    entries: List<LyricsEntry>,
    isWordSynced: Boolean,
    romanizationMap: Map<Int, List<String?>>,
    providerHeader: String?,
    composerFooter: String? = null,
    compactTranslation: Boolean = false,
): List<NativeLyricLine> {
    if (entries.isEmpty()) return emptyList()
    val lines = mutableListOf<NativeLyricLine>()

    if (!providerHeader.isNullOrBlank()) {
        lines.add(
            NativeLyricLine(
                startMs = -2,
                endMs = -1,
                text = providerHeader,
                isMetadata = true,
            ),
        )
    }

    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        if (isWordSynced && entry.words != null) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words!!.filter { !it.isBackground }
            val bgWords = entry.words!!.filter { it.isBackground }
            // The TTML agent marks backing-vocal lines as "v2", which belong
            // on the opposite edge from the lead line.
            val rightAlignBackground = entry.agent?.lowercase() == "v2"

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words!!
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val nativeWords = wordsForMain.toNativeWords(wordPhonetics)
            if (nativeWords.isEmpty()) return@forEachIndexed

            val lineStart = nativeWords.first().startMs.toInt()
            val lineEnd = nativeWords.last().endMs.toInt()
            if (lineEnd <= lineStart) return@forEachIndexed

            // Some word-synced sources carry timing-only lines whose text is
            // blank; the sweep needs a string to walk, so fall back to the
            // words themselves.
            val lineText = entry.text.takeIf { it.isNotBlank() } ?: nativeWords.joinWordsWithLatinGaps()

            val backgroundWords =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    bgWords.toNativeWords(emptyList())
                } else {
                    emptyList()
                }

            lines.add(
                NativeLyricLine(
                    startMs = lineStart,
                    endMs = lineEnd,
                    text = lineText,
                    words = nativeWords,
                    backgroundWords = backgroundWords,
                    rightAlignBackground = rightAlignBackground,
                    translation = translation,
                ),
            )
        } else {
            val nextEntry = entries.getOrNull(index + 1)

            val lineEnd =
                if (nextEntry != null && nextEntry.time > entry.time) {
                    nextEntry.time.toInt()
                } else {
                    (entry.time + LINE_SYNCED_TRAILING_LINE_DURATION_MS).toInt()
                }
            lines.add(
                buildLineSyncedNativeLine(
                    entry = entry,
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                    compactTranslation = compactTranslation,
                ),
            )
        }
    }

    if (!composerFooter.isNullOrBlank()) {
        lines.add(
            NativeLyricLine(
                startMs = 86_400_000,
                endMs = 86_400_001,
                text = composerFooter,
                isMetadata = true,
            ),
        )
    }

    return lines
}

private fun buildLineSyncedNativeLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
    compactTranslation: Boolean = false,
): NativeLyricLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    // The romanization and the translation share one sub-line slot: two
    // paragraphs when there is room, one compact row when singleActiveLine
    // needs the cluster short. shareTranslationParts() splits them back apart.
    val combinedTranslation =
        when {
            normalizedRomanizedText == null -> translation
            translation.isNullOrBlank() -> normalizedRomanizedText
            compactTranslation -> "$normalizedRomanizedText\n$translation"
            else -> "$normalizedRomanizedText\n\n$translation"
        }

    return NativeLyricLine(
        startMs = start,
        endMs = end,
        text = entry.text,
        translation = combinedTranslation,
    )
}

@Composable
private fun SingleActiveLyricLine(
    activeLine: NativeLyricLine?,
    currentPosition: () -> Int,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    showTranslation: Boolean,
    onLineClick: (NativeLyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = activeLine,
        transitionSpec = {
            (fadeIn(tween(durationMillis = 240, easing = LinearEasing)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 5 },
                )).togetherWith(fadeOut(tween(durationMillis = 160, easing = LinearEasing)))
        },
        contentAlignment = Alignment.BottomStart,
        label = "single-active-lyric-line",
        modifier = modifier,
    ) { line ->
        if (line != null) {
            SingleActiveLineCluster(
                line = line,
                currentPosition = currentPosition,
                textColor = textColor,
                normalTextStyle = normalTextStyle,
                accompanimentTextStyle = accompanimentTextStyle,
                phoneticTextStyle = phoneticTextStyle,
                showTranslation = showTranslation,
                onLineClick = onLineClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SingleActiveLineCluster(
    line: NativeLyricLine,
    currentPosition: () -> Int,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    showTranslation: Boolean,
    onLineClick: (NativeLyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.clickable(
                enabled = line.startMs > 0,
            ) { onLineClick(line) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Backing vocals first, then the romanization row, mirroring the
        // lead/backing layout the accompanist view drew.
        line.backgroundText()?.let { backgroundText ->
            if (backgroundText.isNotBlank()) {
                Text(
                    text = backgroundText,
                    style = accompanimentTextStyle,
                    color = textColor.copy(alpha = 0.45f),
                    textAlign = if (line.rightAlignBackground) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val phonetic = line.phoneticRow()
        if (!phonetic.isNullOrBlank()) {
            Text(
                text = phonetic,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (line.isWordSynced) {
            SweptLyricText(
                line = line,
                position = currentPosition,
                style = normalTextStyle,
                brightColor = textColor,
            )
        } else {
            Text(
                text = line.text,
                style = normalTextStyle,
                color = textColor,
            )
        }

        if (showTranslation) {
            TranslationStack(
                translation = line.translation,
                textColor = textColor,
                subLineStyle = phoneticTextStyle,
            )
        }
    }
}

@Composable
private fun SweptLyricText(
    line: NativeLyricLine,
    position: () -> Int,
    style: TextStyle,
    brightColor: Color,
    modifier: Modifier = Modifier,
) {
    // BitChordLyrics' sweep: the unsung base stays dim while a second,
    // full-bright copy is revealed by clipping to the character the clock has
    // sung through. Reading the position inside the draw block keeps the wipe
    // frame-accurate without recomposing the line every frame.
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier) {
        Text(
            text = line.text,
            style = style,
            color = brightColor.copy(alpha = NATIVE_UNSUNG_ALPHA),
            onTextLayout = { layout = it },
        )
        Text(
            text = line.text,
            style = style,
            color = brightColor,
            modifier =
                Modifier.drawWithContent {
                    val positionMs = position().toLong()
                    when {
                        positionMs >= line.endMs.toLong() -> drawContent()
                        positionMs <= line.startMs.toLong() -> Unit
                        else -> layout?.let { sweepTo(it, line.revealedChars(positionMs)) }
                    }
                },
        )
    }
}

private fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealedChars: Float) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)

        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val right =
            if (revealedChars >= end) {
                layout.getLineRight(visualLine)
            } else {
                horizontalAt(layout, revealedChars, start, end)
            }
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = layout.getLineTop(visualLine),
            right = right,
            bottom = layout.getLineBottom(visualLine),
        ) {
            this@sweepTo.drawContent()
        }
    }
}

private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    lineStart: Int,
    lineEnd: Int,
): Float {
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val next =
        layout.getHorizontalPosition(
            (index + 1).coerceAtMost(lineEnd),
            usePrimaryDirection = true,
        )
    return here + (next - here) * (chars - index)
}

@Composable
private fun TranslationStack(
    translation: String?,
    textColor: Color,
    subLineStyle: TextStyle,
) {
    if (translation.isNullOrBlank()) return
    translation
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { row ->
            Text(
                text = row,
                style = subLineStyle,
                color = textColor.copy(alpha = 0.85f),
            )
        }
}

private fun needsLatinWordGap(previous: String, next: String): Boolean {
    val prevTail = previous.lastOrNull()
    val nextHead = next.firstOrNull()
    return isLatinWordChar(prevTail) && isLatinWordChar(nextHead)
}

private fun isLatinWordChar(ch: Char?): Boolean {
    if (ch == null) return false
    return ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '\'' || ch == ','
}

private fun List<NativeLyricWord>.joinWordsWithLatinGaps(): String {
    val builder = StringBuilder()
    forEachIndexed { index, word ->
        builder.append(word.text)
        val next = getOrNull(index + 1)
        if (next != null && needsLatinWordGap(word.text, next.text)) {
            builder.append(' ')
        }
    }
    return builder.toString()
}
