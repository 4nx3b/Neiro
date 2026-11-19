/*
 * Copyright (C) 2026 Kushagra Singh, 4nx3b
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.ui.components.liquid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LiquidGlassDefaults {
    val ButtonBlur = 2.dp
    val ButtonRefractionHeight = 12.dp
    val ButtonRefractionAmount = 24.dp
    val ButtonHeight = 48.dp
    val ButtonPressGrow = 4.dp
    const val ButtonDragInitialDerivative = 0.05f

    val TabsBlur = 8.dp
    val TabsRefractionHeight = 24.dp
    val TabsRefractionAmount = 24.dp
    val TabsHeight = 64.dp
    val TabsPadding = 4.dp
    val TabsPressGrow = 16.dp
    val TabsPanelOffset = 4.dp
    const val TabsContainerAlpha = 0.4f
    val TabsContainerLight = Color(0xFFFAFAFA)
    val TabsContainerDark = Color(0xFF121212)

    val TabHeight = 56.dp
    const val TabPressedScale = 78f / 56f
    const val TabContentPressedScale = 1.2f
    val TabIndicatorRefractionHeight = 10.dp
    val TabIndicatorRefractionAmount = 14.dp
    const val TabIndicatorChromaticAberration = true
    val TabIndicatorInnerShadowRadius = 8.dp
    const val TabIndicatorRestFillAlpha = 0.1f
    const val TabIndicatorPressedShadeAlpha = 0.03f
    const val TabVelocityDivisor = 10f

    val ToggleTrackWidth = 64.dp
    val ToggleTrackHeight = 28.dp
    val ToggleThumbWidth = 40.dp
    val ToggleThumbHeight = 24.dp
    val ToggleDragWidth = 20.dp
    val TogglePadding = 2.dp
    const val TogglePressedScale = 1.5f
    const val ToggleVelocityDivisor = 50f
    val ToggleTrackOffLight = Color(0xFF787878).copy(alpha = 0.2f)
    val ToggleTrackOffDark = Color(0xFF787880).copy(alpha = 0.36f)

    val SliderTrackHeight = 6.dp
    val SliderThumbWidth = 40.dp
    val SliderThumbHeight = 24.dp
    const val SliderPressedScale = 1.5f
    const val SliderVelocityDivisor = 10f
    val SliderTrackLight = Color(0xFF787878).copy(alpha = 0.2f)
    val SliderTrackDark = Color(0xFF787880).copy(alpha = 0.36f)

    val ThumbBlur = 8.dp
    val ThumbRefractionHeight = 10.dp
    val ThumbRefractionAmount = 14.dp
    val ToggleThumbRefractionHeight = 5.dp
    val ToggleThumbRefractionAmount = 10.dp
    const val ThumbHighlightDivisor = 1.5f
    val ThumbShadowRadius = 4.dp
    const val ThumbShadowAlpha = 0.05f
    val ThumbInnerShadowRadius = 4.dp

    const val SquishScaleX = 0.75f
    const val SquishScaleY = 0.25f
    const val SquishLimit = 0.2f

    const val DragPressSpringDamping = 1f
    const val DragPressSpringStiffness = 1000f
    const val DragScaleXSpringDamping = 0.6f
    const val DragScaleXSpringStiffness = 250f
    const val DragScaleYSpringDamping = 0.7f
    const val DragScaleYSpringStiffness = 250f
    const val DragValueSpringDamping = 1f
    const val DragValueSpringStiffness = 1000f
    const val PanelOffsetSpringDamping = 1f
    const val PanelOffsetSpringStiffness = 300f
    const val PanelOffsetSpringThreshold = 0.5f
    const val DragSettleSpringDamping = 0.5f
    const val DragSettleSpringStiffness = 300f
    const val HighlightSpringDamping = 0.5f
    const val HighlightSpringStiffness = 300f

    fun tabIndicatorRestFill(darkGlass: Boolean): Color =
        if (darkGlass) Color.White.copy(alpha = TabIndicatorRestFillAlpha)
        else Color.Black.copy(alpha = TabIndicatorRestFillAlpha)
}
