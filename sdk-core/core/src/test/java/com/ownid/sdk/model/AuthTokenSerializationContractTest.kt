package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

// Covers: MODEL-040, MODEL-050
internal class AuthTokenSerializationContractTest {

    private val json = Json

    @Test
    fun accessTokenSerializesAsPublicObjectWithRawTokenProperty() {
        val token = AccessToken("access-token-value")

        assertThat(json.encodeToString(token)).isEqualTo("""{"token":"access-token-value"}""")
        assertThat(json.decodeFromString<AccessToken>("""{"token":"access-token-value"}""")).isEqualTo(token)
    }

    @Test
    fun authMethodSerializesCanonicalProductValues() {
        val methods = listOf(
            AuthMethod.Otp to "otp",
            AuthMethod.Passkey to "passkey",
            AuthMethod.MagicLink to "magic-link",
            AuthMethod.Password to "password",
            AuthMethod.Deferred to "deferred",
            AuthMethod.SocialGoogle to "social-google",
            AuthMethod.SocialApple to "social-apple",
            AuthMethod.FaceKey to "facekey",
            AuthMethod.Immediate to "immediate",
            AuthMethod.Unknown to "unknown"
        )

        methods.forEach { (method, wireValue) ->
            assertThat(json.encodeToString(method)).isEqualTo("\"$wireValue\"")
            assertThat(method.toString()).isEqualTo(wireValue)
            assertThat(json.decodeFromString<AuthMethod>("\"$wireValue\"")).isEqualTo(method)
        }
    }

    @Test
    fun authMethodDecodesLegacyAliasesAndUnknownFallback() {
        assertThat(json.decodeFromString<AuthMethod>("\"biometrics\"")).isEqualTo(AuthMethod.Passkey)
        assertThat(json.decodeFromString<AuthMethod>("\"desktop-biometrics\"")).isEqualTo(AuthMethod.Passkey)
        assertThat(json.decodeFromString<AuthMethod>("\"email-fallback\"")).isEqualTo(AuthMethod.Otp)
        assertThat(json.decodeFromString<AuthMethod>("\"sms-fallback\"")).isEqualTo(AuthMethod.Otp)
        assertThat(json.decodeFromString<AuthMethod>("\"not-a-known-method\"")).isEqualTo(AuthMethod.Unknown)
    }
}
