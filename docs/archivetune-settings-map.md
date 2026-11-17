# ArchiveTune settings ↔ Neiro: key map

Generated from the ported pages (`archivetuneplayer/.../ui/screens/settings/`) by grepping each
`rememberPreference` / `rememberEnumPreference` key. Columns:

- **Pages**: the ported pages that write the key.
- **Read by the ported stack**: vendored files outside the settings pages that read it.
- **Neiro**: the `AppSettings` value that owns it, if any.
- **Action**:
  - **bridge**: it reads and writes Neiro's value (`ui/player/ArchiveTunePreferenceBridge.kt`, installed once in `BitChordApplication.onCreate`). `rememberPreference`, the `dataStore[key]` getter and the mirrored DataStore all see Neiro's value.
  - **local**: it persists in the module's own DataStore and the ported stack reads it there.
  - **dead**: nothing in this port reads it. The switch is stored, but changes nothing.

Totals: 39 bridged, 29 local, 40 dead.

Pure black: Neiro has no pure-black setting, so `ArchiveTunePlayerHost(pureBlack = false)` in MainActivity stays as it is
until one exists.

| Key | Pages | Read by the ported stack | Neiro | Action |
|---|---|---|---|---|
| `AiRomanizeLyricsKey` | LyricsRomanisationSettings | AiLyricsRomanization, FlamingoPlayer | — | local |
| `AlbumCanvasEnabledKey` | CanvasSettingsScreen | — | — | dead |
| `AppleMusicAnimatedArtworkKey` | AppearanceSettings | — | — | dead |
| `AppleMusicExperienceKey` | AppearanceSettings | — | — | dead |
| `ArchiveTuneCanvasKey` | CanvasSettingsScreen | Player, PlayerMenu, Thumbnail | `AppSettings.animatedCanvas` | **bridge** |
| `ArtistSeparatorsKey` | PlayerSettings | FlamingoPlayer, PlayerMenu | — | local |
| `ArtworkProviderOrderKey` | CanvasSettingsScreen | — | — | dead |
| `AutoHideLyricsPlayerControlsKey` | LyricsSettings | LyricsScreen | — | local |
| `AutoSkipNextOnErrorKey` | PlayerSettings | — | — | dead |
| `AutoStartOnBluetoothKey` | PlayerSettings | — | — | dead |
| `AutomixEnabledKey` | PlayerSettings | — | — | dead |
| `AutomixPerformanceModeKey` | PlayerSettings | — | `AppSettings.automixPerformanceMode` | **bridge** |
| `BackAnimationStyleKey` | AppearanceSettings | — | `AppSettings.backAnimationStyle` | **bridge** |
| `BackdropBlurAmountKey` | AppearanceSettings | Player, Thumbnail | — | local |
| `BackdropEnabledKey` | AppearanceSettings | Player, Thumbnail | — | local |
| `BlurRadiusKey` | AppearanceSettings | LyricsScreen, Player | `AppSettings.blurIntensity` | **bridge** |
| `CanvasAudioReactiveKey` | CanvasSettingsScreen | ProceduralCanvas | — | local |
| `CanvasResolverEndpointsKey` | CanvasSettingsScreen | — | — | dead |
| `CropThumbnailToSquareKey` | AppearanceSettings | Items, Thumbnail | `AppSettings.squareVideoThumbnails` | **bridge** |
| `CrossfadeDurationKey` | PlayerSettings | — | `AppSettings.crossfadeSeconds` (length kept while off) | **bridge** |
| `CrossfadeEnabledKey` | PlayerSettings | — | `AppSettings.crossfadeSeconds > 0` | **bridge** |
| `CrossfadeGaplessKey` | PlayerSettings | — | — | dead |
| `CustomFontNameKey` | AppearanceSettings | — | `AppSettings.customFontName (setCustomFont)` | **bridge** |
| `CustomFontUriKey` | AppearanceSettings | — | `AppSettings.customFontUri (setCustomFont)` | **bridge** |
| `CustomThemeColorKey` | PalettePickerScreen | — | — | dead |
| `DarkModeKey` | AppearanceSettings | Player | `AppSettings.themeMode` | **bridge** |
| `DefaultOpenTabKey` | AppearanceSettings | — | `AppSettings.defaultOpenTab` | **bridge** |
| `DeviceMutePlaybackRecoveryVolumeKey` | PlayerSettings | — | — | dead |
| `DisableAnimationsKey` | AppearanceSettings | FloatingNavigationToolbar | `AppSettings.reduceAnimation` | **bridge** |
| `DisableBlurKey` | AppearanceSettings | LyricsScreen, Player, Thumbnail | `AppSettings.reduceDynamicBlur` | **bridge** |
| `DolbyFfmpegDecoderKey` | PlayerSettings | — | — | dead |
| `EnableBetterLyricsKey` | LyricsSettings | — | — | dead |
| `EnableBetterLyricsPortatoKey` | LyricsSettings | — | — | dead |
| `EnableKugouKey` | LyricsSettings | — | — | dead |
| `EnableLrcLibKey` | LyricsSettings | — | — | dead |
| `EnableMusixmatchExperimentalKey` | LyricsSettings | — | — | dead |
| `EnablePipModeKey` | PlayerSettings | — | — | dead |
| `EnableUnisonLyricsKey` | LyricsSettings | — | — | dead |
| `EnableVideoPlaybackKey` | PlayerSettings | Player | — | local |
| `EnableYouLyPlusLyricsKey` | LyricsSettings | — | — | dead |
| `FlamingoBackgroundEffectKey` | AppearanceSettings | FlamingoPlayer | `AppSettings.movingBlur` | **bridge** |
| `FlamingoHideSlideBarKey` | AppearanceSettings | FlamingoPlayer | `AppSettings.hideSlideBar` | **bridge** |
| `FlamingoShowVolumeBarKey` | AppearanceSettings | FlamingoPlayer | `AppSettings.!hideVolumeBar` | **bridge** |
| `FontPreferenceKey` | AppearanceSettings | — | `AppSettings.appFont` | **bridge** |
| `GridItemsSizeKey` | AppearanceSettings | — | — | dead |
| `HideCachedCardKey` | AppearanceExtrasSettings | — | — | dead |
| `HideLikedSongsCardKey` | AppearanceExtrasSettings | — | — | dead |
| `HideLocalFilesCardKey` | AppearanceExtrasSettings | — | — | dead |
| `HideOfflineCardKey` | AppearanceExtrasSettings | — | — | dead |
| `HideScrollbarKey` | AppearanceSettings | — | — | dead |
| `HideStatusBarKey` | AppearanceSettings | — | `AppSettings.hideStatusBar` | **bridge** |
| `HideTop50CardKey` | AppearanceExtrasSettings | — | — | dead |
| `LiquidGlassAdaptiveLuminanceKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassAdaptiveLuminance` | **bridge** |
| `LiquidGlassBackdropVibrancyKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassBackdropVibrancy` | **bridge** |
| `LiquidGlassBlurRadiusKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassBlurRadius` | **bridge** |
| `LiquidGlassChromaticAberrationKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassChromaticAberration` | **bridge** |
| `LiquidGlassDepth3DKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassDepth3D` | **bridge** |
| `LiquidGlassEnabledKey` | AppearanceSettings, LiquidGlassSettings | GlassSurface, HomeFeedDesignBridge, MiniPlayer | `AppSettings.liquidGlass` | **bridge** |
| `LiquidGlassIntensityKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassIntensity` | **bridge** |
| `LiquidGlassRefractionAmountKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassRefractionAmount` | **bridge** |
| `LiquidGlassRefractionHeightKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassRefractionHeight` | **bridge** |
| `LiquidGlassShadowDepthKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassShadowDepth` | **bridge** |
| `LiquidGlassTintOpacityKey` | LiquidGlassSettings | LiquidGlass | `AppSettings.liquidGlassTintOpacity` | **bridge** |
| `LyricsClickKey` | LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsLineBlurKey` | LyricsSettings | LyricsEnhanced, LyricsV2 | `AppSettings.lyricsBlur` | **bridge** |
| `LyricsLineSpacingKey` | LyricsSettings | LyricsV2 | — | local |
| `LyricsProviderOrderKey` | LyricsSettings | — | — | dead |
| `LyricsRomanizeChineseKey` | LyricsRomanisationSettings, LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsRomanizeHindiKey` | LyricsRomanisationSettings, LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsRomanizeJapaneseKey` | LyricsRomanisationSettings, LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsRomanizeKoreanKey` | LyricsRomanisationSettings, LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsRomanizeOtherLanguagesKey` | LyricsRomanisationSettings, LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `LyricsScrollKey` | LyricsSettings | LyricsV2 | — | local |
| `LyricsSyncOffsetKey` | LyricsMenu (offset dialog) | Player, BitChordPlayer, LyricsScreen, FlamingoPlayer, TikTokPlayer, TikTokSongPage, TikTokMainLyrics, LyricsEnhanced, LyricsV2 | `AppSettings.lyricsOffsetMs` (negated: Neiro's playhead subtracts the offset, the ported stack adds it) | **bridge** |
| `LyricsTextSizeKey` | LyricsSettings | LyricsEnhanced, LyricsV2 | — | local |
| `MinimalHomeModeKey` | AppearanceSettings | — | `AppSettings.minimalHomeMode` | **bridge** |
| `PauseOnDeviceMuteKey` | PlayerSettings | — | — | dead |
| `PermanentShuffleKey` | PlayerSettings | — | — | dead |
| `PersistentQueueKey` | PlayerSettings | — | — | dead |
| `PlayerBackgroundStyleKey` | AppearanceSettings | BitChordPlayer, LyricsEnhanced, LyricsScreen, LyricsV2 … | Neiro's `playerTheme` is a different enum (its own sleeve backgrounds) | local |
| `PlayerCustomBlurKey` | CustomizeBackground | LyricsScreen, Player, StyledLyricsBackground | — | local |
| `PlayerCustomBrightnessKey` | CustomizeBackground | LyricsScreen, Player, StyledLyricsBackground | — | local |
| `PlayerCustomContrastKey` | CustomizeBackground | LyricsScreen, Player, StyledLyricsBackground | — | local |
| `PlayerCustomImageUriKey` | CustomizeBackground | LyricsScreen, Player, StyledLyricsBackground | — | local |
| `PlayerDesignStyleKey` | AppearanceSettings, PlayerSettings | Player, PlayerMenu, Queue, Thumbnail | `AppSettings.playerDesignStyle` | **bridge** |
| `PreloadSongsCountKey` | PlayerSettings | — | — | dead |
| `ProceduralArtworkEnabledKey` | CanvasSettingsScreen | FlamingoPlayer, ProceduralCanvas | — | local |
| `ProceduralArtworkStyleKey` | CanvasSettingsScreen | ProceduralCanvas | — | local |
| `QueueLyricsPreloadCountKey` | LyricsSettings | — | — | dead |
| `RandomThemeOnStartupKey` | AppearanceSettings | — | — | dead |
| `ShowHomeCategoryChipsKey` | AppearanceExtrasSettings | — | — | dead |
| `ShowLyricsPlayerControlsKey` | LyricsSettings | LyricsScreen | — | local |
| `ShowPlayerVolumeBarKey` | AppearanceSettings | Player | `AppSettings.!hideVolumeBar` | **bridge** |
| `ShowTagsInLibraryKey` | AppearanceExtrasSettings | — | — | dead |
| `SkipSilenceKey` | PlayerSettings | — | `AppSettings.skipSilence` | **bridge** |
| `SpotifyCanvasKey` | CanvasSettingsScreen | Player, PlayerMenu | — | local |
| `StopMusicOnTaskClearKey` | PlayerSettings | — | `AppSettings.stopOnTaskRemoved` | **bridge** |
| `SwipeSensitivityKey` | PlayerSettings | MiniPlayer | — | local |
| `SwipeThumbnailKey` | PlayerSettings | MiniPlayer, Thumbnail | — | local |
| `SwipeToSongKey` | PlayerSettings | Items | — | local |
| `TabletModeEnabledKey` | AppearanceSettings | — | `AppSettings.tabletMode` | **bridge** |
| `TidalArtworkFallbackEnabledKey` | CanvasSettingsScreen | — | — | dead |
| `TidalEnabledKey` | CanvasSettingsScreen | — | — | dead |
| `TikTokMainLyricsEnabledKey` | AppearanceSettings | TikTokMainLyrics, TikTokSongPage | `AppSettings.tikTokMainLyrics` | **bridge** |
| `UiScaleFactorKey` | AppearanceSettings | — | `AppSettings.uiScale` | **bridge** |
| `UsbExclusiveAudioKey` | PlayerSettings | — | — | dead |
| `WakelockKey` | PlayerSettings | — | — | dead |

## Status after follow-up 5

The separate ArchiveTune pages group and its activity are gone; the vendored pages still compile but nothing opens them.
Native Neiro rows for the remaining `local` keys are **not built yet**. They are listed below with the category they belong in,
and they keep ArchiveTune's defaults until then:

- Appearance, player card: Backdrop blur, backdrop blur amount, ArchiveTune player background style (shown in place of Neiro's
  row while a ported style is active), Customize background (image plus blur/brightness/contrast).
- Lyrics: text size, line spacing, tap to seek, auto-scroll, enhanced style, show and auto-hide controls, Romanization
  (JP/KR/ZH/HI/other).
- Playback: swipe to change song, swipe sensitivity, swipe song rows, video playback.
- Canvas: Spotify Canvas, animate static artwork, motion style.

Not ported (no engine in Neiro): AI romanisation, audio-reactive canvas (needs a microphone permission Neiro doesn't
request), PiP, auto-start on Bluetooth, wakelock, USB exclusive, Dolby FFmpeg, Tidal, palette theming, grid size, library
cards and chips, preload counts.
