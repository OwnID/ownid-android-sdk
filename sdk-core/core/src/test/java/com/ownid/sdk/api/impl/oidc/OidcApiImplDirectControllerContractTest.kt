package com.ownid.sdk.api.impl.oidc

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.impl.FixedApiBaseUrl
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.TestDispatcherProvider
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertJsonStringsExactly
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.setContext
import com.ownid.sdk.namespace.apiNamespace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: API-090, API-100, API-150, API-200, API-CTRL-030
internal class OidcApiImplDirectControllerContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun startDefaultsToGoogleCapturesContextAccessTokenTraceparentAndCompletesWithTokenUsingOriginalChallenge(): Unit = runTest {
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/oidc/idp/start/google" -> startSuccess(request, challengeId = "default-google-challenge")
                "/api/oidc/idp/complete" -> completeSuccess(request, provider = "Google")
                else -> error("Unexpected request URL: ${request.url}")
            }
        }
        val api = oidcApi(network = network, context = accessTokenContext())

        val startResult = api.start()

        val controller = startResult.successValue()
        assertThat(controller.challenge.challengeId).isEqualTo(ChallengeId("default-google-challenge"))
        val startRequest = network.requests.single()
        val capturedTraceParent = startRequest.header("traceparent")
        assertThat(startRequest.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/start/google")
        assertThat(startRequest.header("Authorization")).isEqualTo("Bearer context-access-token")
        assertThat(capturedTraceParent).isNotNull()
        startRequest.jsonBody(serialization).assertJsonStringsExactly("oauthResponseType" to "id_token")

        val completeResult = controller.completeWithToken("provider-id-token")

        val token = completeResult.successValue()
        assertThat(token.provider).isEqualTo(SocialProvider.GOOGLE)
        assertThat(token.loginId).isEqualTo(LoginId("user@example.com", LoginId.Type.Email))
        val completeRequest = network.requests.last()
        assertThat(completeRequest.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/complete")
        assertThat(completeRequest.header("Authorization")).isEqualTo("Bearer context-access-token")
        assertThat(completeRequest.header("traceparent")).isEqualTo(capturedTraceParent)
        completeRequest.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "default-google-challenge",
            "idToken" to "provider-id-token"
        )
        assertThat(network.requests).hasSize(2)
    }

    @Test
    fun controllerCompletionAndCancelReuseStartTimeScopedContextAfterCurrentScopeMutates(): Unit = runTest {
        val traceParent = "00-cccccccccccccccccccccccccccccccc-dddddddddddddddd-00"
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/oidc/idp/start/apple" -> startSuccess(request, challengeId = "scoped-oidc-challenge")
                "/api/oidc/idp/complete" -> completeSuccess(request, provider = "Apple")
                "/api/oidc/idp/cancel" -> apiSuccess(request.url, code = 204, body = "")
                else -> error("Unexpected request URL: ${request.url}")
            }
        }
        val container = directApiContainer(network, dispatcher).apply {
            setContext { authz = Authz.fromToken("oidc-start-context-token") }
        }
        val api = container.apiNamespace.oidc

        val controller = api.start(
            OidcApi.Params(
                provider = SocialProvider.APPLE,
                oauthResponseType = OauthResponseType.CODE,
                traceParent = traceParent
            )
        ).successValue()

        container.setContext { authz = Authz.fromToken("oidc-mutated-context-token") }

        assertThat(controller.completeWithCode("provider-code").successValue().provider).isEqualTo(SocialProvider.APPLE)
        controller.cancel(Reason.UserClose()).assertUnitSuccess()

        assertJsonPostRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/oidc/idp/start/apple",
            authorization = "Bearer oidc-start-context-token",
            traceParent = traceParent
        )
        assertJsonPostRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/oidc/idp/complete",
            authorization = "Bearer oidc-start-context-token",
            traceParent = traceParent
        )
        network.requests[1].jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "scoped-oidc-challenge",
            "code" to "provider-code"
        )
        assertJsonPostRequest(
            request = network.requests[2],
            url = "https://auth.example.test/api/oidc/idp/cancel",
            authorization = "Bearer oidc-start-context-token",
            traceParent = traceParent
        )
        network.requests[2].jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "scoped-oidc-challenge",
            "reason" to "userClose"
        )
    }

    @Test
    fun startUsesExplicitProviderTokenTraceparentAndCompletesWithCodeUsingOriginalChallenge(): Unit = runTest {
        val traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/oidc/idp/start/apple" -> startSuccess(request, challengeId = "explicit-apple-challenge")
                "/api/oidc/idp/complete" -> completeSuccess(request, provider = "Apple")
                else -> error("Unexpected request URL: ${request.url}")
            }
        }
        val api = oidcApi(network = network, context = accessTokenContext())

        val startResult = api.start(
            OidcApi.Params(
                provider = SocialProvider.APPLE,
                oauthResponseType = OauthResponseType.CODE,
                accessToken = AccessToken("explicit-access-token"),
                traceParent = traceParent
            )
        )

        val controller = startResult.successValue()
        val startRequest = network.requests.single()
        assertThat(startRequest.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/start/apple")
        assertThat(startRequest.header("Authorization")).isEqualTo("Bearer explicit-access-token")
        assertThat(startRequest.header("traceparent")).isEqualTo(traceParent)
        startRequest.jsonBody(serialization).assertJsonStringsExactly("oauthResponseType" to "code")

        val completeResult = controller.completeWithCode("provider-code")

        assertThat(completeResult.successValue().provider).isEqualTo(SocialProvider.APPLE)
        val completeRequest = network.requests.last()
        assertThat(completeRequest.header("Authorization")).isEqualTo("Bearer explicit-access-token")
        assertThat(completeRequest.header("traceparent")).isEqualTo(traceParent)
        completeRequest.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "explicit-apple-challenge",
            "code" to "provider-code"
        )
        assertThat(network.requests).hasSize(2)
    }

    @Test
    fun mismatchedResponseTypeFailsLocallyWithoutCompleteNetworkRequest(): Unit = runTest {
        val network = RecordingNetwork { request ->
            startSuccess(request, challengeId = "challenge-${request.url.encodedPathSegments.last()}")
        }
        val api = oidcApi(network = network)
        val codeController = api.start(OidcApi.Params(oauthResponseType = OauthResponseType.CODE)).successValue()
        val idTokenController = api.start(OidcApi.Params(oauthResponseType = OauthResponseType.ID_TOKEN)).successValue()

        val tokenResult = codeController.completeWithToken("provider-id-token")
        val codeResult = idTokenController.completeWithCode("provider-code")

        tokenResult.assertInvalidArgument()
        codeResult.assertInvalidArgument()
        assertThat(network.requests).hasSize(2)
        assertThat(network.requests.map { it.url.encodedPath }).containsExactly(
            "/api/oidc/idp/start/google",
            "/api/oidc/idp/start/google"
        )
    }

    @Test
    fun explicitCancelSendsCancelRequestWithCapturedChallengeTokenTraceparentAndReason(): Unit = runTest {
        val traceParent = "00-cccccccccccccccccccccccccccccccc-dddddddddddddddd-00"
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/oidc/idp/start/google" -> startSuccess(request, challengeId = "cancel-challenge")
                "/api/oidc/idp/cancel" -> apiSuccess(request.url, code = 204, body = "")
                else -> error("Unexpected request URL: ${request.url}")
            }
        }
        val api = oidcApi(network = network)
        val controller = api.start(OidcApi.Params(accessToken = AccessToken("cancel-access-token"), traceParent = traceParent)).successValue()

        val cancelResult = controller.cancel(Reason.UserClose())

        cancelResult.assertUnitSuccess()
        val cancelRequest = network.requests.last()
        assertThat(cancelRequest.url.toString()).isEqualTo("https://auth.example.test/api/oidc/idp/cancel")
        assertThat(cancelRequest.header("Authorization")).isEqualTo("Bearer cancel-access-token")
        assertThat(cancelRequest.header("traceparent")).isEqualTo(traceParent)
        cancelRequest.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "cancel-challenge",
            "reason" to "userClose"
        )
        assertThat(network.requests).hasSize(2)
    }

    @Test
    fun callerCancellationDuringStartDoesNotSendServerSideCancel(): Unit = runTest {
        val startRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork { request ->
            startRequest.complete(request)
            awaitCancellation()
        }
        val api = oidcApi(network = network)

        val job = launch { api.start() }

        assertThat(startRequest.await().url.encodedPath).isEqualTo("/api/oidc/idp/start/google")
        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requests.map { it.url.encodedPath }).containsExactly("/api/oidc/idp/start/google")
    }

    @Test
    fun callerCancellationDuringControllerCompletionDoesNotSendServerSideCancel(): Unit = runTest {
        val completeRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/oidc/idp/start/google" -> startSuccess(request, challengeId = "completion-cancel-challenge")
                "/api/oidc/idp/complete" -> {
                    completeRequest.complete(request)
                    awaitCancellation()
                }

                else -> error("Unexpected request URL: ${request.url}")
            }
        }
        val api = oidcApi(network = network)
        val controller = api.start().successValue()

        val job = launch { controller.completeWithToken("provider-id-token") }

        assertThat(completeRequest.await().url.encodedPath).isEqualTo("/api/oidc/idp/complete")
        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requests.map { it.url.encodedPath }).containsExactly(
            "/api/oidc/idp/start/google",
            "/api/oidc/idp/complete"
        )
    }

    private fun TestScope.oidcApi(network: Network, context: Context? = null): OidcApi {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return OidcApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            interceptor = null
        )
    }

    private fun directApiContainer(network: Network, dispatcher: CoroutineDispatcher): DIContainer =
        DIContainerImpl(scopeName = "oidc-direct-api").apply {
            register<ApiBaseUrl>(FixedApiBaseUrl(baseUrl))
            register<Network>(network)
            register<Serialization>(serialization)
            register<DispatcherProvider>(TestDispatcherProvider(dispatcher))
            registerFactory<OidcApi>(
                ApiBaseUrl::class.java,
                Network::class.java,
                Serialization::class.java,
                DispatcherProvider::class.java
            ) {
                OidcApiImpl.create(this)
            }
        }

    private fun accessTokenContext(): Context =
        Context.Builder().apply {
            authz = Authz.fromToken(AccessToken("context-access-token"))
        }.build(scopeName = "test-context")

    private fun startSuccess(request: Request, challengeId: String): Network.Response.Success =
        apiSuccess(
            url = request.url,
            code = 201,
            body = """
                {
                  "challengeId": "$challengeId",
                  "timeout": 30000,
                  "clientId": "provider-client-id",
                  "challengeUrl": "https://accounts.example.test/challenge"
                }
            """.trimIndent()
        )

    private fun completeSuccess(request: Request, provider: String): Network.Response.Success =
        apiSuccess(
            url = request.url,
            code = 200,
            body = """
                {
                  "accessToken": "ownid-access-token",
                  "loginId": { "id": "user@example.com", "type": "Email" },
                  "userInfo": {
                    "name": "Ada",
                    "email": "user@example.com"
                  },
                  "provider": "$provider"
                }
            """.trimIndent()
        )

    private fun ApiResult<*, OidcCompleteApiFailure>.assertInvalidArgument() {
        assertThat(this).isInstanceOf(ApiResult.Failure::class.java)
        val failure = (this as ApiResult.Failure).failure
        assertThat(failure).isInstanceOf(OidcCompleteApiFailure.BadRequest.InvalidArgument::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.InvalidArgument)
    }
}
