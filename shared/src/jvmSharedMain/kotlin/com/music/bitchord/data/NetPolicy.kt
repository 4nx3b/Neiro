package com.music.bitchord.data

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * The app-wide proxy and DNS-over-HTTPS policy, ported from ArchiveTune's
 * Internet settings. [Http.client] installs [selector], [routingDns] and
 * [proxyAuthenticator] once at build time; AppSettings republishes the
 * listener's choices here on every change. OkHttp consults a ProxySelector
 * per connection and a Dns per lookup, so the policy switches live, without
 * rebuilding the client or the pools on top of it.
 */
object NetPolicy {

    /** The sentinel the region preference stores when it follows the locale — ArchiveTune's format. */
    const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

    /**
     * The provider names ArchiveTune's Internet page offers, mapped to their
     * DoH endpoints. Custom only counts with an https URL, and a name that
     * is not on the list answers null, which leaves the system resolver in
     * force rather than guessing at a server.
     */
    internal fun dohUrl(provider: String, customUrl: String): String? =
        when (provider) {
            "Google" -> "https://dns.google/dns-query"
            "Cloudflare" -> "https://cloudflare-dns.com/dns-query"
            "AdGuard" -> "https://dns.adguard.com/dns-query"
            "Quad9" -> "https://dns.quad9.net/dns-query"
            "Custom" -> customUrl.takeIf { it.startsWith("https://") }
            else -> null
        }

    @Volatile
    private var proxy: Proxy? = null

    @Volatile
    private var proxyUsername: String = ""

    @Volatile
    private var proxyPassword: String = ""

    @Volatile
    private var dns: Dns = Dns.SYSTEM

    /**
     * Installs the proxy described by the Internet page. An incomplete or
     * out-of-range configuration uninstalls rather than throwing: the rows
     * are editable one field at a time, and half-entered is not a state the
     * app should fail to start from.
     */
    fun updateProxy(
        enabled: Boolean,
        type: Proxy.Type,
        host: String,
        port: Int,
        username: String,
        password: String,
    ) {
        proxyUsername = username
        proxyPassword = password
        proxy =
            if (enabled && (type == Proxy.Type.HTTP || type == Proxy.Type.SOCKS) &&
                host.isNotBlank() && port in 1..65535
            ) {
                Proxy(type, InetSocketAddress.createUnresolved(host, port))
            } else {
                null
            }
    }

    /** Installs the DoH resolver, or the system one when disabled or unusable. */
    fun updateDns(enabled: Boolean, provider: String, customUrl: String) {
        val url = dohUrl(provider, customUrl)
        dns =
            if (enabled && url != null) {
                // The bootstrap client is deliberately plain: resolving the
                // DoH endpoint itself has to stay on the system resolver, or
                // the lookup would recurse through the policy it replaces.
                runCatching {
                    DnsOverHttps
                        .Builder()
                        .client(OkHttpClient())
                        .url(url.toHttpUrl())
                        .build()
                }.getOrDefault(Dns.SYSTEM)
            } else {
                Dns.SYSTEM
            }
    }

    /** Answers NO_PROXY while nothing is configured — the direct connection this replaces. */
    val selector: ProxySelector =
        object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(proxy ?: Proxy.NO_PROXY)

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        }

    /** Delegates to whatever [updateDns] last installed — [Dns.SYSTEM] until DoH is turned on. */
    val routingDns: Dns =
        Dns { host -> dns.lookup(host) }

    /**
     * Answers a proxy's 407 with the configured credentials once. A challenge
     * that arrives on a request already carrying them means they were
     * rejected, and null gives up instead of re-sending the pair forever.
     */
    val proxyAuthenticator: Authenticator =
        Authenticator { _, response ->
            if (response.request.header("Proxy-Authorization") == null &&
                (proxyUsername.isNotBlank() || proxyPassword.isNotBlank())
            ) {
                response.request
                    .newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(proxyUsername, proxyPassword))
                    .build()
            } else {
                null
            }
        }
}
