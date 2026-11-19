/*
 * BRIDGE — replaces ArchiveTune's ui/component/TagsManagementDialog.kt.
 *
 * Playlist tags live in ArchiveTune's Room database (PlaylistTagsViewModel
 * over its DAOs), which this app does not run: its playlists are its own.
 * Same signature; opening it tells the listener so through the host and
 * closes again, rather than showing an editor whose edits go nowhere.
 */
package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import moe.rukamori.archivetune.bridge.ArchiveTuneBridge

@Composable
fun TagsManagementDialog(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        ArchiveTuneBridge.get()?.showMessage("Playlist tags are managed in Neiro's library")
        onDismiss()
    }
}
