package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.BackendSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CredentialSecretCodecTest {
    @Test
    fun roundTripsOAuthAccessSecret() {
        val secret = BackendSecret.OAuthAccess(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAtEpochMillis = 1234L,
        )

        val encoded = CredentialSecretCodec.encode(secret)
        val decoded = CredentialSecretCodec.decode(encoded)

        assertEquals(secret, decoded)
    }

    @Test
    fun roundTripsBearerApiAndConnectionSecrets() {
        listOf(
            BackendSecret.BearerToken("notion-token"),
            BackendSecret.ApiToken("postgres-api-token"),
            BackendSecret.ConnectionString("owned-service-connection"),
        ).forEach { secret ->
            assertEquals(secret, CredentialSecretCodec.decode(CredentialSecretCodec.encode(secret)))
        }
    }

    @Test
    fun encodedSecretDoesNotUsePlainValueOnlyFormat() {
        val encoded = CredentialSecretCodec.encode(BackendSecret.BearerToken("notion-token"))

        assertFalse(encoded == "notion-token")
    }
}
