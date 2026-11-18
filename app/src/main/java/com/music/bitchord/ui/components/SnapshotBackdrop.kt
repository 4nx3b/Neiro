/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 */
package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class SnapshotBackdrop internal constructor() : Backdrop {

    internal var image: ImageBitmap? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? = null
    internal var requests by mutableIntStateOf(0)
    internal var handled = 0
    internal var generation = 0

    fun capture() {
        requests++
    }

    val hasStill: Boolean get() = image != null

    fun release() {
        image = null
        generation++
    }

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val image = image ?: return
        val coordinates = coordinates ?: return
        val source = sourceCoordinates?.takeIf { it.isAttached } ?: return
        withTransform({
            val (offset, panelScale) =
                try {
                    val origin = source.localPositionOf(coordinates)
                    val step = source.localPositionOf(coordinates, Offset(SCALE_PROBE_PX, 0f))
                    origin to ((step.x - origin.x) / SCALE_PROBE_PX).takeIf { it > 0f }
                } catch (_: Exception) {
                    (coordinates.positionInWindow() - source.positionInWindow()) to null
                }
            if (panelScale != null && panelScale != 1f) scale(1f / panelScale, pivot = Offset.Zero)
            translate(-offset.x, -offset.y)
        }) {
            scale(1f / SNAPSHOT_SCALE, pivot = Offset.Zero) { drawImage(image) }
        }
    }
}

@Composable
fun rememberSnapshotBackdrop(): SnapshotBackdrop = remember { SnapshotBackdrop() }

@Composable
fun Modifier.snapshotBackdropSource(backdrop: SnapshotBackdrop, floor: Color = Color.Transparent): Modifier {
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    return this
        .onGloballyPositioned { backdrop.sourceCoordinates = it }
        .drawWithContent {
            drawContent()
            val request = backdrop.requests
            if (request == backdrop.handled) return@drawWithContent
            backdrop.handled = request
            if (size.width < 1f || size.height < 1f) return@drawWithContent
            val layer = graphicsContext.createGraphicsLayer()
            val width = (size.width * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val height = (size.height * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            layer.record(size = IntSize(width, height)) {
                drawRect(floor)
                scale(SNAPSHOT_SCALE, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
            }
            val generation = backdrop.generation
            scope.launch {
                try {
                    val still = layer.toImageBitmap()
                    if (backdrop.handled == request && backdrop.generation == generation) backdrop.image = still
                } finally {
                    graphicsContext.releaseGraphicsLayer(layer)
                }
            }
        }
}

private const val SNAPSHOT_SCALE = 0.25f

private const val SCALE_PROBE_PX = 1000f
