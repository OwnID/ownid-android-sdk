package com.ownid.demo.ops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.OwnIdOperationUiController
import com.ownid.sdk.compose.start
import com.ownid.sdk.compose.appHostedComponent
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.namespace.OwnIdFullInstance
import com.ownid.sdk.namespace.start
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.onSuccess
import kotlinx.coroutines.launch

class OperationViewModel : ViewModel() {

    enum class UiMode(val title: String) {
        Default("Default"),
        Dialog("Dialog"),
        Embedded("Embedded")
    }

    data class ScreenState(
        val loginId: String = "",
        val loginIdType: LoginId.Type? = null,
        val verificationLoginIdType: LoginId.Type = LoginId.Type.Email,
        val accessToken: AccessToken? = null,
        val useAccessToken: Boolean = false,
        val attestationResponse: AttestationResponse? = null,
        val uiMode: UiMode = UiMode.Default
    ) {
        val canStartOperation: Boolean
            get() = if (useAccessToken) accessToken != null else loginId.isNotBlank()

        val canStartDiscoverOperation: Boolean
            get() = loginId.isNotBlank()
    }

    var screenState by mutableStateOf(ScreenState())
        private set

    private val _logEntries = mutableStateListOf<String>()
    val logEntries: List<String> = _logEntries

    var loginIdOperationUiController by mutableStateOf<OwnIdOperationUiController<*, *>?>(null)

    var verificationOperationUiController by mutableStateOf<OwnIdOperationUiController<*, *>?>(null)

    @OptIn(InternalOwnIdAPI::class)
    private val ownId = OwnId.instance() as OwnIdFullInstance

    fun onLoginIdChanged(value: String) {
        screenState = screenState.copy(loginId = value)
    }

    fun onLoginIdTypeSelected(type: LoginId.Type?) {
        screenState = screenState.copy(loginIdType = type)
    }

    fun onVerificationLoginIdTypeSelected(type: LoginId.Type) {
        screenState = screenState.copy(verificationLoginIdType = type)
    }

    fun onUseAccessTokenChanged(useAccessToken: Boolean) {
        if (screenState.accessToken == null) return
        screenState = screenState.copy(useAccessToken = useAccessToken)
    }

    fun onUiModeSelected(mode: UiMode) {
        clearOperationControllers()
        screenState = screenState.copy(uiMode = mode)
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startLoginIdCollectOperation() {
        viewModelScope.launch {
            clearOperationControllers()

            _logEntries.add("Login ID Collect started")

            val useAppHostedUi = screenState.uiMode != UiMode.Default

            val loginIdCollectOperation = ownId.ops
                .withContext {
                    authz = screenState.loginId.takeIf { it.isNotBlank() }?.let { Authz.start(it, screenState.loginIdType) }
                }
                .loginId.collect

            val operationResult = if (useAppHostedUi) {
                val controller = loginIdCollectOperation.appHostedComponent.start()
                loginIdOperationUiController = controller
                controller.whenSettled()
                    .also { if (loginIdOperationUiController == controller) loginIdOperationUiController = null }

            } else {
                loginIdCollectOperation.start().whenSettled()
            }
            operationResult
                .onSuccess { success -> _logEntries.add("Login ID Collect succeeded: $success") }
                .onCanceled { reason -> _logEntries.add("Login ID Collect canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Login ID Collect failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startVerificationOperation() {
        viewModelScope.launch {
            clearOperationControllers()
            val useAppHostedUi = screenState.uiMode != UiMode.Default

            val operations = ownId.ops
                .withContext {
                    authz = if (screenState.useAccessToken) {
                        screenState.accessToken?.let { Authz.fromToken(it) }
                    } else {
                        screenState.loginId.takeIf { it.isNotBlank() }?.let { Authz.start(it, screenState.verificationLoginIdType) }
                    }
                }

            when (screenState.verificationLoginIdType) {
                LoginId.Type.Email -> {
                    _logEntries.add("Email Verification started")
                    val operationResult = if (useAppHostedUi) {
                        val controller = operations.verifications.email.appHostedComponent.start()
                        verificationOperationUiController = controller
                        controller.whenSettled()
                            .also { if (verificationOperationUiController == controller) verificationOperationUiController = null }
                    } else {
                        operations.verifications.email.start().whenSettled()
                    }
                    operationResult
                        .onSuccess { success ->
                            screenState = screenState.copy(
                                accessToken = success as? AccessToken,
                                useAccessToken = screenState.useAccessToken && success is AccessToken
                            )
                            _logEntries.add("Verification succeeded: $success")
                        }
                        .onCanceled { reason -> _logEntries.add("Verification canceled: $reason") }
                        .onError { error ->
                            _logEntries.add("Verification failed: $error")
                        }
                }

                LoginId.Type.PhoneNumber -> {
                    _logEntries.add("Phone Verification started")
                    val operationResult = if (useAppHostedUi) {
                        val controller = operations.verifications.phone.appHostedComponent.start()
                        verificationOperationUiController = controller
                        controller.whenSettled()
                            .also { if (verificationOperationUiController == controller) verificationOperationUiController = null }
                    } else {
                        operations.verifications.phone.start().whenSettled()
                    }
                    operationResult
                        .onSuccess { success ->
                            screenState = screenState.copy(
                                accessToken = success as? AccessToken,
                                useAccessToken = screenState.useAccessToken && success is AccessToken
                            )
                            _logEntries.add("Verification succeeded: $success")
                        }
                        .onCanceled { reason -> _logEntries.add("Verification canceled: $reason") }
                        .onError { error ->
                            _logEntries.add("Verification failed: $error")
                        }
                }

                else -> error("Unsupported verification login ID type: ${screenState.verificationLoginIdType}")
            }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyCreateEnrollOperation() {
        viewModelScope.launch {
            _logEntries.add("Passkey Create started")

            screenState = screenState.copy(attestationResponse = null)

            ownId.ops
                .withContext {
                    authz = if (screenState.useAccessToken) {
                        screenState.accessToken?.let { Authz.fromToken(it) }
                    } else {
                        screenState.loginId.takeIf { it.isNotBlank() }?.let { Authz.start(it, screenState.loginIdType) }
                    }
                }
                .passkeys.create.start()
                .whenSettled()
                .onSuccess { success ->
                    screenState = screenState.copy(attestationResponse = success)
                    _logEntries.add("Passkey Create succeeded: $success")
                }
                .onCanceled { reason -> _logEntries.add("Passkey Create canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Passkey Create failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyEnrollOperation() {
        val attestationResponse = screenState.attestationResponse ?: return
        val accessToken = screenState.accessToken ?: return

        viewModelScope.launch {
            _logEntries.add("Passkey Enroll started")

            ownId.ops
                .withContext { authz = Authz.fromToken(accessToken) }
                .passkeys.enroll.start(PasskeyEnrollOperation.Params(proofToken = attestationResponse.proofToken))
                .whenSettled()
                .onSuccess {
                    screenState = screenState.copy(attestationResponse = null)
                    _logEntries.add("Passkey Enroll succeeded")
                }
                .onCanceled { reason -> _logEntries.add("Passkey Enroll canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Passkey Enroll failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyAssertionOperation() {
        viewModelScope.launch {
            _logEntries.add("Passkey Assertion started")

            ownId.ops
                .withContext {
                    authz = if (screenState.useAccessToken) {
                        screenState.accessToken?.let { Authz.fromToken(it) }
                    } else {
                        screenState.loginId.takeIf { it.isNotBlank() }?.let { Authz.start(it, screenState.loginIdType) }
                    }
                }
                .passkeys.auth.start()
                .whenSettled()
                .onSuccess { success ->
                    screenState = screenState.copy(accessToken = success, useAccessToken = screenState.useAccessToken)
                    _logEntries.add("Passkey Assertion succeeded: $success")
                }
                .onCanceled { reason -> _logEntries.add("Passkey Assertion canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Passkey Assertion failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startDiscoverOperation() {
        val loginId = screenState.loginId.takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            _logEntries.add("Discover started")

            ownId.ops
                .withContext { authz = Authz.start(loginId, screenState.loginIdType) }
                .login.start()
                .whenSettled()
                .onSuccess { response ->
                    if (response is LoginResponse.Success) {
                        screenState = screenState.copy(accessToken = response.accessToken, useAccessToken = screenState.useAccessToken)
                    }
                    _logEntries.add("Discover result: $response")
                }
                .onCanceled { reason -> _logEntries.add("Discover canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Discover failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startLoginOperation() {
        val accessToken = screenState.accessToken ?: return

        viewModelScope.launch {
            _logEntries.add("Login started")

            ownId.ops
                .withContext { authz = Authz.fromToken(accessToken) }
                .login.start()
                .whenSettled()
                .onSuccess { response ->
                    if (response is LoginResponse.Success) {
                        screenState = screenState.copy(accessToken = response.accessToken, useAccessToken = screenState.useAccessToken)
                    }
                    _logEntries.add("Login result: $response")
                }
                .onCanceled { reason -> _logEntries.add("Login canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Login failed: $error")
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startSignInWithGoogleOperation() {
        viewModelScope.launch {
            _logEntries.add("Sign In with Google started")

            ownId.ops
                .withContext { authz = screenState.accessToken?.let { Authz.fromToken(it) } }
                .socialLogin.signInWithGoogle.start()
                .whenSettled()
                .onSuccess { success ->
                    screenState = screenState.copy(
                        loginId = success.loginId.id,
                        loginIdType = success.loginId.type,
                        accessToken = success.accessToken
                    )
                    _logEntries.add("Sign In with Google succeeded: $success")
                }
                .onCanceled { reason -> _logEntries.add("Sign In with Google canceled: $reason") }
                .onError { error ->
                    _logEntries.add("Sign In with Google failed: $error")
                }
        }
    }

    private fun clearOperationControllers() {
        loginIdOperationUiController = null
        verificationOperationUiController = null
    }
}
