package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.impl.LoginIdConfigurationProviderImpl
import com.ownid.sdk.capability.impl.LoginIdValidatorImpl
import com.ownid.sdk.capability.impl.SerializationImpl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
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
    fun accessTokenLoginIdAcceptsActualStringSubjects() {
        val cases = listOf(
            "null" to LoginId("null", LoginId.Type.Email),
            "123" to LoginId("123", LoginId.Type.Email),
            "true" to LoginId("true", LoginId.Type.Email),
            "Email:\u03b4\u03bf\u03ba\u03b9\u03bc\u03ae@example.test" to LoginId("\u03b4\u03bf\u03ba\u03b9\u03bc\u03ae@example.test", LoginId.Type.Email)
        )

        cases.forEach { (subject, expected) ->
            val payload = """{"sub":${json.encodeToString(subject)}}"""

            assertThat(accessToken(payload).getLoginId(SerializationImpl(), TokenLoginIdValidator)).isEqualTo(expected)
        }
    }

    @Test
    fun accessTokenLoginIdRejectsNonStringSubjects() {
        listOf("null", "123", "true", "{}", "[]").forEach { subject ->
            val error = assertThrows(TokenException::class.java) {
                accessToken("""{"sub":$subject}""").getLoginId(SerializationImpl(), TokenLoginIdValidator)
            }

            assertThat(error.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        }
    }

    @Test
    fun accessTokenLoginIdRequiresThreeNonEmptyUnpaddedBase64UrlSegments() {
        val payload = """{"sub":"Email:user@example.test"}""".encodeToByteArray().encodeToBase64UrlSafe()
        val validToken = AccessToken("header.$payload.signature")

        assertThat(validToken.getLoginId(SerializationImpl(), TokenLoginIdValidator))
            .isEqualTo(LoginId("user@example.test", LoginId.Type.Email))

        val invalidTokens = buildList {
            addAll(
                listOf(
                    "",
                    "header",
                    "header.$payload",
                    "header.$payload.signature.extra",
                    ".$payload.signature",
                    "header..signature",
                    "header.$payload."
                )
            )
            listOf('=', ' ', '+', '/', '*').forEach { forbidden ->
                add("head${forbidden}er.$payload.signature")
                add("header.$payload$forbidden.signature")
                add("header.$payload.sign${forbidden}ature")
            }
        }

        invalidTokens.forEach { token ->
            val error = assertThrows(TokenException::class.java) {
                AccessToken(token).getLoginId(SerializationImpl(), TokenLoginIdValidator)
            }

            assertThat(error.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        }
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

    @Test
    fun untypedTokenSubjectUsesEmbeddedInferenceAndBothFormsUseConfiguredValidation() {
        val configuration = LoginIdConfigurationProviderImpl().apply {
            setConfiguration(listOf(LoginId.Type.PhoneNumber), mapOf(LoginId.Type.PhoneNumber to "ext-[0-9]+".toRegex()))
        }
        val validator = LoginIdValidatorImpl(configuration)
        assertThat(accessToken("""{"sub":"PhoneNumber:ext-123"}""").getLoginId(SerializationImpl(), validator))
            .isEqualTo(LoginId("ext-123", LoginId.Type.PhoneNumber))
        val inferredFailure = assertThrows(TokenException::class.java) {
            accessToken("""{"sub":"ext-123"}""").getLoginId(SerializationImpl(), validator)
        }
        val rawValidationFailure = assertThrows(TokenException::class.java) {
            accessToken("""{"sub":"1234567"}""").getLoginId(SerializationImpl(), validator)
        }
        val typedFailure = assertThrows(TokenException::class.java) {
            accessToken("""{"sub":"PhoneNumber:1234567"}""").getLoginId(SerializationImpl(), validator)
        }
        assertThat(inferredFailure.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(rawValidationFailure.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(typedFailure.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(typedFailure.cause).isInstanceOf(LoginIdValidationException.ValidationFailed::class.java)
    }

    private fun accessToken(payload: String): AccessToken =
        AccessToken("header.${payload.encodeToByteArray().encodeToBase64UrlSafe()}.signature")
}

private object TokenLoginIdValidator : LoginIdValidator {
    override fun determineLoginIdType(loginId: String): LoginId.Type = LoginId.Type.Email

    override fun validate(loginId: LoginId): LoginId = loginId
}
