package com.ownid.sdk.api.impl.oidc

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.ErrorCode
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: WIRE-200
internal class OidcCompleteApiCallErrorMappingContractTest {

    private val url = "https://auth.example.test/oidc/idp/complete".toHttpUrl()
    private val codeCall = OidcChallengeCompleteWithCodeApiCall(
        serialization = SerializationImpl(),
        request = Request.Builder().url(url).build()
    )
    private val call = OidcChallengeCompleteWithIdTokenApiCall(
        serialization = SerializationImpl(),
        request = Request.Builder().url(url).build()
    )

    @Test
    fun mapHttpErrorMapsInvalidArgumentToBadRequest() {
        val failure = call.mapHttpError(
            httpError(
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"idToken is required"}"""
            )
        )

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.BadRequest.InvalidArgument::class.java)
        failure as OidcCompleteApiFailure.BadRequest.InvalidArgument
        assertThat(failure.errorCode).isEqualTo(ErrorCode.InvalidArgument)
    }

    @Test
    fun mapHttpErrorMapsInvalidChallengeToBadRequest() {
        val failure = call.mapHttpError(
            httpError(
                statusCode = 400,
                body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"challenge-123"}"""
            )
        )

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.BadRequest.InvalidChallenge::class.java)
        failure as OidcCompleteApiFailure.BadRequest.InvalidChallenge
        assertThat(failure.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(failure.challengeId.value).isEqualTo("challenge-123")
    }

    @Test
    fun mapHttpErrorMapsIntegrationErrorToProviderFailed() {
        val failure = call.mapHttpError(
            httpError(
                statusCode = 424,
                body = """{"errorCode":"integration_error","message":"provider failed","scope":"data"}"""
            )
        )

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.FailedDependency.ProviderFailed::class.java)
        failure as OidcCompleteApiFailure.FailedDependency.ProviderFailed
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.scope).isEqualTo(ApiFailureScope.DATA)
    }

    @Test
    fun mapHttpErrorMapsMissingCapabilityProviderToMissingProvider() {
        val failure = call.mapHttpError(
            httpError(
                statusCode = 424,
                body = """{"errorCode":"missing_capability_provider","message":"missing provider","capability":"oidc","scope":"session"}"""
            )
        )

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.FailedDependency.MissingProvider::class.java)
        failure as OidcCompleteApiFailure.FailedDependency.MissingProvider
        assertThat(failure.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(failure.capability).isEqualTo("oidc")
        assertThat(failure.scope).isEqualTo(ApiFailureScope.SESSION)
    }

    @Test
    fun mapHttpErrorMapsBlankForbiddenFallbackForCompleteVariants() {
        val codeForbidden = codeCall.mapHttpError(httpError(statusCode = 403, body = ""))
        assertThat(codeForbidden).isInstanceOf(OidcCompleteApiFailure.Forbidden::class.java)
        assertThat(codeForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val idTokenForbidden = call.mapHttpError(httpError(statusCode = 403, body = "  "))
        assertThat(idTokenForbidden).isInstanceOf(OidcCompleteApiFailure.Forbidden::class.java)
        assertThat(idTokenForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
    }

    @Test
    fun mapHttpErrorMapsUnhandledHttpStatusToUnexpectedApiContractFailure() {
        val response = httpError(statusCode = 500, body = """{"message":"server error"}""")

        val failure = call.mapHttpError(response)

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.Unexpected::class.java)
        failure as OidcCompleteApiFailure.Unexpected
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
        val cause = failure.cause as ApiUnexpectedCause.ApiContract
        assertThat(cause.failure).isSameInstanceAs(response)
        assertThat(cause.error).isNull()
    }

    @Test
    fun mapHttpErrorMapsMalformedErrorBodyToUnexpectedApiContractFailure() {
        val response = httpError(statusCode = 400, body = """{"message":"missing discriminator"}""")

        val failure = call.mapHttpError(response)

        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.Unexpected::class.java)
        failure as OidcCompleteApiFailure.Unexpected
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
        val cause = failure.cause as ApiUnexpectedCause.ApiContract
        assertThat(cause.failure).isSameInstanceAs(response)
        assertThat(cause.error).isNotNull()
    }

    private fun httpError(statusCode: Int, body: String): Network.Response.Fail.HttpError =
        Network.Response.Fail.HttpError(
            url = url,
            statusCode = statusCode,
            headers = emptyMap(),
            body = body
        )
}
