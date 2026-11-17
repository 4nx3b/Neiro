package com.music.bitchord.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.NetPolicy
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.ChoiceAlert
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.components.TextValueAlert
import com.music.bitchord.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The Internet page, ported from ArchiveTune's Internet settings: the region
 * YouTube Music requests pin themselves to, DNS over HTTPS, and a proxy the
 * whole shared client routes through — the categorization is upstream's. The
 * warning card leads, because the two rows below it are the ones a listener
 * in a blocked region reaches for, and the choices they enable are the ones
 * worth a caution. ArchiveTune's IP rotation and stream bypass stay off this
 * page — nothing behind them exists here — and the region picker is the
 * app's own choice page rather than upstream's dialog, because a
 * thirty-six-option list is a page, not an alert.
 */
@Composable
fun InternetScreen(
    /** Opens the region picker — the choice page, like every long list here. */
    onOpenRegion: () -> Unit,
    /** Opens one of this page's dialogs — see [InternetDialogHost]. */
    onOpenDialog: (InternetDialog) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val region by AppSettings.ytMusicRegion.collectAsStateWithLifecycle()
    val dnsOverHttpsEnabled by AppSettings.dnsOverHttpsEnabled.collectAsStateWithLifecycle()
    val dnsOverHttpsProvider by AppSettings.dnsOverHttpsProvider.collectAsStateWithLifecycle()
    val proxyEnabled by AppSettings.proxyEnabled.collectAsStateWithLifecycle()
    val proxyType by AppSettings.proxyType.collectAsStateWithLifecycle()
    val proxyHost by AppSettings.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by AppSettings.proxyPort.collectAsStateWithLifecycle()
    val proxyUsername by AppSettings.proxyUsername.collectAsStateWithLifecycle()
    val proxyPassword by AppSettings.proxyPassword.collectAsStateWithLifecycle()
    val systemDefaultLabel = stringResource(R.string.system_default)

    // The proxy test's outcome, kept across recompositions so it is still on
    // the row when the coroutine that fetched it comes back.
    var testingProxy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.internet))
        InternetWarningCard()

        // ── YouTube Music region ────────────────────────────────────────
        SettingsGroup(header = stringResource(R.string.youtube_music_region)) {
            SettingsRow(
                icon = SfIcons.Pin,
                title = stringResource(R.string.youtube_music_region),
                subtitle = stringResource(R.string.youtube_music_region_desc),
                value = ytRegionLabel(region, systemDefaultLabel),
                onClick = onOpenRegion,
            )
        }

        // ── DNS over HTTPS ───────────────────────────────────────────────
        SettingsGroup(header = stringResource(R.string.dns_over_https)) {
            SettingsRow(
                icon = SfIcons.Dns,
                title = stringResource(R.string.dns_over_https),
                subtitle = stringResource(R.string.dns_over_https_desc),
                trailing = {
                    IosSwitch(
                        checked = dnsOverHttpsEnabled,
                        onCheckedChange = AppSettings::setDnsOverHttpsEnabled,
                    )
                },
                onClick = { AppSettings.setDnsOverHttpsEnabled(!dnsOverHttpsEnabled) },
            )
            if (dnsOverHttpsEnabled) {
                RowDivider()
                SettingsRow(
                    icon = SfIcons.Language,
                    title = stringResource(R.string.dns_provider),
                    value = dnsOverHttpsProvider,
                    onClick = { onOpenDialog(InternetDialog.DNS_PROVIDER) },
                )
            }
        }

        // ── Proxy ────────────────────────────────────────────────────────
        SettingsGroup(header = stringResource(R.string.proxy)) {
            SettingsRow(
                icon = SfIcons.Wifi,
                title = stringResource(R.string.enable_proxy),
                trailing = {
                    IosSwitch(
                        checked = proxyEnabled,
                        onCheckedChange = AppSettings::setProxyEnabled,
                    )
                },
                onClick = { AppSettings.setProxyEnabled(!proxyEnabled) },
            )
            if (proxyEnabled) {
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.proxy_type),
                    value = proxyType,
                    onClick = { onOpenDialog(InternetDialog.PROXY_TYPE) },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.proxy_host),
                    value = proxyHost.ifBlank { null },
                    onClick = { onOpenDialog(InternetDialog.PROXY_HOST) },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.proxy_port),
                    value = proxyPort.toString(),
                    onClick = { onOpenDialog(InternetDialog.PROXY_PORT) },
                )
            }
        }

        // ── Proxy Authorization (optional) ────────────────────────────────
        if (proxyEnabled) {
            SettingsGroup(header = stringResource(R.string.proxy_auth)) {
                SettingsRow(
                    title = stringResource(R.string.username),
                    value = proxyUsername.ifBlank { null },
                    onClick = { onOpenDialog(InternetDialog.PROXY_USERNAME) },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.password),
                    // Never spelled out: the row only needs to say whether one is set.
                    value = "••••••".takeIf { proxyPassword.isNotBlank() },
                    onClick = { onOpenDialog(InternetDialog.PROXY_PASSWORD) },
                )
                RowDivider()
                SettingsRow(
                    icon = SfIcons.Check,
                    title = stringResource(R.string.test_proxy_connection),
                    subtitle = when {
                        testingProxy -> stringResource(R.string.testing_proxy_connection)
                        else -> testResult
                    },
                    enabled = !testingProxy,
                    onClick = {
                        if (!testingProxy) {
                            testingProxy = true
                            testResult = null
                            scope.launch(Dispatchers.IO) {
                                val outcome: String =
                                    if (proxyHost.isBlank() || proxyPort !in 1..65535) {
                                        context.getString(
                                            R.string.proxy_connection_failed,
                                            context.getString(R.string.proxy_connection_invalid_configuration),
                                        )
                                    } else {
                                        // Built on NetPolicy's live policy, so what is
                                        // tested is exactly what the rows show: the
                                        // proxy, its credentials and the DoH resolver
                                        // the shared client would consult.
                                        try {
                                            val client = OkHttpClient.Builder()
                                                .proxySelector(NetPolicy.selector)
                                                .dns(NetPolicy.routingDns)
                                                .proxyAuthenticator(NetPolicy.proxyAuthenticator)
                                                .connectTimeout(10, TimeUnit.SECONDS)
                                                .readTimeout(10, TimeUnit.SECONDS)
                                                .build()
                                            val request = Request.Builder()
                                                .url("https://music.youtube.com/generate_204")
                                                .build()
                                            client.newCall(request).execute().use { response ->
                                                if (response.isSuccessful || response.code == 204) {
                                                    context.getString(R.string.proxy_connection_success)
                                                } else {
                                                    context.getString(
                                                        R.string.proxy_connection_failed,
                                                        "HTTP ${response.code}",
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            context.getString(
                                                R.string.proxy_connection_failed,
                                                e.message ?: context.getString(R.string.error_unknown),
                                            )
                                        }
                                    }
                                withContext(Dispatchers.Main) {
                                    testResult = outcome
                                    testingProxy = false
                                }
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The country codes ArchiveTune's region picker offers, ported as-is — the
 * same thirty-five countries in the same order, so the list upstream's
 * listeners know is the list this one shows.
 */
internal val YT_MUSIC_REGIONS = linkedMapOf(
    "JP" to "Japan",
    "KR" to "South Korea",
    "US" to "United States",
    "GB" to "United Kingdom",
    "CN" to "China",
    "TW" to "Taiwan",
    "HK" to "Hong Kong",
    "FR" to "France",
    "DE" to "Germany",
    "ES" to "Spain",
    "MX" to "Mexico",
    "BR" to "Brazil",
    "RU" to "Russia",
    "IT" to "Italy",
    "NL" to "Netherlands",
    "PL" to "Poland",
    "TR" to "Turkey",
    "AU" to "Australia",
    "CA" to "Canada",
    "IN" to "India",
    "ID" to "Indonesia",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "NG" to "Nigeria",
    "PH" to "Philippines",
    "MY" to "Malaysia",
    "SG" to "Singapore",
    "AR" to "Argentina",
    "CL" to "Chile",
    "CO" to "Colombia",
    "PE" to "Peru",
    "ZA" to "South Africa",
    "EG" to "Egypt",
    "SA" to "Saudi Arabia",
    "AE" to "United Arab Emirates",
)

/** The picker's name for a stored region code — [NetPolicy.SYSTEM_DEFAULT] included. */
internal fun ytRegionLabel(code: String, systemDefaultLabel: String): String =
    if (code == NetPolicy.SYSTEM_DEFAULT) systemDefaultLabel else YT_MUSIC_REGIONS[code] ?: code

/**
 * Applies a region change the way ArchiveTune does: the visitor data and every
 * cached response were minted in the old region, so the process is replaced
 * rather than half-invalidated. The caller holds the process open briefly
 * after the toast, which is why this is a plain function and not an effect.
 */
internal fun restartForRegionChange(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.youtube_music_region_restarting),
        Toast.LENGTH_SHORT,
    ).show()
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
    }
    exitProcess(0)
}

/** Upstream's warning card: what DoH can and cannot fix, and what a free proxy costs. */
@Composable
private fun InternetWarningCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .padding(top = 12.dp)
            .clip(GroupShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = SfIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.internet_warning_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(
            text = stringResource(R.string.internet_warning_doh),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.internet_warning_proxy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Which of the Internet page's dialogs is open. Rendered by the activity —
 * like Discord's — so the scrim covers the tab bar and mini player, and so
 * the drafts survive the dialog's own recomposition alone.
 */
enum class InternetDialog {
    DNS_PROVIDER,
    PROXY_TYPE,
    CUSTOM_DNS_URL,
    PROXY_HOST,
    PROXY_PORT,
    PROXY_USERNAME,
    PROXY_PASSWORD,
}

/**
 * The Internet page's dialogs, rendered by the activity so their scrim covers
 * the tab bar and mini player like every other alert in the app. A pick
 * commits at once; the text fields draft into a local value that only a Save
 * writes, because half-entered is not a state the app should route through.
 */
@Composable
fun InternetDialogHost(
    which: InternetDialog,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    when (which) {
        InternetDialog.DNS_PROVIDER -> {
            val current by AppSettings.dnsOverHttpsProvider.collectAsStateWithLifecycle()
            ChoiceAlert(
                hazeState = hazeState,
                title = stringResource(R.string.dns_provider),
                message = null,
                // NetPolicy.dohUrl's own list, in its order.
                options = listOf("Cloudflare", "Google", "AdGuard", "Quad9", "Custom"),
                selected = current,
                label = { it },
                onSelect = {
                    AppSettings.setDnsOverHttpsProvider(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.PROXY_TYPE -> {
            val current by AppSettings.proxyType.collectAsStateWithLifecycle()
            ChoiceAlert(
                hazeState = hazeState,
                title = stringResource(R.string.proxy_type),
                message = null,
                options = listOf("HTTP", "SOCKS"),
                selected = current,
                label = { it },
                onSelect = {
                    AppSettings.setProxyType(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.CUSTOM_DNS_URL -> {
            val stored by AppSettings.dnsOverHttpsCustomUrl.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(stored) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.dns_custom_url),
                message = stringResource(R.string.dns_custom_url_message),
                placeholder = "https://",
                value = input,
                onValueChange = { input = it },
                // The same bar NetPolicy.dohUrl holds the Custom provider to:
                // a custom resolver this page cannot use is not worth saving.
                saveEnabled = input.startsWith("https://"),
                onSave = {
                    AppSettings.setDnsOverHttpsCustomUrl(input.trim())
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.PROXY_HOST -> {
            val stored by AppSettings.proxyHost.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(stored) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.proxy_host),
                message = stringResource(R.string.proxy_host_message),
                placeholder = "192.168.1.1",
                value = input,
                onValueChange = { input = it },
                saveEnabled = input.isNotBlank(),
                onSave = {
                    AppSettings.setProxyHost(input.trim())
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.PROXY_PORT -> {
            val stored by AppSettings.proxyPort.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(stored.toString()) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.proxy_port),
                message = stringResource(R.string.proxy_port_message),
                placeholder = "8080",
                value = input,
                onValueChange = { input = it.filter { it.isDigit() } },
                // The range NetPolicy.updateProxy itself accepts.
                saveEnabled = (input.toIntOrNull() ?: 0) in 1..65535,
                onSave = {
                    input.toIntOrNull()?.let(AppSettings::setProxyPort)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.PROXY_USERNAME -> {
            val stored by AppSettings.proxyUsername.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(stored) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.username),
                message = stringResource(R.string.proxy_username_message),
                placeholder = "",
                value = input,
                onValueChange = { input = it },
                // Blank is a real value here: authorization is optional, and
                // clearing it is how a listener turns auth off.
                onSave = {
                    AppSettings.setProxyUsername(input)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        InternetDialog.PROXY_PASSWORD -> {
            val stored by AppSettings.proxyPassword.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(stored) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.password),
                message = stringResource(R.string.proxy_password_message),
                placeholder = "",
                value = input,
                onValueChange = { input = it },
                onSave = {
                    AppSettings.setProxyPassword(input)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}
