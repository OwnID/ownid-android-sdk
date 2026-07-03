package com.ownid.sdk.api.impl.passkey

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.assertUnexpectedApiContractResponseFailure
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationConveyancePreference
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.KeyAlgorithmType
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.ResidentKey
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.TransportType
import com.ownid.sdk.model.UserVerification
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

// Covers: WIRE-080
internal class AttestationOptionsApiCallMappingContractTest {

    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun createBuildsAttestationOptionsRequestWithLoginIdDisplayNameAccessTokenAndTraceParent() {
        val call = AttestationOptionsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            accountDisplayName = "User Example",
            accessToken = AccessToken("access-token"),
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/attestation/options",
            authorization = "Bearer access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        val body = call.request.jsonBody()
        val loginId = body.getValue("loginId").jsonObject
        assertThat(loginId.jsonString("id")).isEqualTo("user@example.com")
        assertThat(loginId.jsonString("type")).isEqualTo("Email")
        assertThat(body.jsonString("accountDisplayName")).isEqualTo("User Example")
    }

    @Test
    fun createBuildsEmptyOptionsRequestWhenNoBodyFieldsAreProvided() {
        val call = attestationOptionsCall()

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/attestation/options",
            authorization = null,
            traceParent = null
        )
        assertThat(call.request.jsonBody()).isEmpty()
    }

    @Test
    fun mapHttpSuccessMapsWebAuthnAttestationOptionsAndDefaultAlgorithms() {
        val call = attestationOptionsCall()

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 200,
                body = """
                    {
                      "rp": { "id": "login.example.test", "name": "Example" },
                      "user": { "id": "dXNlci0x", "name": "user@example.com", "displayName": "User Example" },
                      "challenge": "Y2hhbGxlbmdl",
                      "pubKeyCredParams": [],
                      "attestation": "direct",
                      "authenticatorSelection": {
                        "authenticatorAttachment": "platform",
                        "userVerification": "required",
                        "residentKey": "preferred"
                      },
                      "timeout": 45000,
                      "excludeCredentials": [
                        {
                          "type": "public-key",
                          "id": "Y3JlZC0x",
                          "transports": ["internal", "hybrid"]
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

        val options = result.successValue()
        assertThat(options.rp.id).isEqualTo("login.example.test")
        assertThat(options.rp.name).isEqualTo("Example")
        assertThat(options.user.id).isEqualTo("dXNlci0x")
        assertThat(options.user.name).isEqualTo("user@example.com")
        assertThat(options.user.displayName).isEqualTo("User Example")
        assertThat(options.challenge).isEqualTo(ChallengeId("Y2hhbGxlbmdl"))
        assertThat(options.pubKeyCredParams.map { it.type to it.alg }).containsExactly(
            CredentialType.PUBLIC_KEY to KeyAlgorithmType.ES256,
            CredentialType.PUBLIC_KEY to KeyAlgorithmType.RS256
        ).inOrder()
        assertThatObject(options.attestation).isEqualTo(AttestationConveyancePreference.DIRECT)
        val authenticatorSelection = requireNotNull(options.authenticatorSelection)
        assertThatObject(authenticatorSelection.authenticatorAttachment).isEqualTo(AuthenticatorAttachment.PLATFORM)
        assertThatObject(authenticatorSelection.userVerification).isEqualTo(UserVerification.REQUIRED)
        assertThatObject(authenticatorSelection.residentKey).isEqualTo(ResidentKey.PREFERRED)
        assertThat(options.timeout).isEqualTo(Timeout(45000))

        val excluded = requireNotNull(options.excludeCredentials).single()
        assertThat(excluded.type).isEqualTo(CredentialType.PUBLIC_KEY)
        assertThat(excluded.id).isEqualTo("Y3JlZC0x")
        assertThat(excluded.transports).containsExactly(TransportType.INTERNAL, TransportType.HYBRID).inOrder()
    }

    @Test
    fun mapHttpSuccessAcceptsPaddedBase64UrlAttestationOptions() {
        val call = attestationOptionsCall()
        val challenge = paddedBase64UrlFixture("a")
        val userId = paddedBase64UrlFixture("ab")
        val credentialId = paddedBase64UrlFixture("credential")

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 200,
                body = """
                    {
                      "rp": { "id": "login.example.test", "name": "Example" },
                      "user": { "id": "$userId", "name": "user@example.com", "displayName": "User Example" },
                      "challenge": "$challenge",
                      "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
                      "excludeCredentials": [
                        {
                          "type": "public-key",
                          "id": "$credentialId"
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

        val options = result.successValue()
        assertThat(options.challenge).isEqualTo(ChallengeId(challenge))
        assertThat(options.user.id).isEqualTo(userId)
        assertThat(requireNotNull(options.excludeCredentials).single().id).isEqualTo(credentialId)
    }

    @Test
    fun mapHttpSuccessMapsInvalidBase64UrlAttestationOptionsToUnexpected() {
        malformedAttestationOptionsBase64UrlCases().forEach { testCase ->
            val call = attestationOptionsCall()

            val failure = call.mapHttpSuccess(
                apiSuccess(
                    url = call.request.url,
                    code = 200,
                    body = testCase.body
                )
            ).failureValue()

            assertWithMessage("Malformed Base64url ${testCase.field} should map to unexpected")
                .that(failure)
                .isInstanceOf(PasskeyAttestationStartApiFailure.Unexpected::class.java)
            assertUnexpectedApiContractResponseFailure(failure as ApiUnexpectedFailure, statusCode = 200)
        }
    }

    @Test
    fun mapHttpErrorMapsBadLoginIdRequestVariants() {
        val invalidArgument = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"login id is required"}"""
            )
        )
        assertThat(invalidArgument).isInstanceOf(PasskeyAttestationStartApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PasskeyAttestationStartApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("login id is required")

        val invalidLoginId = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """
                    {
                      "errorCode": "login_id_validation_failed",
                      "message": "login id does not match configured regex",
                      "loginId": { "id": "bad@example", "type": "Email" },
                      "regex": ".+@example\\.com"
                    }
                """.trimIndent()
            )
        )
        assertThat(invalidLoginId).isInstanceOf(PasskeyAttestationStartApiFailure.BadRequest.InvalidLoginId::class.java)
        invalidLoginId as PasskeyAttestationStartApiFailure.BadRequest.InvalidLoginId
        assertThat(invalidLoginId.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(invalidLoginId.message).isEqualTo("login id does not match configured regex")
        assertThat(invalidLoginId.loginId).isEqualTo(LoginId("bad@example", LoginId.Type.Email))
        assertThat(invalidLoginId.regex).isEqualTo(".+@example\\.com")

        val unsupportedType = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"login_id_type_not_supported","message":"login id type is not supported"}"""
            )
        )
        assertThat(unsupportedType).isInstanceOf(PasskeyAttestationStartApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        unsupportedType as PasskeyAttestationStartApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(unsupportedType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupportedType.message).isEqualTo("login id type is not supported")
    }

    @Test
    fun mapHttpErrorMapsForbiddenFailedDependencyAndLimitFailures() {
        val forbidden = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 403,
                body = """{"errorCode":"forbidden","message":"attestation is not allowed"}"""
            )
        )
        assertThat(forbidden).isInstanceOf(PasskeyAttestationStartApiFailure.Forbidden::class.java)
        forbidden as PasskeyAttestationStartApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("attestation is not allowed")

        val blankForbidden = attestationOptionsCall().mapHttpError(apiHttpError(url = baseUrl, statusCode = 403, body = ""))
        assertThat(blankForbidden).isInstanceOf(PasskeyAttestationStartApiFailure.Forbidden::class.java)
        assertThat(blankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val missingProvider = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 424,
                body = """
                    {
                      "errorCode": "missing_capability_provider",
                      "message": "session provider is not configured",
                      "capability": "session",
                      "scope": "session"
                    }
                """.trimIndent()
            )
        )
        assertThat(missingProvider).isInstanceOf(PasskeyAttestationStartApiFailure.FailedDependency.MissingProvider::class.java)
        missingProvider as PasskeyAttestationStartApiFailure.FailedDependency.MissingProvider
        assertThat(missingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(missingProvider.message).isEqualTo("session provider is not configured")
        assertThat(missingProvider.capability).isEqualTo("session")
        assertThat(missingProvider.scope).isEqualTo(ApiFailureScope.SESSION)

        val maximumChallenges = attestationOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 429,
                body = """{"errorCode":"maximum_challenges_reached","message":"challenge limit reached"}"""
            )
        )
        assertThat(maximumChallenges).isInstanceOf(PasskeyAttestationStartApiFailure.MaximumChallengesReached::class.java)
        maximumChallenges as PasskeyAttestationStartApiFailure.MaximumChallengesReached
        assertThat(maximumChallenges.errorCode).isEqualTo(ErrorCode.MaximumChallengesReached)
        assertThat(maximumChallenges.message).isEqualTo("challenge limit reached")
    }

    @Test
    fun mapHttpErrorMapsUnhandledAndMalformedResponsesToUnexpectedFailure() {
        val unhandledStatus = attestationOptionsCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 500, body = """{"message":"server error"}""")
        )
        assertThat(unhandledStatus).isInstanceOf(PasskeyAttestationStartApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(unhandledStatus as ApiUnexpectedFailure, statusCode = 500)

        val malformedBody = attestationOptionsCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 400, body = """{"message":"missing discriminator"}""")
        )
        assertThat(malformedBody).isInstanceOf(PasskeyAttestationStartApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(malformedBody as ApiUnexpectedFailure, statusCode = 400)
    }

    private fun attestationOptionsCall(): AttestationOptionsApiCall =
        AttestationOptionsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            loginId = null,
            accountDisplayName = null,
            accessToken = null,
            traceParent = null
        )

    private fun malformedAttestationOptionsBase64UrlCases(): List<MalformedAttestationOptionsBase64UrlCase> =
        listOf(
            MalformedAttestationOptionsBase64UrlCase(
                field = "user.id",
                body = malformedAttestationOptionsBody(
                    userId = "YQ=",
                    challenge = paddedBase64UrlFixture("a"),
                    excludeCredentialId = paddedBase64UrlFixture("credential")
                )
            ),
            MalformedAttestationOptionsBase64UrlCase(
                field = "challenge",
                body = malformedAttestationOptionsBody(
                    userId = paddedBase64UrlFixture("ab"),
                    challenge = "YQ=",
                    excludeCredentialId = paddedBase64UrlFixture("credential")
                )
            ),
            MalformedAttestationOptionsBase64UrlCase(
                field = "excludeCredentials.id",
                body = malformedAttestationOptionsBody(
                    userId = paddedBase64UrlFixture("ab"),
                    challenge = paddedBase64UrlFixture("a"),
                    excludeCredentialId = "YQ="
                )
            )
        )

    private fun malformedAttestationOptionsBody(userId: String, challenge: String, excludeCredentialId: String): String =
        """
            {
              "rp": { "id": "login.example.test", "name": "Example" },
              "user": { "id": "$userId", "name": "user@example.com", "displayName": "User Example" },
              "challenge": "$challenge",
              "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
              "excludeCredentials": [
                {
                  "type": "public-key",
                  "id": "$excludeCredentialId"
                }
              ]
            }
        """.trimIndent()

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}

private data class MalformedAttestationOptionsBase64UrlCase(val field: String, val body: String)
