/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

private const val GRAIN_TILE_PX = 128
private const val GRAIN_SEED = 0x5EED

private val grainTile: ImageBitmap by lazy {
    val bitmap = android.graphics.Bitmap.createBitmap(GRAIN_TILE_PX, GRAIN_TILE_PX, android.graphics.Bitmap.Config.ARGB_8888)
    val random = Random(GRAIN_SEED)
    val pixels = IntArray(GRAIN_TILE_PX * GRAIN_TILE_PX) {
        val v = 128 + random.nextInt(-96, 97)
        android.graphics.Color.argb(255, v, v, v)
    }
    bitmap.setPixels(pixels, 0, GRAIN_TILE_PX, 0, 0, GRAIN_TILE_PX, GRAIN_TILE_PX)
    bitmap.asImageBitmap()
}

private val grainBrush: Brush by lazy {
    ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
}

internal fun acrylicGrainBrush(): Brush = grainBrush

internal fun scaledCorners(shape: CornerBasedShape, factor: Float): Shape {
    if (factor == 1f) return shape
    return object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val cap = minOf(size.width, size.height) / 2f
            fun r(px: Float) = CornerRadius((px * factor).coerceIn(0f, cap))
            val ts = shape.topStart.toPx(size, density)
            val te = shape.topEnd.toPx(size, density)
            val be = shape.bottomEnd.toPx(size, density)
            val bs = shape.bottomStart.toPx(size, density)
            val ltr = layoutDirection == LayoutDirection.Ltr
            return Outline.Rounded(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = r(if (ltr) ts else te),
                    topRightCornerRadius = r(if (ltr) te else ts),
                    bottomRightCornerRadius = r(if (ltr) be else bs),
                    bottomLeftCornerRadius = r(if (ltr) bs else be),
                ),
            )
        }
    }
}
