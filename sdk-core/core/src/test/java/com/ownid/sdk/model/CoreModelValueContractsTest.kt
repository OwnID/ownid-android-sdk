package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

// Covers: MODEL-010, MODEL-150, MODEL-160
internal class CoreModelValueContractsTest {

    private val json = Json

    @Test
    fun timeoutClampsNegativeValuesAndComparesByNormalizedMilliseconds() {
        val negativeTimeout = Timeout(-25)
        val zeroTimeout = Timeout(0)

        assertThat(negativeTimeout.milliseconds).isEqualTo(0L)
        assertThat(negativeTimeout).isEqualTo(zeroTimeout)
        assertThat(negativeTimeout.hashCode()).isEqualTo(zeroTimeout.hashCode())
        assertThat(negativeTimeout.toString()).isEqualTo("0")
        assertThat(negativeTimeout).isNotEqualTo(Timeout(1))
    }

    @Test
    fun challengeIdPreservesRawValueForValueAndDiagnosticText() {
        val rawValue = "  challenge-123  "
        val challengeId = ChallengeId(rawValue)

        assertThat(challengeId.value).isEqualTo(rawValue)
        assertThat(challengeId.toString()).isEqualTo(rawValue)
        assertThat(challengeId).isEqualTo(ChallengeId(rawValue))
        assertThat(challengeId).isNotEqualTo(ChallengeId(rawValue.trim()))
    }

    @Test
    fun operationChannelRedactsChannelOnlyInDiagnosticText() {
        val channel = OperationChannel(channel = "person@example.com", id = "channel-id")
        val sameChannel = OperationChannel(channel = "person@example.com", id = "channel-id")

        assertThat(channel).isEqualTo(sameChannel)
        assertThat(channel.hashCode()).isEqualTo(sameChannel.hashCode())
        assertThat(channel).isNotEqualTo(OperationChannel(channel = "other@example.com", id = "channel-id"))
        assertThat(channel.toString()).isEqualTo("OperationChannel(channel='*', id='channel-id')")
        assertThat(channel.toString()).doesNotContain("person@example.com")
    }

    @Test
    fun tokensCompareByRawTokenButShortenDiagnosticText() {
        val rawToken = "1234567890abcdefghij"

        val accessToken = AccessToken(rawToken)
        assertThat(accessToken).isEqualTo(AccessToken(rawToken))
        assertThat(accessToken.hashCode()).isEqualTo(AccessToken(rawToken).hashCode())
        assertThat(accessToken).isNotEqualTo(AccessToken("$rawToken-x"))
        assertThat(accessToken.toString()).isEqualTo("AccessToken(token=12345678..[4]...cdefghij)")
        assertThat(accessToken.toString()).doesNotContain(rawToken)

        val proofToken = ProofToken(rawToken)
        assertThat(proofToken).isEqualTo(ProofToken(rawToken))
        assertThat(proofToken.hashCode()).isEqualTo(ProofToken(rawToken).hashCode())
        assertThat(proofToken).isNotEqualTo(ProofToken("$rawToken-x"))
        assertThat(proofToken.toString()).isEqualTo("ProofToken(token=12345678..[4]...cdefghij)")
        assertThat(proofToken.toString()).doesNotContain(rawToken)
    }

    @Test
    fun loginIdStoresRawValueAndMasksOnlyDiagnosticText() {
        val email = LoginId("person@example.com", LoginId.Type.Email)
        assertThat(email.id).isEqualTo("person@example.com")
        assertThat(email.maskedId).isEqualTo("p****n@example.com")
        assertThat(email.toString()).isEqualTo("LoginId(id='p****n@example.com', type=Email)")
        assertThat(email.toString()).doesNotContain("person@example.com")

        val phone = LoginId("+15551234567", LoginId.Type.PhoneNumber)
        assertThat(phone.maskedId).isEqualTo("+15*****4567")
        assertThat(phone.toString()).isEqualTo("LoginId(id='+15*****4567', type=PhoneNumber)")

        val username = LoginId("  username  ", LoginId.Type.UserName)
        assertThat(username.id).isEqualTo("  username  ")
        assertThat(username.maskedId).isEqualTo("us" + "\u2022".repeat(4) + "me")
        assertThat(username.toString()).isEqualTo("LoginId(id='us" + "\u2022".repeat(4) + "me', type=UserName)")
    }

    @Test
    fun loginIdEqualityAndHashCodeUseRawIdAndType() {
        val loginId = LoginId("User@example.com", LoginId.Type.Email)
        val same = LoginId("User@example.com", LoginId.Type.Email)

        assertThat(loginId).isEqualTo(same)
        assertThat(loginId.hashCode()).isEqualTo(same.hashCode())
        assertThat(loginId).isNotEqualTo(LoginId("user@example.com", LoginId.Type.Email))
        assertThat(loginId).isNotEqualTo(LoginId("User@example.com", LoginId.Type.UserName))
    }

    @Test
    fun loginIdAndTypeSerializeWithExactPublicFieldAndEnumNames() {
        val loginId = LoginId("person@example.com", LoginId.Type.Email)

        assertThat(json.encodeToString(loginId)).isEqualTo("""{"id":"person@example.com","type":"Email"}""")
        assertThat(json.decodeFromString<LoginId>("""{"id":"person@example.com","type":"Email"}""")).isEqualTo(loginId)

        LoginId.Type.values().forEach { type ->
            assertThat(json.encodeToString(type)).isEqualTo("\"${type.name}\"")
            assertThat(json.decodeFromString<LoginId.Type>("\"${type.name}\"")).isEqualTo(type)
        }

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LoginId.Type>("\"email\"")
        }
    }

    @Test
    fun loginIdConfigurationStoresConstructorValuesAsSupplied() {
        val supportedTypes = listOf(LoginId.Type.UserName, LoginId.Type.UserName, LoginId.Type.Email)
        val validationRegexes = mapOf(
            LoginId.Type.Email to Regex(".+@example\\.com"),
            LoginId.Type.PhoneNumber to null
        )

        val configuration = LoginIdConfiguration(supportedTypes, validationRegexes)

        assertThat(configuration.supportedTypes).isSameInstanceAs(supportedTypes)
        assertThat(configuration.validationRegexes).isSameInstanceAs(validationRegexes)
        assertThat(LoginIdConfiguration.DEFAULT_EMAIL.supportedTypes).containsExactly(LoginId.Type.Email)
        assertThat(LoginIdConfiguration.DEFAULT_EMAIL.validationRegexes).containsExactly(LoginId.Type.Email, null)
    }

    @Test
    fun userSerializesWithRawLoginIdAndCanonicalAuthMethod() {
        val user = User(LoginId("person@example.com", LoginId.Type.Email), AuthMethod.Passkey)

        assertThat(json.encodeToString(user)).isEqualTo("""{"loginId":{"id":"person@example.com","type":"Email"},"authMethod":"passkey"}""")

        val decoded = json.decodeFromString<User>("""{"loginId":{"id":"person@example.com","type":"Email"},"authMethod":"passkey"}""")
        assertThat(decoded.loginId).isEqualTo(user.loginId)
        assertThat(decoded.authMethod).isEqualTo(AuthMethod.Passkey)
    }

    @Test
    fun languageTagSerializesTagStringAndDecodesThroughLocaleFallbacks() {
        assertThat(json.encodeToString(LanguageTag("en", "US"))).isEqualTo("\"en-US\"")
        assertThat(json.decodeFromString<LanguageTag>("\"en-US\"")).isEqualTo(LanguageTag("en", "US"))
        assertThat(json.decodeFromString<LanguageTag>("\"iw-IL\"")).isEqualTo(LanguageTag("he", "IL"))
        assertThat(json.decodeFromString<LanguageTag>("\"\"")).isEqualTo(LanguageTag.DEFAULT)

        val tag = LanguageTag("pt", "BR")
        assertThat(tag.tagString).isEqualTo("pt-BR")
        assertThat(tag.toLanguageOnly()).isEqualTo(LanguageTag("pt", ""))
        assertThat(tag.toString()).isEqualTo("pt-BR")
    }

    @Test
    fun socialProviderExposesOwnIdWireValues() {
        assertThat(SocialProvider.GOOGLE.wireValue).isEqualTo("Google")
        assertThat(SocialProvider.APPLE.wireValue).isEqualTo("Apple")
        assertThat(SocialProvider.GOOGLE.toString()).isEqualTo("GOOGLE")
        assertThat(OauthResponseType.CODE.name).isEqualTo("CODE")
        assertThat(OauthResponseType.ID_TOKEN.name).isEqualTo("ID_TOKEN")
    }

    @Test
    fun socialChallengeSerializesPublicFieldsAndComparesByValue() {
        val challenge = SocialChallenge(
            challengeId = ChallengeId("challenge-id"),
            timeout = Timeout(-1),
            clientId = "client-id",
            challengeUrl = "https://example.com/challenge"
        )
        val same = SocialChallenge(
            challengeId = ChallengeId("challenge-id"),
            timeout = Timeout(0),
            clientId = "client-id",
            challengeUrl = "https://example.com/challenge"
        )

        assertThat(json.encodeToString(challenge))
            .isEqualTo("""{"challengeId":"challenge-id","timeout":0,"clientId":"client-id","challengeUrl":"https://example.com/challenge"}""")
        assertThat(
            json.decodeFromString<SocialChallenge>(
                """{"challengeId":"challenge-id","timeout":0,"clientId":"client-id","challengeUrl":"https://example.com/challenge"}"""
            )
        )
            .isEqualTo(same)
        assertThat(challenge).isEqualTo(same)
        assertThat(challenge.hashCode()).isEqualTo(same.hashCode())
        assertThat(challenge).isNotEqualTo(SocialChallenge(ChallengeId("other"), Timeout(0), "client-id", "https://example.com/challenge"))
        assertThat(challenge.toString()).isEqualTo(
            "SocialChallenge(challengeId=challenge-id, timeout=0, clientId=client-id, challengeUrl=https://example.com/challenge)"
        )
    }

    @Test
    fun accessTokenWithUserInfoComparesByRawValuesButRedactsDiagnosticText() {
        val rawToken = "1234567890abcdefghij"
        val result = AccessTokenWithUserInfo(
            accessToken = AccessToken(rawToken),
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            userInfo = mapOf("email" to "person@example.com"),
            provider = SocialProvider.GOOGLE
        )
        val same = AccessTokenWithUserInfo(
            accessToken = AccessToken(rawToken),
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            userInfo = mapOf("email" to "person@example.com"),
            provider = SocialProvider.GOOGLE
        )

        assertThat(result).isEqualTo(same)
        assertThat(result.hashCode()).isEqualTo(same.hashCode())
        assertThat(result).isNotEqualTo(
            AccessTokenWithUserInfo(
                AccessToken("$rawToken-x"),
                LoginId("person@example.com", LoginId.Type.Email),
                mapOf("email" to "person@example.com"),
                SocialProvider.GOOGLE
            )
        )
        val expectedText = "AccessTokenWithUserInfo(accessToken=AccessToken(token=12345678..[4]...cdefghij), " +
                "loginId=LoginId(id='p****n@example.com', type=Email), userInfo='*', provider=GOOGLE)"

        assertThat(result.toString()).isEqualTo(expectedText)
        assertThat(result.toString()).doesNotContain(rawToken)
        assertThat(result.toString()).doesNotContain("email=person@example.com")
    }

    @Test
    fun verificationChallengeDirectConstructionKeepsSuppliedValuesExceptNestedTimeoutNormalization() {
        val channel = OperationChannel("person@example.com", "channel-id")
        val challenge = VerificationChallenge(
            challengeId = ChallengeId("challenge-id"),
            resendPolicy = VerificationChallenge.ResendPolicy(allow = false, attempts = -2, debounce = -3),
            timeout = Timeout(-10),
            attempts = -4,
            methods = VerificationChallenge.Methods(
                otp = VerificationChallenge.Methods.Otp(length = -5),
                magicLink = VerificationChallenge.Methods.MagicLink()
            ),
            channel = channel
        )
        val same = VerificationChallenge(
            challengeId = ChallengeId("challenge-id"),
            resendPolicy = VerificationChallenge.ResendPolicy(allow = false, attempts = -2, debounce = -3),
            timeout = Timeout(0),
            attempts = -4,
            methods = VerificationChallenge.Methods(
                otp = VerificationChallenge.Methods.Otp(length = -5),
                magicLink = VerificationChallenge.Methods.MagicLink()
            ),
            channel = OperationChannel("person@example.com", "channel-id")
        )

        assertThat(challenge).isEqualTo(same)
        assertThat(challenge.hashCode()).isEqualTo(same.hashCode())
        assertThat(challenge.resendPolicy.toString()).isEqualTo("ResendPolicy(allow=false, attempts=-2, debounce=-3)")
        assertThat(challenge.methods.toString()).isEqualTo("Methods(otp=Otp(length=-5), magicLink=MagicLink)")
        val expectedText = "VerificationChallenge(challengeId=challenge-id, timeout=0, attempts=-4, " +
                "methods=Methods(otp=Otp(length=-5), magicLink=MagicLink), channel=OperationChannel(channel='*', id='channel-id'))"

        assertThat(challenge.toString()).isEqualTo(expectedText)
        assertThat(challenge.toString()).doesNotContain("person@example.com")
    }

    @Test
    fun verificationMethodExposesOwnIdValues() {
        assertThat(VerificationMethod.MAGIC_LINK.value).isEqualTo("MagicLink")
        assertThat(VerificationMethod.MAGIC_LINK.toString()).isEqualTo("MagicLink")
        assertThat(VerificationMethod.OTP.value).isEqualTo("Otp")
        assertThat(VerificationMethod.OTP.toString()).isEqualTo("Otp")
    }
}
