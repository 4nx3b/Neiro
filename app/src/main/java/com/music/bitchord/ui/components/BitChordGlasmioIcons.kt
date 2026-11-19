package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.music.bitchord.ui.icons.BitChordIcons
import io.github.neoruaa.glasmio.icons.GlasmioIconResolver
import io.github.neoruaa.glasmio.icons.MaterialGlasmioIconResolver

/**
 * The app's answer to Glasmio's SF Symbol names: the four tab glyphs the
 * navigation bar asks for resolve to BitChord's own icons — the same ones the
 * tabs have always drawn — and everything else falls through to Glasmio's
 * stock Material mapping, so the fork's controls keep their glyph fallback
 * without shipping an SF Symbols font.
 */
val BitChordGlasmioIconResolver = GlasmioIconResolver { systemName ->
    when {
        systemName.startsWith("house") -> BitChordIcons.Home
        systemName.startsWith("safari") -> BitChordIcons.TabExplore
        systemName.startsWith("music.note.list") -> BitChordIcons.TabLibrary
        systemName.startsWith("magnifyingglass") -> BitChordIcons.TabSearch
        else -> null
    } ?: MaterialGlasmioIconResolver.resolve(systemName)
}
