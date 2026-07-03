package com.ownid.sdk.api.impl.oidc

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcCancelApiFailure
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.api.impl.FixedApiBaseUrl
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.TestDispatcherProvider
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.SocialProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: WIRE-210
internal class OidcStartCancelApiCallErrorMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()
    private val startUrl = "https://auth.example.test/api/oidc/idp/start/google".toHttpUrl()
    private val cancelUrl = "https://auth.example.test/api/oidc/idp/cancel".toHttpUrl()
    private val startCall = OidcChallengeStartApiCall(serialization, Request.Builder().url(startUrl).build())
    private val cancelCall = OidcChallengeCancelApiCall(serialization, Request.Builder().url(cancelUrl).build())

    @Test
    fun oidcApiStartDefaultsNullProviderToGoogleAtRequestSource(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            apiSuccess(
                url = request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "oidc-challenge-default",
                      "timeout": 30000,
                      "clientId": "google-client-id",
                      "challengeUrl": "https://accounts.example.test/challenge"
                    }
                """.trimIndent()
            )
        }
        val api = OidcApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = null,
            interceptor = null
        )

        val result = api.start(OidcApi.Params(provider = null, oauthResponseType = OauthResponseType.ID_TOKEN))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recordedRequest = network.requests.single()
        assertThat(recordedRequest.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/start/google")
        assertThat(recordedRequest.header("traceparent")).isNotNull()
        recordedRequest.jsonBody(serialization).let { body ->
            assertThat(body.jsonString("oauthResponseType")).isEqualTo("id_token")
            assertThat(body.keys).containsExactly("oauthResponseType")
        }
    }

    @Test
    fun startCreateBuildsAppleProviderRequest() {
        val call = OidcChallengeStartApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            provider = SocialProvider.APPLE,
            oauthResponseType = OauthResponseType.ID_TOKEN,
            accessToken = null,
            loginIdHint = null,
            redirectUri = null,
            traceParent = null
        )

        assertThat(call.request.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/start/apple")
        call.request.jsonBody(serialization).let { body ->
            assertThat(body.jsonString("oauthResponseType")).isEqualTo("id_token")
            assertThat(body.keys).containsExactly("oauthResponseType")
        }
    }

    @Test
    fun startMapHttpErrorMapsBadRequestForbiddenDependencyAndRateLimit() {
        val invalidArgument = startCall.mapHttpError(
            httpError(startUrl, statusCode = 400, body = """{"errorCode":"invalid_argument","message":"provider is required"}""")
        )
        assertThat(invalidArgument).isInstanceOf(OidcStartApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as OidcStartApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val forbidden = startCall.mapHttpError(
            httpError(startUrl, statusCode = 403, body = """{"errorCode":"forbidden","message":"oidc start is not allowed"}""")
        )
        assertThat(forbidden).isInstanceOf(OidcStartApiFailure.Forbidden::class.java)
        forbidden as OidcStartApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val missingProvider = startCall.mapHttpError(
            httpError(
                startUrl,
                statusCode = 424,
                body = """{"errorCode":"missing_capability_provider","message":"missing oidc provider","capability":"oidc","scope":"session"}"""
            )
        )
        assertThat(missingProvider).isInstanceOf(OidcStartApiFailure.FailedDependency.MissingProvider::class.java)
        missingProvider as OidcStartApiFailure.FailedDependency.MissingProvider
        assertThat(missingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(missingProvider.capability).isEqualTo("oidc")
        assertThat(missingProvider.scope).isEqualTo(ApiFailureScope.SESSION)

        val maximumChallenges = startCall.mapHttpError(
            httpError(startUrl, statusCode = 429, body = """{"errorCode":"maximum_challenges_reached","message":"challenge limit reached"}""")
        )
        assertThat(maximumChallenges).isInstanceOf(OidcStartApiFailure.MaximumChallengesReached::class.java)
        maximumChallenges as OidcStartApiFailure.MaximumChallengesReached
        assertThat(maximumChallenges.errorCode).isEqualTo(ErrorCode.MaximumChallengesReached)
    }

    @Test
    fun startMapHttpErrorMapsBlankMalformedAndUnhandledResponsesToFallbacks() {
        val blankForbidden = startCall.mapHttpError(httpError(startUrl, statusCode = 403, body = ""))
        assertThat(blankForbidden).isInstanceOf(OidcStartApiFailure.Forbidden::class.java)
        assertThat(blankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        startCall.mapHttpError(httpError(startUrl, statusCode = 400, body = """{"message":"missing discriminator"}"""))
            .assertUnexpected(statusCode = 400)

        startCall.mapHttpError(httpError(startUrl, statusCode = 500, body = """{"message":"server error"}"""))
            .assertUnexpected(statusCode = 500)
    }

    @Test
    fun cancelMapHttpErrorMapsBadChallengeAndForbiddenFailures() {
        val invalidChallenge = cancelCall.mapHttpError(
            httpError(cancelUrl, statusCode = 400, body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"oidc-1"}""")
        )
        assertThat(invalidChallenge).isInstanceOf(OidcCancelApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as OidcCancelApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("oidc-1"))

        val maximumAttempts = cancelCall.mapHttpError(
            httpError(
                cancelUrl,
                statusCode = 400,
                body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"oidc-1"}"""
            )
        )
        assertThat(maximumAttempts).isInstanceOf(OidcCancelApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttempts as OidcCancelApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttempts.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttempts.challengeId).isEqualTo(ChallengeId("oidc-1"))

        val forbidden = cancelCall.mapHttpError(
            httpError(cancelUrl, statusCode = 403, body = """{"errorCode":"forbidden","message":"oidc cancel is not allowed"}""")
        )
        assertThat(forbidden).isInstanceOf(OidcCancelApiFailure.Forbidden::class.java)
        forbidden as OidcCancelApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
    }

    @Test
    fun cancelMapHttpErrorMapsBlankMalformedAndUnhandledResponsesToFallbacks() {
        val blankForbidden = cancelCall.mapHttpError(httpError(cancelUrl, statusCode = 403, body = "  "))
        assertThat(blankForbidden).isInstanceOf(OidcCancelApiFailure.Forbidden::class.java)
        assertThat(blankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        cancelCall.mapHttpError(httpError(cancelUrl, statusCode = 400, body = """{"message":"missing discriminator"}"""))
            .assertUnexpected(statusCode = 400)

        cancelCall.mapHttpError(httpError(cancelUrl, statusCode = 500, body = """{"message":"server error"}"""))
            .assertUnexpected(statusCode = 500)
    }

    private fun ApiFailure.assertUnexpected(statusCode: Int) {
        assertThat(this).isInstanceOf(ApiUnexpectedFailure::class.java)
        assertUnexpectedApiContractFailure(this as ApiUnexpectedFailure, statusCode)
    }

    private fun httpError(url: HttpUrl, statusCode: Int, body: String): Network.Response.Fail.HttpError =
        apiHttpError(url, statusCode, body)
}
