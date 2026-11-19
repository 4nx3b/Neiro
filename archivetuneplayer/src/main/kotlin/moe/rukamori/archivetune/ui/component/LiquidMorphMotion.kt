package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/*
 * The liquid-morph dropdown spring, ported from iOS 27's UIKitCore/
 * AnimationKit: the spring MorphAnimationSettings.liquidMorph copies to
 * width, height, position and transform — stiffness (2 * PI / response)^2
 * for the native 0.3 s response and unit mass, damping 0.8 (a light
 * settle), visible from 0.1% of travel. This is the motion this stack's
 * menus and sheets open on.
 */
internal val LiquidDropdownMorphSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 438.64908f,
    visibilityThreshold = 0.001f,
)

/** The liquid-morph entrance alpha: 120 ms. */
internal const val LiquidMorphAlphaInMs = 120

/** The liquid-morph collapse: 300 ms. */
internal const val LiquidMorphCloseMs = 300
