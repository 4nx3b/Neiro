package com.music.bitchord.ui.player

import android.content.Context
import com.music.bitchord.data.settings.AppFont
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.BackAnimationStyle
import com.music.bitchord.data.settings.DefaultOpenTab
import com.music.bitchord.data.settings.LiquidGlassIntensity
import com.music.bitchord.data.settings.LyricsBackgroundStyle
import com.music.bitchord.data.settings.MiniPlayerBackgroundStyle
import com.music.bitchord.data.settings.PlayerButtonsStyle
import com.music.bitchord.data.settings.PlayerDesignStyle
import com.music.bitchord.data.settings.PlayerSliderStyle
import com.music.bitchord.data.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.FlamingoBackgroundEffectKey
import moe.rukamori.archivetune.constants.FlamingoHideSlideBarKey
import moe.rukamori.archivetune.constants.FlamingoShowVolumeBarKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.LiquidGlassAdaptiveLuminanceKey
import moe.rukamori.archivetune.constants.LiquidGlassBackdropVibrancyKey
import moe.rukamori.archivetune.constants.LiquidGlassBlurRadiusKey
import moe.rukamori.archivetune.constants.LiquidGlassChromaticAberrationKey
import moe.rukamori.archivetune.constants.LiquidGlassDepth3DKey
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.constants.LiquidGlassIntensityKey
import moe.rukamori.archivetune.constants.LiquidGlassRefractionAmountKey
import moe.rukamori.archivetune.constants.LiquidGlassRefractionHeightKey
import moe.rukamori.archivetune.constants.LiquidGlassShadowDepthKey
import moe.rukamori.archivetune.constants.LiquidGlassTintOpacityKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.TikTokMainLyricsEnabledKey
import moe.rukamori.archivetune.constants.AutomixPerformanceModeKey
import moe.rukamori.archivetune.constants.BackAnimationStyleKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CustomFontNameKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HideStatusBarKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.MinimalHomeModeKey
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.TabletModeEnabledKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.constants.UiScaleFactorKey
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.utils.BridgedPreferences
import moe.rukamori.archivetune.utils.mirrorBridgedPreferences
import moe.rukamori.archivetune.constants.AppFontPreference as AtFont
import moe.rukamori.archivetune.constants.AutomixPerformanceMode as AtAutomix
import moe.rukamori.archivetune.constants.LiquidGlassIntensity as AtGlassIntensity
import moe.rukamori.archivetune.constants.BackAnimationStyle as AtBackAnimation
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle as AtLyricsBackground
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle as AtMiniPlayerBackground
import moe.rukamori.archivetune.constants.PlayerButtonsStyle as AtButtons
import moe.rukamori.archivetune.constants.PlayerDesignStyle as AtStyle
import moe.rukamori.archivetune.constants.SliderStyle as AtSlider

/**
 * One source of truth for every setting Neiro and the ported ArchiveTune
 * stack both have: each key below reads, writes and observes Neiro's own
 * AppSettings, so a change on ArchiveTune's settings pages shows in Neiro's
 * and the reverse. Installed once, at app start (BitChordApplication), so
 * the pages are bridged whichever player style is active. The full key map,
 * including the keys left local and why, is docs/archivetune-settings-map.md.
 *
 * The mappings are explicit, both ways, and JVM-tested
 * (ArchiveTunePreferenceBridgeTest). Where one side has a value the other
 * lacks, the nearest equivalent is chosen and noted.
 */
object ArchiveTunePreferenceBridge {
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        enumKey<PlayerDesignStyle, AtStyle>(PlayerDesignStyleKey, AppSettings.playerDesignStyle, { toAt(it) }) { AppSettings.setPlayerDesignStyle(fromAt(it)) }
        enumKey<PlayerSliderStyle, AtSlider>(SliderStyleKey, AppSettings.playerSliderStyle, { toAt(it) }) { AppSettings.setPlayerSliderStyle(fromAt(it)) }
        enumKey<PlayerButtonsStyle, AtButtons>(PlayerButtonsStyleKey, AppSettings.playerButtonsStyle, { toAt(it) }) { AppSettings.setPlayerButtonsStyle(fromAt(it)) }
        enumKey<ThemeMode, DarkMode>(DarkModeKey, AppSettings.themeMode, { toAt(it) }) { AppSettings.setThemeMode(fromAt(it)) }
        enumKey<AppFont, AtFont>(FontPreferenceKey, AppSettings.appFont, { toAt(it) }) { AppSettings.setAppFont(fromAt(it)) }
        enumKey<BackAnimationStyle, AtBackAnimation>(BackAnimationStyleKey, AppSettings.backAnimationStyle, { toAt(it) }) { AppSettings.setBackAnimationStyle(fromAt(it)) }
        enumKey<MiniPlayerBackgroundStyle, AtMiniPlayerBackground>(MiniPlayerBackgroundStyleKey, AppSettings.miniPlayerBackgroundStyle, { toAt(it) }) {
            AppSettings.setMiniPlayerBackgroundStyle(fromAt(it))
        }
        enumKey<LyricsBackgroundStyle, AtLyricsBackground>(LyricsBackgroundStyleKey, AppSettings.lyricsBackgroundStyle, { toAt(it) }) {
            AppSettings.setLyricsBackgroundStyle(fromAt(it))
        }
        enumKey<AutomixPerformanceMode, AtAutomix>(AutomixPerformanceModeKey, AppSettings.automixPerformanceMode, { toAt(it) }) {
            AppSettings.setAutomixPerformanceMode(fromAt(it))
        }
        enumKey<DefaultOpenTab, NavigationTab>(DefaultOpenTabKey, AppSettings.defaultOpenTab, { toAt(it) }) { AppSettings.setDefaultOpenTab(fromAt(it)) }

        BridgedPreferences.bridge(
            key = ThumbnailCornerRadiusKey,
            read = { AppSettings.thumbnailCornerRadius.value },
            write = AppSettings::setThumbnailCornerRadius,
            changes = AppSettings.thumbnailCornerRadius.distinct(),
        )
        BridgedPreferences.bridge(
            key = DisableAnimationsKey,
            read = { AppSettings.reduceAnimation.value },
            write = AppSettings::setReduceAnimation,
            changes = AppSettings.reduceAnimation.distinct(),
        )
        BridgedPreferences.bridge(
            key = HideStatusBarKey,
            read = { AppSettings.hideStatusBar.value },
            write = AppSettings::setHideStatusBar,
            changes = AppSettings.hideStatusBar.distinct(),
        )
        BridgedPreferences.bridge(
            key = UiScaleFactorKey,
            read = { AppSettings.uiScale.value },
            write = AppSettings::setUiScale,
            changes = AppSettings.uiScale.distinct(),
        )
        BridgedPreferences.bridge(
            key = TabletModeEnabledKey,
            read = { AppSettings.tabletMode.value },
            write = AppSettings::setTabletMode,
            changes = AppSettings.tabletMode.distinct(),
        )
        BridgedPreferences.bridge(
            key = MinimalHomeModeKey,
            read = { AppSettings.minimalHomeMode.value },
            write = AppSettings::setMinimalHomeMode,
            changes = AppSettings.minimalHomeMode.distinct(),
        )
        BridgedPreferences.bridge(
            key = SkipSilenceKey,
            read = { AppSettings.skipSilence.value },
            write = AppSettings::setSkipSilence,
            changes = AppSettings.skipSilence.distinct(),
        )
        BridgedPreferences.bridge(
            key = StopMusicOnTaskClearKey,
            read = { AppSettings.stopOnTaskRemoved.value },
            write = AppSettings::setStopOnTaskRemoved,
            changes = AppSettings.stopOnTaskRemoved.distinct(),
        )
        // ArchiveTune asks "show the volume bar", Neiro "hide" it.
        BridgedPreferences.bridge(
            key = ShowPlayerVolumeBarKey,
            read = { !AppSettings.hideVolumeBar.value },
            write = { AppSettings.setHideVolumeBar(!it) },
            changes = AppSettings.hideVolumeBar.map { !it }.distinctUntilChanged(),
        )
        // The custom font file: Neiro writes name and uri together.
        BridgedPreferences.bridge(
            key = CustomFontUriKey,
            read = { AppSettings.customFontUri.value },
            write = { AppSettings.setCustomFont(AppSettings.customFontName.value, it) },
            changes = AppSettings.customFontUri.distinct(),
        )
        BridgedPreferences.bridge(
            key = CustomFontNameKey,
            read = { AppSettings.customFontName.value },
            write = { AppSettings.setCustomFont(it, AppSettings.customFontUri.value) },
            changes = AppSettings.customFontName.distinct(),
        )

        // Crossfade: ArchiveTune keeps a switch and a length, Neiro one length
        // in seconds where 0 means off. The length last chosen comes back
        // when the switch is turned on again.
        BridgedPreferences.bridge(
            key = CrossfadeEnabledKey,
            read = { AppSettings.crossfadeSeconds.value > 0 },
            write = { on ->
                val now = AppSettings.crossfadeSeconds.value
                if (on && now == 0) AppSettings.setCrossfadeSeconds(lastCrossfadeSeconds)
                if (!on && now > 0) {
                    lastCrossfadeSeconds = now
                    AppSettings.setCrossfadeSeconds(0)
                }
            },
            changes = AppSettings.crossfadeSeconds.map { it > 0 }.distinctUntilChanged(),
        )
        BridgedPreferences.bridge(
            key = CrossfadeDurationKey,
            read = { crossfadeLength(AppSettings.crossfadeSeconds.value, lastCrossfadeSeconds) },
            write = { length ->
                val seconds = length.roundToInt().coerceAtLeast(1)
                lastCrossfadeSeconds = seconds
                if (AppSettings.crossfadeSeconds.value > 0) AppSettings.setCrossfadeSeconds(seconds)
            },
            changes = AppSettings.crossfadeSeconds.map { crossfadeLength(it, lastCrossfadeSeconds) }.distinctUntilChanged(),
        )

        // ---- Twins Neiro already has a row for: one setting, Neiro's row. ----
        boolKey(HidePlayerThumbnailKey, AppSettings.hidePlayerThumbnail, AppSettings::setHidePlayerThumbnail)
        boolKey(TikTokMainLyricsEnabledKey, AppSettings.tikTokMainLyrics, AppSettings::setTikTokMainLyrics)
        boolKey(LyricsLineBlurKey, AppSettings.lyricsBlur, AppSettings::setLyricsBlur)
        // ArchiveTune's own animated canvas ↔ Neiro's "Animated cover art".
        boolKey(ArchiveTuneCanvasKey, AppSettings.animatedCanvas, AppSettings::setAnimatedCanvas)
        boolKey(FlamingoHideSlideBarKey, AppSettings.hideSlideBar, AppSettings::setHideSlideBar)
        boolKey(FlamingoBackgroundEffectKey, AppSettings.movingBlur, AppSettings::setMovingBlur)
        boolKey(CropThumbnailToSquareKey, AppSettings.squareVideoThumbnails, AppSettings::setSquareVideoThumbnails)
        boolKey(DisableBlurKey, AppSettings.reduceDynamicBlur, AppSettings::setReduceDynamicBlur)
        // Flamingo asks "show the volume bar", Neiro "hide" it — as the
        // player-wide ShowPlayerVolumeBarKey above.
        BridgedPreferences.bridge(
            key = FlamingoShowVolumeBarKey,
            read = { !AppSettings.hideVolumeBar.value },
            write = { AppSettings.setHideVolumeBar(!it) },
            changes = AppSettings.hideVolumeBar.map { !it }.distinctUntilChanged(),
        )
        // The full-cover blur, in dp on both sides (0..64, 48 by default).
        floatKey(BlurRadiusKey, AppSettings.blurIntensity, AppSettings::setBlurIntensity)
        // Liquid Glass: the same tuning, the same units and defaults on both sides.
        boolKey(LiquidGlassEnabledKey, AppSettings.liquidGlass, AppSettings::setLiquidGlass)
        enumKey<LiquidGlassIntensity, AtGlassIntensity>(LiquidGlassIntensityKey, AppSettings.liquidGlassIntensity, { toAt(it) }) {
            AppSettings.setLiquidGlassIntensity(fromAt(it))
        }
        floatKey(LiquidGlassRefractionHeightKey, AppSettings.liquidGlassRefractionHeight, AppSettings::setLiquidGlassRefractionHeight)
        floatKey(LiquidGlassRefractionAmountKey, AppSettings.liquidGlassRefractionAmount, AppSettings::setLiquidGlassRefractionAmount)
        floatKey(LiquidGlassBlurRadiusKey, AppSettings.liquidGlassBlurRadius, AppSettings::setLiquidGlassBlurRadius)
        floatKey(LiquidGlassTintOpacityKey, AppSettings.liquidGlassTintOpacity, AppSettings::setLiquidGlassTintOpacity)
        floatKey(LiquidGlassShadowDepthKey, AppSettings.liquidGlassShadowDepth, AppSettings::setLiquidGlassShadowDepth)
        boolKey(LiquidGlassDepth3DKey, AppSettings.liquidGlassDepth3D, AppSettings::setLiquidGlassDepth3D)
        boolKey(LiquidGlassChromaticAberrationKey, AppSettings.liquidGlassChromaticAberration, AppSettings::setLiquidGlassChromaticAberration)
        boolKey(LiquidGlassBackdropVibrancyKey, AppSettings.liquidGlassBackdropVibrancy, AppSettings::setLiquidGlassBackdropVibrancy)
        boolKey(LiquidGlassAdaptiveLuminanceKey, AppSettings.liquidGlassAdaptiveLuminance, AppSettings::setLiquidGlassAdaptiveLuminance)

        // Vendored code that reads `dataStore.data` flows (not
        // rememberPreference) sees the bridged values too.
        mirrorBridgedPreferences(context.applicationContext)
    }

    /** ArchiveTune's default length, until one is chosen. */
    @Volatile
    private var lastCrossfadeSeconds = 5

    /** The length ArchiveTune's slider shows: the live one, or the last one while off. */
    fun crossfadeLength(seconds: Int, last: Int): Float = (if (seconds > 0) seconds else last).toFloat()

    private fun boolKey(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        source: kotlinx.coroutines.flow.StateFlow<Boolean>,
        write: (Boolean) -> Unit,
    ) {
        BridgedPreferences.bridge(key = key, read = { source.value }, write = write, changes = source.distinct())
    }

    private fun floatKey(
        key: androidx.datastore.preferences.core.Preferences.Key<Float>,
        source: kotlinx.coroutines.flow.StateFlow<Float>,
        write: (Float) -> Unit,
    ) {
        BridgedPreferences.bridge(key = key, read = { source.value }, write = write, changes = source.distinct())
    }

    private fun <T> Flow<T>.distinct(): Flow<T> = distinctUntilChanged()

    private fun <N, A : Enum<A>> enumKey(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        source: kotlinx.coroutines.flow.StateFlow<N>,
        toAt: (N) -> A,
        write: (A) -> Unit,
    ) {
        BridgedPreferences.bridgeEnum(
            key = key,
            read = { toAt(source.value) },
            write = write,
            changes = source.map { toAt(it) }.distinctUntilChanged(),
        )
    }

    // ---- Player design style: ArchiveTune's V-names ↔ Neiro's descriptive names.

    fun toAt(style: PlayerDesignStyle): AtStyle =
        when (style) {
            PlayerDesignStyle.BITCHORD -> AtStyle.BITCHORD
            PlayerDesignStyle.MATERIAL_EXTENDED -> AtStyle.V9
            PlayerDesignStyle.APPLE_MUSIC -> AtStyle.APPLE_MUSIC
            PlayerDesignStyle.TIKTOK -> AtStyle.TIKTOK
        }

    fun fromAt(style: AtStyle): PlayerDesignStyle =
        when (style) {
            AtStyle.BITCHORD -> PlayerDesignStyle.BITCHORD
            AtStyle.V9 -> PlayerDesignStyle.MATERIAL_EXTENDED
            AtStyle.APPLE_MUSIC -> PlayerDesignStyle.APPLE_MUSIC
            AtStyle.TIKTOK -> PlayerDesignStyle.TIKTOK
        }

    // ---- Seek bar.

    fun toAt(style: PlayerSliderStyle): AtSlider =
        when (style) {
            PlayerSliderStyle.STANDARD -> AtSlider.Standard
            PlayerSliderStyle.WAVY -> AtSlider.Wavy
            PlayerSliderStyle.THICK -> AtSlider.Thick
            PlayerSliderStyle.CIRCULAR -> AtSlider.Circular
            PlayerSliderStyle.SIMPLE -> AtSlider.Simple
        }

    fun fromAt(style: AtSlider): PlayerSliderStyle =
        when (style) {
            AtSlider.Standard -> PlayerSliderStyle.STANDARD
            AtSlider.Wavy -> PlayerSliderStyle.WAVY
            AtSlider.Thick -> PlayerSliderStyle.THICK
            AtSlider.Circular -> PlayerSliderStyle.CIRCULAR
            AtSlider.Simple -> PlayerSliderStyle.SIMPLE
        }

    // ---- Player buttons: same names on both sides.

    fun toAt(style: PlayerButtonsStyle): AtButtons =
        when (style) {
            PlayerButtonsStyle.DEFAULT -> AtButtons.DEFAULT
            PlayerButtonsStyle.SECONDARY -> AtButtons.SECONDARY
        }

    fun fromAt(style: AtButtons): PlayerButtonsStyle =
        when (style) {
            AtButtons.DEFAULT -> PlayerButtonsStyle.DEFAULT
            AtButtons.SECONDARY -> PlayerButtonsStyle.SECONDARY
        }

    // ---- Dark mode: ArchiveTune ON/OFF/AUTO ↔ Neiro DARK/LIGHT/SYSTEM.

    fun toAt(mode: ThemeMode): DarkMode =
        when (mode) {
            ThemeMode.SYSTEM -> DarkMode.AUTO
            ThemeMode.LIGHT -> DarkMode.OFF
            ThemeMode.DARK -> DarkMode.ON
        }

    fun fromAt(mode: DarkMode): ThemeMode =
        when (mode) {
            DarkMode.AUTO -> ThemeMode.SYSTEM
            DarkMode.OFF -> ThemeMode.LIGHT
            DarkMode.ON -> ThemeMode.DARK
        }

    // ---- Font. Neiro also has LEGACY (its older SF Pro cut), which
    //      ArchiveTune reads as its DEFAULT; picking DEFAULT there keeps it.

    fun toAt(font: AppFont): AtFont =
        when (font) {
            AppFont.DEFAULT, AppFont.LEGACY -> AtFont.DEFAULT
            AppFont.SYSTEM -> AtFont.SYSTEM
            AppFont.CUSTOM -> AtFont.CUSTOM
        }

    fun fromAt(font: AtFont): AppFont =
        when (font) {
            AtFont.DEFAULT -> if (fontIsLegacy()) AppFont.LEGACY else AppFont.DEFAULT
            AtFont.SYSTEM -> AppFont.SYSTEM
            AtFont.CUSTOM -> AppFont.CUSTOM
        }

    /** Separate so the JVM test never reaches AppSettings. */
    @Volatile
    internal var fontIsLegacy: () -> Boolean = { AppSettings.appFont.value == AppFont.LEGACY }

    // ---- Back gesture animation: same names.

    fun toAt(style: BackAnimationStyle): AtBackAnimation =
        when (style) {
            BackAnimationStyle.DEFAULT -> AtBackAnimation.DEFAULT
            BackAnimationStyle.PREDICTIVE -> AtBackAnimation.PREDICTIVE
        }

    fun fromAt(style: AtBackAnimation): BackAnimationStyle =
        when (style) {
            AtBackAnimation.DEFAULT -> BackAnimationStyle.DEFAULT
            AtBackAnimation.PREDICTIVE -> BackAnimationStyle.PREDICTIVE
        }

    // ---- Mini player surface: same names.

    fun toAt(style: MiniPlayerBackgroundStyle): AtMiniPlayerBackground =
        when (style) {
            MiniPlayerBackgroundStyle.THEME -> AtMiniPlayerBackground.THEME
            MiniPlayerBackgroundStyle.GRADIENT -> AtMiniPlayerBackground.GRADIENT
            MiniPlayerBackgroundStyle.GLOW -> AtMiniPlayerBackground.GLOW
            MiniPlayerBackgroundStyle.FROSTED -> AtMiniPlayerBackground.FROSTED
            MiniPlayerBackgroundStyle.LIQUID_GLASS -> AtMiniPlayerBackground.LIQUID_GLASS
        }

    fun fromAt(style: AtMiniPlayerBackground): MiniPlayerBackgroundStyle =
        when (style) {
            AtMiniPlayerBackground.THEME -> MiniPlayerBackgroundStyle.THEME
            AtMiniPlayerBackground.GRADIENT -> MiniPlayerBackgroundStyle.GRADIENT
            AtMiniPlayerBackground.GLOW -> MiniPlayerBackgroundStyle.GLOW
            AtMiniPlayerBackground.FROSTED -> MiniPlayerBackgroundStyle.FROSTED
            AtMiniPlayerBackground.LIQUID_GLASS -> MiniPlayerBackgroundStyle.LIQUID_GLASS
        }

    // ---- Lyrics background. ArchiveTune's CUSTOM (a picked image) has no
    //      Neiro counterpart; it lands on Neiro's DEFAULT.

    fun toAt(style: LyricsBackgroundStyle): AtLyricsBackground =
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> AtLyricsBackground.DEFAULT
            LyricsBackgroundStyle.FOLLOW_THEME -> AtLyricsBackground.FOLLOW_THEME
            LyricsBackgroundStyle.COLORING -> AtLyricsBackground.COLORING
            LyricsBackgroundStyle.MOVING_BLUR -> AtLyricsBackground.MOVING_BLUR
        }

    fun fromAt(style: AtLyricsBackground): LyricsBackgroundStyle =
        when (style) {
            AtLyricsBackground.DEFAULT, AtLyricsBackground.CUSTOM -> LyricsBackgroundStyle.DEFAULT
            AtLyricsBackground.FOLLOW_THEME -> LyricsBackgroundStyle.FOLLOW_THEME
            AtLyricsBackground.COLORING -> LyricsBackgroundStyle.COLORING
            AtLyricsBackground.MOVING_BLUR -> LyricsBackgroundStyle.MOVING_BLUR
        }

    // ---- AutoMix analysis budget: same names.

    fun toAt(mode: AutomixPerformanceMode): AtAutomix =
        when (mode) {
            AutomixPerformanceMode.EFFICIENT -> AtAutomix.EFFICIENT
            AutomixPerformanceMode.BALANCED -> AtAutomix.BALANCED
            AutomixPerformanceMode.PERFORMANCE -> AtAutomix.PERFORMANCE
        }

    fun fromAt(mode: AtAutomix): AutomixPerformanceMode =
        when (mode) {
            AtAutomix.EFFICIENT -> AutomixPerformanceMode.EFFICIENT
            AtAutomix.BALANCED -> AutomixPerformanceMode.BALANCED
            AtAutomix.PERFORMANCE -> AutomixPerformanceMode.PERFORMANCE
        }

    // ---- Liquid Glass intensity tier: same names.

    fun toAt(tier: LiquidGlassIntensity): AtGlassIntensity =
        when (tier) {
            LiquidGlassIntensity.SUBTLE -> AtGlassIntensity.SUBTLE
            LiquidGlassIntensity.STANDARD -> AtGlassIntensity.STANDARD
            LiquidGlassIntensity.VIVID -> AtGlassIntensity.VIVID
        }

    fun fromAt(tier: AtGlassIntensity): LiquidGlassIntensity =
        when (tier) {
            AtGlassIntensity.SUBTLE -> LiquidGlassIntensity.SUBTLE
            AtGlassIntensity.STANDARD -> LiquidGlassIntensity.STANDARD
            AtGlassIntensity.VIVID -> LiquidGlassIntensity.VIVID
        }

    // ---- Tab the app opens on.

    fun toAt(tab: DefaultOpenTab): NavigationTab =
        when (tab) {
            DefaultOpenTab.HOME -> NavigationTab.HOME
            DefaultOpenTab.SEARCH -> NavigationTab.SEARCH
            DefaultOpenTab.LIBRARY -> NavigationTab.LIBRARY
        }

    fun fromAt(tab: NavigationTab): DefaultOpenTab =
        when (tab) {
            NavigationTab.HOME -> DefaultOpenTab.HOME
            NavigationTab.SEARCH -> DefaultOpenTab.SEARCH
            NavigationTab.LIBRARY -> DefaultOpenTab.LIBRARY
        }
}
