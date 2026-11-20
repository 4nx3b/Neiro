/*
 * The selection pill's motion — the travel spring, the held lift, the
 * acceleration squash and the glass-to-flat hand-off, with their constants —
 * is ported from liquid_glass_easy's LiquidGlassAnimatedNavBar and
 * LiquidGlassLensMotion (pub.dev/packages/liquid_glass_easy, 4.3.4).
 * Copyright (c) 2025 Ahmed Gamil, MIT License.
 *
 * The rendering is Zenith's own, on the vendored backdrop library: the lens
 * refracts the tab bar's exported glass and its recorded tab row rather than a
 * screen capture.
 */

package com.music.bitchord.ui.components.floatingtabbar

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.isGlassSupported
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

internal val PILL_GROW_HEIGHT = 22.dp

@Stable
internal class GlassPillMotion(
    private val scope: CoroutineScope,
    initialIndex: Int,
    private val slow: Double = 1.0,
) {
    private val slowSq = (slow * slow).toFloat()
    private val valueSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val pressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f / slowSq, visibilityThreshold = 0.001f)
    private val liftXSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 250f / slowSq, visibilityThreshold = 0.001f)
    private val liftYSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 250f / slowSq, visibilityThreshold = 0.001f)
    private val stretchSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.05f)

    private val valueAnim = Animatable(initialIndex.toFloat(), 0.001f)
    private val pressAnim = Animatable(0f, 0.001f)
    private val liftXAnim = Animatable(0f, 0.001f)
    private val liftYAnim = Animatable(0f, 0.001f)
    private val stretchAnim = Animatable(0f, 0.05f)

    private var held = false
    private var liftJob: Job? = null
    private var dropJob: Job? = null
    private var settleJob: Job? = null
    private var lastDragPos = 0f
    private var lastDragNs = 0L
    private var dragPos by mutableFloatStateOf(Float.NaN)
    private var heldStretch by mutableFloatStateOf(0f)

    val position: Float get() = dragPos.let { if (it.isNaN()) valueAnim.value else it }

    val liftX: Float get() = liftXAnim.value

    val liftY: Float get() = liftYAnim.value

    val material: Float
        get() = maxOf(pressAnim.value, (liftXAnim.value + liftYAnim.value) / 2f).coerceIn(0f, 1f)

    val glassPresence: Float get() = material

    val blurMix: Float get() = 1f - material

    val deviation: Float get() = stretch()

    var isFlat by mutableStateOf(true)
        private set

    var flatVisible by mutableStateOf(true)
        private set

    var index: Int = initialIndex
        private set

    val isHeld: Boolean get() = held

    var stepDp: Float = 0f

    var reduceMotion: Boolean = false

    var squashScale: Float = 1f

    fun animateTo(next: Int) {
        if (next == index) return
        if (reduceMotion) {
            snapTo(next)
            return
        }
        index = next
        held = false
        settleTo(next.toFloat(), null)
    }

    fun snapTo(target: Int) {
        index = target
        held = false
        settleJob?.cancel()
        liftJob?.cancel()
        dropJob?.cancel()
        scope.launch {
            handOffDrag()
            valueAnim.snapTo(target.toFloat())
            pressAnim.snapTo(0f)
            liftXAnim.snapTo(0f)
            liftYAnim.snapTo(0f)
            stretchAnim.snapTo(0f)
            flatVisible = true
            isFlat = true
        }
    }

    fun enterFrom(from: Double, next: Int) {
        if (reduceMotion) {
            snapTo(next)
            return
        }
        index = next
        held = false
        settleTo(next.toFloat(), from.toFloat())
    }

    fun startDrag() {
        if (held) return
        held = true
        settleJob?.cancel()
        val from = position
        scope.launch { valueAnim.stop() }
        dragPos = from
        heldStretch = stretchAnim.value
        lastDragPos = from
        lastDragNs = 0L
        if (!reduceMotion) raise()
    }

    fun dragTo(position: Float) {
        if (!held) return
        val now = System.nanoTime()
        if (lastDragNs != 0L) {
            val dt = (now - lastDragNs) / 1e9f
            if (dt > 0f) {
                val raw = (position - lastDragPos) / dt
                heldStretch += (raw - heldStretch) * 0.35f
            }
        }
        lastDragPos = position
        lastDragNs = now
        dragPos = position
    }

    private suspend fun handOffDrag() {
        val from = dragPos
        if (from.isNaN()) return
        valueAnim.snapTo(from)
        stretchAnim.snapTo(heldStretch)
        dragPos = Float.NaN
        heldStretch = 0f
    }

    fun release(next: Int) {
        if (!held) return
        held = false
        index = next
        if (reduceMotion) {
            snapTo(next)
            return
        }
        settleTo(next.toFloat(), null)
    }

    private fun settleTo(target: Float, from: Float?) {
        settleJob?.cancel()
        settleJob = scope.launch {
            handOffDrag()
            raise()
            if (from != null) valueAnim.snapTo(from)
            var landed = false
            valueAnim.animateTo(target, valueSpec) {
                if (!landed && abs(value - target) < LAND_STEPS) {
                    landed = true
                    lower()
                }
            }
            if (!landed) lower()
        }
    }

    private fun raise() {
        dropJob?.cancel()
        liftJob?.cancel()
        isFlat = false
        liftJob = scope.launch {
            launch {
                pressAnim.animateTo(1f, pressSpec)
                flatVisible = false
            }
            launch { liftXAnim.animateTo(1f, liftXSpec) }
            launch { liftYAnim.animateTo(1f, liftYSpec) }
        }
    }

    private fun lower() {
        liftJob?.cancel()
        dropJob?.cancel()
        flatVisible = true
        dropJob = scope.launch {
            val a = launch { pressAnim.animateTo(0f, pressSpec) }
            val b = launch { liftXAnim.animateTo(0f, liftXSpec) }
            val c = launch { liftYAnim.animateTo(0f, liftYSpec) }
            val d = launch { stretchAnim.animateTo(0f, stretchSpec) }
            a.join()
            b.join()
            c.join()
            d.join()
            if (!held) isFlat = true
        }
    }

    private fun stretch(): Float {
        if (position.isNaN()) return 0f
        val speed = if (dragPos.isNaN()) {
            abs(valueAnim.velocity) + abs(stretchAnim.value)
        } else {
            abs(heldStretch)
        }
        return (speed * STRETCH_PER_STEP_PER_S).coerceAtMost(MAX_STRETCH) * squashScale
    }

    fun flatSize(rest: Size, lifted: Size): Size {
        val s = stretch()
        return Size(
            PillGeometry.visibleWidth(rest.width, lifted.width, liftX, s, flat = true),
            PillGeometry.liveHeight(rest.height, lifted.height, liftY, s).coerceAtMost(rest.height),
        )
    }

    fun liveSize(rest: Size, lifted: Size): Size {
        val s = stretch()
        return Size(
            PillGeometry.liveWidth(rest.width, lifted.width, liftX, s),
            PillGeometry.liveHeight(rest.height, lifted.height, liftY, s),
        )
    }

    fun visibleWidth(rest: Size, lifted: Size, glass: Boolean): Float =
        PillGeometry.visibleWidth(rest.width, lifted.width, liftX, stretch(), flat = !glass || isFlat)

    private companion object {
        const val STRETCH_PER_STEP_PER_S = 0.012f
        const val MAX_STRETCH = 0.12f

        const val LAND_STEPS = 0.05f
    }
}

internal object PillGeometry {
    fun liveWidth(restWidth: Float, liftedWidth: Float, liftX: Float, stretch: Float): Float =
        (restWidth + (liftedWidth - restWidth) * liftX) * (1f + stretch)

    fun liveHeight(restHeight: Float, liftedHeight: Float, liftY: Float, stretch: Float): Float =
        (restHeight + (liftedHeight - restHeight) * liftY) * (1f - stretch / 3f)

    fun visibleWidth(restWidth: Float, liftedWidth: Float, liftX: Float, stretch: Float, flat: Boolean): Float {
        val live = liveWidth(restWidth, liftedWidth, liftX, stretch)
        return if (flat) live.coerceAtMost(restWidth) else live
    }
}

internal fun Modifier.pillPlacement(center: () -> Offset, size: () -> Size): Modifier =
    layout { measurable, constraints ->
        val s = size()
        val w = s.width.roundToInt().coerceAtLeast(1)
        val h = s.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.minWidth, constraints.minHeight) {
            val c = center()
            placeable.place((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt())
        }
    }

@Composable
internal fun FlatSelectionPill(
    color: Color,
    modifier: Modifier,
    barGlass: LayerBackdrop? = null,
) {
    val shape = remember { RoundedCornerShape(percent = 50) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val ind by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !isGlassSupported()) {
        Box(modifier.background(color, shape))
        return
    }
    val page = LocalAppBackdrop.current
    val blurPx = with(LocalDensity.current) { INDICATOR_BLUR.toPx() * ind.blur }
    val backdrop = remember(page, barGlass) { PageAndBarBackdrop(page, barGlass) }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = INDICATOR_BRIGHTNESS * ind.brightness,
                    saturation = INDICATOR_SATURATION * ind.saturation,
                )
                blur(blurPx)
                if (ind.restLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    lens(
                        refractionHeight = PILL_LENS_HEIGHT.toPx() * ind.restRefractionWidth,
                        refractionAmount = PILL_LENS_AMOUNT.toPx() * ind.restRefraction,
                        depthEffect = ind.depth,
                        chromaticAberration = false,
                    )
                }
            },
            highlight = {
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = (REST_RIM_ALPHA * ind.rim).coerceIn(0f, 1f),
                )
            },
            shadow = null,
            innerShadow = {
                InnerShadow(
                    radius = REST_INNER_RADIUS * ind.innerRadius,
                    offset = DpOffset(0.dp, 0.dp),
                    alpha = (REST_INNER_ALPHA * ind.innerShadow).coerceIn(0f, 1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    color = color.copy(alpha = (color.alpha * ind.fill).coerceIn(0f, 1f)),
                    size = size,
                )
            },
        ),
    )
}

private val INDICATOR_BLUR = 10.dp

private const val INDICATOR_SATURATION = 2.4f

private const val INDICATOR_BRIGHTNESS = 0.05f

private const val REST_RIM_ALPHA = 0.45f
private val REST_INNER_RADIUS = 5.dp
private const val REST_INNER_ALPHA = 0.55f

private val LIFT_INNER_RADIUS = 10.dp

private class PageAndBarBackdrop(
    private val page: Backdrop,
    private val bar: Backdrop?,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        bar?.let { with(it) { drawBackdrop(density, coordinates, layerBlock) } }
    }
}

@Composable
internal fun GlassSelectionPill(
    motion: GlassPillMotion,
    fillColor: Color,
    barGlass: LayerBackdrop,
    tabRow: LayerBackdrop,
    modifier: Modifier,
    iconTint: Color = Color.Unspecified,
) {
    val page = LocalAppBackdrop.current
    val ind by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    val shape = remember { RoundedCornerShape(percent = 50) }
    val darkGlass = glassContentColor() == Color.White
    val edge = if (darkGlass) {
        Color.White.copy(alpha = BAR_RIM_ALPHA)
    } else {
        Color.Black.copy(alpha = BAR_RIM_ALPHA * 0.7f)
    }
    val iconLiftMul = ind.iconLift
    val fillMul = ind.fill
    val tabsBackdrop = remember(tabRow, iconTint, motion, iconLiftMul) {
        PillTabsBackdrop(tabRow, iconTint) { 1f + PILL_ICON_LIFT_SCALE * iconLiftMul * motion.material }
    }
    val backdrop = remember(page, barGlass, tabsBackdrop, fillColor, edge, fillMul) {
        SelectionPillBackdrop(page, barGlass, tabsBackdrop, Color.Unspecified, edge) {
            fillColor.copy(alpha = (fillColor.alpha * fillMul * (1f - motion.material)).coerceIn(0f, 1f))
        }
    }
    Box(
        modifier.graphicsLayer { alpha = 1f - motion.blurMix }.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val restAmount = if (ind.restLens) PILL_LENS_AMOUNT.toPx() * ind.restRefraction else 0f
                    val restHeight = if (ind.restLens) PILL_LENS_HEIGHT.toPx() * ind.restRefractionWidth else 0f
                    val liftAmount = PILL_LENS_AMOUNT.toPx() * ind.liftRefraction
                    val liftHeight = PILL_LENS_HEIGHT.toPx() * ind.liftRefractionWidth
                    lens(
                        refractionHeight = restHeight + (liftHeight - restHeight) * motion.glassPresence,
                        refractionAmount = restAmount + (liftAmount - restAmount) * motion.material,
                        depthEffect = ind.depth,
                        chromaticAberration = ind.rainbow > 0f,
                        chromaticStrength = PILL_CHROMATIC_STRENGTH * ind.rainbow * motion.material,
                    )
                }
            },
            highlight = {
                val live = motion.material * rimOf(motion.glassPresence)
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = restBlend((REST_RIM_ALPHA * ind.rim).coerceIn(0f, 1f), live),
                )
            },
            shadow = {
                val alpha = motion.material * rimOf(motion.glassPresence)
                if (alpha > 0f) PILL_SHADOW.copy(alpha = (alpha * ind.shadow).coerceIn(0f, 1f)) else null
            },
            innerShadow = {
                val live = motion.material * rimOf(motion.glassPresence)
                InnerShadow(
                    radius = (REST_INNER_RADIUS + (LIFT_INNER_RADIUS - REST_INNER_RADIUS) * live) * ind.innerRadius,
                    offset = DpOffset(0.dp, 0.dp),
                    alpha = restBlend((REST_INNER_ALPHA * ind.innerShadow).coerceIn(0f, 1f), live),
                )
            },
        ),
    )
}

private val PILL_LENS_HEIGHT = 16.dp
private val PILL_LENS_AMOUNT = 32.dp

private const val PILL_ICON_LIFT_SCALE = 0.2f

private const val PILL_CHROMATIC_STRENGTH = 0.15f

private fun rimOf(presence: Float): Float = ((presence - 0.45f) / 0.55f).coerceIn(0f, 1f)

private fun restBlend(rest: Float, live: Float): Float = (rest + (1f - rest) * live).coerceIn(0f, 1f)

private val BAR_RIM_WIDTH = 1.dp
private const val BAR_RIM_ALPHA = 0.35f

private val BAR_HALO_REACH = 8.dp
private const val BAR_HALO_ALPHA = 0.10f
private const val BAR_HALO_STEPS = 7

private val PILL_SHADOW = Shadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.3f),
)

private class SelectionPillBackdrop(
    private val page: Backdrop,
    private val bar: LayerBackdrop,
    private val tabs: Backdrop,
    private val tint: Color,
    private val edge: Color,
    private val fill: () -> Color,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        with(bar) { drawBackdrop(density, coordinates, layerBlock) }
        drawBarRim(bar, coordinates, edge)
        val color = fill()
        if (color.alpha > 0f) {
            drawRect(
                color = color,
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3, size.height * 3),
            )
        }
        if (tint != Color.Unspecified) {
            drawTinted(tint) { with(tabs) { drawBackdrop(density, coordinates, layerBlock) } }
        } else {
            with(tabs) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

private fun DrawScope.drawBarRim(bar: LayerBackdrop, coordinates: LayoutCoordinates?, edge: Color) {
    val barCoordinates = bar.recordedLayerCoordinates ?: return
    val here = coordinates ?: return
    if (!barCoordinates.isAttached || !here.isAttached) return
    val offset = try {
        barCoordinates.localPositionOf(here)
    } catch (_: Exception) {
        here.positionInWindow() - barCoordinates.positionInWindow()
    }
    val barSize = barCoordinates.size.toSize()
    if (barSize.width <= 0f || barSize.height <= 0f) return
    val strokeWidth = BAR_RIM_WIDTH.toPx()
    val radius = minOf(barSize.width, barSize.height) / 2f
    val haloReach = BAR_HALO_REACH.toPx()
    val ring = haloReach / BAR_HALO_STEPS
    val rim = Brush.verticalGradient(
        colors = listOf(edge, edge.copy(alpha = edge.alpha * 0.3f), edge),
        startY = 0f,
        endY = barSize.height,
    )
    translate(-offset.x, -offset.y) {
        for (i in 1..BAR_HALO_STEPS) {
            val t = i / BAR_HALO_STEPS.toFloat()
            val grow = haloReach * t
            drawRoundRect(
                color = Color.Black.copy(alpha = BAR_HALO_ALPHA * (1f - t)),
                topLeft = Offset(-grow, -grow),
                size = Size(barSize.width + grow * 2f, barSize.height + grow * 2f),
                cornerRadius = CornerRadius(radius + grow, radius + grow),
                style = Stroke(width = ring),
            )
        }
        drawRoundRect(
            brush = rim,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(barSize.width - strokeWidth, barSize.height - strokeWidth),
            cornerRadius = CornerRadius(radius - strokeWidth / 2f, radius - strokeWidth / 2f),
            style = Stroke(width = strokeWidth),
        )
    }
}

private class PillTabsBackdrop(
    private val tabs: Backdrop,
    private val tint: Color,
    private val iconScale: () -> Float = { 1f },
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val s = iconScale()
        val draw: DrawScope.() -> Unit = {
            scale(s, pivot = center) {
                with(tabs) { drawBackdrop(density, coordinates, layerBlock) }
            }
        }
        if (tint != Color.Unspecified) drawTinted(tint, draw) else draw()
    }
}

private fun DrawScope.drawTinted(tint: Color, block: DrawScope.() -> Unit) {
    val paint = Paint().apply { colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn) }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f), paint)
    block()
    canvas.restore()
}
