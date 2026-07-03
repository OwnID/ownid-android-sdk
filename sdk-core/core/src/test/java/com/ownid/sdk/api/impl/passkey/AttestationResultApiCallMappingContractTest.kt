package com.ownid.sdk.api.impl.passkey

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.jsonStringArray
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.TransportType
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

// Covers: WIRE-090
internal class AttestationResultApiCallMappingContractTest {

    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun createBuildsVerifyRequestAndMapsStructuredOwnIdDataWithoutNormalization() {
        val call = AttestationResultApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            attestationResult = attestationResult(authenticatorAttachment = AuthenticatorAttachment.CROSS_PLATFORM),
            accessToken = AccessToken("access-token"),
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/attestation/result",
            authorization = "Bearer access-token",
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        val body = call.request.jsonBody()
        assertThat(body.jsonString("id")).isEqualTo("credential-id")
        assertThat(body.jsonString("type")).isEqualTo("public-key")
        assertThat(body.jsonString("authenticatorAttachment")).isEqualTo("cross-platform")
        val response = body.getValue("response").jsonObject
        assertThat(response.jsonString("clientDataJSON")).isEqualTo("client-data-json")
        assertThat(response.jsonString("attestationObject")).isEqualTo("attestation-object")
        assertThat(response.jsonStringArray("transports")).containsExactly("internal", "hybrid").inOrder()

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 200,
                body = """
                    {
                      "proofToken": "proof-token",
                      "ownIdData": {"session":{"id":"abc","roles":["admin","user"]},"active":true}
                    }
                """.trimIndent()
            )
        )

        val responseBody = result.successValue()
        assertThat(responseBody.proofToken).isEqualTo(ProofToken("proof-token"))
        assertThat(responseBody.ownIdData).isEqualTo("""{"session":{"id":"abc","roles":["admin","user"]},"active":true}""")
    }

    @Test
    fun mapHttpSuccessPreservesStringOwnIdDataAsRawStringValue() {
        val call = attestationResultCall()

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 200,
                body = """{"proofToken":"proof-token","ownIdData":"string-owned-data"}"""
            )
        )

        val response = result.successValue()
        assertThat(response.proofToken).isEqualTo(ProofToken("proof-token"))
        assertThat(response.ownIdData).isEqualTo("string-owned-data")
    }

    @Test
    fun createOmitsNullAuthenticatorAttachmentFromVerifyRequest() {
        val call = AttestationResultApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            attestationResult = attestationResult(authenticatorAttachment = null),
            accessToken = null,
            traceParent = null
        )

        assertThat(call.request.jsonBody()).doesNotContainKey("authenticatorAttachment")
    }

    @Test
    fun mapHttpErrorMapsBadChallengeRequestVariants() {
        val invalidArgument = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"attestation result is required"}"""
            )
        )
        assertThat(invalidArgument).isInstanceOf(PasskeyAttestationVerifyApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PasskeyAttestationVerifyApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("attestation result is required")

        val invalidChallenge = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"challenge-123"}"""
            )
        )
        assertThat(invalidChallenge).isInstanceOf(PasskeyAttestationVerifyApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PasskeyAttestationVerifyApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("challenge-123"))

        val maximumAttempts = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"challenge-123"}"""
            )
        )
        assertThat(maximumAttempts).isInstanceOf(PasskeyAttestationVerifyApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttempts as PasskeyAttestationVerifyApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttempts.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttempts.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttempts.challengeId).isEqualTo(ChallengeId("challenge-123"))
    }

    @Test
    fun mapHttpErrorMapsAuthAndUserFailures() {
        val unauthorized = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 401,
                body = """{"errorCode":"unauthorized","message":"access token is missing"}"""
            )
        )
        assertThat(unauthorized).isInstanceOf(PasskeyAttestationVerifyApiFailure.Unauthorized::class.java)
        unauthorized as PasskeyAttestationVerifyApiFailure.Unauthorized
        assertThat(unauthorized.errorCode).isEqualTo(ErrorCode.Unauthorized)
        assertThat(unauthorized.message).isEqualTo("access token is missing")

        val blankUnauthorized = attestationResultCall().mapHttpError(apiHttpError(url = baseUrl, statusCode = 401, body = ""))
        assertThat(blankUnauthorized).isInstanceOf(PasskeyAttestationVerifyApiFailure.Unauthorized::class.java)
        assertThat(blankUnauthorized.errorCode).isEqualTo(ErrorCode.Unauthorized)

        val forbidden = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 403,
                body = """{"errorCode":"forbidden","message":"attestation verification is not allowed"}"""
            )
        )
        assertThat(forbidden).isInstanceOf(PasskeyAttestationVerifyApiFailure.Forbidden::class.java)
        forbidden as PasskeyAttestationVerifyApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("attestation verification is not allowed")

        val blankForbidden = attestationResultCall().mapHttpError(apiHttpError(url = baseUrl, statusCode = 403, body = "  "))
        assertThat(blankForbidden).isInstanceOf(PasskeyAttestationVerifyApiFailure.Forbidden::class.java)
        assertThat(blankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val userNotFound = attestationResultCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 404,
                body = """{"errorCode":"user_not_found","message":"account does not exist"}"""
            )
        )
        assertThat(userNotFound).isInstanceOf(PasskeyAttestationVerifyApiFailure.UserNotFound::class.java)
        userNotFound as PasskeyAttestationVerifyApiFailure.UserNotFound
        assertThat(userNotFound.errorCode).isEqualTo(ErrorCode.UserNotFound)
        assertThat(userNotFound.message).isEqualTo("account does not exist")
    }

    @Test
    fun mapHttpErrorMapsUnexpectedBranches() {
        val unhandledStatus = attestationResultCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 500, body = """{"message":"server error"}""")
        )
        assertThat(unhandledStatus).isInstanceOf(PasskeyAttestationVerifyApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(unhandledStatus as ApiUnexpectedFailure, statusCode = 500)

        val malformedBody = attestationResultCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 400, body = """{"message":"missing discriminator"}""")
        )
        assertThat(malformedBody).isInstanceOf(PasskeyAttestationVerifyApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(malformedBody as ApiUnexpectedFailure, statusCode = 400)
    }

    private fun attestationResultCall(): AttestationResultApiCall =
        AttestationResultApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            attestationResult = attestationResult(authenticatorAttachment = AuthenticatorAttachment.PLATFORM),
            accessToken = null,
            traceParent = null
        )

    private fun attestationResult(authenticatorAttachment: AuthenticatorAttachment?): AttestationResult =
        AttestationResult(
            id = "credential-id",
            type = CredentialType.PUBLIC_KEY,
            response = AttestationResult.AuthenticatorResponse(
                clientDataJSON = "client-data-json",
                attestationObject = "attestation-object",
                transports = listOf(TransportType.INTERNAL, TransportType.HYBRID)
            ),
            authenticatorAttachment = authenticatorAttachment
        )
}
