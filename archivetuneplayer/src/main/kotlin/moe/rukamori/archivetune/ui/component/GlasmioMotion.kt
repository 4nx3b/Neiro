package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/*
 * Glasmio's DropdownMorphSpring, ported verbatim (the myself-tsuki fork's
 * IosMenus): the spring UIKitCore/AnimationKit's MorphAnimationSettings
 * liquidMorph copies to width, height, position and transform on iOS 27 —
 * stiffness (2 * PI / response)^2 for the native 0.3 s response and unit
 * mass, damping 0.8 (a light settle), visible from 0.1% of travel. This is
 * the motion the fork's own menus open on, and what this stack's menus and
 * sheets open on now.
 */
internal val GlasmioDropdownMorphSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 438.64908f,
    visibilityThreshold = 0.001f,
)

/** Glasmio's liquid-morph entrance alpha: 120 ms, the fork's morph case. */
internal const val GlasmioMorphAlphaInMs = 120

/** Glasmio's liquid-morph collapse: 300 ms, the fork's morph case. */
internal const val GlasmioMorphCloseMs = 300
