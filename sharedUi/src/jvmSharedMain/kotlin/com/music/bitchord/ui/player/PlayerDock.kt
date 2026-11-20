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

package com.music.bitchord.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

@Stable
class PlayerDock {
    private var miniArt: LayoutCoordinates? = null
    private var miniArtCorner: Dp = 8.dp

    var sheetOffset: (() -> Float)? by mutableStateOf(null)

    var sheetTravel by mutableFloatStateOf(0f)

    var attached by mutableStateOf(false)
        internal set

    fun attachExternal(offset: () -> Float, travelPx: () -> Float) {
        externalTravel = travelPx
        externalOffset = offset
        sheetOffset = offset
        attached = true
    }

    fun detachExternal() {
        val installed = externalOffset ?: return
        externalOffset = null
        externalTravel = null
        if (sheetOffset === installed) {
            sheetOffset = null
            attached = false
        }
    }

    private var externalOffset: (() -> Float)? = null
    private var externalTravel: (() -> Float)? = null

    fun miniArtScreenRect(): Rect? = miniArtOnScreen()

    fun reportMiniArt(coordinates: LayoutCoordinates, corner: Dp) {
        miniArt = coordinates
        miniArtCorner = corner
    }

    fun releaseMiniArt(coordinates: LayoutCoordinates) {
        if (miniArt === coordinates) miniArt = null
    }

    internal fun hasMiniArt(): Boolean = miniArt?.isAttached == true

    internal fun miniArtOnScreen(): Rect? {
        val coordinates = miniArt?.takeIf { it.isAttached } ?: return null
        val size = coordinates.size.toSize()
        return Rect(
            coordinates.localToScreen(Offset.Zero),
            coordinates.localToScreen(Offset(size.width, size.height)),
        )
    }

    internal fun miniArtCorner(): Dp = miniArtCorner

    internal fun offset(): Float? = sheetOffset?.invoke()?.takeUnless { it.isNaN() }

    fun openFraction(): Float {
        val travel = externalTravel?.invoke() ?: sheetTravel
        val offset = offset() ?: return 0f
        if (travel <= 0f) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    val coversMiniArt: Boolean
        get() = coversMiniArtState.value

    private val coversMiniArtState = derivedStateOf { attached && openFraction() > 0f }
}

val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }
