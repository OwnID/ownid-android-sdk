package com.ownid.sdk.api.impl.passkey

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAttestationApi
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
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.TransportType
import com.ownid.sdk.model.setContext
import com.ownid.sdk.namespace.apiNamespace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: API-070, API-080, API-130, API-140, API-180, API-190, API-CTRL-020
internal class PasskeyDirectControllerContractTest {

    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()
    private val traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"

    @Test
    fun assertionStartWithParamsReturnsControllerThatCapturesOptionsAccessTokenTraceAndExplicitCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/assertion/options" -> apiSuccess(request.url, 201, assertionOptionsBody(challenge = "YXNzZXJ0aW9uLWNoYWxsZW5nZQ"))
                "/api/passkeys/assertion/result" -> apiSuccess(request.url, 200, """{"accessToken":"verified-access-token"}""")
                "/api/passkeys/assertion/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = assertionApi(network, dispatcher)

        val controller = api.start(
            PasskeyAssertionApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                accessToken = AccessToken("param-access-token"),
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.assertionOptions.challenge).isEqualTo(ChallengeId("YXNzZXJ0aW9uLWNoYWxsZW5nZQ"))
        val startRequest = network.requests.single()
        assertJsonPostRequest(
            request = startRequest,
            url = "https://auth.example.test/api/passkeys/assertion/options",
            authorization = "Bearer param-access-token",
            traceParent = traceParent
        )
        val loginId = startRequest.jsonBody().getValue("loginId").jsonObject
        assertThat(loginId.jsonString("id")).isEqualTo("param@example.com")
        assertThat(loginId.jsonString("type")).isEqualTo("Email")

        val verifyResult = controller.verify(assertionResult()).successValue()

        assertThat(verifyResult).isEqualTo(AccessToken("verified-access-token"))
        val verifyRequest = network.requests.last()
        assertJsonPostRequest(
            request = verifyRequest,
            url = "https://auth.example.test/api/passkeys/assertion/result",
            authorization = "Bearer param-access-token",
            traceParent = traceParent
        )
        assertThat(verifyRequest.jsonBody().jsonString("id")).isEqualTo("assertion-credential-id")

        controller.cancel(Reason.MoveToOtherChallenge()).assertUnitSuccess()

        val cancelRequest = network.requests.last()
        assertJsonPostRequest(
            request = cancelRequest,
            url = "https://auth.example.test/api/passkeys/assertion/cancel",
            authorization = null,
            traceParent = traceParent
        )
        cancelRequest.jsonBody().let { body ->
            assertThat(body.jsonString("challenge")).isEqualTo("YXNzZXJ0aW9uLWNoYWxsZW5nZQ")
            assertThat(body.jsonString("reason")).isEqualTo("moveToOtherChallenge")
        }
    }

    @Test
    fun assertionStartUsesContextLoginIdWhenParamsAreOmitted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            apiSuccess(request.url, 201, assertionOptionsBody(challenge = "Y29udGV4dC1hc3NlcnRpb24tY2hhbGxlbmdl"))
        }
        val api = assertionApi(
            network = network,
            dispatcher = dispatcher,
            context = context {
                authz = Authz.start(LoginId("context@example.com", LoginId.Type.Email))
            }
        )

        val controller = api.start().successValue()

        assertThat(controller.assertionOptions.challenge).isEqualTo(ChallengeId("Y29udGV4dC1hc3NlcnRpb24tY2hhbGxlbmdl"))
        val loginId = network.requests.single().jsonBody().getValue("loginId").jsonObject
        assertThat(loginId.jsonString("id")).isEqualTo("context@example.com")
        assertThat(loginId.jsonString("type")).isEqualTo("Email")
    }

    @Test
    fun assertionStartAndVerifyUseContextAccessTokenWhenParamsOmitAccessToken() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/assertion/options" -> apiSuccess(request.url, 201, assertionOptionsBody(challenge = "Y29udGV4dC10b2tlbi1hc3NlcnRpb24"))
                "/api/passkeys/assertion/result" -> apiSuccess(request.url, 200, """{"accessToken":"verified-context-access-token"}""")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = assertionApi(
            network = network,
            dispatcher = dispatcher,
            context = context {
                authz = Authz.fromToken(AccessToken("context-access-token"))
            }
        )

        val controller = api.start(
            PasskeyAssertionApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.verify(assertionResult()).successValue()).isEqualTo(AccessToken("verified-context-access-token"))
        assertJsonPostRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/passkeys/assertion/options",
            authorization = "Bearer context-access-token",
            traceParent = traceParent
        )
        assertJsonPostRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/passkeys/assertion/result",
            authorization = "Bearer context-access-token",
            traceParent = traceParent
        )
    }

    @Test
    fun assertionControllerVerifyReusesStartTimeScopedContextAndCancelReusesChallengeTraceAfterCurrentScopeMutates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/assertion/options" -> apiSuccess(request.url, 201, assertionOptionsBody(challenge = "YXNzZXJ0aW9uLXNjb3BlZA"))
                "/api/passkeys/assertion/result" -> apiSuccess(request.url, 200, """{"accessToken":"verified-scoped-access-token"}""")
                "/api/passkeys/assertion/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val container = directApiContainer(network, dispatcher).apply {
            setContext { authz = Authz.fromToken("assertion-start-context-token") }
        }
        val api = container.apiNamespace.passkeys.assertion

        val controller = api.start(
            PasskeyAssertionApi.Params(
                loginId = LoginId("assertion@example.com", LoginId.Type.Email),
                traceParent = traceParent
            )
        ).successValue()

        container.setContext { authz = Authz.fromToken("assertion-mutated-context-token") }

        assertThat(controller.verify(assertionResult()).successValue()).isEqualTo(AccessToken("verified-scoped-access-token"))
        controller.cancel(Reason.UserClose()).assertUnitSuccess()

        assertJsonPostRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/passkeys/assertion/options",
            authorization = "Bearer assertion-start-context-token",
            traceParent = traceParent
        )
        assertJsonPostRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/passkeys/assertion/result",
            authorization = "Bearer assertion-start-context-token",
            traceParent = traceParent
        )
        assertJsonPostRequest(
            request = network.requests[2],
            url = "https://auth.example.test/api/passkeys/assertion/cancel",
            authorization = null,
            traceParent = traceParent
        )
        assertThat(network.requests[2].jsonBody().jsonString("challenge")).isEqualTo("YXNzZXJ0aW9uLXNjb3BlZA")
    }

    @Test
    fun assertionCallerCancellationDuringStartDoesNotSendServerSideCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val optionsRequest = CompletableDeferred<String>()
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/assertion/options" -> {
                    optionsRequest.complete(request.url.encodedPath)
                    awaitCancellation()
                }

                "/api/passkeys/assertion/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = assertionApi(network, dispatcher)

        val job = launch {
            api.start(
                PasskeyAssertionApi.Params(
                    loginId = LoginId("param@example.com", LoginId.Type.Email),
                    accessToken = AccessToken("param-access-token"),
                    traceParent = traceParent
                )
            )
        }

        assertThat(optionsRequest.await()).isEqualTo("/api/passkeys/assertion/options")

        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requestPaths()).doesNotContain("/api/passkeys/assertion/cancel")
    }

    @Test
    fun assertionCallerCancellationDuringControllerVerifyDoesNotSendServerSideCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resultRequest = CompletableDeferred<String>()
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/assertion/options" -> apiSuccess(
                    request.url,
                    201,
                    assertionOptionsBody(challenge = "YXNzZXJ0aW9uLXZlcmlmeS1jYW5jZWw")
                )

                "/api/passkeys/assertion/result" -> {
                    resultRequest.complete(request.url.encodedPath)
                    awaitCancellation()
                }

                "/api/passkeys/assertion/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = assertionApi(network, dispatcher)
        val controller = api.start(
            PasskeyAssertionApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                accessToken = AccessToken("param-access-token"),
                traceParent = traceParent
            )
        ).successValue()

        val job = launch { controller.verify(assertionResult()) }

        assertThat(resultRequest.await()).isEqualTo("/api/passkeys/assertion/result")

        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requestPaths()).containsExactly(
            "/api/passkeys/assertion/options",
            "/api/passkeys/assertion/result"
        ).inOrder()
        assertThat(network.requestPaths()).doesNotContain("/api/passkeys/assertion/cancel")
    }

    @Test
    fun attestationControllerVerifyReusesStartTimeScopedContextAndCancelReusesChallengeTraceAfterCurrentScopeMutates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/attestation/options" -> apiSuccess(request.url, 200, attestationOptionsBody(challenge = "YXR0ZXN0YXRpb24tc2NvcGVk"))
                "/api/passkeys/attestation/result" -> apiSuccess(request.url, 200, """{"proofToken":"scoped-proof-token","ownIdData":{"session":"scoped"}}""")
                "/api/passkeys/attestation/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val container = directApiContainer(network, dispatcher).apply {
            setContext {
                authz = Authz.fromToken("attestation-start-context-token")
                accountDisplayName = "Start Display"
            }
        }
        val api = container.apiNamespace.passkeys.attestation

        val controller = api.start(
            PasskeyAttestationApi.Params(
                loginId = LoginId("attestation@example.com", LoginId.Type.Email),
                traceParent = traceParent
            )
        ).successValue()

        container.setContext {
            authz = Authz.fromToken("attestation-mutated-context-token")
            accountDisplayName = "Mutated Display"
        }

        val verifyResult = controller.verify(attestationResult()).successValue()
        assertThat(verifyResult.proofToken.token).isEqualTo("scoped-proof-token")
        assertThat(verifyResult.ownIdData).isEqualTo("""{"session":"scoped"}""")
        controller.cancel(Reason.UserClose()).assertUnitSuccess()

        assertJsonPostRequest(
            request = network.requests[0],
            url = "https://auth.example.test/api/passkeys/attestation/options",
            authorization = "Bearer attestation-start-context-token",
            traceParent = traceParent
        )
        assertThat(network.requests[0].jsonBody().jsonString("accountDisplayName")).isEqualTo("Start Display")
        assertJsonPostRequest(
            request = network.requests[1],
            url = "https://auth.example.test/api/passkeys/attestation/result",
            authorization = "Bearer attestation-start-context-token",
            traceParent = traceParent
        )
        assertJsonPostRequest(
            request = network.requests[2],
            url = "https://auth.example.test/api/passkeys/attestation/cancel",
            authorization = null,
            traceParent = traceParent
        )
        assertThat(network.requests[2].jsonBody().jsonString("challenge")).isEqualTo("YXR0ZXN0YXRpb24tc2NvcGVk")
    }

    @Test
    fun attestationStartWithParamsAndContextReturnsControllerThatCapturesOptionsAccessTokenTraceAndExplicitCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/attestation/options" -> apiSuccess(request.url, 200, attestationOptionsBody(challenge = "YXR0ZXN0YXRpb24tY2hhbGxlbmdl"))
                "/api/passkeys/attestation/result" -> apiSuccess(request.url, 200, """{"proofToken":"proof-token","ownIdData":{"session":"created"}}""")
                "/api/passkeys/attestation/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = attestationApi(
            network = network,
            dispatcher = dispatcher,
            context = context {
                authz = Authz.fromToken(AccessToken("context-access-token"))
                accountDisplayName = "Context Display Name"
            }
        )

        val controller = api.start(
            PasskeyAttestationApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                accountDisplayName = null,
                accessToken = null,
                traceParent = traceParent
            )
        ).successValue()

        assertThat(controller.attestationOptions.challenge).isEqualTo(ChallengeId("YXR0ZXN0YXRpb24tY2hhbGxlbmdl"))
        val startRequest = network.requests.single()
        assertJsonPostRequest(
            request = startRequest,
            url = "https://auth.example.test/api/passkeys/attestation/options",
            authorization = "Bearer context-access-token",
            traceParent = traceParent
        )
        val body = startRequest.jsonBody()
        val loginId = body.getValue("loginId").jsonObject
        assertThat(loginId.jsonString("id")).isEqualTo("param@example.com")
        assertThat(loginId.jsonString("type")).isEqualTo("Email")
        assertThat(body.jsonString("accountDisplayName")).isEqualTo("Context Display Name")

        val verifyResult = controller.verify(attestationResult()).successValue()

        assertThat(verifyResult.proofToken.token).isEqualTo("proof-token")
        assertThat(verifyResult.ownIdData).isEqualTo("""{"session":"created"}""")
        val verifyRequest = network.requests.last()
        assertJsonPostRequest(
            request = verifyRequest,
            url = "https://auth.example.test/api/passkeys/attestation/result",
            authorization = "Bearer context-access-token",
            traceParent = traceParent
        )
        assertThat(verifyRequest.jsonBody().jsonString("id")).isEqualTo("attestation-credential-id")

        controller.cancel(Reason.SystemError("credential manager failed")).assertUnitSuccess()

        val cancelRequest = network.requests.last()
        assertJsonPostRequest(
            request = cancelRequest,
            url = "https://auth.example.test/api/passkeys/attestation/cancel",
            authorization = null,
            traceParent = traceParent
        )
        cancelRequest.jsonBody().let { cancelBody ->
            assertThat(cancelBody.jsonString("challenge")).isEqualTo("YXR0ZXN0YXRpb24tY2hhbGxlbmdl")
            assertThat(cancelBody.jsonString("reason")).isEqualTo("systemError")
        }
    }

    @Test
    fun attestationCallerCancellationDuringStartDoesNotSendServerSideCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val optionsRequest = CompletableDeferred<String>()
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/attestation/options" -> {
                    optionsRequest.complete(request.url.encodedPath)
                    awaitCancellation()
                }

                "/api/passkeys/attestation/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = attestationApi(network, dispatcher)

        val job = launch {
            api.start(
                PasskeyAttestationApi.Params(
                    loginId = LoginId("param@example.com", LoginId.Type.Email),
                    accountDisplayName = "Param Display Name",
                    accessToken = AccessToken("param-access-token"),
                    traceParent = traceParent
                )
            )
        }

        assertThat(optionsRequest.await()).isEqualTo("/api/passkeys/attestation/options")

        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requestPaths()).doesNotContain("/api/passkeys/attestation/cancel")
    }

    @Test
    fun attestationCallerCancellationDuringControllerVerifyDoesNotSendServerSideCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resultRequest = CompletableDeferred<String>()
        val network = RecordingNetwork { request ->
            when (request.url.encodedPath) {
                "/api/passkeys/attestation/options" -> apiSuccess(request.url, 200, attestationOptionsBody(challenge = "dmVyaWZ5LWNhbmNlbGxhdGlvbg"))
                "/api/passkeys/attestation/result" -> {
                    resultRequest.complete(request.url.encodedPath)
                    awaitCancellation()
                }

                "/api/passkeys/attestation/cancel" -> apiSuccess(request.url, 204, "")
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val api = attestationApi(network, dispatcher)
        val controller = api.start(
            PasskeyAttestationApi.Params(
                loginId = LoginId("param@example.com", LoginId.Type.Email),
                accountDisplayName = "Param Display Name",
                accessToken = AccessToken("param-access-token"),
                traceParent = traceParent
            )
        ).successValue()

        val job = launch { controller.verify(attestationResult()) }

        assertThat(resultRequest.await()).isEqualTo("/api/passkeys/attestation/result")

        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(network.requestPaths()).containsExactly(
            "/api/passkeys/attestation/options",
            "/api/passkeys/attestation/result"
        ).inOrder()
        assertThat(network.requestPaths()).doesNotContain("/api/passkeys/attestation/cancel")
    }

    private fun assertionApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null
    ): PasskeyAssertionApiImpl =
        PasskeyAssertionApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = passkeyTestSerialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            loginIdValidator = null,
            interceptor = null
        )

    private fun attestationApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null
    ): PasskeyAttestationApiImpl =
        PasskeyAttestationApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = passkeyTestSerialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            loginIdValidator = null,
            interceptor = null
        )

    private fun directApiContainer(network: Network, dispatcher: CoroutineDispatcher): DIContainer =
        DIContainerImpl(scopeName = "passkey-direct-api").apply {
            register<ApiBaseUrl>(FixedApiBaseUrl(baseUrl))
            register<Network>(network)
            register<Serialization>(passkeyTestSerialization)
            register<DispatcherProvider>(TestDispatcherProvider(dispatcher))
            registerFactory<PasskeyAssertionApi>(
                ApiBaseUrl::class.java,
                Network::class.java,
                Serialization::class.java,
                DispatcherProvider::class.java
            ) {
                PasskeyAssertionApiImpl.create(this)
            }
            registerFactory<PasskeyAttestationApi>(
                ApiBaseUrl::class.java,
                Network::class.java,
                Serialization::class.java,
                DispatcherProvider::class.java
            ) {
                PasskeyAttestationApiImpl.create(this)
            }
        }

    private fun context(block: Context.Builder.() -> Unit): Context =
        Context.Builder().apply(block).build(scopeName = "test")

    private fun assertionOptionsBody(challenge: String): String =
        """
            {
              "challenge": "$challenge",
              "rpId": "login.example.test",
              "allowCredentials": [
                {
                  "type": "public-key",
                  "id": "Y3JlZC0x",
                  "transports": ["internal"]
                }
              ],
              "userVerification": "required",
              "timeout": 45000
            }
        """.trimIndent()

    private fun attestationOptionsBody(challenge: String): String =
        """
            {
              "rp": { "id": "login.example.test", "name": "Example" },
              "user": { "id": "dXNlci0x", "name": "user@example.com", "displayName": "User Example" },
              "challenge": "$challenge",
              "pubKeyCredParams": [
                { "type": "public-key", "alg": -7 }
              ],
              "attestation": "direct",
              "timeout": 45000
            }
        """.trimIndent()

    private fun assertionResult(): AssertionResult =
        AssertionResult(
            id = "assertion-credential-id",
            type = CredentialType.PUBLIC_KEY,
            response = AssertionResult.AuthenticatorResponse(
                clientDataJSON = "client-data-json",
                authenticatorData = "authenticator-data",
                signature = "signature",
                userHandle = "user-handle"
            ),
            authenticatorAttachment = AuthenticatorAttachment.PLATFORM
        )

    private fun attestationResult(): AttestationResult =
        AttestationResult(
            id = "attestation-credential-id",
            type = CredentialType.PUBLIC_KEY,
            response = AttestationResult.AuthenticatorResponse(
                clientDataJSON = "client-data-json",
                attestationObject = "attestation-object",
                transports = listOf(TransportType.INTERNAL)
            ),
            authenticatorAttachment = AuthenticatorAttachment.PLATFORM
        )
}
