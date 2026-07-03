package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

// Covers: MODEL-130
internal class PasskeyValueContractTest {

    private val json = Json
    private val longValue = "1234567890abcdefghij"
    private val shortenedLongValue = "12345678..[4]...cdefghij"

    @Test
    fun passkeyEnumsSerializeWireValuesAndUseWireValuesAsDiagnosticText() {
        assertWireValue(CredentialType.PUBLIC_KEY, "public-key")
        assertWireValue(AuthenticatorAttachment.PLATFORM, "platform")
        assertWireValue(AuthenticatorAttachment.CROSS_PLATFORM, "cross-platform")
        assertWireValue(AttestationConveyancePreference.ENTERPRISE, "enterprise")
        assertWireValue(KeyAlgorithmType.ES256, "-7")
        assertWireValue(KeyAlgorithmType.RS256, "-257")
        assertWireValue(ResidentKey.DISCOURAGED, "discouraged")
        assertWireValue(TransportType.SMART_CARD, "smart-card")
        assertWireValue(TransportType.CABLE, "cable")
        assertWireValue(UserVerification.REQUIRED, "required")
    }

    @Test
    fun credentialDescriptorSerializesPublicFieldsAndShortensDiagnosticId() {
        val descriptor = PublicKeyCredentialDescriptor(
            type = CredentialType.PUBLIC_KEY,
            id = longValue,
            transports = listOf(TransportType.INTERNAL, TransportType.HYBRID)
        )

        assertThat(json.encodeToString(descriptor)).isEqualTo("""{"type":"public-key","id":"1234567890abcdefghij","transports":["internal","hybrid"]}""")

        val decoded = json.decodeFromString<PublicKeyCredentialDescriptor>(
            """{"type":"public-key","id":"1234567890abcdefghij","transports":["internal","hybrid"]}"""
        )
        assertThat(decoded.type).isEqualTo(CredentialType.PUBLIC_KEY)
        assertThat(decoded.id).isEqualTo(longValue)
        assertThat(decoded.transports).containsExactly(TransportType.INTERNAL, TransportType.HYBRID).inOrder()

        assertThat(descriptor.toString()).isEqualTo("PublicKeyCredentialDescriptor(type=public-key, id=$shortenedLongValue, transports=[internal, hybrid])")
        assertThat(descriptor.toString()).doesNotContain(longValue)
    }

    @Test
    fun assertionModelsSerializePublicFieldsAndShortenDeclaredDiagnosticValues() {
        val response = AssertionResult.AuthenticatorResponse(
            clientDataJSON = "client-data",
            authenticatorData = "authenticator-data",
            signature = "signature",
            userHandle = longValue
        )
        val result = AssertionResult(
            id = longValue,
            type = CredentialType.PUBLIC_KEY,
            response = response,
            authenticatorAttachment = AuthenticatorAttachment.PLATFORM
        )
        val options = AssertionOptions(
            challenge = ChallengeId("challenge-id"),
            rpId = "example.com",
            allowCredentials = listOf(PublicKeyCredentialDescriptor(CredentialType.PUBLIC_KEY, "credential-id")),
            userVerification = UserVerification.PREFERRED,
            timeout = Timeout(1000)
        )

        val expectedOptionsJson = """{"challenge":"challenge-id","rpId":"example.com","allowCredentials":""" +
                """[{"type":"public-key","id":"credential-id"}],"userVerification":"preferred","timeout":1000}"""
        val expectedResultJson = """{"id":"1234567890abcdefghij","type":"public-key","response":""" +
                """{"clientDataJSON":"client-data","authenticatorData":"authenticator-data","signature":"signature",""" +
                """"userHandle":"1234567890abcdefghij"},"authenticatorAttachment":"platform"}"""

        assertThat(json.encodeToString(options)).isEqualTo(expectedOptionsJson)
        assertThat(json.encodeToString(result)).isEqualTo(expectedResultJson)

        assertThat(response.toString()).isEqualTo(
            "AuthenticatorResponse(clientDataJSON=client-data, authenticatorData=authenticator-data, signature=signature, userHandle=$shortenedLongValue)"
        )
        assertThat(result.toString()).startsWith("AssertionResult(id=$shortenedLongValue, type=public-key")
        assertThat(result.toString()).doesNotContain("id=$longValue")
        assertThat(result.toString()).doesNotContain("userHandle=$longValue")
    }

    @Test
    fun attestationOptionsSerializePublicFieldsAndMaskUserDiagnosticText() {
        val user = AttestationOptions.User(id = longValue, name = "person@example.com", displayName = "Person Example")
        val options = AttestationOptions(
            rp = AttestationOptions.RelayingParty(id = "example.com", name = "Example"),
            user = user,
            challenge = ChallengeId("challenge-id"),
            pubKeyCredParams = listOf(AttestationOptions.PubKeyCredParams(CredentialType.PUBLIC_KEY, KeyAlgorithmType.ES256)),
            attestation = AttestationConveyancePreference.NONE,
            authenticatorSelection = AttestationOptions.AuthenticatorSelection(
                authenticatorAttachment = AuthenticatorAttachment.PLATFORM,
                userVerification = UserVerification.REQUIRED,
                residentKey = ResidentKey.PREFERRED
            ),
            timeout = Timeout(2500),
            excludeCredentials = listOf(PublicKeyCredentialDescriptor(CredentialType.PUBLIC_KEY, "credential-id"))
        )

        val expectedOptionsJson = """{"rp":{"id":"example.com","name":"Example"},"user":""" +
                """{"id":"1234567890abcdefghij","name":"person@example.com","displayName":"Person Example"},""" +
                """"challenge":"challenge-id","pubKeyCredParams":[{"type":"public-key","alg":"-7"}],"attestation":"none",""" +
                """"authenticatorSelection":{"authenticatorAttachment":"platform","userVerification":"required","residentKey":"preferred"},""" +
                """"timeout":2500,"excludeCredentials":[{"type":"public-key","id":"credential-id"}]}"""

        assertThat(json.encodeToString(options)).isEqualTo(expectedOptionsJson)

        assertThat(user.toString()).isEqualTo("User(id=$shortenedLongValue, name='*', displayName='*')")
        assertThat(user.toString()).doesNotContain("person@example.com")
        assertThat(user.toString()).doesNotContain("Person Example")
        assertThat(options.toString()).contains("user=User(id=$shortenedLongValue, name='*', displayName='*')")
    }

    @Test
    fun attestationResultAndResponseShortenOrMaskDeclaredDiagnosticValues() {
        val authenticatorResponse = AttestationResult.AuthenticatorResponse(
            clientDataJSON = "client-data",
            attestationObject = longValue,
            transports = listOf(TransportType.INTERNAL)
        )
        val result = AttestationResult(
            id = longValue,
            type = CredentialType.PUBLIC_KEY,
            response = authenticatorResponse,
            authenticatorAttachment = AuthenticatorAttachment.PLATFORM
        )
        val response = AttestationResponse(ProofToken(longValue), ownIdData = """{"secret":true}""")

        val expectedResultJson = """{"id":"1234567890abcdefghij","type":"public-key","response":""" +
                """{"clientDataJSON":"client-data","attestationObject":"1234567890abcdefghij","transports":["internal"]},""" +
                """"authenticatorAttachment":"platform"}"""

        assertThat(json.encodeToString(result)).isEqualTo(expectedResultJson)

        assertThat(authenticatorResponse.toString())
            .isEqualTo("AuthenticatorResponse(clientDataJSON=client-data, attestationObject=$shortenedLongValue, transports=[internal])")
        assertThat(result.toString()).startsWith("AttestationResult(id=$shortenedLongValue, type=public-key")
        assertThat(result.toString()).doesNotContain("id=$longValue")
        assertThat(result.toString()).doesNotContain("attestationObject=$longValue")

        assertThat(response.toString()).isEqualTo("AttestationResponse(proofToken=ProofToken(token=$shortenedLongValue), ownIdData='*')")
        assertThat(response.toString()).doesNotContain("""{"secret":true}""")
        assertThat(response.toString()).doesNotContain(longValue)
    }

    private inline fun <reified T> assertWireValue(value: T, wireValue: String) {
        assertThat(json.encodeToString(value)).isEqualTo("\"$wireValue\"")
        assertThat(json.decodeFromString<T>("\"$wireValue\"")).isEqualTo(value)
        assertThat(value.toString()).isEqualTo(wireValue)
    }
}
