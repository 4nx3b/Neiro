package com.music.bitchord.ui.screens

/**
 * How a card on a Settings page is framed: whether its header shows, and
 * whether it sits flush under the page title or keeps its own top space.
 *
 * On a category page the large title already names the page, so a card whose
 * header would only repeat it drops the header, and the first card sits
 * straight under the title. Every other card keeps its header and its gap.
 * Before this, every card on a category page lost both, so the cards ran
 * into each other with nothing to say where one group ended. Pure, so it is
 * tested on the JVM (SettingsGroupSpacingTest).
 */
internal data class SettingsGroupChrome(val showHeader: Boolean, val flush: Boolean)

internal fun settingsGroupChrome(
    inPage: Boolean,
    first: Boolean,
    header: String?,
    pageTitle: String?,
): SettingsGroupChrome {
    if (!inPage) return SettingsGroupChrome(showHeader = header != null, flush = false)
    val repeatsTitle = header != null && pageTitle != null && header.trim().equals(pageTitle.trim(), ignoreCase = true)
    val showHeader = header != null && !repeatsTitle
    return SettingsGroupChrome(showHeader = showHeader, flush = first && !showHeader)
}
