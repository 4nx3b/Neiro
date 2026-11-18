package com.music.bitchord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R as AtR

@Composable
internal fun PortedBackdropRow() {
    var enabled by rememberPreference(BackdropEnabledKey, true)
    SettingsRow(
        icon = SfIcons.BlurOn,
        title = stringResource(AtR.string.album_backdrop),
        subtitle = stringResource(AtR.string.album_backdrop_desc),
        trailing = { IosSwitch(checked = enabled, onCheckedChange = { enabled = it }) },
        onClick = { enabled = !enabled },
    )
}

@Composable
internal fun PortedBackdropAmountRow() {
    val enabled by rememberPreference(BackdropEnabledKey, true)
    var amount by rememberPreference(BackdropBlurAmountKey, 60)
    SliderRow(
        icon = SfIcons.BlurOn,
        title = stringResource(AtR.string.backdrop_blur_amount),
        value = "$amount%",
        sliderValue = amount.toFloat(),
        onSliderValue = { amount = it.roundToInt() },
        valueRange = 0f..100f,
        steps = 19,
        enabled = enabled,
    )
}
