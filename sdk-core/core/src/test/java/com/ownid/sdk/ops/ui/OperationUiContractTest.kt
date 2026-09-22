package com.ownid.sdk.ops.ui

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.injectInstanceDefaults
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.UiError
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.impl.assertionOptions
import com.ownid.sdk.ops.impl.assertionResult
import com.ownid.sdk.ops.impl.attestationOptions
import com.ownid.sdk.ops.impl.attestationResult
import com.ownid.sdk.ops.ui.impl.PasskeyAssertionUiImpl
import com.ownid.sdk.ops.ui.impl.PasskeyAttestationUiImpl
import com.ownid.sdk.ops.ui.impl.SignInWithGoogleUiImpl
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(InternalOwnIdAPI::class, ExperimentalCoroutinesApi::class)
// Covers: UI-010, UI-020, UI-120, UI-130, UI-280
internal class OperationUiContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginIdCollectUiStateValueEqualityExcludesCallbacks() {
        var firstCallbackCalls = 0
        var secondCallbackCalls = 0
        val base = LoginIdCollectUi.UiState(
            loginIdValue = "person@example.com",
            collectableLoginIdTypes = listOf(LoginId.Type.Email),
            error = UiError(ErrorCode.LoginIdValidationFailed, "Invalid"),
            onLoginIdChange = { firstCallbackCalls += 1 },
            onContinue = { firstCallbackCalls += 1 },
            onCancel = { firstCallbackCalls += 1 }
        )
        val sameValuesDifferentCallbacks = base.copy(
            onLoginIdChange = { secondCallbackCalls += 1 },
            onContinue = { secondCallbackCalls += 1 },
            onCancel = { secondCallbackCalls += 1 }
        )

        assertThat(sameValuesDifferentCallbacks).isEqualTo(base)
        assertThat(sameValuesDifferentCallbacks.hashCode()).isEqualTo(base.hashCode())

        sameValuesDifferentCallbacks.onLoginIdChange("updated@example.com")
        sameValuesDifferentCallbacks.onContinue()
        sameValuesDifferentCallbacks.onCancel()

        assertThat(firstCallbackCalls).isEqualTo(0)
        assertThat(secondCallbackCalls).isEqualTo(3)
        assertThat(base.copy(loginIdValue = "other@example.com")).isNotEqualTo(base)
    }

    @Test
    fun verificationUiStateValueEqualityExcludesCallbacks() {
        val challenge = verificationChallenge()
        val error = UiError(ErrorCode.VerificationCodeWrong, "Wrong code")
        val emailState = EmailVerificationUi.UiState(
            challenge = challenge,
            busy = true,
            error = error,
            onCodeEntered = {},
            onCancel = {},
            onNotYou = {},
            onResend = {}
        )
        val phoneState = PhoneVerificationUi.UiState(
            challenge = challenge,
            busy = true,
            error = error,
            onCodeEntered = {},
            onCancel = {},
            onNotYou = {},
            onResend = {}
        )

        assertThat(emailState.copy(onCodeEntered = { error("unused") }, onCancel = { error("unused") })).isEqualTo(emailState)
        assertThat(emailState.copy(onNotYou = { error("unused") }, onResend = { error("unused") }).hashCode()).isEqualTo(emailState.hashCode())
        assertThat(emailState.copy(busy = false)).isNotEqualTo(emailState)

        assertThat(phoneState.copy(onCodeEntered = { error("unused") }, onCancel = { error("unused") })).isEqualTo(phoneState)
        assertThat(phoneState.copy(onNotYou = { error("unused") }, onResend = { error("unused") }).hashCode()).isEqualTo(phoneState.hashCode())
        assertThat(phoneState.copy(error = null)).isNotEqualTo(phoneState)
    }

    @Test
    fun defaultCoreOperationUiStartupFailuresAreReportedThroughCallbacks() {
        val container = DIContainerImpl(scopeName = "ui-defaults")
        container.injectInstanceDefaults(InstanceName("ui-defaults"), OwnIdConfigurationImpl(appId = "app123"))

        val loginFailures = mutableListOf<StartupFailure>()
        container.getOrThrow<LoginIdCollectUi>().start(loginIdController()) { error, details ->
            loginFailures += StartupFailure(error, details)
        }

        val emailFailures = mutableListOf<StartupFailure>()
        container.getOrThrow<EmailVerificationUi>().start(emailVerificationController()) { error, details ->
            emailFailures += StartupFailure(error, details)
        }

        val phoneFailures = mutableListOf<StartupFailure>()
        container.getOrThrow<PhoneVerificationUi>().start(phoneVerificationController()) { error, details ->
            phoneFailures += StartupFailure(error, details)
        }

        assertStartupFailure(loginFailures.single(), "LoginIdCollectUi")
        assertStartupFailure(emailFailures.single(), "EmailVerificationUi")
        assertStartupFailure(phoneFailures.single(), "PhoneVerificationUi")
    }

    @Test
    fun passkeyUiAdaptersForwardActivityAndOptionsToProviderWithoutFrameworkCalls() = runTest {
        val activity = mockk<Activity>()
        val assertionOptions = assertionOptions()
        val assertionResult = assertionResult()
        val attestationOptions = attestationOptions()
        val attestationResult = attestationResult()
        val assertionSuccess = Passkey.Result.Success(assertionResult)
        val attestationSuccess = Passkey.Result.Success(attestationResult)
        val passkeyProvider = RecordingPasskeyProvider(assertionSuccess, attestationSuccess)

        val assertionUi = PasskeyAssertionUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
            passkeyProvider = { passkeyProvider }
        )
        val attestationUi = PasskeyAttestationUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
            passkeyProvider = { passkeyProvider }
        )

        assertThat(assertionUi.getCredential(assertionOptions)).isSameInstanceAs(assertionSuccess)
        assertThat(attestationUi.createCredential(attestationOptions, isAutoSelectAllowed = false))
            .isSameInstanceAs(attestationSuccess)
        assertThat(passkeyProvider.assertionRequests).containsExactly(PasskeyAssertionRequest(activity, assertionOptions))
        assertThat(passkeyProvider.attestationRequests).containsExactly(PasskeyAttestationRequest(activity, attestationOptions, false))
    }

    @Test
    fun signInWithGoogleUiForwardsActivityAndChallengeParamsToProviderWithoutFrameworkCalls() = runTest {
        val activity = mockk<Activity>()
        val expected = SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token")
        val provider = RecordingSignInWithGoogleProvider(expected)
        val ui = SignInWithGoogleUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
            signInWithGoogleProvider = { provider }
        )

        val result = ui.signIn(webClientId = "web-client-id", nonce = "nonce")

        assertThat(result).isSameInstanceAs(expected)
        assertThat(provider.signInRequests).hasSize(1)
        assertThat(provider.signInRequests.single().activity).isSameInstanceAs(activity)
        assertThat(provider.signInRequests.single().params.webClientId).isEqualTo("web-client-id")
        assertThat(provider.signInRequests.single().params.nonce).isEqualTo("nonce")
    }

    @Test
    fun operationUiAdaptersPreserveProviderFailuresAndCancellations() = runTest {
        val activity = mockk<Activity>()
        val passkeyError = IllegalStateException("passkey provider failed")
        val passkeyReason = Reason.UserClose("passkey dismissed")
        val assertionResults: List<Passkey.Result<AssertionResult>> = listOf(
            Passkey.Result.Fail(passkeyError),
            Passkey.Result.Canceled(passkeyReason)
        )
        val attestationResults: List<Passkey.Result<AttestationResult>> = listOf(
            Passkey.Result.Fail(passkeyError),
            Passkey.Result.Canceled(passkeyReason)
        )

        assertionResults.forEach { expected ->
            val provider = RecordingPasskeyProvider(expected, Passkey.Result.Success(attestationResult()))
            val ui = PasskeyAssertionUiImpl(
                uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
                passkeyProvider = { provider }
            )

            assertThat(ui.getCredential(assertionOptions())).isSameInstanceAs(expected)
        }
        attestationResults.forEach { expected ->
            val provider = RecordingPasskeyProvider(Passkey.Result.Success(assertionResult()), expected)
            val ui = PasskeyAttestationUiImpl(
                uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
                passkeyProvider = { provider }
            )

            assertThat(ui.createCredential(attestationOptions(), false)).isSameInstanceAs(expected)
        }

        val socialError = IllegalArgumentException("Google provider failed")
        val socialReason = Reason.SystemError("Google canceled")
        listOf<SignInWithSocial.Result>(
            SignInWithSocial.Result.Fail(socialError),
            SignInWithSocial.Result.Canceled(socialReason)
        ).forEach { expected ->
            val provider = RecordingSignInWithGoogleProvider(expected)
            val ui = SignInWithGoogleUiImpl(
                uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
                signInWithGoogleProvider = { provider }
            )

            assertThat(ui.signIn("client", "nonce")).isSameInstanceAs(expected)
        }
    }

    @Test
    fun resolutionFailuresKeepExactThrowableAndSkipCapabilities() = runTest {
        val error = IllegalStateException("no host")
        val cancellation = CancellationException("resolved cancellation")
        val passkey = RecordingPasskeyProvider(
            assertionResult = Passkey.Result.Success(assertionResult()),
            attestationResult = Passkey.Result.Success(attestationResult())
        )
        val assertion = PasskeyAssertionUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Failure(error) } },
            passkeyProvider = { passkey }
        )
        val attestation = PasskeyAttestationUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Failure(cancellation) } },
            passkeyProvider = { passkey }
        )
        val google = RecordingSignInWithGoogleProvider(SignInWithSocial.Result.Success("unused", "unused"))
        val googleUi = SignInWithGoogleUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Failure(error) } },
            signInWithGoogleProvider = { google }
        )

        assertThat((assertion.getCredential(assertionOptions()) as Passkey.Result.Fail).error).isSameInstanceAs(error)
        assertThat((attestation.createCredential(attestationOptions(), false) as Passkey.Result.Fail).error)
            .isSameInstanceAs(cancellation)
        assertThat((googleUi.signIn("client", null) as SignInWithSocial.Result.Fail).error).isSameInstanceAs(error)
        assertThat(passkey.assertionRequests).isEmpty()
        assertThat(passkey.attestationRequests).isEmpty()
        assertThat(google.signInRequests).isEmpty()
    }

    @Test
    fun thrownCancellationFromResolutionPropagates() = runTest {
        val cancellation = CancellationException("lookup canceled")
        val passkey = RecordingPasskeyProvider(
            assertionResult = Passkey.Result.Success(assertionResult()),
            attestationResult = Passkey.Result.Success(attestationResult())
        )
        val assertion = PasskeyAssertionUiImpl(
            uiContextProvider = { UiContextProvider { throw cancellation } },
            passkeyProvider = { passkey }
        )

        val thrown = try {
            assertion.getCredential(assertionOptions())
            error("Expected cancellation")
        } catch (cause: CancellationException) {
            cause
        }
        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(thrown).hasMessageThat().isEqualTo(cancellation.message)
        assertThat(passkey.assertionRequests).isEmpty()
    }

    @Test
    fun hostResolutionAndPasskeyInvocationRunOnMainBeforeCallerResumes() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        Dispatchers.setMain(mainDispatcher)
        val activity = mockk<Activity>()
        var lookups = 0
        val passkey = RecordingPasskeyProvider(
            assertionResult = Passkey.Result.Success(assertionResult()),
            attestationResult = Passkey.Result.Success(attestationResult())
        )
        val assertion = PasskeyAssertionUiImpl(
            uiContextProvider = { UiContextProvider { lookups += 1; UiContextProvider.Result.Success(activity) } },
            passkeyProvider = { passkey }
        )
        val attestation = PasskeyAttestationUiImpl(
            uiContextProvider = { UiContextProvider { lookups += 1; UiContextProvider.Result.Success(activity) } },
            passkeyProvider = { passkey }
        )
        try {
            val result = callerScope.async { assertion.getCredential(assertionOptions()) }
            callerScheduler.runCurrent()
            assertThat(lookups).isEqualTo(0)
            assertThat(passkey.assertionRequests).isEmpty()

            mainScheduler.runCurrent()
            assertThat(lookups).isEqualTo(1)
            assertThat(passkey.assertionRequests.single().activity).isSameInstanceAs(activity)
            assertThat(passkey.assertionInterceptors.single()).isSameInstanceAs(Dispatchers.Main)
            assertThat(result.isCompleted).isFalse()
            callerScheduler.runCurrent()
            assertThat(result.isCompleted).isTrue()

            val createResult = callerScope.async { attestation.createCredential(attestationOptions(), false) }
            callerScheduler.runCurrent()
            assertThat(lookups).isEqualTo(1)
            mainScheduler.runCurrent()
            assertThat(lookups).isEqualTo(2)
            assertThat(passkey.attestationRequests.single().activity).isSameInstanceAs(activity)
            assertThat(passkey.attestationInterceptors.single()).isSameInstanceAs(Dispatchers.Main)
            assertThat(createResult.isCompleted).isFalse()
            callerScheduler.runCurrent()
            assertThat(createResult.isCompleted).isTrue()
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun googleCancelIsAdmittedToTheActiveProviderOnMain() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val callerScheduler = TestCoroutineScheduler()
        val callerDispatcher = StandardTestDispatcher(callerScheduler)
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        Dispatchers.setMain(mainDispatcher)
        val activity = mockk<Activity>()
        val signInStarted = CompletableDeferred<Unit>()
        val signInResult = CompletableDeferred<SignInWithSocial.Result>()
        val provider = RecordingSignInWithGoogleProvider(SignInWithSocial.Result.Success("unused", "unused")) {
            signInStarted.complete(Unit)
            signInResult.await()
        }
        val ui = SignInWithGoogleUiImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
            signInWithGoogleProvider = { provider }
        )

        try {
            val signIn = callerScope.async { ui.signIn("client", "nonce") }
            callerScheduler.runCurrent()
            mainScheduler.runCurrent()
            signInStarted.await()

            val cancel = callerScope.async { ui.cancel() }
            callerScheduler.runCurrent()
            assertThat(provider.cancelCalls).isEqualTo(0)

            mainScheduler.runCurrent()
            assertThat(provider.cancelCalls).isEqualTo(1)
            assertThat(provider.cancelInterceptors.single()).isSameInstanceAs(Dispatchers.Main)
            assertThat(cancel.isCompleted).isFalse()

            callerScheduler.runCurrent()
            assertThat(cancel.isCompleted).isTrue()

            val expected = SignInWithSocial.Result.Canceled(Reason.UserClose("provider canceled"))
            signInResult.complete(expected)
            mainScheduler.runCurrent()
            callerScheduler.runCurrent()
            assertThat(signIn.await()).isSameInstanceAs(expected)
        } finally {
            callerScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun verificationChallenge(): VerificationChallenge =
        VerificationChallenge(
            challengeId = ChallengeId("challenge-id"),
            resendPolicy = VerificationChallenge.ResendPolicy(allow = true, attempts = 3, debounce = 1),
            timeout = Timeout(60_000),
            attempts = 3,
            methods = VerificationChallenge.Methods(otp = VerificationChallenge.Methods.Otp(length = 6), magicLink = null),
            channel = OperationChannel(channel = "user@example.test", id = "channel-id")
        )

    private fun loginIdController(): OperationController<LoginId, LoginIdCollectOperationFailure> =
        FakeOperationController(OperationID(OperationType.LOGIN_ID_COLLECT, "login-id"))

    private fun emailVerificationController(): OperationController<AccessOrProofToken, EmailVerificationOperationFailure> =
        FakeOperationController(OperationID(OperationType.EMAIL_VERIFICATION, "verification"))

    private fun phoneVerificationController(): OperationController<AccessOrProofToken, PhoneVerificationOperationFailure> =
        FakeOperationController(OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "verification"))

    private fun assertStartupFailure(failure: StartupFailure, capabilityName: String) {
        assertThat(failure.error).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure.details).contains(capabilityName)
        assertThat(failure.details).contains("No UI implementation registered")
    }

    private data class StartupFailure(val error: Throwable?, val details: String?)

    private data class PasskeyAssertionRequest(val activity: Activity, val assertionOptions: AssertionOptions)

    private data class PasskeyAttestationRequest(
        val activity: Activity,
        val attestationOptions: AttestationOptions,
        val isAutoSelectAllowed: Boolean
    )

    private data class SocialSignInRequest(
        val activity: Activity,
        val params: SignInWithSocial.Params
    )

    private class RecordingPasskeyProvider(
        private val assertionResult: Passkey.Result<AssertionResult>,
        private val attestationResult: Passkey.Result<AttestationResult>
    ) : Passkey {
        val assertionRequests = mutableListOf<PasskeyAssertionRequest>()
        val assertionInterceptors = mutableListOf<ContinuationInterceptor?>()
        val attestationRequests = mutableListOf<PasskeyAttestationRequest>()
        val attestationInterceptors = mutableListOf<ContinuationInterceptor?>()

        override suspend fun getCredential(activity: Activity, assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> {
            assertionRequests += PasskeyAssertionRequest(activity, assertionOptions)
            assertionInterceptors += currentCoroutineContext()[ContinuationInterceptor]
            return assertionResult
        }

        override suspend fun createCredential(
            activity: Activity,
            attestationOptions: AttestationOptions,
            isAutoSelectAllowed: Boolean
        ): Passkey.Result<AttestationResult> {
            attestationRequests += PasskeyAttestationRequest(activity, attestationOptions, isAutoSelectAllowed)
            attestationInterceptors += currentCoroutineContext()[ContinuationInterceptor]
            return attestationResult
        }
    }

    private class RecordingSignInWithGoogleProvider(
        private val signInResult: SignInWithSocial.Result,
        private val signInHandler: suspend () -> SignInWithSocial.Result = { signInResult }
    ) : SignInWithGoogle {
        val signInRequests = mutableListOf<SocialSignInRequest>()
        val cancelInterceptors = mutableListOf<ContinuationInterceptor?>()
        var cancelCalls = 0
            private set

        override suspend fun signIn(activity: Activity, params: SignInWithSocial.Params): SignInWithSocial.Result {
            signInRequests += SocialSignInRequest(activity, params)
            return signInHandler()
        }

        override suspend fun cancel() {
            cancelCalls += 1
            cancelInterceptors += currentCoroutineContext()[ContinuationInterceptor]
        }

        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable? = null
    }

    private class FakeOperationController<Result, Failure : OperationFailure>(
        override val operationId: OperationID
    ) : OperationController<Result, Failure> {
        override suspend fun whenSettled(): OperationResult<Result, Failure> =
            OperationResult.Canceled(Reason.Unknown("unused"))

        override fun abort(reason: Reason): Unit = Unit
    }

}
