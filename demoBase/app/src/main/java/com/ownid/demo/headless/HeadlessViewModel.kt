package com.ownid.demo.headless

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.demo.DemoIdentityPlatform
import com.ownid.sdk.OwnId
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.api.onError
import com.ownid.sdk.api.onSuccess
import com.ownid.sdk.api.toUiError
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthRequirements
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.onUnavailable
import com.ownid.sdk.namespace.OwnIdHeadless
import com.ownid.sdk.namespace.availability
import com.ownid.sdk.namespace.start
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.getOrNull
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.onSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class HeadlessViewModel : ViewModel() {

    data class ScreenState(
        val email: String = "",
        val flowState: FlowState = FlowState.Idle
    )

    sealed interface FlowState {
        data object Idle : FlowState
        data object Loading : FlowState
        data class EmailVerification(
            val challenge: VerificationChallenge,
            val busy: Boolean = false,
            val error: UiError? = null,
            val resendCount: Int = 0,
            val resendAvailableAtMs: Long = 0L
        ) : FlowState

        data object Completed : FlowState
        data object Failed : FlowState
    }

    private val emailVerification = EmailVerificationCoordinator()

    var screenState by mutableStateOf(ScreenState())
        private set

    private val _logEntries = mutableStateListOf<String>()
    val logEntries: List<String> = _logEntries

    fun onEmailChanged(value: String) {
        screenState = screenState.copy(email = value)
    }

    fun start() {
        viewModelScope.launch {
            val email = screenState.email
            screenState = screenState.copy(flowState = FlowState.Loading)

            val headless = OwnId.headless.withContext {
                authz = Authz.start(email, LoginId.Type.Email)
            }

            headless.auth.discover.start()
                .onSuccess { loginResponse ->
                    when (loginResponse) {
                        is LoginResponse.AuthRequired -> handleAuthRequirements(loginResponse.authRequirements, headless)

                        is LoginResponse.AccountNotFound -> {
                            val details = loginResponse.reason ?: "Account not found"
                            _logEntries.add("Discover: account not found: $details")

                            if (registerDemoUser(email)) {
                                screenState = screenState.copy(flowState = FlowState.Idle)
                                start()
                                return@launch
                            }

                            screenState = screenState.copy(flowState = FlowState.Failed)
                            return@launch
                        }

                        is LoginResponse.AccountBlocked -> {
                            val details = loginResponse.reason ?: "Account is blocked"
                            _logEntries.add("Discover: account blocked: $details")
                            screenState = screenState.copy(flowState = FlowState.Failed)
                            return@launch
                        }

                        is LoginResponse.Success -> {
                            _logEntries.add("Discover succeeded with session: $loginResponse")
                            screenState = screenState.copy(flowState = FlowState.Completed)
                            passkeyEnroll(loginResponse.accessToken)
                            return@launch
                        }
                    }
                }
                .onError { error ->
                    _logEntries.add("Discover failed: $error")
                    screenState = screenState.copy(flowState = FlowState.Failed)
                    return@launch
                }
        }
    }

    private suspend fun handleAuthRequirements(authRequirements: AuthRequirements, headless: OwnIdHeadless) {
        val hasPasskeyAuth = authRequirements.operations.any { it.type == OperationType.PASSKEY_AUTH }
        var shouldEnrollAfterOtp = !hasPasskeyAuth

        if (hasPasskeyAuth) {
            var passkeyMissing = false
            val accessToken = runPasskeyAuthentication(headless = headless, onMissingPasskey = { passkeyMissing = true })

            if (accessToken != null) {
                val accessToken = login(accessToken)
                if (accessToken != null) {
                    screenState = screenState.copy(flowState = FlowState.Completed)
                    return
                }
            }
            shouldEnrollAfterOtp = passkeyMissing
        }

        if (authRequirements.operations.any { it.type == OperationType.EMAIL_VERIFICATION }) {
            when (val accessOrProofToken = emailVerification.run(headless)) {
                is AccessToken -> {
                    val accessToken = login(accessOrProofToken, registerMissingUser = true)
                    if (accessToken != null) {
                        screenState = screenState.copy(flowState = FlowState.Completed)
                        if (shouldEnrollAfterOtp) {
                            passkeyEnroll(accessToken)
                        }
                    } else {
                        screenState = screenState.copy(flowState = FlowState.Failed)
                    }
                }

                is ProofToken -> {
                    _logEntries.add("Email Verification returned proof token: $accessOrProofToken")
                    screenState = screenState.copy(flowState = FlowState.Failed)
                }

                null -> Unit
            }
            return
        }

        _logEntries.add("No verification method available")
        screenState = screenState.copy(flowState = FlowState.Failed)
    }

    private suspend fun runPasskeyAuthentication(headless: OwnIdHeadless, onMissingPasskey: () -> Unit = {}): AccessToken? {
        val passkeyAuth = headless.passkeys.auth

        var passkeyAvailable = true
        passkeyAuth.availability().onUnavailable { message ->
            passkeyAvailable = false
            _logEntries.add("Passkey Authentication is not available: $message")
        }
        if (!passkeyAvailable) return null

        return passkeyAuth.start()
            .also { controller -> addCloseable(controller) }
            .whenSettled()
            .onSuccess { accessToken -> _logEntries.add("Passkey Authentication succeeded: $accessToken") }
            .onCanceled { reason -> _logEntries.add("Passkey Authentication canceled: $reason") }
            .onError { error ->
                _logEntries.add("Passkey Authentication failed: $error")
                if (error is PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys) {
                    onMissingPasskey()
                }
            }
            .getOrNull()
    }

    private suspend fun login(accessToken: AccessToken, registerMissingUser: Boolean = false): AccessToken? =
        OwnId.headless.withContext { authz = Authz.fromToken(accessToken) }.auth.login.start().fold(
            onSuccess = { loginResponse ->
                when (loginResponse) {
                    is LoginResponse.AuthRequired -> {
                        _logEntries.add("Login: TargetScore not achieved: ${loginResponse.reason}")
                        null
                    }

                    is LoginResponse.AccountNotFound -> {
                        _logEntries.add("Login: Account not found: ${loginResponse.reason}")
                        if (registerMissingUser && registerDemoUser(screenState.email)) login(accessToken) else null
                    }

                    is LoginResponse.AccountBlocked -> {
                        _logEntries.add("Login: Account blocked: ${loginResponse.reason}")
                        null
                    }

                    is LoginResponse.Success -> {
                        _logEntries.add("Login succeeded: $loginResponse")
                        loginResponse.accessToken
                    }
                }
            },
            onError = { error ->
                _logEntries.add("Login failed: $error")
                null
            }
        )

    private fun passkeyEnroll(accessToken: AccessToken) {
        viewModelScope.launch {
            val passkeyEnroll = OwnId.headless
                .withContext { authz = Authz.fromToken(accessToken) }
                .passkeys.enroll

            passkeyEnroll.availability().onUnavailable { message ->
                _logEntries.add("Passkey Enroll is not available: $message")
                return@launch
            }

            passkeyEnroll.start()
                .also { controller -> addCloseable(controller) }
                .whenSettled()
                .onSuccess { response -> _logEntries.add("Passkey Enroll succeeded: ${response.loginId}") }
                .onCanceled { reason -> _logEntries.add("Passkey Enroll canceled: $reason") }
                .onError { error -> _logEntries.add("Passkey Enroll failed: $error") }
        }
    }

    fun resendEmailVerification() = emailVerification.resend()

    fun cancelEmailVerification() = emailVerification.cancel()

    fun completeEmailVerification(otpCode: String) = emailVerification.complete(otpCode)

    private inner class EmailVerificationCoordinator {
        private var pending: CompletableDeferred<AccessOrProofToken?>? = null
        private var controller: EmailVerificationApi.Controller? = null

        suspend fun run(headless: OwnIdHeadless): AccessOrProofToken? {
            clear()

            val deferred = CompletableDeferred<AccessOrProofToken?>()
            pending = deferred

            headless.verifications.email.start()
                .onSuccess { controller ->
                    if (pending !== deferred) return@onSuccess

                    this.controller = controller
                    _logEntries.add("Email Verification started: waiting for OTP")
                    screenState = screenState.copy(
                        flowState = FlowState.EmailVerification(
                            challenge = controller.challenge,
                            busy = false,
                            error = null,
                            resendCount = 0,
                            resendAvailableAtMs = if (!controller.challenge.resendPolicy.allow) 0L
                            else SystemClock.elapsedRealtime() + controller.challenge.resendPolicy.debounce.toLong() * 1000L
                        )
                    )
                }
                .onError { error ->
                    if (pending !== deferred) return@onError

                    _logEntries.add("Email Verification failed: $error")
                    screenState = screenState.copy(flowState = FlowState.Failed)
                    finish(null)
                }

            return deferred.await()
        }

        fun resend() {
            viewModelScope.launch {
                val state = screenState.flowState as? FlowState.EmailVerification ?: return@launch
                val controller = controller ?: return@launch
                if (state.busy) return@launch

                if (!controller.challenge.resendPolicy.allow) return@launch
                if (state.resendCount >= controller.challenge.resendPolicy.attempts) return@launch
                if (state.resendAvailableAtMs > SystemClock.elapsedRealtime()) return@launch

                screenState = screenState.copy(flowState = state.copy(busy = true, error = null))

                controller.resend()
                    .onSuccess {
                        _logEntries.add("Email Verification resent")
                        screenState = screenState.copy(
                            flowState = state.copy(
                                busy = false,
                                error = null,
                                resendCount = state.resendCount + 1,
                                resendAvailableAtMs = SystemClock.elapsedRealtime() + controller.challenge.resendPolicy.debounce.toLong() * 1000L
                            )
                        )
                    }
                    .onError { error ->
                        _logEntries.add("Email Verification resend: $error")
                        screenState = screenState.copy(flowState = state.copy(busy = false, error = error.toUiError()))
                    }
            }
        }

        fun cancel() {
            viewModelScope.launch {
                val state = screenState.flowState as? FlowState.EmailVerification ?: return@launch
                val controller = controller ?: return@launch
                if (state.busy) return@launch
                screenState = screenState.copy(flowState = state.copy(busy = true, error = null))

                controller.cancel(Reason.UserClose())
                    .onSuccess { _logEntries.add("Email Verification canceled") }
                    .onError { error -> _logEntries.add("Email Verification cancellation: $error") }

                screenState = screenState.copy(flowState = FlowState.Failed)
                finish(null)
            }
        }

        fun complete(otpCode: String) {
            viewModelScope.launch {
                val state = screenState.flowState as? FlowState.EmailVerification ?: return@launch
                val controller = controller ?: return@launch
                if (state.busy) return@launch
                screenState = screenState.copy(flowState = state.copy(busy = true, error = null))

                controller.completeWithCode(otpCode)
                    .onSuccess { token ->
                        _logEntries.add("Email Verification succeeded: $token")
                        finish(token)
                    }
                    .onError { error ->
                        _logEntries.add("Email Verification: ${error.toUiError()}\n$error")
                        if (error is EmailVerificationCompleteApiFailure.BadRequest.WrongCode) {
                            screenState = screenState.copy(flowState = state.copy(busy = false, error = error.toUiError()))
                        } else {
                            screenState = screenState.copy(flowState = FlowState.Failed)
                            finish(null)
                        }
                    }
            }
        }

        fun clear() {
            finish(null)
        }

        private fun finish(token: AccessOrProofToken?) {
            pending?.complete(token)
            pending = null
            controller = null
        }
    }

    override fun onCleared() {
        emailVerification.clear()
        super.onCleared()
    }

    private suspend fun registerDemoUser(email: String): Boolean =
        DemoIdentityPlatform.register(
            name = email,
            email = email,
            password = "SomeRandomLongAndCrypticPassword",
            ownIdData = null
        )
            .onSuccess { _logEntries.add("Registering user: OK") }
            .onFailure { error -> _logEntries.add("Registering user failed: $error") }
            .isSuccess
}
