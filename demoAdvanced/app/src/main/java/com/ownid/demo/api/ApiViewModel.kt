package com.ownid.demo.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.OwnId
import com.ownid.sdk.api.EmailEnrollApi
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PhoneEnrollApi
import com.ownid.sdk.api.onError
import com.ownid.sdk.api.onSuccess
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialChallenge
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.namespace.OwnIdFullInstance
import com.ownid.sdk.namespace.start
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
import kotlinx.coroutines.launch

class ApiViewModel : ViewModel() {
    enum class Mode(val title: String) {
        LoginId("Login ID"),
        AccessToken("Access Token")
    }

    data class VerificationScreenState(
        val challenge: VerificationChallenge? = null,
        val status: String? = null,
        val resend: () -> Unit = {},
        val cancel: () -> Unit = {},
        val completeWithCode: (String) -> Unit = {},
        val proofToken: ProofToken? = null,
        val accessTokenResult: AccessToken? = null,
        val enrollResult: String? = null
    ) {
        val isActive: Boolean
            get() = challenge != null || proofToken != null
    }

    data class PasskeyCreateState(
        val attestationOptions: AttestationOptions? = null,
        val status: String? = null,
        val createPasskey: () -> Unit = {},
        val cancelPasskey: () -> Unit = {},
        val attestationResult: AttestationResult? = null,
        val verifyAttestation: (AttestationResult) -> Unit = {},
        val attestationResponse: AttestationResponse? = null,
        val enrollResult: String? = null
    ) {
        val isActive: Boolean
            get() = attestationOptions != null || attestationResult != null || attestationResponse != null
    }

    data class PasskeyAssertionsState(
        val assertionOptions: AssertionOptions? = null,
        val status: String? = null,
        val runAssertion: () -> Unit = {},
        val cancelAssertion: () -> Unit = {},
        val assertionResult: AssertionResult? = null,
        val verifyAssertion: (AssertionResult) -> Unit = {},
        val accessTokenResult: AccessToken? = null
    ) {
        val isActive: Boolean
            get() = assertionOptions != null || assertionResult != null
    }

    data class DiscoverState(
        val discoverResponse: LoginResponse? = null,
        val discoverStatus: String? = null
    ) {
        val hasState: Boolean
            get() = discoverResponse != null || discoverStatus != null
    }

    data class LoginState(
        val loginResponse: LoginResponse? = null,
        val loginStatus: String? = null
    ) {
        val hasState: Boolean
            get() = loginResponse != null || loginStatus != null
    }

    data class OidcState(
        val challenge: SocialChallenge? = null,
        val status: String? = null,
        val authorize: () -> Unit = {},
        val cancel: () -> Unit = {},
        val result: AccessTokenWithUserInfo? = null
    ) {
        val isActive: Boolean
            get() = challenge != null

        val hasState: Boolean
            get() = challenge != null || status != null || result != null
    }

    data class ScreenState(
        val mode: Mode = Mode.LoginId,
        val loginId: String = "",
        val loginIdType: LoginId.Type = LoginId.Type.Email,
        val accessToken: AccessToken? = null
    ) {
        val canStartOperation: Boolean
            get() = when (mode) {
                Mode.LoginId -> loginId.isNotBlank()
                Mode.AccessToken -> accessToken != null
            }
    }

    var screenState by mutableStateOf(ScreenState())
        private set

    var verificationState by mutableStateOf(VerificationScreenState())
        private set

    var passkeyCreateState by mutableStateOf(PasskeyCreateState())
        private set

    var passkeyAssertionsState by mutableStateOf(PasskeyAssertionsState())
        private set

    var discoverState by mutableStateOf(DiscoverState())
        private set

    var loginState by mutableStateOf(LoginState())
        private set

    var oidcState by mutableStateOf(OidcState())
        private set

    @OptIn(InternalOwnIdAPI::class)
    private val ownId = OwnId.instance() as OwnIdFullInstance

    fun onLoginIdChanged(value: String) {
        screenState = screenState.copy(loginId = value)
    }

    fun onLoginIdTypeSelected(type: LoginId.Type) {
        screenState = screenState.copy(loginIdType = type)
    }

    fun onModeSelected(mode: Mode) {
        if (mode == Mode.AccessToken && screenState.accessToken == null) return
        screenState = screenState.copy(mode = mode)
    }

    fun resetVerificationState() {
        verificationState = VerificationScreenState()
    }

    fun resetPasskeyCreateState() {
        passkeyCreateState = PasskeyCreateState()
    }

    fun resetPasskeyAssertionsState() {
        passkeyAssertionsState = PasskeyAssertionsState()
    }

    fun resetDiscoverState() {
        discoverState = DiscoverState()
    }

    fun resetLoginState() {
        loginState = LoginState()
    }

    fun resetOidcState() {
        oidcState = OidcState()
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startVerificationChallenge() {
        resetVerificationState()

        viewModelScope.launch {
            val api = ownId.api.withContext {
                authz = if (screenState.mode == Mode.AccessToken) {
                    screenState.accessToken?.let { Authz.fromToken(it) }
                } else {
                    Authz.start(screenState.loginId, screenState.loginIdType)
                }
            }

            when (screenState.loginIdType) {
                LoginId.Type.Email -> api.verifications.email.start()
                    .onSuccess { controller ->
                        verificationState = VerificationScreenState(
                            challenge = controller.challenge,
                            resend = {
                                viewModelScope.launch {
                                    controller.resend()
                                        .onSuccess { verificationState = verificationState.copy(status = "Resend succeeded", enrollResult = null) }
                                        .onError { error ->
                                            verificationState = verificationState.copy(status = error.toString())
                                        }
                                }
                            },
                            cancel = {
                                viewModelScope.launch {
                                    controller.cancel(Reason.UserClose())
                                        .onSuccess { verificationState = VerificationScreenState(status = "Verification canceled") }
                                        .onError { error ->
                                            verificationState = verificationState.copy(status = error.toString())
                                        }
                                }
                            },
                            completeWithCode = { code ->
                                viewModelScope.launch {
                                    controller.completeWithCode(code)
                                        .onSuccess { token ->
                                            when (token) {
                                                is AccessToken -> {
                                                    screenState = screenState.copy(accessToken = token)
                                                    verificationState = VerificationScreenState(accessTokenResult = token)
                                                }

                                                is ProofToken -> verificationState = VerificationScreenState(proofToken = token)
                                            }
                                        }
                                        .onError { error ->
                                            verificationState = verificationState.copy(status = error.toString())
                                        }
                                }
                            }
                        )
                    }
                    .onError { error ->
                        verificationState = VerificationScreenState(status = error.toString())
                    }

                LoginId.Type.PhoneNumber -> api.verifications.phone.start()
                    .onSuccess { controller ->
                        verificationState = VerificationScreenState(
                            challenge = controller.challenge,
                            resend = {
                                viewModelScope.launch {
                                    controller.resend()
                                        .onSuccess { verificationState = verificationState.copy(status = "Resend succeeded", enrollResult = null) }
                                        .onError { error -> verificationState = verificationState.copy(status = error.toString()) }
                                }
                            },
                            cancel = {
                                viewModelScope.launch {
                                    controller.cancel(Reason.UserClose())
                                        .onSuccess { verificationState = VerificationScreenState(status = "Verification canceled") }
                                        .onError { error -> verificationState = verificationState.copy(status = error.toString()) }
                                }
                            },
                            completeWithCode = { code ->
                                viewModelScope.launch {
                                    controller.completeWithCode(code)
                                        .onSuccess { token ->
                                            when (token) {
                                                is AccessToken -> {
                                                    screenState = screenState.copy(accessToken = token)
                                                    verificationState = VerificationScreenState(accessTokenResult = token)
                                                }

                                                is ProofToken -> verificationState = VerificationScreenState(proofToken = token)
                                            }
                                        }
                                        .onError { error -> verificationState = verificationState.copy(status = error.toString()) }
                                }
                            }
                        )
                    }
                    .onError { error ->
                        verificationState = VerificationScreenState(status = error.toString())
                    }

                else -> error("Unsupported verification login ID type: ${screenState.loginIdType}")
            }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startVerificationEnrollment() {
        val proofToken = verificationState.proofToken ?: return
        val accessToken = screenState.accessToken ?: return

        verificationState = verificationState.copy(enrollResult = null)

        viewModelScope.launch {
            val api = ownId.api.withContext {
                authz = Authz.fromToken(accessToken)
            }

            when (screenState.loginIdType) {
                LoginId.Type.Email -> {
                    api.enroll.email.start(params = EmailEnrollApi.Params(proofToken = proofToken))
                        .onSuccess {
                            verificationState = VerificationScreenState(enrollResult = "Verification enrollment succeeded")
                        }.onError { error ->
                            verificationState = verificationState.copy(enrollResult = error.toString())
                        }
                }

                LoginId.Type.PhoneNumber -> {
                    api.enroll.phone.start(params = PhoneEnrollApi.Params(proofToken = proofToken))
                        .onSuccess {
                            verificationState = VerificationScreenState(enrollResult = "Verification enrollment succeeded")
                        }.onError { error ->
                            verificationState = verificationState.copy(enrollResult = error.toString())
                        }
                }

                else -> error("Unsupported verification login ID type: ${screenState.loginIdType}")
            }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyCreateChallenge() {
        resetPasskeyCreateState()

        viewModelScope.launch {
            val api = ownId.api.withContext {
                authz = if (screenState.mode == Mode.AccessToken) {
                    screenState.accessToken?.let { Authz.fromToken(it) }
                } else {
                    Authz.start(screenState.loginId, screenState.loginIdType)
                }
            }

            api.passkeys.attestation.start()
                .onSuccess { controller ->
                    passkeyCreateState = PasskeyCreateState(
                        attestationOptions = controller.attestationOptions,
                        verifyAttestation = { attestationResult ->
                            viewModelScope.launch {
                                controller.verify(attestationResult)
                                    .onSuccess { response -> passkeyCreateState = PasskeyCreateState(attestationResponse = response) }
                                    .onError { error ->
                                        passkeyCreateState = passkeyCreateState.copy(status = error.toString())
                                    }
                            }
                        },
                        createPasskey = {
                            viewModelScope.launch {
                                passkeyCreateState = when (
                                    val passkeyResult = OwnId.getInstanceContainer()!!
                                        .getOrThrow<PasskeyAttestationUi>()
                                        .createCredential(controller.attestationOptions, isAutoSelectAllowed = true)
                                ) {
                                    is Passkey.Result.Success -> passkeyCreateState.copy(status = null, attestationResult = passkeyResult.data)
                                    is Passkey.Result.Fail -> passkeyCreateState.copy(status = passkeyResult.error.toString(), attestationResult = null)
                                    is Passkey.Result.Canceled -> passkeyCreateState.copy(status = passkeyResult.reason.toString(), attestationResult = null)
                                }
                            }
                        },
                        cancelPasskey = {
                            viewModelScope.launch {
                                controller.cancel(Reason.UserClose())
                                    .onSuccess { passkeyCreateState = PasskeyCreateState(status = "Passkey create canceled") }
                                    .onError { error ->
                                        passkeyCreateState = passkeyCreateState.copy(status = error.toString())
                                    }
                            }
                        }
                    )
                }
                .onError { error ->
                    passkeyCreateState = PasskeyCreateState(status = error.toString())
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyEnrollment() {
        val proofToken = passkeyCreateState.attestationResponse?.proofToken ?: return
        val accessToken = screenState.accessToken ?: return

        passkeyCreateState = passkeyCreateState.copy(enrollResult = null)

        viewModelScope.launch {
            val api = ownId.api.withContext {
                authz = Authz.fromToken(accessToken)
            }

            api.enroll.passkey.start(params = PasskeyEnrollApi.Params(proofToken = proofToken))
                .onSuccess { passkeyCreateState = PasskeyCreateState(enrollResult = "Passkey enroll succeeded") }
                .onError { error ->
                    passkeyCreateState = passkeyCreateState.copy(enrollResult = error.toString())
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startPasskeyAssertionChallenge() {
        resetPasskeyAssertionsState()

        viewModelScope.launch {
            val params = when (screenState.mode) {
                Mode.LoginId -> PasskeyAssertionApi.Params(
                    loginId = screenState.loginId.takeIf { it.isNotBlank() }?.let { LoginId(it, screenState.loginIdType) }
                )

                Mode.AccessToken -> PasskeyAssertionApi.Params(
                    accessToken = screenState.accessToken
                )
            }

            ownId.api.passkeys.assertion.start(params)
                .onSuccess { controller ->
                    passkeyAssertionsState = PasskeyAssertionsState(
                        assertionOptions = controller.assertionOptions,
                        verifyAssertion = { assertionResult ->
                            viewModelScope.launch {
                                controller.verify(assertionResult)
                                    .onSuccess { accessToken ->
                                        screenState = screenState.copy(accessToken = accessToken)
                                        passkeyAssertionsState = PasskeyAssertionsState(accessTokenResult = accessToken)
                                    }
                                    .onError { error ->
                                        passkeyAssertionsState = passkeyAssertionsState.copy(status = error.toString())
                                    }
                            }
                        },
                        runAssertion = {
                            viewModelScope.launch {
                                passkeyAssertionsState = when (
                                    val result = OwnId.getInstanceContainer()!!.getOrThrow<PasskeyAssertionUi>().getCredential(controller.assertionOptions)
                                ) {
                                    is Passkey.Result.Success -> passkeyAssertionsState.copy(status = null, assertionResult = result.data)
                                    is Passkey.Result.Fail -> passkeyAssertionsState.copy(status = result.error.toString(), assertionResult = null)
                                    is Passkey.Result.Canceled -> passkeyAssertionsState.copy(status = result.reason.toString(), assertionResult = null)
                                }
                            }
                        },
                        cancelAssertion = {
                            viewModelScope.launch {
                                controller.cancel(Reason.UserClose())
                                    .onSuccess { passkeyAssertionsState = PasskeyAssertionsState(status = "Assertion canceled") }
                                    .onError { error ->
                                        passkeyAssertionsState = passkeyAssertionsState.copy(status = error.toString())
                                    }
                            }
                        }
                    )
                }
                .onError { error ->
                    passkeyAssertionsState = PasskeyAssertionsState(status = error.toString())
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startDiscoverLoginDiscover() {
        resetDiscoverState()

        viewModelScope.launch {
            ownId.api.withContext {
                authz = Authz.start(screenState.loginId, screenState.loginIdType)
            }.auth.discover.start()
                .onSuccess { response ->
                    val accessToken = (response as? LoginResponse.Success)?.accessToken
                    if (accessToken != null) {
                        screenState = screenState.copy(accessToken = accessToken)
                    }
                    discoverState = DiscoverState(discoverResponse = response)
                }
                .onError { error ->
                    discoverState = DiscoverState(discoverStatus = error.toString())
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startDiscoverLoginLogin() {
        val accessToken = screenState.accessToken ?: return
        resetLoginState()

        viewModelScope.launch {
            ownId.api.withContext {
                authz = Authz.fromToken(accessToken)
            }.auth.login.start()
                .onSuccess { response ->
                    val nextAccessToken = (response as? LoginResponse.Success)?.accessToken ?: accessToken
                    screenState = screenState.copy(accessToken = nextAccessToken)
                    loginState = LoginState(loginResponse = response)
                }
                .onError { error ->
                    loginState = LoginState(loginStatus = error.toString())
                }
        }
    }

    @OptIn(InternalOwnIdAPI::class)
    fun startOidcChallenge() {
        resetOidcState()

        viewModelScope.launch {
            val api = ownId.api.withContext {
                authz = if (screenState.mode == Mode.AccessToken) {
                    screenState.accessToken?.let { Authz.fromToken(it) }
                } else {
                    null
                }
            }

            api.oidc.start(OidcApi.Params(provider = SocialProvider.GOOGLE))
                .onSuccess { controller ->
                    oidcState = OidcState(
                        challenge = controller.challenge,
                        authorize = {
                            viewModelScope.launch {
                                when (
                                    val signInResult = OwnId.getInstanceContainer()!!
                                        .getOrThrow<SignInWithGoogleUi>()
                                        .signIn(controller.challenge.clientId, controller.challenge.challengeId.value)
                                ) {
                                    is SignInWithSocial.Result.Success -> {
                                        controller.completeWithToken(signInResult.idToken)
                                            .onSuccess { result ->
                                                screenState = screenState.copy(
                                                    loginId = result.loginId.id,
                                                    loginIdType = result.loginId.type,
                                                    accessToken = result.accessToken
                                                )
                                                oidcState = OidcState(result = result)
                                            }
                                            .onError { error ->
                                                oidcState = oidcState.copy(status = error.toString())
                                            }
                                    }

                                    is SignInWithSocial.Result.Canceled ->
                                        oidcState = oidcState.copy(status = signInResult.reason.toString())

                                    is SignInWithSocial.Result.Fail ->
                                        oidcState = oidcState.copy(status = signInResult.error.toString())
                                }
                            }
                        },
                        cancel = {
                            viewModelScope.launch {
                                controller.cancel(Reason.UserClose())
                                    .onSuccess { oidcState = OidcState(status = "OIDC canceled") }
                                    .onError { error ->
                                        oidcState = oidcState.copy(status = error.toString())
                                    }
                            }
                        }
                    )
                }
                .onError { error ->
                    oidcState = OidcState(status = error.toString())
                }
        }
    }
}
