package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

// Covers: MODEL-130
internal class PasskeyValueContractTest {

    private val json = Json
    private val longValue = "1234567890abcdefghij"
    private val shortenedLongValue = "12345678..[4]...cdefghij"

    @Test
    fun passkeyEnumsSerializeDeclaredWireShapesAndRejectInvalidAlgorithmValues() {
        assertWireValue(CredentialType.PUBLIC_KEY, "public-key")
        assertWireValue(AuthenticatorAttachment.PLATFORM, "platform")
        assertWireValue(AuthenticatorAttachment.CROSS_PLATFORM, "cross-platform")
        assertWireValue(AttestationConveyancePreference.ENTERPRISE, "enterprise")
        listOf(
            KeyAlgorithmType.ES256 to -7,
            KeyAlgorithmType.RS256 to -257
        ).forEach { (value, wireValue) ->
            assertNumericWireValue(value, wireValue)
        }
        listOf("\"-7\"", "\"-257\"", "-8", "-7.0", "true", "null", "[]", "{}").forEach { invalidJson ->
            assertThrows(SerializationException::class.java) {
                json.decodeFromString<KeyAlgorithmType>(invalidJson)
            }
        }
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
    fun assertionOptionsAndCredentialDescriptorsUseValueEqualityAcrossPublicFields() {
        fun descriptor(
            id: String = longValue,
            transports: List<TransportType>? = listOf(TransportType.INTERNAL, TransportType.HYBRID)
        ): PublicKeyCredentialDescriptor = PublicKeyCredentialDescriptor(
            type = CredentialType.PUBLIC_KEY,
            id = id,
            transports = transports
        )

        val firstDescriptor = descriptor()
        val secondDescriptor = descriptor(id = "second-credential", transports = listOf(TransportType.USB))
        val credentials = listOf(firstDescriptor, secondDescriptor)

        assertEqualsAndHashCodeContract(firstDescriptor, descriptor(), descriptor())
        listOf(
            descriptor(id = "different-credential"),
            descriptor(transports = listOf(TransportType.HYBRID, TransportType.INTERNAL))
        ).forEach { unequalDescriptor ->
            assertThat(firstDescriptor).isNotEqualTo(unequalDescriptor)
        }

        fun options(
            challenge: ChallengeId = ChallengeId("challenge-id"),
            rpId: String = "example.com",
            allowCredentials: List<PublicKeyCredentialDescriptor>? = credentials,
            userVerification: UserVerification? = UserVerification.PREFERRED,
            timeout: Timeout? = Timeout(1000)
        ): AssertionOptions = AssertionOptions(
            challenge = challenge,
            rpId = rpId,
            allowCredentials = allowCredentials,
            userVerification = userVerification,
            timeout = timeout
        )

        val options = options()
        val equalOptions = options(
            allowCredentials = listOf(
                descriptor(),
                descriptor(id = "second-credential", transports = listOf(TransportType.USB))
            )
        )
        val transitiveOptions = options(
            allowCredentials = listOf(
                descriptor(),
                descriptor(id = "second-credential", transports = listOf(TransportType.USB))
            )
        )

        assertEqualsAndHashCodeContract(options, equalOptions, transitiveOptions)
        listOf(
            options(challenge = ChallengeId("different-challenge")),
            options(rpId = "different.example.com"),
            options(allowCredentials = credentials.reversed()),
            options(userVerification = UserVerification.REQUIRED),
            options(timeout = Timeout(1001))
        ).forEach { unequalOptions ->
            assertThat(options).isNotEqualTo(unequalOptions)
        }
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
                """"challenge":"challenge-id","pubKeyCredParams":[{"type":"public-key","alg":-7}],"attestation":"none",""" +
                """"authenticatorSelection":{"authenticatorAttachment":"platform","userVerification":"required","residentKey":"preferred"},""" +
                """"timeout":2500,"excludeCredentials":[{"type":"public-key","id":"credential-id"}]}"""

        assertThat(json.encodeToString(options)).isEqualTo(expectedOptionsJson)
        assertThat(json.decodeFromString<AttestationOptions>(expectedOptionsJson).pubKeyCredParams.single().alg)
            .isEqualTo(KeyAlgorithmType.ES256)

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

    private fun assertNumericWireValue(value: KeyAlgorithmType, wireValue: Int) {
        assertThat(json.encodeToString(value)).isEqualTo(wireValue.toString())
        assertThat(json.decodeFromString<KeyAlgorithmType>(wireValue.toString())).isEqualTo(value)
        assertThat(value.toString()).isEqualTo(wireValue.toString())
    }

    private fun assertEqualsAndHashCodeContract(value: Any, equalValue: Any, transitiveValue: Any) {
        assertThat(value).isEqualTo(value)
        assertThat(value).isEqualTo(equalValue)
        assertThat(equalValue).isEqualTo(value)
        assertThat(equalValue).isEqualTo(transitiveValue)
        assertThat(value).isEqualTo(transitiveValue)
        assertThat(value).isNotEqualTo(null)
        assertThat(value).isNotEqualTo(Any())
        assertThat(value.hashCode()).isEqualTo(equalValue.hashCode())
        assertThat(value.hashCode()).isEqualTo(transitiveValue.hashCode())
    }
}
