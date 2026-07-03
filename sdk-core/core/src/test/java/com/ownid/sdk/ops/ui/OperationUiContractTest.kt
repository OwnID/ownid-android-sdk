package com.ownid.sdk.ops.ui

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.injectInstanceDefaults
import com.ownid.sdk.di.register
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
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: UI-010, UI-020, UI-120, UI-130, UI-280
internal class OperationUiContractTest {

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
    fun defaultUiContextProviderFallsBackToRegisteredAndroidContextWithoutFrameworkCalls() {
        val container = DIContainerImpl(scopeName = "ui-context")
        container.register<Context>(TestContext)

        container.injectInstanceDefaults(InstanceName("ui-context"), OwnIdConfigurationImpl(appId = "app123"))

        assertThat(container.getOrThrow<UiContextProvider>().current()).isSameInstanceAs(TestContext)
    }

    @Test
    fun passkeyUiAdaptersForwardContextAndOptionsToProviderWithoutFrameworkCalls() = runTest {
        val assertionOptions = assertionOptions()
        val assertionResult = assertionResult()
        val attestationOptions = attestationOptions()
        val attestationResult = attestationResult()
        val passkeyProvider = RecordingPasskeyProvider(
            assertionResult = Passkey.Result.Success(assertionResult),
            attestationResult = Passkey.Result.Success(attestationResult)
        )

        val assertionUi = PasskeyAssertionUiImpl(
            uiContextProvider = { UiContextProvider { TestContext } },
            passkeyProvider = { passkeyProvider }
        )
        val attestationUi = PasskeyAttestationUiImpl(
            uiContextProvider = { UiContextProvider { TestContext } },
            passkeyProvider = { passkeyProvider }
        )

        assertThat(assertionUi.getCredential(assertionOptions)).isEqualTo(Passkey.Result.Success(assertionResult))
        assertThat(attestationUi.createCredential(attestationOptions, isAutoSelectAllowed = false))
            .isEqualTo(Passkey.Result.Success(attestationResult))
        assertThat(passkeyProvider.assertionRequests).containsExactly(PasskeyAssertionRequest(TestContext, assertionOptions))
        assertThat(passkeyProvider.attestationRequests).containsExactly(PasskeyAttestationRequest(TestContext, attestationOptions, false))
    }

    @Test
    fun signInWithGoogleUiForwardsContextAndChallengeParamsToProviderWithoutFrameworkCalls() = runTest {
        val expected = SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token")
        val provider = RecordingSignInWithGoogleProvider(expected)
        val ui = SignInWithGoogleUiImpl(
            uiContextProvider = { UiContextProvider { TestContext } },
            signInWithGoogleProvider = { provider }
        )

        val result = ui.signIn(webClientId = "web-client-id", nonce = "nonce")

        assertThat(result).isSameInstanceAs(expected)
        assertThat(provider.signInRequests).hasSize(1)
        assertThat(provider.signInRequests.single().context).isSameInstanceAs(TestContext)
        assertThat(provider.signInRequests.single().webClientId).isEqualTo("web-client-id")
        assertThat(provider.signInRequests.single().nonce).isEqualTo("nonce")
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

    private data class PasskeyAssertionRequest(val context: Context, val assertionOptions: AssertionOptions)

    private data class PasskeyAttestationRequest(
        val context: Context,
        val attestationOptions: AttestationOptions,
        val isAutoSelectAllowed: Boolean
    )

    private class RecordingPasskeyProvider(
        private val assertionResult: Passkey.Result<AssertionResult>,
        private val attestationResult: Passkey.Result<AttestationResult>
    ) : Passkey {
        val assertionRequests = mutableListOf<PasskeyAssertionRequest>()
        val attestationRequests = mutableListOf<PasskeyAttestationRequest>()

        override suspend fun getCredential(context: Context, assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> {
            assertionRequests += PasskeyAssertionRequest(context, assertionOptions)
            return assertionResult
        }

        override suspend fun createCredential(
            context: Context,
            attestationOptions: AttestationOptions,
            isAutoSelectAllowed: Boolean
        ): Passkey.Result<AttestationResult> {
            attestationRequests += PasskeyAttestationRequest(context, attestationOptions, isAutoSelectAllowed)
            return attestationResult
        }
    }

    private class RecordingSignInWithGoogleProvider(
        private val signInResult: SignInWithSocial.Result
    ) : SignInWithGoogle {
        val signInRequests = mutableListOf<SignInWithSocial.Params>()

        override suspend fun signIn(params: SignInWithSocial.Params): SignInWithSocial.Result {
            signInRequests += params
            return signInResult
        }

        override suspend fun cancel(): Unit = Unit

        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable? = null
    }

    private class FakeOperationController<Result, Failure : OperationFailure>(
        override val operationId: OperationID
    ) : OperationController<Result, Failure> {
        override suspend fun whenSettled(): OperationResult<Result, Failure> =
            OperationResult.Canceled(Reason.Unknown("unused"))

        override fun abort(reason: Reason): Unit = Unit
    }

    private object TestContext : ContextWrapper(null)
}
