package com.music.bitchord

import com.music.bitchord.data.scrobbling.LastFM
import org.junit.Assert.assertEquals
import org.junit.Test

class LastFmProviderTest {

    private val buildKey = "build-api-key"
    private val buildSecret = "build-secret"

    @Test
    fun `the default endpoint passes the stored pair through`() {
        assertEquals(
            "stored" to "stored-secret",
            LastFM.credentialsFor(
                endpoint = LastFM.DEFAULT_API_ENDPOINT,
                storedApiKey = "stored",
                storedSecret = "stored-secret",
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }

    @Test
    fun `a custom endpoint passes the stored pair through`() {
        assertEquals(
            "stored" to "stored-secret",
            LastFM.credentialsFor(
                endpoint = "https://scrobble.example.org/2.0/",
                storedApiKey = "stored",
                storedSecret = "stored-secret",
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }

    @Test
    fun `librefm swaps the build pair for the compat pair`() {
        assertEquals(
            LastFM.LIBREFM_COMPAT_API_KEY to LastFM.LIBREFM_COMPAT_SECRET,
            LastFM.credentialsFor(
                endpoint = LastFM.LIBREFM_API_ENDPOINT,
                storedApiKey = buildKey,
                storedSecret = buildSecret,
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }

    @Test
    fun `librefm with a blank store gets the compat pair`() {
        assertEquals(
            LastFM.LIBREFM_COMPAT_API_KEY to LastFM.LIBREFM_COMPAT_SECRET,
            LastFM.credentialsFor(
                endpoint = LastFM.LIBREFM_API_ENDPOINT,
                storedApiKey = "",
                storedSecret = "",
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }

    @Test
    fun `a user-typed librefm pair is theirs`() {
        assertEquals(
            "my-librefm-key" to "my-librefm-secret",
            LastFM.credentialsFor(
                endpoint = LastFM.LIBREFM_API_ENDPOINT,
                storedApiKey = "my-librefm-key",
                storedSecret = "my-librefm-secret",
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }

    @Test
    fun `a half-overridden pair stays whole - the build half is not swapped out`() {
        assertEquals(
            "my-librefm-key" to buildSecret,
            LastFM.credentialsFor(
                endpoint = LastFM.LIBREFM_API_ENDPOINT,
                storedApiKey = "my-librefm-key",
                storedSecret = buildSecret,
                defaultApiKey = buildKey,
                defaultSecret = buildSecret,
            ),
        )
    }
}
