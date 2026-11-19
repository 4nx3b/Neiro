/*
 * BRIDGE — replaces ArchiveTune's viewmodels/ContentSettingsViewModel.kt.
 *
 * ArchiveTune's lyrics settings page takes this view model as a default
 * parameter but reads nothing from it; the original drives the AI content
 * filter (ArchiveTune's own AI integration, not part of this app). An empty
 * view model, registered in BridgeViewModels, so hiltViewModel() resolves.
 */
package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel

class ContentSettingsViewModel : ViewModel()
