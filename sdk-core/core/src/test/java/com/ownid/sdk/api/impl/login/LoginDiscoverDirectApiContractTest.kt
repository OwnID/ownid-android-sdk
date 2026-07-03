package com.ownid.sdk.api.impl.login

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.impl.FixedApiBaseUrl
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.TestDispatcherProvider
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: API-030
internal class LoginDiscoverDirectApiContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()
    private val traceParent = "00-11111111111111111111111111111111-2222222222222222-01"

    @Test
    fun discoverUsesParamsLoginIdBeforeContextLoginIdAndSendsLoginWireRequest(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply { enqueue { success(it) } }
        val api = discoverApi(
            network = network,
            dispatcher = dispatcher,
            context = context(Authz.start(LoginId("context@example.com", LoginId.Type.Email)))
        )

        api.start(
            DiscoverApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                traceParent = traceParent
            )
        ).assertLoginSuccess()

        assertDiscoverRequest(
            request = network.requests.single(),
            loginId = LoginId("param@example.com", LoginId.Type.Email)
        )
    }

    @Test
    fun discoverUsesContextLoginIdWhenParamsOmitLoginId(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply { enqueue { success(it) } }
        val api = discoverApi(
            network = network,
            dispatcher = dispatcher,
            context = context(Authz.start(LoginId("context@example.com", LoginId.Type.Email)))
        )

        api.start(DiscoverApi.Params(traceParent = traceParent)).assertLoginSuccess()

        assertDiscoverRequest(
            request = network.requests.single(),
            loginId = LoginId("context@example.com", LoginId.Type.Email)
        )
    }

    @Test
    fun discoverMissingLoginIdFailsBeforeNetwork(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork()
        val api = discoverApi(network = network, dispatcher = dispatcher)

        val result = api.start(DiscoverApi.Params(traceParent = traceParent))

        val failure = result.failureValue()
        assertThat(failure).isInstanceOf(DiscoverApiFailure.BadRequest.InvalidArgument::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(network.requests).isEmpty()
    }

    @Test
    fun discoverMapsRawContextLoginIdValidationFailuresBeforeNetwork(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        val invalidNetwork = RecordingNetwork()
        val invalidApi = discoverApi(
            network = invalidNetwork,
            dispatcher = dispatcher,
            context = context(Authz.start("bad-email")),
            loginIdValidator = ThrowingLoginIdValidator(
                LoginIdValidationException.ValidationFailed(
                    message = "email does not match validation rule",
                    loginId = LoginId("bad-email", LoginId.Type.Email),
                    regex = "^.+@.+$"
                )
            )
        )

        val invalid = invalidApi.start(DiscoverApi.Params(traceParent = traceParent))
        val invalidFailure = invalid.failureValue()
        assertThat(invalidFailure).isInstanceOf(DiscoverApiFailure.BadRequest.InvalidLoginId::class.java)
        invalidFailure as DiscoverApiFailure.BadRequest.InvalidLoginId
        assertThat(invalidFailure.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(invalidFailure.loginId).isEqualTo(LoginId("bad-email", LoginId.Type.Email))
        assertThat(invalidNetwork.requests).isEmpty()

        val unsupportedNetwork = RecordingNetwork()
        val unsupportedApi = discoverApi(
            network = unsupportedNetwork,
            dispatcher = dispatcher,
            context = context(Authz.start("unsupported")),
            loginIdValidator = ThrowingLoginIdValidator(LoginIdValidationException.TypeNotSupported("login ID type is not supported"))
        )

        val unsupported = unsupportedApi.start(DiscoverApi.Params(traceParent = traceParent))
        val unsupportedFailure = unsupported.failureValue()
        assertThat(unsupportedFailure).isInstanceOf(DiscoverApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        assertThat(unsupportedFailure.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupportedNetwork.requests).isEmpty()
    }

    @Test
    fun loginUsesParamsAccessTokenBeforeContextAccessTokenAndFallsBackToContext(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paramsNetwork = RecordingNetwork().apply { enqueue { success(it) } }
        val paramsApi = loginApi(
            network = paramsNetwork,
            dispatcher = dispatcher,
            context = context(Authz.fromToken("context-access-token"))
        )

        paramsApi.start(LoginApi.Params(accessToken = AccessToken("param-access-token"), traceParent = traceParent)).assertLoginSuccess()

        assertLoginRequest(
            request = paramsNetwork.requests.single(),
            authorization = "Bearer param-access-token"
        )

        val contextNetwork = RecordingNetwork().apply { enqueue { success(it) } }
        val contextApi = loginApi(
            network = contextNetwork,
            dispatcher = dispatcher,
            context = context(Authz.fromToken("context-access-token"))
        )

        contextApi.start(LoginApi.Params(traceParent = traceParent)).assertLoginSuccess()

        assertLoginRequest(
            request = contextNetwork.requests.single(),
            authorization = "Bearer context-access-token"
        )
    }

    @Test
    fun loginMissingAccessTokenFailsBeforeNetwork(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork()
        val api = loginApi(network = network, dispatcher = dispatcher)

        val result = api.start(LoginApi.Params(traceParent = traceParent))

        val failure = result.failureValue()
        assertThat(failure).isInstanceOf(LoginApiFailure.BadRequest.InvalidArgument::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(network.requests).isEmpty()
    }

    @Test
    fun callerCancellationDuringDiscoverOrLoginPropagatesWithoutServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val discoverNetwork = RecordingNetwork().apply {
            enqueue {
                awaitCancellation()
            }
        }
        val discoverJob = launch {
            discoverApi(discoverNetwork, dispatcher).start(
                DiscoverApi.Params(loginId = LoginId("cancel@example.com", LoginId.Type.Email), traceParent = traceParent)
            )
        }

        runCurrent()
        assertThat(discoverNetwork.requestPaths()).containsExactly("/api/login")
        discoverJob.cancelAndJoin()
        assertThat(discoverJob.isCancelled).isTrue()
        assertThat(discoverNetwork.requestPaths()).containsExactly("/api/login")

        val loginNetwork = RecordingNetwork().apply {
            enqueue {
                awaitCancellation()
            }
        }
        val loginJob = launch {
            loginApi(loginNetwork, dispatcher).start(LoginApi.Params(accessToken = AccessToken("cancel-access-token"), traceParent = traceParent))
        }

        runCurrent()
        assertThat(loginNetwork.requestPaths()).containsExactly("/api/login")
        loginJob.cancelAndJoin()
        assertThat(loginJob.isCancelled).isTrue()
        assertThat(loginNetwork.requestPaths()).containsExactly("/api/login")
    }

    private fun discoverApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null,
        loginIdValidator: LoginIdValidator? = null
    ): DiscoverApi =
        DiscoverApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            loginIdValidator = loginIdValidator,
            interceptor = null
        )

    private fun loginApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null
    ): LoginApi =
        LoginApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            interceptor = null
        )

    private fun assertDiscoverRequest(request: Request, loginId: LoginId) {
        assertPostLoginRequest(request = request, authorization = null)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId", "extendedClientCapabilities")
        assertThat(body.getValue("loginId").jsonObject.jsonString("id")).isEqualTo(loginId.id)
        assertThat(body.getValue("loginId").jsonObject.jsonString("type")).isEqualTo(loginId.type.name)
        assertThat(body.passkeyPeek()).isTrue()
    }

    private fun assertLoginRequest(request: Request, authorization: String) {
        assertPostLoginRequest(request = request, authorization = authorization)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("extendedClientCapabilities")
        assertThat(body.passkeyPeek()).isTrue()
    }

    private fun assertPostLoginRequest(request: Request, authorization: String?) {
        assertJsonPostRequest(
            request = request,
            url = "https://auth.example.test/api/login",
            authorization = authorization,
            traceParent = traceParent
        )
    }

    private fun ApiResult<LoginResponse, *>.assertLoginSuccess() {
        val response = successValue()
        assertThat(response).isInstanceOf(LoginResponse.Success::class.java)
        response as LoginResponse.Success
        assertThat(response.accessToken).isEqualTo(AccessToken("result-access-token"))
        assertThat(response.sessionPayload).isEqualTo("""{"sessionId":"session-1"}""")
    }

    private fun context(authz: Authz): Context = Context.Builder().apply { this.authz = authz }.build("LoginDiscoverDirectApiContractTest")

    private fun success(request: Request): Network.Response.Success =
        apiSuccess(request.url, code = 201, body = """{"accessToken":"result-access-token","sessionPayload":{"sessionId":"session-1"}}""")

    private fun JsonObject.passkeyPeek(): Boolean? =
        getValue("extendedClientCapabilities").jsonObject
            .getValue("passkeys").jsonObject
            .getValue("peek").jsonPrimitive.booleanOrNull

    private class ThrowingLoginIdValidator(private val error: LoginIdValidationException) : LoginIdValidator {
        override fun determineLoginIdType(loginId: String): LoginId.Type = throw error

        override fun validate(loginId: LoginId): LoginId = loginId
    }
}
