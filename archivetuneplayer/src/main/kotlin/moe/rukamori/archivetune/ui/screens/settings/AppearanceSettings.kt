/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.AppleMusicAnimatedArtworkKey
import moe.rukamori.archivetune.constants.AppleMusicExperienceKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.CustomFontNameKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.FlamingoBackgroundEffectKey
import moe.rukamori.archivetune.constants.FlamingoShowVolumeBarKey
import moe.rukamori.archivetune.constants.FlamingoHideSlideBarKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HideStatusBarKey
import moe.rukamori.archivetune.constants.GridItemSize
import moe.rukamori.archivetune.constants.GridItemsSizeKey
import moe.rukamori.archivetune.constants.HideScrollbarKey
import moe.rukamori.archivetune.constants.BackAnimationStyle
import moe.rukamori.archivetune.constants.BackAnimationStyleKey
import moe.rukamori.archivetune.constants.MinimalHomeModeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.TikTokMainLyricsEnabledKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.UiScaleFactorKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.FrostedHeaderPill
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.theme.CustomFontLoader
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.asPaddingValues
import moe.rukamori.archivetune.ui.screens.ScreenHeaderHaze
import moe.rukamori.archivetune.ui.screens.rememberScreenHeaderHaze
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import moe.rukamori.archivetune.ui.component.SettingsPageTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController, scrollTo: String? = null) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (darkMode, onDarkModeChange) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )
    val (playerDesignStyle, onPlayerDesignStyleChange) =
        rememberEnumPreference(
            PlayerDesignStyleKey,
            defaultValue = PlayerDesignStyle.APPLE_MUSIC,
        )
    val (tikTokMainLyrics, onTikTokMainLyricsChange) =
        rememberPreference(
            TikTokMainLyricsEnabledKey,
            defaultValue = false,
        )
    val (flamingoBackgroundEffect, onFlamingoBackgroundEffectChange) =
        rememberPreference(FlamingoBackgroundEffectKey, defaultValue = false)
    val (flamingoShowVolumeBar, onFlamingoShowVolumeBarChange) =
        rememberPreference(FlamingoShowVolumeBarKey, defaultValue = true)
    val (flamingoHideSlideBar, onFlamingoHideSlideBarChange) =
        rememberPreference(FlamingoHideSlideBarKey, defaultValue = false)
    val (_, onAppleMusicExperienceChange) =
        rememberPreference(
            AppleMusicExperienceKey,
            defaultValue = false,
        )
    val (appleMusicAnimatedArtwork, onAppleMusicAnimatedArtworkChange) =
        rememberPreference(
            AppleMusicAnimatedArtworkKey,
            defaultValue = true,
        )
    var showSfProFontPicker by rememberSaveable {
        mutableStateOf(false)
    }
    val (showPlayerVolumeBar, onShowPlayerVolumeBarChange) =
        rememberPreference(
            ShowPlayerVolumeBarKey,
            defaultValue = true,
        )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) =
        rememberPreference(
            CropThumbnailToSquareKey,
            defaultValue = false,
        )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(
            DisableAnimationsKey,
            defaultValue = defaultDisableAnimations,
        )
    val (hideStatusBar, onHideStatusBarChange) =
        rememberPreference(
            HideStatusBarKey,
            defaultValue = false,
        )
    val (uiScale, onUiScaleChange) =
        rememberPreference(
            UiScaleFactorKey,
            defaultValue = 1.0f,
        )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 48f)
    val (backdropEnabled, onBackdropEnabledChange) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount, onBackdropBlurAmountChange) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )
    val (gridItemSize, onGridItemSizeChange) =
        rememberEnumPreference(
            GridItemsSizeKey,
            defaultValue = GridItemSize.SMALL,
        )
    val (hideScrollbar, onHideScrollbarChange) =
        rememberPreference(HideScrollbarKey, defaultValue = false)
    val (backAnimationStyle, onBackAnimationStyleChange) =
        rememberEnumPreference(
            BackAnimationStyleKey,
            defaultValue = BackAnimationStyle.DEFAULT,
        )
    val (minimalHomeMode, onMinimalHomeModeChange) =
        rememberPreference(MinimalHomeModeKey, defaultValue = false)

    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    val availableBackgroundStyles =
        PlayerBackgroundStyle.entries.filter {
            it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
    // None of the remaining styles customizes the legacy player background /
    // slider machinery — the customization page was the legacy styles' own.
    val isPlayerStyleCustomizationEnabled = false
    val isVolumeBarSupported = false
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    LaunchedEffect(isPlayerStyleCustomizationEnabled, playerBackground) {
        if (!isPlayerStyleCustomizationEnabled && playerBackground != PlayerBackgroundStyle.DEFAULT) {
            onPlayerBackgroundChange(PlayerBackgroundStyle.DEFAULT)
        }
    }

    if (showSfProFontPicker) {
        SfProFontPickerDialog(
            onDismiss = { showSfProFontPicker = false },

            appliedFontName = customFontName.takeIf { fontPreference == AppFontPreference.CUSTOM }.orEmpty(),
            onApply = { uri, name ->
                onCustomFontUriChange(uri)
                onCustomFontNameChange(name)
                onFontPreferenceChange(AppFontPreference.CUSTOM)
                Toast.makeText(
                    context,
                    context.getString(R.string.sf_pro_applied, name),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    val headerHaze = rememberScreenHeaderHaze()
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
                SettingsPageTopBar(
                    titleText = stringResource(R.string.appearance),
                    onBack = navController::navigateUp,
                    onBackLongClick = navController::backToMain,
                )
            },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .hazeSource(headerHaze)
                .padding(top = topPadding)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(
                modifier = positions.modifierFor("dynamic_theme", "color_source"),
                title = stringResource(R.string.theme),
            ) {
                item {
                    Column(modifier = positions.modifierFor("liquid_glass_customisation")) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.liquid_glass_settings_title)) },
                            description = stringResource(R.string.liquid_glass_customisation_subtitle),
                            icon = { Icon(painterResource(R.drawable.sliders), null) },
                            onClick = { navController.navigate("settings/appearance/liquid_glass") },
                        )
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            Text(
                                text = stringResource(R.string.liquid_glass_effects_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 56.dp, top = 4.dp, end = 16.dp),
                            )
                        }
                    }
                }

                item {
                    Column(modifier = positions.modifierFor("app_icon")) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.app_icon)) },
                            description = stringResource(R.string.app_icon_description),
                            icon = { Icon(painterResource(R.drawable.app_icon_small), null) },
                            onClick = { navController.navigate("settings/appearance/icon") },
                        )
                    }
                }

                item {
                    Column(modifier = positions.modifierFor("dark_theme")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.dark_theme)) },
                            icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                            selectedValue = darkMode,
                            onValueSelected = onDarkModeChange,
                            valueText = {
                                when (it) {
                                    DarkMode.ON -> stringResource(R.string.dark_theme_on)
                                    DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                                    DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                                }
                            },
                        )
                    }
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_blur)) },
                        description = stringResource(R.string.disable_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_off), null) },
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("disable_animations"),
                        title = { Text(stringResource(R.string.disable_animations)) },
                        description = stringResource(R.string.disable_animations_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = disableAnimations,
                        onCheckedChange = onDisableAnimationsChange,
                    )
                }

                item {
                    Column(modifier = positions.modifierFor("hide_status_bar")) {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.hide_status_bar)) },
                            description = stringResource(R.string.hide_status_bar_desc),
                            icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                            checked = hideStatusBar,
                            onCheckedChange = onHideStatusBarChange,
                        )
                    }
                }

                item {
                    Column(modifier = positions.modifierFor("ui_scale")) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.ui_scale)) },
                            description = stringResource(R.string.ui_scale_desc),
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            content = {
                                Spacer(modifier = Modifier.height(10.dp))
                                Slider(
                                    value = uiScale,
                                    onValueChange = { v ->
                                        onUiScaleChange((v * 100f).roundToInt() / 100f)
                                    },
                                    valueRange = 0.85f..1.30f,
                                    steps = 44,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = stringResource(R.string.ui_scale_value, (uiScale * 100f).roundToInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 56.dp, top = 4.dp),
                                )
                            },
                        )
                    }
                }

                item {
                    Column(modifier = positions.modifierFor("blur_intensity")) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.blur_intensity)) },
                            description = stringResource(R.string.blur_intensity_value, blurRadius.roundToInt()),
                            icon = { Icon(painterResource(R.drawable.blur_on), null) },
                            isEnabled = !disableBlur,
                            content = {
                                Spacer(modifier = Modifier.height(10.dp))
                                Slider(
                                    value = blurRadius,
                                    onValueChange = onBlurRadiusChange,
                                    valueRange = 0f..64f,
                                    steps = 63,
                                    enabled = !disableBlur,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }
                }

                if (playerDesignStyle != PlayerDesignStyle.APPLE_MUSIC) {
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.album_backdrop)) },
                            description = stringResource(R.string.album_backdrop_desc),
                            icon = { Icon(painterResource(R.drawable.blur_on), null) },
                            checked = backdropEnabled,
                            onCheckedChange = onBackdropEnabledChange,
                        )
                    }

                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("backdrop_blur", "backdrop_blur_amount"),
                            title = { Text(stringResource(R.string.backdrop_blur_amount)) },
                            description = stringResource(R.string.backdrop_blur_amount_value, backdropBlurAmount),
                            icon = { Icon(painterResource(R.drawable.blur_on), null) },
                            isEnabled = backdropEnabled,
                            content = {
                                Spacer(modifier = Modifier.height(10.dp))
                                Slider(
                                    value = backdropBlurAmount.toFloat(),
                                    onValueChange = { onBackdropBlurAmountChange(it.roundToInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    enabled = backdropEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }
                }

                item {
                    Column(modifier = positions.modifierFor("font_preference")) {
                        EnumListPreference(
                            modifier = positions.modifierFor("use_system_font"),
                            title = { Text(stringResource(R.string.font_preference)) },
                            description = stringResource(R.string.font_preference_desc),
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            selectedValue = fontPreference,
                            onValueSelected = onFontPreferenceSelected,
                            valueText = {
                                when (it) {
                                    AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                                    AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                                    AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                                }
                            },
                        )
                    }
                }

                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    val customFontDescription =
                        if (customFontName.isNotBlank()) {
                            customFontName
                        } else if (customFontUri.isBlank()) {
                            stringResource(R.string.custom_font_desc)
                        } else {
                            customFontUri
                        }
                    PreferenceEntry(
                        modifier = positions.modifierFor("custom_font"),
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontDescription,
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        onClick = pickCustomFont,
                    )
                }
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("sf_pro_fonts"),
                        title = { Text(stringResource(R.string.sf_pro_fonts)) },
                        description = stringResource(R.string.sf_pro_fonts_desc),
                        icon = { Icon(painterResource(R.drawable.solar_download_minimalistic_linear), null) },
                        onClick = { showSfProFontPicker = true },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("disable_blur"),
                title = stringResource(R.string.player),
            ) {
                item {
                    Column(modifier = positions.modifierFor("player_design_style")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.player_design_style)) },
                            icon = { Icon(painterResource(R.drawable.palette), null) },
                            selectedValue = playerDesignStyle,
                            onValueSelected = { style ->
                                onPlayerDesignStyleChange(style)
                                if (style != PlayerDesignStyle.APPLE_MUSIC) {
                                    onAppleMusicExperienceChange(false)
                                }
                            },
                            valueText = {
                                when (it) {
                                    PlayerDesignStyle.APPLE_MUSIC ->
                                        stringResource(R.string.player_design_apple_music)
                                    PlayerDesignStyle.BITCHORD ->
                                        stringResource(R.string.player_design_bitchord)
                                    PlayerDesignStyle.TIKTOK ->
                                        stringResource(R.string.player_design_tiktok)
                                }
                            },
                        )
                    }
                }

                if (playerDesignStyle == PlayerDesignStyle.TIKTOK) {
                    item {
                        Column(modifier = positions.modifierFor("tiktok_main_lyrics")) {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.tiktok_main_lyrics)) },
                                description = stringResource(R.string.tiktok_main_lyrics_desc),
                                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                                checked = tikTokMainLyrics,
                                onCheckedChange = onTikTokMainLyricsChange,
                            )
                        }
                    }
                }

                if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                    item {
                        Column(modifier = positions.modifierFor("flamingo_player")) {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.flamingo_settings_background_effect)) },
                                description = stringResource(R.string.flamingo_settings_background_effect_desc),
                                icon = { Icon(painterResource(R.drawable.flamingo_np_airplay), null) },
                                checked = flamingoBackgroundEffect,
                                onCheckedChange = onFlamingoBackgroundEffectChange,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SwitchPreference(
                                title = { Text(stringResource(R.string.flamingo_settings_volume_bar)) },
                                description = stringResource(R.string.flamingo_settings_volume_bar_desc),
                                icon = { Icon(painterResource(R.drawable.flamingo_np_volume), null) },
                                checked = flamingoShowVolumeBar,
                                onCheckedChange = onFlamingoShowVolumeBarChange,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SwitchPreference(
                                title = { Text(stringResource(R.string.flamingo_settings_hide_slide_bar)) },
                                description = stringResource(R.string.flamingo_settings_hide_slide_bar_desc),
                                icon = { Icon(painterResource(R.drawable.flamingo_np_more), null) },
                                checked = flamingoHideSlideBar,
                                onCheckedChange = onFlamingoHideSlideBarChange,
                            )
                        }
                    }
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("show_player_volume_bar"),
                        title = { Text(stringResource(R.string.show_player_volume_bar)) },
                        description =
                            if (isVolumeBarSupported) {
                                null
                            } else {
                                stringResource(R.string.player_volume_bar_v7_v8_only)
                            },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = showPlayerVolumeBar,
                        onCheckedChange = onShowPlayerVolumeBarChange,
                        isEnabled = isVolumeBarSupported,
                    )
                }

                item {
                    Column(modifier = positions.modifierFor("player_background_style")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.player_background_style)) },
                            description =
                                if (isPlayerStyleCustomizationEnabled) {
                                    null
                                } else {
                                    stringResource(R.string.player_background_style_v8_v9_desc)
                                },
                            icon = { Icon(painterResource(R.drawable.gradient), null) },
                            selectedValue = playerBackground,
                            onValueSelected = onPlayerBackgroundChange,
                            isEnabled = isPlayerStyleCustomizationEnabled,
                            valueText = {
                                when (it) {
                                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                    PlayerBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
                                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                    PlayerBackgroundStyle.COLORING -> stringResource(R.string.coloring)
                                    PlayerBackgroundStyle.BLUR_GRADIENT -> stringResource(R.string.blur_gradient)
                                    PlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                                    PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow Animated"
                                }
                            },
                        )
                    }
                }

                item(visible = playerBackground == PlayerBackgroundStyle.CUSTOM) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("customized_background"),
                        title = { Text(stringResource(R.string.customized_background)) },
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        onClick = { navController.navigate("customize_background") },
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("crop_thumbnail_to_square"),
                        title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                        description = stringResource(R.string.crop_thumbnail_to_square_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = cropThumbnailToSquare,
                        onCheckedChange = onCropThumbnailToSquareChange,
                    )
                }

            }

            PreferenceGroup(
                modifier = positions.modifierFor("aod_ambient_group"),
                title = stringResource(R.string.aod_ambient_group_title),
            ) {
                item {
                    Column(modifier = positions.modifierFor("aod_customize")) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.aod_customize_title)) },
                            description = stringResource(R.string.aod_customize_entry_desc),
                            icon = { Icon(painterResource(R.drawable.bedtime), null) },
                            onClick = { navController.navigate("settings/appearance/aod_customized") },
                        )
                    }
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("home_screen"),
                title = stringResource(R.string.home),
            ) {
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("minimal_home_mode"),
                        title = { Text(stringResource(R.string.minimal_home_mode)) },
                        description = stringResource(R.string.minimal_home_mode_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        checked = minimalHomeMode,
                        onCheckedChange = onMinimalHomeModeChange,
                    )
                }

                item {
                    Column(modifier = positions.modifierFor("default_open_tab")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.default_open_tab)) },
                            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                            selectedValue = defaultOpenTab,
                            onValueSelected = onDefaultOpenTabChange,
                            valueText = {
                                when (it) {
                                    NavigationTab.HOME -> stringResource(R.string.home)
                                    NavigationTab.SEARCH -> stringResource(R.string.search)
                                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                                }
                            },
                        )
                    }
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("app_language"),
                title = stringResource(R.string.misc),
            ) {
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("navigation_bar_settings"),
                        title = { Text(stringResource(R.string.navigation_bar_settings_title)) },
                        description = stringResource(R.string.navigation_bar_settings_subtitle),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        onClick = { navController.navigate("settings/appearance/navigation_bar") },
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("hide_scrollbar"),
                        title = { Text(stringResource(R.string.hide_scrollbar)) },
                        description = stringResource(R.string.hide_scrollbar_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = hideScrollbar,
                        onCheckedChange = onHideScrollbarChange,
                    )
                }

                item {
                    Column(modifier = positions.modifierFor("back_animation_style")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.back_animation_style)) },
                            icon = { Icon(painterResource(R.drawable.arrow_back), null) },
                            selectedValue = backAnimationStyle,
                            onValueSelected = onBackAnimationStyleChange,
                            valueText = {
                                when (it) {
                                    BackAnimationStyle.DEFAULT -> stringResource(R.string.back_animation_style_default)
                                    BackAnimationStyle.PREDICTIVE -> stringResource(R.string.back_animation_style_predictive)
                                }
                            },
                        )
                    }
                }

            }

            PreferenceGroup(
                modifier = positions.modifierFor("extras"),
                title = stringResource(R.string.extras),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.extras)) },
                        description = stringResource(R.string.settings_extras_subtitle),
                        icon = { Icon(painterResource(R.drawable.discover_tune), null) },
                        onClick = { navController.navigate("settings/appearance/extras") },
                    )
                }
            }
        }

        ScreenHeaderHaze(
            hazeState = headerHaze,
            systemBarsTopPadding = systemBarsTopPadding,
        )
        }
}
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
