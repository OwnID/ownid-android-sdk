package com.ownid.sdk.api.impl.verification

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.impl.FixedApiBaseUrl
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.TestDispatcherProvider
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
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
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.VerificationMethod
import com.ownid.sdk.model.setContext
import com.ownid.sdk.namespace.apiNamespace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: API-040, API-050, API-110, API-120, API-160, API-170, API-CTRL-010
internal class EmailPhoneVerificationDirectControllerContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()
    private val traceParent = "00-11111111111111111111111111111111-2222222222222222-01"

    @Test
    fun emailStartReturnsControllerWhoseLinkedCallsReuseCapturedChallengeTokenAndTraceparent(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("email-challenge", "u***r@example.com", "email-channel-id")) }
            enqueue { success(it, 200, """{"accessToken":"email-complete-access"}""") }
            enqueue { success(it, 204, "") }
            enqueue { success(it, 204, "") }
        }
        val api = emailApi(network, dispatcher)

        val controller = api.start(
            EmailVerificationApi.Params(
                loginId = LoginId("user@example.com", LoginId.Type.Email),
                loginIdHintId = "email-hint-id",
                accessToken = AccessToken("email-start-access"),
                verificationMethods = setOf(VerificationMethod.OTP, VerificationMethod.MAGIC_LINK),
                magicLinkRedirectUrl = "ownid://email/redirect",
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.challenge).isEqualTo(challenge("email-challenge", "u***r@example.com", "email-channel-id"))
        assertStartRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/verifications/email/start",
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            loginIdHintId = "email-hint-id",
            authorization = "Bearer email-start-access"
        )

        assertThat(controller.completeWithCode("123456").successValue()).isEqualTo(AccessToken("email-complete-access"))
        assertLinkedChallengeRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/verifications/email/complete",
            challengeId = "email-challenge",
            authorization = "Bearer email-start-access",
            expectedKeys = setOf("challengeId", "code")
        )
        assertThat(network.requests[1].jsonBody(serialization).jsonString("code")).isEqualTo("123456")

        controller.resend().assertUnitSuccess()
        assertLinkedChallengeRequest(
            request = network.requests[2],
            url = "https://auth.example.test/api/verifications/email/resend",
            challengeId = "email-challenge",
            authorization = "Bearer email-start-access",
            expectedKeys = setOf("challengeId")
        )

        controller.cancel(Reason.UserClose("dismissed")).assertUnitSuccess()
        assertLinkedChallengeRequest(
            request = network.requests[3],
            url = "https://auth.example.test/api/verifications/email/cancel",
            challengeId = "email-challenge",
            authorization = "Bearer email-start-access",
            expectedKeys = setOf("challengeId", "reason")
        )
        assertThat(network.requests[3].jsonBody(serialization).jsonString("reason")).isEqualTo("userClose")
    }

    @Test
    fun emailControllerLinkedCallsReuseStartTimeScopedContextAfterCurrentScopeMutates(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("email-scoped-challenge", "u***r@example.com", "email-scoped-channel-id")) }
            enqueue { success(it, 200, """{"accessToken":"email-complete-access"}""") }
            enqueue { success(it, 204, "") }
            enqueue { success(it, 204, "") }
        }
        val container = directApiContainer(network, dispatcher).apply {
            setContext { authz = Authz.fromToken("email-start-context-token") }
        }
        val api = container.apiNamespace.verifications.email

        val controller = api.start(
            EmailVerificationApi.Params(
                loginId = LoginId("scoped@example.com", LoginId.Type.Email),
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.challenge.channel).isEqualTo(OperationChannel("u***r@example.com", "email-scoped-channel-id"))
        container.setContext { authz = Authz.fromToken("email-mutated-context-token") }

        assertThat(controller.completeWithCode("111222").successValue()).isEqualTo(AccessToken("email-complete-access"))
        controller.resend().assertUnitSuccess()
        controller.cancel(Reason.UserClose("dismissed")).assertUnitSuccess()

        listOf(
            network.requests[0] to "https://auth.example.test/api/verifications/email/start",
            network.requests[1] to "https://auth.example.test/api/verifications/email/complete",
            network.requests[2] to "https://auth.example.test/api/verifications/email/resend",
            network.requests[3] to "https://auth.example.test/api/verifications/email/cancel"
        ).forEach { (request, url) ->
            assertJsonPostRequest(request, url, "Bearer email-start-context-token", traceParent)
        }
        assertStartRequestWithoutHint(
            request = network.requests[0],
            url = "https://auth.example.test/api/verifications/email/start",
            loginId = LoginId("scoped@example.com", LoginId.Type.Email),
            authorization = "Bearer email-start-context-token"
        )
        assertThat(network.requests.drop(1).map { it.jsonBody(serialization).jsonString("challengeId") })
            .containsExactly("email-scoped-challenge", "email-scoped-challenge", "email-scoped-challenge")
            .inOrder()
    }

    @Test
    fun phoneStartReturnsControllerWhoseLinkedCallsReuseCapturedChallengeTokenAndTraceparent(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-challenge", "+1******4567", "phone-channel-id")) }
            enqueue { success(it, 200, """{"accessToken":"phone-complete-access"}""") }
            enqueue { success(it, 204, "") }
            enqueue { success(it, 204, "") }
        }
        val api = phoneApi(network, dispatcher)

        val controller = api.start(
            PhoneVerificationApi.Params(
                loginId = LoginId("+15551234567", LoginId.Type.PhoneNumber),
                loginIdHintId = "phone-hint-id",
                accessToken = AccessToken("phone-start-access"),
                verificationMethods = setOf(VerificationMethod.OTP, VerificationMethod.MAGIC_LINK),
                magicLinkRedirectUrl = "ownid://phone/redirect",
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.challenge).isEqualTo(challenge("phone-challenge", "+1******4567", "phone-channel-id"))
        assertStartRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/verifications/phone/start",
            loginId = LoginId("+15551234567", LoginId.Type.PhoneNumber),
            loginIdHintId = "phone-hint-id",
            authorization = "Bearer phone-start-access"
        )

        assertThat(controller.completeWithCode("654321").successValue()).isEqualTo(AccessToken("phone-complete-access"))
        assertLinkedChallengeRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/verifications/phone/complete",
            challengeId = "phone-challenge",
            authorization = "Bearer phone-start-access",
            expectedKeys = setOf("challengeId", "code")
        )
        assertThat(network.requests[1].jsonBody(serialization).jsonString("code")).isEqualTo("654321")

        controller.resend().assertUnitSuccess()
        assertLinkedChallengeRequest(
            request = network.requests[2],
            url = "https://auth.example.test/api/verifications/phone/resend",
            challengeId = "phone-challenge",
            authorization = "Bearer phone-start-access",
            expectedKeys = setOf("challengeId")
        )

        controller.cancel(Reason.UserClose("dismissed")).assertUnitSuccess()
        assertLinkedChallengeRequest(
            request = network.requests[3],
            url = "https://auth.example.test/api/verifications/phone/cancel",
            challengeId = "phone-challenge",
            authorization = "Bearer phone-start-access",
            expectedKeys = setOf("challengeId", "reason")
        )
        assertThat(network.requests[3].jsonBody(serialization).jsonString("reason")).isEqualTo("userClose")
    }

    @Test
    fun phoneControllerLinkedCallsReuseStartTimeScopedContextAfterCurrentScopeMutates(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-scoped-challenge", "+1******3000", "phone-scoped-channel-id")) }
            enqueue { success(it, 200, """{"accessToken":"phone-complete-access"}""") }
            enqueue { success(it, 204, "") }
            enqueue { success(it, 204, "") }
        }
        val container = directApiContainer(network, dispatcher).apply {
            setContext { authz = Authz.fromToken("phone-start-context-token") }
        }
        val api = container.apiNamespace.verifications.phone

        val controller = api.start(
            PhoneVerificationApi.Params(
                loginId = LoginId("+15551230000", LoginId.Type.PhoneNumber),
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.challenge.channel).isEqualTo(OperationChannel("+1******3000", "phone-scoped-channel-id"))
        container.setContext { authz = Authz.fromToken("phone-mutated-context-token") }

        assertThat(controller.completeWithCode("333444").successValue()).isEqualTo(AccessToken("phone-complete-access"))
        controller.resend().assertUnitSuccess()
        controller.cancel(Reason.UserClose("dismissed")).assertUnitSuccess()

        listOf(
            network.requests[0] to "https://auth.example.test/api/verifications/phone/start",
            network.requests[1] to "https://auth.example.test/api/verifications/phone/complete",
            network.requests[2] to "https://auth.example.test/api/verifications/phone/resend",
            network.requests[3] to "https://auth.example.test/api/verifications/phone/cancel"
        ).forEach { (request, url) ->
            assertJsonPostRequest(request, url, "Bearer phone-start-context-token", traceParent)
        }
        assertStartRequestWithoutHint(
            request = network.requests[0],
            url = "https://auth.example.test/api/verifications/phone/start",
            loginId = LoginId("+15551230000", LoginId.Type.PhoneNumber),
            authorization = "Bearer phone-start-context-token"
        )
        assertThat(network.requests.drop(1).map { it.jsonBody(serialization).jsonString("challengeId") })
            .containsExactly("phone-scoped-challenge", "phone-scoped-challenge", "phone-scoped-challenge")
            .inOrder()
    }

    @Test
    fun emailStartUsesContextLoginIdOrContextAccessTokenWhenParamsOmitThem(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextLoginIdNetwork = RecordingNetwork().apply { enqueue { success(it, 201, challengeBody("email-context-login-id")) } }
        val contextLoginIdApi = emailApi(
            network = contextLoginIdNetwork,
            dispatcher = dispatcher,
            context = context(Authz.start(LoginId("context@example.com", LoginId.Type.Email)))
        )

        contextLoginIdApi.start(
            EmailVerificationApi.Params(accessToken = AccessToken("email-param-token"), traceParent = traceParent)
        ).successValue()

        assertStartRequestUsesLoginIdAndAuthorization(
            request = contextLoginIdNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/email/start",
            loginId = LoginId("context@example.com", LoginId.Type.Email),
            authorization = "Bearer email-param-token"
        )

        val contextTokenNetwork = RecordingNetwork().apply { enqueue { success(it, 201, challengeBody("email-context-token")) } }
        val contextTokenApi = emailApi(
            network = contextTokenNetwork,
            dispatcher = dispatcher,
            context = context(Authz.fromToken("email-context-token"))
        )

        contextTokenApi.start(
            EmailVerificationApi.Params(loginId = LoginId("param@example.com", LoginId.Type.Email), traceParent = traceParent)
        ).successValue()

        assertStartRequestUsesLoginIdAndAuthorization(
            request = contextTokenNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/email/start",
            loginId = LoginId("param@example.com", LoginId.Type.Email),
            authorization = "Bearer email-context-token"
        )
    }

    @Test
    fun phoneStartUsesContextLoginIdOrContextAccessTokenWhenParamsOmitThem(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextLoginIdNetwork = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-context-login-id", "+1******0001", "phone-context-login-id-channel-id")) }
        }
        val contextLoginIdApi = phoneApi(
            network = contextLoginIdNetwork,
            dispatcher = dispatcher,
            context = context(Authz.start(LoginId("+15550000001", LoginId.Type.PhoneNumber)))
        )

        contextLoginIdApi.start(
            PhoneVerificationApi.Params(accessToken = AccessToken("phone-param-token"), traceParent = traceParent)
        ).successValue()

        assertStartRequestUsesLoginIdAndAuthorization(
            request = contextLoginIdNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/phone/start",
            loginId = LoginId("+15550000001", LoginId.Type.PhoneNumber),
            authorization = "Bearer phone-param-token"
        )

        val contextTokenNetwork = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-context-token", "+1******0002", "phone-context-token-channel-id")) }
        }
        val contextTokenApi = phoneApi(
            network = contextTokenNetwork,
            dispatcher = dispatcher,
            context = context(Authz.fromToken("phone-context-token"))
        )

        contextTokenApi.start(
            PhoneVerificationApi.Params(loginId = LoginId("+15550000002", LoginId.Type.PhoneNumber), traceParent = traceParent)
        ).successValue()

        assertStartRequestUsesLoginIdAndAuthorization(
            request = contextTokenNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/phone/start",
            loginId = LoginId("+15550000002", LoginId.Type.PhoneNumber),
            authorization = "Bearer phone-context-token"
        )
    }

    @Test
    fun emailControllerDoesNotSendServerCancelWithoutExplicitCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply { enqueue { success(it, 201, challengeBody("email-drop-challenge")) } }
        val api = emailApi(network, dispatcher)

        val controller = api.start(
            EmailVerificationApi.Params(
                loginId = LoginId("drop@example.com", LoginId.Type.Email),
                accessToken = AccessToken("email-drop-token"),
                traceParent = traceParent
            )
        ).successValue()
        assertThat(controller.challenge.challengeId).isEqualTo(ChallengeId("email-drop-challenge"))

        assertThat(network.requests).hasSize(1)
        assertThat(network.requestPaths()).doesNotContain("/api/verifications/email/cancel")
    }

    @Test
    fun phoneControllerDoesNotSendServerCancelWithoutExplicitCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-drop-challenge", "+1******0003", "phone-drop-channel-id")) }
        }
        val api = phoneApi(network, dispatcher)

        val controller = api.start(
            PhoneVerificationApi.Params(
                loginId = LoginId("+15550000003", LoginId.Type.PhoneNumber),
                accessToken = AccessToken("phone-drop-token"),
                traceParent = traceParent
            )
        ).successValue()
        assertThat(controller.challenge.challengeId).isEqualTo(ChallengeId("phone-drop-challenge"))

        assertThat(network.requests).hasSize(1)
        assertThat(network.requestPaths()).doesNotContain("/api/verifications/phone/cancel")
    }

    @Test
    fun cancellingCallerDuringEmailStartDoesNotSendServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val startRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork().apply {
            enqueue {
                startRequest.complete(it)
                awaitCancellation()
            }
        }
        val api = emailApi(network, dispatcher)

        val job = launch {
            api.start(
                EmailVerificationApi.Params(
                    loginId = LoginId("cancel-start@example.com", LoginId.Type.Email),
                    accessToken = AccessToken("email-cancel-start-token"),
                    traceParent = traceParent
                )
            )
        }

        assertThat(startRequest.await().url.encodedPath).endsWith("/verifications/email/start")
        job.cancelAndJoin()

        assertThat(network.requestPaths()).doesNotContain("/api/verifications/email/cancel")
    }

    @Test
    fun cancellingCallerDuringPhoneStartDoesNotSendServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val startRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork().apply {
            enqueue {
                startRequest.complete(it)
                awaitCancellation()
            }
        }
        val api = phoneApi(network, dispatcher)

        val job = launch {
            api.start(
                PhoneVerificationApi.Params(
                    loginId = LoginId("+15550000004", LoginId.Type.PhoneNumber),
                    accessToken = AccessToken("phone-cancel-start-token"),
                    traceParent = traceParent
                )
            )
        }

        assertThat(startRequest.await().url.encodedPath).endsWith("/verifications/phone/start")
        job.cancelAndJoin()

        assertThat(network.requestPaths()).doesNotContain("/api/verifications/phone/cancel")
    }

    @Test
    fun cancellingCallerDuringEmailControllerMethodDoesNotSendServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val completeRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("email-controller-cancel")) }
            enqueue {
                completeRequest.complete(it)
                awaitCancellation()
            }
        }
        val api = emailApi(network, dispatcher)
        val controller = api.start(
            EmailVerificationApi.Params(
                loginId = LoginId("cancel-controller@example.com", LoginId.Type.Email),
                accessToken = AccessToken("email-controller-token"),
                traceParent = traceParent
            )
        ).successValue()

        val job = launch { controller.completeWithCode("000001") }

        assertThat(completeRequest.await().url.encodedPath).endsWith("/verifications/email/complete")
        job.cancelAndJoin()

        assertThat(network.requestPaths()).doesNotContain("/api/verifications/email/cancel")
    }

    @Test
    fun cancellingCallerDuringPhoneControllerMethodDoesNotSendServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val completeRequest = CompletableDeferred<Request>()
        val network = RecordingNetwork().apply {
            enqueue { success(it, 201, challengeBody("phone-controller-cancel", "+1******0005", "phone-controller-cancel-channel-id")) }
            enqueue {
                completeRequest.complete(it)
                awaitCancellation()
            }
        }
        val api = phoneApi(network, dispatcher)
        val controller = api.start(
            PhoneVerificationApi.Params(
                loginId = LoginId("+15550000005", LoginId.Type.PhoneNumber),
                accessToken = AccessToken("phone-controller-token"),
                traceParent = traceParent
            )
        ).successValue()

        val job = launch { controller.completeWithCode("000002") }

        assertThat(completeRequest.await().url.encodedPath).endsWith("/verifications/phone/complete")
        job.cancelAndJoin()

        assertThat(network.requestPaths()).doesNotContain("/api/verifications/phone/cancel")
    }

    private fun emailApi(network: Network, dispatcher: CoroutineDispatcher, context: Context? = null): EmailVerificationApi =
        EmailVerificationApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            loginIdValidator = null,
            interceptor = null
        )

    private fun phoneApi(network: Network, dispatcher: CoroutineDispatcher, context: Context? = null): PhoneVerificationApi =
        PhoneVerificationApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            loginIdValidator = null,
            interceptor = null
        )

    private fun directApiContainer(network: Network, dispatcher: CoroutineDispatcher): DIContainer =
        DIContainerImpl(scopeName = "verification-direct-api").apply {
            register<ApiBaseUrl>(FixedApiBaseUrl(baseUrl))
            register<Network>(network)
            register<Serialization>(serialization)
            register<DispatcherProvider>(TestDispatcherProvider(dispatcher))
            registerFactory<EmailVerificationApi>(
                ApiBaseUrl::class.java,
                Network::class.java,
                Serialization::class.java,
                DispatcherProvider::class.java
            ) {
                EmailVerificationApiImpl.create(this)
            }
            registerFactory<PhoneVerificationApi>(
                ApiBaseUrl::class.java,
                Network::class.java,
                Serialization::class.java,
                DispatcherProvider::class.java
            ) {
                PhoneVerificationApiImpl.create(this)
            }
        }

    private fun assertStartRequest(request: Request, url: String, loginId: LoginId, loginIdHintId: String, authorization: String) {
        assertStartRequestUsesLoginIdAndAuthorization(request, url, loginId, authorization)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId", "loginIdHintId", "verificationMethods", "magicLinkRedirectUrl")
        assertThat(body.jsonString("loginIdHintId")).isEqualTo(loginIdHintId)
        val expectedRedirectUrl = if (loginId.type == LoginId.Type.Email) "ownid://email/redirect" else "ownid://phone/redirect"
        assertThat(body.jsonString("magicLinkRedirectUrl")).isEqualTo(expectedRedirectUrl)
        assertThat(body.getValue("verificationMethods").jsonArray.map { it.jsonPrimitive.contentOrNull }).containsExactly("Otp", "MagicLink")
    }

    private fun assertStartRequestWithoutHint(request: Request, url: String, loginId: LoginId, authorization: String) {
        assertStartRequestUsesLoginIdAndAuthorization(request, url, loginId, authorization)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId")
    }

    private fun assertStartRequestUsesLoginIdAndAuthorization(request: Request, url: String, loginId: LoginId, authorization: String) {
        assertJsonPostRequest(request, url, authorization, traceParent)

        val body = request.jsonBody(serialization)
        assertThat(body.getValue("loginId").jsonObject.jsonString("id")).isEqualTo(loginId.id)
        assertThat(body.getValue("loginId").jsonObject.jsonString("type")).isEqualTo(
            when (loginId.type) {
                LoginId.Type.Email -> "Email"
                LoginId.Type.PhoneNumber -> "PhoneNumber"
                else -> error("Unexpected verification login ID type: ${loginId.type}")
            }
        )
    }

    private fun assertLinkedChallengeRequest(request: Request, url: String, challengeId: String, authorization: String, expectedKeys: Set<String>) {
        assertJsonPostRequest(request, url, authorization, traceParent)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactlyElementsIn(expectedKeys)
        assertThat(body.jsonString("challengeId")).isEqualTo(challengeId)
    }

    private fun context(authz: Authz): Context = Context.Builder().apply { this.authz = authz }.build("VerificationDirectControllerContractTest")

    private fun challenge(id: String, channel: String, channelId: String): VerificationChallenge =
        VerificationChallenge(
            challengeId = ChallengeId(id),
            resendPolicy = VerificationChallenge.ResendPolicy(allow = true, attempts = 2, debounce = 1),
            timeout = Timeout(30000),
            attempts = 3,
            methods = VerificationChallenge.Methods(
                otp = VerificationChallenge.Methods.Otp(length = 6),
                magicLink = VerificationChallenge.Methods.MagicLink()
            ),
            channel = OperationChannel(channel = channel, id = channelId)
        )

    private fun challengeBody(
        challengeId: String,
        channel: String = "u***r@example.com",
        channelId: String = "$challengeId-channel-id"
    ): String {
        return """
            {
              "challengeId": "$challengeId",
              "resendPolicy": {
                "allow": true,
                "attempts": 2,
                "debounce": 1
              },
              "timeout": 30000,
              "attempts": 3,
              "channel": {
                "channel": "$channel",
                "id": "$channelId"
              },
              "methods": {
                "otp": { "length": 6 },
                "magicLink": {}
              }
            }
        """.trimIndent()
    }

    private fun success(request: Request, code: Int, body: String): Network.Response.Success =
        apiSuccess(request.url, code, body)
}
