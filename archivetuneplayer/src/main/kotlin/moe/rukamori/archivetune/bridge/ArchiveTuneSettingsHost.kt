/*
 * BRIDGE — the hosting entry point for ArchiveTune's own settings pages.
 *
 * What the app mounts to show one of the ported settings pages (Appearance,
 * Player, Lyrics, …). The pages are ArchiveTune's, copied byte-for-byte; they
 * navigate between themselves through a NavController with ArchiveTune's
 * route strings, so this hosts a small NavHost carrying exactly the routes
 * the copied pages navigate to. Routes that lead to ArchiveTune integrations
 * this app does not carry (accounts, sources, AOD, icon packs, …) land on a
 * plain "not part of this app" page instead of throwing, so no row in a
 * copied page can crash the app.
 *
 * The setup the pages read during their very first composition (the
 * preference store, the entry points' context, the view-model registry) runs
 * in a remember, before the NavHost composes — the same ordering rule as
 * ArchiveTunePlayerHost.
 */

package moe.rukamori.archivetune.bridge

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphNavigator
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.di.BridgeEntryPoints
import moe.rukamori.archivetune.ui.screens.settings.AppearanceExtrasSettings
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.screens.settings.CanvasSettingsScreen
import moe.rukamori.archivetune.ui.screens.settings.CustomizeBackground
import moe.rukamori.archivetune.ui.screens.settings.LiquidGlassSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsRomanisationSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsSettings
import moe.rukamori.archivetune.ui.screens.settings.PlayerSettings
import moe.rukamori.archivetune.utils.PreferenceStore

/** The ported pages a host can open, each with ArchiveTune's own route. */
enum class ArchiveTuneSettingsPageId(
    val route: String,
    /** The page's own title, from ArchiveTune's catalogue (all its locales). */
    @param:StringRes val title: Int,
) {
    APPEARANCE("settings/appearance", R.string.appearance),
    APPEARANCE_EXTRAS("settings/appearance/extras", R.string.extras),
    PLAYER("settings/player", R.string.player),
    LYRICS("settings/lyrics", R.string.lyrics),
    ROMANISATION("settings/lyrics/romanisation", R.string.romanization),
    BACKGROUND("customize_background", R.string.customize_background_title),
    LIQUID_GLASS("settings/appearance/liquid_glass", R.string.liquid_glass),
    CANVAS("settings/player/canvas", R.string.canvas_settings_title),
    ;

    companion object {
        fun fromName(name: String?): ArchiveTuneSettingsPageId? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Routes the copied pages navigate to that lead to ArchiveTune integrations
 * this app does not carry. Each gets an inert page.
 */
internal val UnportedSettingsRoutes =
    listOf(
        "settings/appearance/aod_customized",
        "settings/appearance/icon",
        "settings/appearance/navigation_bar",
        "settings/language_packs",
        "settings/lyrics/providers",
        "settings/player/audiophile",
        "settings/sources",
    )

/** Same setup ArchiveTunePlayerHost does, for a settings page mounted on its own. */
fun ensureArchiveTuneSettingsReady(context: Context) {
    val app = context.applicationContext
    PreferenceStore.start(app)
    if (BridgeEntryPoints.context == null) BridgeEntryPoints.context = app
    BridgeViewModels.init(app)
}

/**
 * One ported ArchiveTune settings page, full screen, with back leaving it
 * through [onBack] once the page's own back stack is empty.
 */
@Composable
fun ArchiveTuneSettingsPage(
    page: ArchiveTuneSettingsPageId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Before anything below composes — see the file comment.
    remember(context) { ensureArchiveTuneSettingsReady(context) }

    val currentOnBack by rememberUpdatedState(onBack)
    val navController = remember(context) { BridgeSettingsNavController(context) { currentOnBack() } }

    val statusBarTop = stableTopInset()
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides WindowInsets.systemBars,
        // No player is mounted under a settings page; the one page that
        // reads it (the canvas check) falls back to its probe song.
        LocalPlayerConnection provides null,
        LocalStableSystemBarsTopPadding provides statusBarTop,
    ) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            NavHost(navController = navController, startDestination = page.route) {
                composable(ArchiveTuneSettingsPageId.APPEARANCE.route) { AppearanceSettings(navController) }
                composable(ArchiveTuneSettingsPageId.APPEARANCE_EXTRAS.route) { AppearanceExtrasSettings(navController) }
                composable(ArchiveTuneSettingsPageId.PLAYER.route) { PlayerSettings(navController) }
                composable(ArchiveTuneSettingsPageId.LYRICS.route) { LyricsSettings(navController = navController) }
                composable(ArchiveTuneSettingsPageId.ROMANISATION.route) { LyricsRomanisationSettings(navController = navController) }
                composable(ArchiveTuneSettingsPageId.BACKGROUND.route) { CustomizeBackground(navController) }
                composable(ArchiveTuneSettingsPageId.LIQUID_GLASS.route) { LiquidGlassSettings(navController = navController) }
                composable(ArchiveTuneSettingsPageId.CANVAS.route) { CanvasSettingsScreen(navController = navController) }
                UnportedSettingsRoutes.forEach { route ->
                    composable(route) { UnportedSettingsPage(onBack = { navController.navigateUp() }) }
                }
            }
        }
    }
}

/** Where a row leading to an ArchiveTune-only integration goes. */
@Composable
private fun UnportedSettingsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.bridge_settings_unported_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.bridge_settings_unported_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.bridge_settings_back)) }
    }
}

/**
 * A NavHostController whose "up" from the page it was opened on leaves the
 * host (the copied pages' back arrows call navigateUp / popBackStack, which
 * on their own would do nothing at the root of the stack).
 *
 * The graph navigator NavHost's createGraph looks up ("navigation") is added
 * alongside the composable and dialog ones — createNavController() installs
 * all three on the controllers navigation-compose itself hands out, and the
 * NavHost below dies with "Could not find Navigator with name 'navigation'"
 * without it. ComposeNavGraphNavigator would carry the same name but is
 * internal to navigation-compose; this public twin is what non-compose hosts
 * use, and nothing on these pages' flat graph misses its compose extras.
 */
private class BridgeSettingsNavController(
    context: Context,
    private val onExit: () -> Unit,
) : NavHostController(context) {
    init {
        navigatorProvider.addNavigator(NavGraphNavigator(navigatorProvider))
        navigatorProvider.addNavigator(ComposeNavigator())
        navigatorProvider.addNavigator(DialogNavigator())
    }

    override fun navigateUp(): Boolean {
        if (previousBackStackEntry == null) {
            onExit()
            return true
        }
        return super.navigateUp()
    }

    override fun popBackStack(): Boolean {
        if (previousBackStackEntry == null) {
            onExit()
            return true
        }
        return super.popBackStack()
    }
}
