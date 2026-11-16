# ArchiveTune settings port — staged, not yet compiled

Upstream: 4nx3b/ArchiveTune @ c0c97f7 (`app/src/main/kotlin/moe/rukamori/archivetune/...`).

`staged/` mirrors the module's source tree (`archivetuneplayer/src/main/kotlin/moe/rukamori/archivetune/`).
It sits outside `src/` on purpose: nothing here is built until it is moved in, because there is no
local Android toolchain and the closure has not been through CI yet.

## What is staged
- Byte-for-byte upstream copies: the 17 settings files (Appearance, AppearanceExtras, Player, Lyrics,
  LyricsRomanisation, CustomizeBackground, LiquidGlass, CanvasSettingsScreen, CanvasCheckDialog,
  PalettePicker, SfProFontPickerDialog, SettingsComponents/Cards/Dimensions/Models/AutoScroll/Search),
  plus the closure found by `closure.py`: ui/component/{ArtistSeparatorsDialog, Preference,
  SettingsPageTopBar, SpringySwitch, ThumbnailCornerRadiusSelector}, ui/svg/{DynamicSVGImage,
  SVGString, palette}, ui/theme/SfProFontCatalog, ui/theme/palette/TonalPalettes,
  ui/utils/NavControllerUtils, utils/CanvasResolverEndpoints.
- BRIDGE shims: ui/screens/HomeFeedDesignBridge.kt (ScreenHeaderHaze on haze 1.3.1),
  tidal/TidalCanvasCheck.kt (Skipped), viewmodels/ContentSettingsViewModel.kt (empty; register it in
  BridgeViewModels), ui/component/TagsManagementDialog.kt (host message + dismiss).
- BridgeLocals.patch: adds `LocalPlayerAwareWindowInsets` (the settings host must provide it).

## Still to do before it compiles
1. Gradle (archivetuneplayer): `com.caverock:androidsvg-aar:1.4`, `androidx.compose.ui:ui-tooling-preview`.
2. Resources: every `R.string` / `R.drawable` the staged files use that the module's res lacks (all locales).
3. Member-level gaps: `members.py` lists names upstream declares that the module lacks; the remaining
   list (closure-report.txt) is mostly local variables, but CI will be the judge.
4. `bridge/ArchiveTuneSettingsHost.kt` (page enum, synchronous setup in a remember, NavController,
   theme, LocalPlayerAwareWindowInsets), the Neiro SettingsSheet group, the key map + BridgedPreferences.
5. Neiro has no pure-black setting, so `pureBlack = false` in MainActivity stays until one exists.

Re-run the analysis: clone ArchiveTune to /tmp/AT, then `python3 closure.py <seeds>` with
`EXCL=MainActivity.kt,ui/screens/HomeFeedDesign.kt,tidal/TidalCanvasCheck.kt,ui/component/TagsManagementDialog.kt,viewmodels/ContentSettingsViewModel.kt DEPTH=10`
(the paths in `dep.py` assume /home/user/workspace and /tmp/AT).
