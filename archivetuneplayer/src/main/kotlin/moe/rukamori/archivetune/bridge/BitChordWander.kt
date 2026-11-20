/*
 * BRIDGE(neiro) — the BitChord style's background wander, for the ported
 * Apple Music and TikTok styles. A verbatim copy of sharedUi's
 * ArtworkMeshBackdrop BlurWanderDrift (18 dp radius, ±3° per leg, 5-10 s
 * legs) and its frame loop, so all three styles drift the same way.
 * Neiro — GNU General Public License v3.0 or later.
 */
package moe.rukamori.archivetune.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

class BitChordWander(
    private val maxDriftDp: Float = DefaultWanderRadiusDp,
    private val minLegDurationMs: Float = MinLegDurationMs,
    private val maxLegDurationMs: Float = MaxLegDurationMs,
) {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)
    private val rotationState = mutableFloatStateOf(0f)

    val xDp: FloatState get() = xState
    val yDp: FloatState get() = yState
    val rotationDeg: FloatState get() = rotationState

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var fromRotation = 0f
    private var toRotation = 0f
    private var legDurationMs = 0f
    private var legElapsedMs = 0f

    init {
        startNextLeg()
    }

    fun advance(deltaMs: Float) {
        if (deltaMs <= 0f) return
        legElapsedMs += deltaMs
        while (legElapsedMs >= legDurationMs) {
            legElapsedMs -= legDurationMs
            startNextLeg()
        }

        val t = legElapsedMs / legDurationMs
        val eased = 0.5f - 0.5f * kotlin.math.cos(kotlin.math.PI.toFloat() * t)
        xState.floatValue = fromX + (toX - fromX) * eased
        yState.floatValue = fromY + (toY - fromY) * eased
        rotationState.floatValue = fromRotation + (toRotation - fromRotation) * eased
    }

    private fun startNextLeg() {
        fromX = toX
        fromY = toY
        fromRotation = toRotation

        val radius = maxDriftDp * kotlin.math.sqrt(kotlin.random.Random.nextFloat())
        val angle = kotlin.random.Random.nextFloat() * TwoPi
        toX = kotlin.math.cos(angle) * radius
        toY = kotlin.math.sin(angle) * radius
        toRotation = fromRotation + (kotlin.random.Random.nextFloat() - 0.5f) * 6f
        legDurationMs = minLegDurationMs + kotlin.random.Random.nextFloat() * (maxLegDurationMs - minLegDurationMs)
    }

    companion object {
        const val DefaultWanderRadiusDp = 18f
        const val MinLegDurationMs = 5_000f
        const val MaxLegDurationMs = 10_000f
        const val Overscan = 1.06f
        private const val TwoPi = (2 * kotlin.math.PI).toFloat()
    }
}

@Composable
fun rememberBitChordWander(active: Boolean): BitChordWander {
    val wander = remember { BitChordWander() }
    if (active) {
        LaunchedEffect(wander) {
            var last = System.nanoTime()
            while (true) {
                withFrameNanos {
                    val now = System.nanoTime()
                    val deltaMs = (now - last).coerceIn(0L, 100_000_000L) / 1_000_000f
                    last = now
                    wander.advance(deltaMs)
                }
            }
        }
    }
    return wander
}

fun Modifier.bitChordWander(wander: BitChordWander): Modifier =
    graphicsLayer {
        scaleX = BitChordWander.Overscan
        scaleY = BitChordWander.Overscan
        translationX = wander.xDp.floatValue.dp.toPx()
        translationY = wander.yDp.floatValue.dp.toPx()
        rotationZ = wander.rotationDeg.floatValue
    }
