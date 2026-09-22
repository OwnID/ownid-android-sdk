package com.ownid.sdk.provider

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.flow.AcceptingLoginIdValidator
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.JsonSerialization
import com.ownid.sdk.flow.RecordingOperationEntry
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.flow.boost.impl.BoostCreatePasskeyFlowImpl
import com.ownid.sdk.flow.boost.impl.BoostLoginFlowImpl
import com.ownid.sdk.flow.recordingEntry
import com.ownid.sdk.flow.unavailableEntry
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.SignInWithGoogleOperationFailure
import com.ownid.sdk.ops.ui.impl.SignInWithGoogleUiImpl
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeElitePluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeSocialPluginImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.ContinuationInterceptor
import com.ownid.sdk.model.Context as OwnIdContext

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: PROVIDER-CALLBACK-010
internal class OwnIdProviderRuntimeBoundaryContractTest {

    @Test
    fun eliteFlowSessionAndPasswordProviderCallbacksRunOnMainDispatcherBoundary() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        Dispatchers.setMain(mainDispatcher)

        val sessionCreate = RecordingSessionCreate()
        val passwordAuthenticate = RecordingPasswordAuthenticate()
        val plugin = WebBridgeElitePluginImpl(
            serialization = TestSerialization,
            loginIdValidator = TestLoginIdValidator,
            sessionCreate = sessionCreate,
            passwordAuthenticate = passwordAuthenticate,
            logger = null
        )

        try {
            val sessionResult = callerScope.async { plugin.handle(flowMessage("session_create", SESSION_CREATE_PAYLOAD)) }

            callerScheduler.runCurrent()
            assertThat(sessionCreate.events).isEmpty()
            assertThat(sessionResult.isCompleted).isFalse()

            mainScheduler.runCurrent()
            assertThat(sessionCreate.events).containsExactly("available", "create").inOrder()
            assertThat(sessionCreate.params.single().loginId).isEqualTo(LoginId("person@example.com", LoginId.Type.Email))
            assertThat(sessionCreate.params.single().accessToken).isEqualTo(AccessToken("access-token"))
            assertThat(sessionCreate.params.single().authMethod).isSameInstanceAs(AuthMethod.Passkey)
            assertThat(sessionCreate.params.single().sessionPayload).isEqualTo("""{"host":"session"}""")

            callerScheduler.runCurrent()
            assertThat(sessionResult.await().success?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull).isEqualTo("logged-in")

            val passwordResult = callerScope.async { plugin.handle(flowMessage("auth_password_authenticate", PASSWORD_AUTHENTICATE_PAYLOAD)) }

            callerScheduler.runCurrent()
            assertThat(passwordAuthenticate.events).isEmpty()
            assertThat(passwordResult.isCompleted).isFalse()

            mainScheduler.runCurrent()
            assertThat(passwordAuthenticate.events).containsExactly("available", "authenticate").inOrder()
            assertThat(passwordAuthenticate.params.single().loginId).isEqualTo(LoginId("person@example.com", LoginId.Type.Email))
            assertThat(passwordAuthenticate.params.single().password).isEqualTo("correct horse battery staple")

            callerScheduler.runCurrent()
            assertThat(passwordResult.await().success?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull).isEqualTo("logged-in")
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun webBridgeSocialAvailabilityPrecedesMainActivityHandoff() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        Dispatchers.setMain(mainDispatcher)

        val provider = RecordingSignInWithGoogle()
        val plugin = WebBridgeSocialPluginImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(TestActivity) } },
            signInWithGoogle = provider,
            serialization = TestSerialization
        )

        try {
            val result = callerScope.async { plugin.handle(socialMessage) }

            callerScheduler.runCurrent()

            assertThat(provider.events).containsExactly("available")
            mainScheduler.runCurrent()
            callerScheduler.runCurrent()

            assertThat(provider.events).containsExactly("available", "signIn").inOrder()
            assertThat(provider.signInActivities.single()).isSameInstanceAs(TestActivity)
            assertThat(provider.signInParams.single().webClientId).isEqualTo("web-client-id")
            assertThat(provider.signInParams.single().nonce).isEqualTo("challenge-id")
            assertThat(result.await().success?.jsonPrimitive?.contentOrNull).isEqualTo("google-id-token")
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun boostLoginSessionCreateCallbacksRunOnMainDispatcherBoundary() = runTest {
        val loginId = LoginId("boost-login@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("boost-login-access-token")
        val sessionPayload = "boost-login-session-payload"

        withMainDispatcherBoundaryHarness {
            val sessionCreate = RecordingSessionCreate()
            val operations = loginSuccessOperations(loginId, accessToken, sessionPayload)
            val flow = BoostLoginFlowImpl(
                userRepository = null,
                ownIdOperation = operations,
                userJourney = null,
                sessionCreate = sessionCreate,
                loginIdValidator = AcceptingLoginIdValidator,
                serialization = JsonSerialization,
                defaultDispatcher = callerDispatcher,
                instanceJob = instanceJob,
                context = null,
                logger = null
            )
            val result = callerScope.async {
                flow.start(BoostFlowContext { this.loginId = loginId }).whenSettled()
            }

            val response = awaitBoostSessionCreateSuccess(sessionCreate, result, loginId, accessToken, sessionPayload) as BoostFlow.Response.Login
            assertThat(response.session).isEqualTo("host-session")
        }
    }

    @Test
    fun boostCreatePasskeySessionCreateCallbacksRunOnMainDispatcherBoundary() = runTest {
        val loginId = LoginId("boost-create@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("boost-create-access-token")
        val sessionPayload = "boost-create-session-payload"

        withMainDispatcherBoundaryHarness {
            val sessionCreate = RecordingSessionCreate()
            val operations = loginSuccessOperations(loginId, accessToken, sessionPayload)
            val flow = BoostCreatePasskeyFlowImpl(
                userRepository = null,
                ownIdOperation = operations,
                boostFlowLogin = FailingBoostLoginFlow,
                loginIdValidator = AcceptingLoginIdValidator,
                serialization = JsonSerialization,
                userJourney = null,
                sessionCreate = sessionCreate,
                defaultDispatcher = callerDispatcher,
                instanceJob = instanceJob,
                context = null,
                logger = null
            )
            val result = callerScope.async {
                flow.start(BoostFlowContext { this.loginId = loginId }).whenSettled()
            }

            val response = awaitBoostSessionCreateSuccess(sessionCreate, result, loginId, accessToken, sessionPayload) as BoostFlow.Response.Login
            assertThat(response.session).isEqualTo("host-session")
        }
    }

    @Test
    fun directSignInWithGoogleUiProviderInvocationRunsOnMain() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        Dispatchers.setMain(mainDispatcher)

        val provider = RecordingSignInWithGoogle()
        var hostLookups = 0
        val ui = SignInWithGoogleUiImpl(
            uiContextProvider = { UiContextProvider { hostLookups += 1; UiContextProvider.Result.Success(TestActivity) } },
            signInWithGoogleProvider = { provider }
        )

        try {
            val result = callerScope.async {
                ui.signIn(webClientId = "web-client-id", nonce = "challenge-id")
            }

            callerScheduler.advanceUntilIdle()

            assertThat(hostLookups).isEqualTo(0)
            assertThat(provider.events).isEmpty()
            mainScheduler.runCurrent()

            assertThat(hostLookups).isEqualTo(1)
            assertThat(provider.events).containsExactly("signIn")
            assertThat(provider.signInInterceptors.single()).isSameInstanceAs(Dispatchers.Main)
            assertThat(provider.signInActivities.single()).isSameInstanceAs(TestActivity)
            assertThat(provider.signInParams.single().webClientId).isEqualTo("web-client-id")
            assertThat(provider.signInParams.single().nonce).isEqualTo("challenge-id")
            callerScheduler.runCurrent()
            val signInResult = result.await() as SignInWithSocial.Result.Success
            assertThat(signInResult.id).isEqualTo("google-user")
            assertThat(signInResult.idToken).isEqualTo("google-id-token")
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private val appContext: Context
        get() = RuntimeEnvironment.getApplication()

    private fun flowMessage(action: String, params: String): WebBridgePlugin.Message =
        message(pluginId = "FLOW", action = action, params = params)

    private val socialMessage: WebBridgePlugin.Message
        get() = message(
            pluginId = "SOCIAL",
            action = "Google",
            params = """{"clientId":"web-client-id","challengeId":"challenge-id"}"""
        )

    private fun message(pluginId: String, action: String, params: String): WebBridgePlugin.Message =
        WebBridgePlugin.Message(
            webView = WebView(appContext),
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

    private suspend fun <T> withMainDispatcherBoundaryHarness(block: suspend MainDispatcherBoundaryHarness.() -> T): T {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        val instanceJob = Job()
        Dispatchers.setMain(mainDispatcher)

        return try {
            MainDispatcherBoundaryHarness(
                mainScheduler = mainScheduler,
                callerScheduler = callerScheduler,
                callerDispatcher = callerDispatcher,
                callerScope = callerScope,
                instanceJob = instanceJob
            ).block()
        } finally {
            instanceJob.cancel()
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class MainDispatcherBoundaryHarness(
        private val mainScheduler: TestCoroutineScheduler,
        private val callerScheduler: TestCoroutineScheduler,
        val callerDispatcher: TestDispatcher,
        val callerScope: CoroutineScope,
        val instanceJob: Job
    ) {
        suspend fun awaitBoostSessionCreateSuccess(
            sessionCreate: RecordingSessionCreate,
            result: Deferred<FlowResult<*, *>>,
            loginId: LoginId,
            accessToken: AccessToken,
            sessionPayload: String
        ): Any? {
            callerScheduler.advanceUntilIdle()
            assertThat(sessionCreate.events).isEmpty()
            assertThat(result.isCompleted).isFalse()

            mainScheduler.advanceUntilIdle()
            assertThat(sessionCreate.events).containsExactly("available", "create").inOrder()
            val sessionParams = sessionCreate.params.single()
            assertThat(sessionParams.loginId).isEqualTo(loginId)
            assertThat(sessionParams.accessToken).isEqualTo(accessToken)
            assertThat(sessionParams.authMethod).isSameInstanceAs(AuthMethod.Immediate)
            assertThat(sessionParams.sessionPayload).isEqualTo(sessionPayload)

            callerScheduler.advanceUntilIdle()
            val flowResult = result.await()
            assertThat(flowResult).isInstanceOf(FlowResult.Success::class.java)
            return (flowResult as FlowResult.Success<*>).success
        }
    }

    private class RecordingSessionCreate : SessionCreate {
        val events = mutableListOf<String>()
        val params = mutableListOf<SessionCreate.Params>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            return true
        }

        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> {
            events += "create"
            this.params += params
            return Result.success(SessionOutput("host-session"))
        }
    }

    private class RecordingPasswordAuthenticate : PasswordAuthenticate {
        val events = mutableListOf<String>()
        val params = mutableListOf<PasswordAuthenticate.Params>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            return true
        }

        override suspend fun authenticate(params: PasswordAuthenticate.Params): Result<SessionOutput> {
            events += "authenticate"
            this.params += params
            return Result.success(SessionOutput("password-session"))
        }
    }

    private class RecordingSignInWithGoogle : SignInWithGoogle {
        val events = mutableListOf<String>()
        val signInActivities = mutableListOf<Activity>()
        val signInParams = mutableListOf<SignInWithSocial.Params>()
        val signInInterceptors = mutableListOf<ContinuationInterceptor?>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            events += "available"
            return true
        }

        override suspend fun signIn(activity: Activity, params: SignInWithSocial.Params): SignInWithSocial.Result {
            events += "signIn"
            signInActivities += activity
            signInParams += params
            signInInterceptors += currentCoroutineContext()[ContinuationInterceptor]
            return SignInWithSocial.Result.Success(id = "google-user", idToken = "google-id-token")
        }

        override suspend fun cancel() = Unit

        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable? = null
    }

    private class RecordingOwnIdOperation(
        private val loginIdCollectEntry: RecordingOperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>,
        override val login: OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure>
    ) : OwnIdOperation {
        override val loginId: OwnIdOperation.LoginId
            get() = object : OwnIdOperation.LoginId {
                override val collect: OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> = loginIdCollectEntry
            }

        override val verifications: OwnIdOperation.Verifications
            get() = object : OwnIdOperation.Verifications {
                override val email: OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure> =
                    unavailableEntry(OperationType.EMAIL_VERIFICATION)
                override val phone: OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure> =
                    unavailableEntry(OperationType.PHONE_NUMBER_VERIFICATION)
            }

        override val passkeys: OwnIdOperation.Passkeys
            get() = object : OwnIdOperation.Passkeys {
                override val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
                    unavailableEntry(OperationType.PASSKEY_CREATION)
                override val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> =
                    unavailableEntry(OperationType.PASSKEY_AUTH)
                override val enroll: OperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> =
                    unavailableEntry(OperationType.PASSKEY_ENROLLMENT)
            }

        override val socialLogin: OwnIdOperation.SocialLogin
            get() = object : OwnIdOperation.SocialLogin {
                override val signInWithGoogle: OperationEntry<SignInWithGoogleOperation.Params?, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
                    unavailableEntry(OperationType.OIDC_AUTHENTICATION_GOOGLE)
            }

        override fun withContext(scopeName: String, block: OwnIdContext.Builder.() -> Unit): OwnIdOperation = this
        override fun withProviders(scopeName: String, block: OwnIdProvidersRegistrar.() -> Unit): OwnIdOperation = this
    }

    private object FailingBoostLoginFlow : BoostFlow.Login {
        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> =
            object : BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
                override suspend fun whenSettled(): FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> =
                    error("Unexpected child login flow")

                override fun abort(reason: Reason) = Unit
            }
    }

    private fun loginSuccessOperations(loginId: LoginId, accessToken: AccessToken, sessionPayload: String): RecordingOwnIdOperation =
        RecordingOwnIdOperation(
            loginIdCollectEntry = recordingEntry(
                OperationType.LOGIN_ID_COLLECT,
                OperationResult.Success(loginId)
            ),
            login = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(LoginResponse.Success(accessToken, sessionPayload)),
                availability = Availability.Available()
            )
        )

    private object TestLoginIdValidator : LoginIdValidator {
        override fun determineLoginIdType(loginId: String): LoginId.Type = LoginId.Type.Email
        override fun validate(loginId: LoginId): LoginId = loginId
    }

    private object TestSerialization : Serialization {
        override val json: Json = Json
    }

    private object TestContext : ContextWrapper(null)

    private object TestActivity : Activity()

    private companion object {
        private const val SESSION_CREATE_PAYLOAD: String =
            """{"session":{"host":"session"},"metadata":{"loginId":"person@example.com","authToken":"access-token","authMethod":"passkey"}}"""

        private const val PASSWORD_AUTHENTICATE_PAYLOAD: String =
            """{"loginId":"person@example.com","password":"correct horse battery staple"}"""
    }
}
