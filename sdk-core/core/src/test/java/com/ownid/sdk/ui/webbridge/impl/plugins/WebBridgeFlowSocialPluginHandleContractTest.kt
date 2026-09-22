package com.ownid.sdk.ui.webbridge.impl.plugins

import android.app.Activity
import android.net.Uri
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.provider.PasswordAuthenticate
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.SessionOutput
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.ContinuationInterceptor

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: WB-BRIDGE-240, WB-BRIDGE-250, WB-BRIDGE-260, WB-BRIDGE-270
internal class WebBridgeFlowSocialPluginHandleContractTest {

    private val serialization = SerializationImpl()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    @Test
    fun providerBackedFlowWrappersCaptureParamsAndReturnLoggedInStatus(): Unit = runTest {
        DispatchersForTest.installMain(testScheduler)
        val sessionCreate = RecordingSessionCreate()
        val passwordAuthenticate = RecordingPasswordAuthenticate()
        val plugin = flowPlugin(sessionCreate = sessionCreate, passwordAuthenticate = passwordAuthenticate)

        try {
            val sessionResult = async { plugin.handle(flowMessage("session_create", SESSION_CREATE_PAYLOAD)) }
            val passwordResult = async { plugin.handle(flowMessage("auth_password_authenticate", PASSWORD_AUTHENTICATE_PAYLOAD)) }
            runCurrent()

            assertThat(sessionResult.await().success!!.jsonObject["status"]!!.jsonPrimitive.content).isEqualTo("logged-in")
            val sessionParams = sessionCreate.createParams.single()
            assertThat(sessionCreate.events).containsExactly("available", "create").inOrder()
            assertThat(sessionParams.loginId).isEqualTo(LoginId("person@example.test", LoginId.Type.Email))
            assertThat(sessionParams.accessToken).isEqualTo(AccessToken("access-token"))
            assertThat(sessionParams.authMethod).isSameInstanceAs(AuthMethod.Passkey)
            assertThat(sessionParams.sessionPayload).isEqualTo("""{"host":"session"}""")

            assertThat(passwordResult.await().success!!.jsonObject["status"]!!.jsonPrimitive.content).isEqualTo("logged-in")
            val passwordParams = passwordAuthenticate.authenticateParams.single()
            assertThat(passwordAuthenticate.events).containsExactly("available", "authenticate").inOrder()
            assertThat(passwordParams.loginId).isEqualTo(LoginId("person@example.test", LoginId.Type.Email))
            assertThat(passwordParams.password).isEqualTo("correct horse battery staple")
        } finally {
            DispatchersForTest.resetMain()
        }
    }

    @Test
    fun providerFailuresAndUnavailableProvidersReturnSourceDefinedFailurePayloads(): Unit = runTest {
        DispatchersForTest.installMain(testScheduler)
        val failedSession = RecordingSessionCreate(createResult = Result.failure(IllegalStateException("session rejected")))
        val unavailablePassword = RecordingPasswordAuthenticate(available = false)
        val plugin = flowPlugin(sessionCreate = failedSession, passwordAuthenticate = unavailablePassword)
        val noProviderPlugin = flowPlugin(sessionCreate = null, passwordAuthenticate = null)

        try {
            val sessionResult = async { plugin.handle(flowMessage("session_create", SESSION_CREATE_PAYLOAD)) }
            val passwordResult = async { plugin.handle(flowMessage("auth_password_authenticate", PASSWORD_AUTHENTICATE_PAYLOAD)) }
            runCurrent()

            val sessionPayload = sessionResult.await().success!!.jsonObject
            assertThat(sessionPayload["status"]!!.jsonPrimitive.content).isEqualTo("fail")
            assertThat(sessionPayload["reason"]!!.jsonPrimitive.content).isEqualTo("session rejected")
            assertThat(failedSession.events).containsExactly("available", "create").inOrder()

            val passwordPayload = passwordResult.await().success!!.jsonObject
            assertThat(passwordPayload["status"]!!.jsonPrimitive.content).isEqualTo("fail")
            assertThat(passwordPayload["reason"]!!.jsonPrimitive.content).isEqualTo("PasswordAuthenticate is unavailable")
            assertThat(unavailablePassword.events).containsExactly("available").inOrder()

            val missingProvider = noProviderPlugin.handle(flowMessage("session_create", SESSION_CREATE_PAYLOAD))
            assertThat(missingProvider.error!!["message"]!!.jsonPrimitive.content).isEqualTo("Unknown action: session_create")
        } finally {
            DispatchersForTest.resetMain()
        }
    }

    @Test
    fun malformedFlowParamsReturnBridgeErrorBeforeProviderInvocation(): Unit = runTest {
        DispatchersForTest.installMain(testScheduler)
        val sessionCreate = RecordingSessionCreate()
        val passwordAuthenticate = RecordingPasswordAuthenticate()
        val plugin = flowPlugin(sessionCreate = sessionCreate, passwordAuthenticate = passwordAuthenticate)

        try {
            val missingSessionParams = async { plugin.handle(flowMessage("session_create", null)) }
            val missingPassword = async { plugin.handle(flowMessage("auth_password_authenticate", """{"loginId":"person@example.test"}""")) }
            runCurrent()

            assertThat(missingSessionParams.await().error!!["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("Unexpected: params=null")
            assertThat(missingPassword.await().error!!["message"]!!.jsonPrimitive.contentOrNull).contains("Field 'password' is required")
            assertThat(sessionCreate.events).isEmpty()
            assertThat(passwordAuthenticate.events).isEmpty()
        } finally {
            DispatchersForTest.resetMain()
        }
    }

    @Test
    fun operationWrapperSuccessInvokesSideEffectAfterWrappedFunction(): Unit = runTest {
        val events = mutableListOf<String>()
        val wrapper = RecordingEventWrapper(action = "onFinish", result = JsonPrimitive(true), events = events)
        val sideEffects = mutableListOf<WebBridgeOperationEventWrapper>()
        val plugin = flowPlugin(sessionCreate = null, passwordAuthenticate = null).apply {
            addEventWrappers(listOf(wrapper))
            setWrapperSideEffect {
                events += "sideEffect"
                sideEffects += it
            }
        }

        val result = plugin.handle(flowMessage("onFinish", """{"loginId":"person@example.test"}"""))

        assertThat(result.success).isEqualTo(JsonPrimitive(true))
        assertThat(result.error).isNull()
        assertThat(wrapper.params).containsExactly("""{"loginId":"person@example.test"}""")
        assertThat(events).containsExactly("run", "sideEffect").inOrder()
        assertThat(sideEffects).containsExactly(wrapper)
    }

    @Test
    fun operationWrapperFailureReturnsErrorWithoutInvokingSideEffect(): Unit = runTest {
        val events = mutableListOf<String>()
        val wrapper = RecordingEventWrapper(action = "onClose", failure = IllegalStateException("callback failed"), events = events)
        val sideEffects = mutableListOf<WebBridgeOperationEventWrapper>()
        val plugin = flowPlugin(sessionCreate = null, passwordAuthenticate = null).apply {
            addEventWrappers(listOf(wrapper))
            setWrapperSideEffect {
                events += "sideEffect"
                sideEffects += it
            }
        }

        val result = plugin.handle(flowMessage("onClose", null))

        assertThat(result.success).isNull()
        assertThat(result.error!!["message"]!!.jsonPrimitive.content).isEqualTo("callback failed")
        assertThat(events).containsExactly("run")
        assertThat(sideEffects).isEmpty()
    }

    @Test
    fun socialPluginMapsSuccessCanceledFailedAndMissingProviderResults(): Unit = runTest {
        val successProvider = RecordingSignInWithGoogle(result = SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token"))
        val success = socialPlugin(successProvider).handle(socialMessage())
        assertThat(success.success!!.jsonPrimitive.content).isEqualTo("id-token")
        assertThat(successProvider.events).containsExactly("available", "signIn").inOrder()
        assertThat(successProvider.signInActivities.single()).isSameInstanceAs(activity)
        val availabilityParams = successProvider.availabilityParams.single() as SignInWithSocial.Params
        assertThat(availabilityParams.webClientId).isEqualTo("web-client-id")
        assertThat(availabilityParams.nonce).isEqualTo("challenge-id")
        assertThat(successProvider.signInParams.single().webClientId).isEqualTo("web-client-id")
        assertThat(successProvider.signInParams.single().nonce).isEqualTo("challenge-id")

        val canceled = socialPlugin(RecordingSignInWithGoogle(result = SignInWithSocial.Result.Canceled(Reason.UserClose("dismissed"))))
            .handle(socialMessage())
        val canceledError = requireNotNull(canceled.error)
        assertThat(canceledError["type"]!!.jsonPrimitive.content).isEqualTo("ABORTED")
        assertThat(canceledError["message"]!!.jsonPrimitive.content).isEqualTo("userClose: dismissed")

        val failed = socialPlugin(RecordingSignInWithGoogle(result = SignInWithSocial.Result.Fail(IllegalStateException("provider failed"))))
            .handle(socialMessage())
        assertThat(failed.error!!["message"]!!.jsonPrimitive.content).isEqualTo("provider failed")

        val missingProvider = socialPlugin(signInWithGoogle = null).handle(socialMessage())
        assertThat(missingProvider.error!!["message"]!!.jsonPrimitive.content).isEqualTo("WebBridgeSocialPlugin: Google Sign In is not supported")
    }

    @Test
    fun socialAvailabilityRunsBeforeUiLookupAndUnavailableSkipsPresentation(): Unit = runTest {
        val provider = RecordingSignInWithGoogle(available = false)
        var hostLookups = 0

        val result = socialPlugin(provider, onResolution = { hostLookups += 1 }).handle(socialMessage())

        assertThat(result.error!!["message"]!!.jsonPrimitive.content)
            .isEqualTo("WebBridgeSocialPlugin: Google Sign In is unavailable")
        assertThat(provider.events).containsExactly("available")
        assertThat(hostLookups).isEqualTo(0)
        assertThat(provider.signInActivities).isEmpty()
        assertThat(provider.signInParams).isEmpty()
    }

    @Test
    fun socialSuccessResolvesOneCurrentActivityOnMainBeforeSignIn(): Unit = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val callerScope = CoroutineScope(StandardTestDispatcher(callerScheduler))
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        val provider = RecordingSignInWithGoogle()
        var hostLookups = 0

        try {
            val result = callerScope.async {
                socialPlugin(provider, onResolution = { hostLookups += 1 }).handle(socialMessage())
            }

            callerScheduler.advanceUntilIdle()
            assertThat(provider.events).containsExactly("available")
            assertThat(hostLookups).isEqualTo(0)

            mainScheduler.runCurrent()
            assertThat(hostLookups).isEqualTo(1)
            assertThat(provider.events).containsExactly("available", "signIn").inOrder()
            assertThat(provider.signInActivities).containsExactly(activity)
            assertThat(provider.signInInterceptors.single()).isSameInstanceAs(Dispatchers.Main)

            callerScheduler.runCurrent()
            assertThat(result.await().success!!.jsonPrimitive.content).isEqualTo("id-token")
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun socialPluginValidatesParamsBeforeProviderSignIn(): Unit = runTest {
        val provider = RecordingSignInWithGoogle(result = SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token"))
        val missingParams = socialPlugin(provider).handle(socialMessage(params = null))
        val missingClientId = socialPlugin(provider).handle(socialMessage(params = """{"challengeId":"challenge-id"}"""))

        assertThat(missingParams.error!!["message"]!!.jsonPrimitive.content).isEqualTo("WebBridgeSocialPlugin: No params set for 'Google'")
        assertThat(missingClientId.error!!["message"]!!.jsonPrimitive.content).isEqualTo("WebBridgeSocialPlugin: 'clientId' is required")
        assertThat(provider.events).isEmpty()
    }

    @Test
    fun socialHostFailuresAfterAvailabilityStayGenericAndSkipSignIn(): Unit = runTest {
        for (error in listOf(
            IllegalStateException("no host"), CancellationException("host canceled"),
            Passkey.NoCredentialError(), AssertionError("host failed")
        )) {
            val provider = RecordingSignInWithGoogle()
            var hostLookups = 0
            val result = socialPlugin(
                provider,
                UiContextProvider.Result.Failure(error),
                onResolution = { hostLookups += 1 }
            ).handle(socialMessage())

            assertThat(result.success).isNull()
            assertThat(result.error!!["message"]?.jsonPrimitive?.content).isEqualTo(error.message)
            assertThat(result.toResultString()).contains("\"type\":\"UNKNOWN\"")
            assertThat(provider.events).containsExactly("available")
            assertThat(hostLookups).isEqualTo(1)
            assertThat(provider.signInActivities).isEmpty()
            assertThat(provider.signInParams).isEmpty()
        }
    }

    private fun flowPlugin(
        sessionCreate: SessionCreate?,
        passwordAuthenticate: PasswordAuthenticate?
    ): WebBridgeElitePluginImpl =
        WebBridgeElitePluginImpl(
            serialization = serialization,
            loginIdValidator = RecordingLoginIdValidator(),
            sessionCreate = sessionCreate,
            passwordAuthenticate = passwordAuthenticate,
            logger = null
        )

    private fun socialPlugin(
        signInWithGoogle: SignInWithGoogle?,
        resolution: UiContextProvider.Result = UiContextProvider.Result.Success(activity),
        onResolution: () -> Unit = {}
    ): WebBridgeSocialPluginImpl =
        WebBridgeSocialPluginImpl(
            uiContextProvider = { UiContextProvider { onResolution(); resolution } },
            signInWithGoogle = signInWithGoogle,
            serialization = serialization
        )

    private fun flowMessage(action: String, params: String?): WebBridgePlugin.Message =
        message(pluginId = "FLOW", action = action, params = params)

    private fun socialMessage(
        params: String? = """{"clientId":"web-client-id","challengeId":"challenge-id"}"""
    ): WebBridgePlugin.Message =
        message(pluginId = "SOCIAL", action = "Google", params = params)

    private fun message(pluginId: String, action: String, params: String?): WebBridgePlugin.Message =
        WebBridgePlugin.Message(
            webView = WebView(RuntimeEnvironment.getApplication()),
            sourceOrigin = Uri.parse("https://example.test"),
            isMainFrame = true,
            allowedOriginRules = setOf("https://example.test"),
            payload = WebBridgePlugin.Message.Payload(
                pluginId = pluginId,
                action = action,
                callbackPath = "ownid.callback",
                params = params
            )
        )

    private class RecordingSessionCreate(
        private val available: Boolean = true,
        private val createResult: Result<SessionOutput> = Result.success(SessionOutput("session"))
    ) : SessionCreate {
        val events = mutableListOf<String>()
        val createParams = mutableListOf<SessionCreate.Params>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            return available
        }

        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> {
            events += "create"
            createParams += params
            return createResult
        }
    }

    private class RecordingPasswordAuthenticate(
        private val available: Boolean = true,
        private val authenticateResult: Result<SessionOutput> = Result.success(SessionOutput("session"))
    ) : PasswordAuthenticate {
        val events = mutableListOf<String>()
        val authenticateParams = mutableListOf<PasswordAuthenticate.Params>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            return available
        }

        override suspend fun authenticate(params: PasswordAuthenticate.Params): Result<SessionOutput> {
            events += "authenticate"
            authenticateParams += params
            return authenticateResult
        }
    }

    private class RecordingSignInWithGoogle(
        private val available: Boolean = true,
        private val result: SignInWithSocial.Result = SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token")
    ) : SignInWithGoogle {
        val events = mutableListOf<String>()
        val availabilityParams = mutableListOf<Capability.Params?>()
        val signInActivities = mutableListOf<Activity>()
        val signInParams = mutableListOf<SignInWithSocial.Params>()
        val signInInterceptors = mutableListOf<ContinuationInterceptor?>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            availabilityParams += params
            return available
        }

        override suspend fun signIn(activity: Activity, params: SignInWithSocial.Params): SignInWithSocial.Result {
            events += "signIn"
            signInActivities += activity
            signInParams += params
            signInInterceptors += currentCoroutineContext()[ContinuationInterceptor]
            return result
        }

        override suspend fun cancel() = Unit

        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable? = null
    }

    private class RecordingEventWrapper(
        action: String,
        private val result: JsonElement = JsonObject(emptyMap()),
        private val failure: Throwable? = null,
        private val events: MutableList<String>
    ) : WebBridgeOperationEventWrapper {
        override val webBridgePluginAction: String = action
        override val isTerminal: Boolean = true
        val params = mutableListOf<String?>()

        override suspend fun runWrappedFunction(params: String?, serialization: com.ownid.sdk.capability.Serialization): JsonElement {
            events += "run"
            this.params += params
            failure?.let { throw it }
            return result
        }
    }

    private class RecordingLoginIdValidator : LoginIdValidator {
        override fun determineLoginIdType(loginId: String): LoginId.Type = LoginId.Type.Email
        override fun validate(loginId: LoginId): LoginId = loginId
    }

    private object DispatchersForTest {
        fun installMain(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) {
            kotlinx.coroutines.Dispatchers.setMain(StandardTestDispatcher(scheduler))
        }

        fun resetMain() {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    private companion object {
        private const val SESSION_CREATE_PAYLOAD: String =
            """{"session":{"host":"session"},"metadata":{"loginId":"person@example.test","authToken":"access-token","authMethod":"passkey"}}"""

        private const val PASSWORD_AUTHENTICATE_PAYLOAD: String =
            """{"loginId":"person@example.test","password":"correct horse battery staple"}"""
    }
}
