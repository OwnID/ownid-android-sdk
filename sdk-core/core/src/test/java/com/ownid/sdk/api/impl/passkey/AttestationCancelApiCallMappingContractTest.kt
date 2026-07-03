package com.ownid.sdk.api.impl.passkey

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.PasskeyAttestationCancelApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.Reason
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

// Covers: WIRE-100
internal class AttestationCancelApiCallMappingContractTest {

    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun createBuildsCancelRequestWithChallengeReasonAndTraceParent() {
        val call = AttestationCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            challenge = ChallengeId("attestation-challenge-1"),
            reason = Reason.SystemError("platform failed"),
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/attestation/cancel",
            authorization = null,
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        call.request.jsonBody().let { body ->
            assertThat(body.jsonString("challenge")).isEqualTo("attestation-challenge-1")
            assertThat(body.jsonString("reason")).isEqualTo("systemError")
        }
    }

    @Test
    fun createMapsNullCancelReasonToUnknown() {
        val call = AttestationCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            challenge = ChallengeId("attestation-challenge-1"),
            reason = null,
            traceParent = null
        )

        assertThat(call.request.jsonBody().jsonString("reason")).isEqualTo("unknown")
    }

    @Test
    fun mapHttpSuccessMapsNoContentToUnit() {
        val call = attestationCancelCall()

        call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 204, body = "")).assertUnitSuccess()
    }

    @Test
    fun mapHttpErrorMapsBadChallengeRequestVariants() {
        val invalidArgument = attestationCancelCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"challenge is required"}"""
            )
        )
        assertThat(invalidArgument).isInstanceOf(PasskeyAttestationCancelApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PasskeyAttestationCancelApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("challenge is required")

        val invalidChallenge = attestationCancelCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"challenge-123"}"""
            )
        )
        assertThat(invalidChallenge).isInstanceOf(PasskeyAttestationCancelApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PasskeyAttestationCancelApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("challenge-123"))

        val unknown = attestationCancelCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"unknown","message":"server could not classify cancel error"}"""
            )
        )
        assertThat(unknown).isInstanceOf(PasskeyAttestationCancelApiFailure.BadRequest.Unknown::class.java)
        unknown as PasskeyAttestationCancelApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("server could not classify cancel error")
    }

    @Test
    fun mapHttpErrorMapsMaximumAttemptsAndUnexpectedBranches() {
        val maximumAttempts = attestationCancelCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"challenge-123"}"""
            )
        )
        assertThat(maximumAttempts).isInstanceOf(PasskeyAttestationCancelApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttempts as PasskeyAttestationCancelApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttempts.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttempts.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttempts.challengeId).isEqualTo(ChallengeId("challenge-123"))

        val unhandledStatus = attestationCancelCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 500, body = """{"message":"server error"}""")
        )
        assertThat(unhandledStatus).isInstanceOf(PasskeyAttestationCancelApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(unhandledStatus as ApiUnexpectedFailure, statusCode = 500)

        val malformedBody = attestationCancelCall().mapHttpError(
            apiHttpError(url = baseUrl, statusCode = 400, body = """{"message":"missing discriminator"}""")
        )
        assertThat(malformedBody).isInstanceOf(PasskeyAttestationCancelApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(malformedBody as ApiUnexpectedFailure, statusCode = 400)
    }

    private fun attestationCancelCall(): AttestationCancelApiCall =
        AttestationCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            challenge = ChallengeId("attestation-challenge-1"),
            reason = Reason.UserClose(),
            traceParent = null
        )
}
