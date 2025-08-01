package com.ownid.demo.gigya.screen.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.dsl.PageAction
import com.ownid.sdk.dsl.start
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdIntegrationError
import com.ownid.sdk.gigya
import com.ownid.sdk.viewmodel.OwnIdSocialViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

class AuthViewModel : ViewModel() {

    @Immutable
    sealed class UiState {
        data class LoggedIn(val name: String, val email: String) : UiState()
        data class Error(val error: String) : UiState()
        data class ConflictingAccount(val loginId: String, val message: String) : UiState()
        data class OnAccountNotFound(val loginId: String, val ownIdData: String?) : UiState()
    }

    private val gigya: Gigya<GigyaAccount> = Gigya.getInstance(GigyaAccount::class.java)

    private val _uiStateFlow: MutableStateFlow<UiState?> = MutableStateFlow(null)
    val uiStateFlow: StateFlow<UiState?> = _uiStateFlow.asStateFlow()

    init {
        if (gigya.isLoggedIn) {
            gigya.getAccount(true, object : GigyaCallback<GigyaAccount>() {
                override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
                override fun onError(error: GigyaError) = onGigyaError(error)
            })
        }
    }

    @MainThread
    fun doRegisterWithPassword(name: String, email: String, password: String) {
        val params = mutableMapOf<String, Any>()
        params["profile"] = JSONObject().put("firstName", name).toString()

        gigya.register(email, password, params, object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)
        })
    }

    @MainThread
    fun doLoginWithPassword(email: String, password: String) {
        gigya.login(email, password, object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)
        })
    }

    @MainThread
    fun finishRegisterWithOwnId(name: String) {
        val result = _uiStateFlow.value as UiState.OnAccountNotFound

        val email = result.loginId
        val password = OwnId.gigya.ownIdCore.generatePassword(9)

        val params = mutableMapOf<String, Any>()
        params["profile"] = JSONObject().put("firstName", name).toString()

        val paramsWithOwnIdData = if (result.ownIdData != null)
            OwnIdGigya.appendWithOwnIdData(params, result.ownIdData)
        else
            params

        gigya.register(email, password, paramsWithOwnIdData, object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)
        })
    }

//    @MainThread
//    fun runLoginWithGoogle() {
//        gigya.login(GigyaDefinitions.Providers.GOOGLE, mutableMapOf(), object : GigyaLoginCallback<GigyaAccount>() {
//            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
//            override fun onError(error: GigyaError) = onGigyaError(error)
//
//            override fun onConflictingAccounts(response: GigyaApiResponse, resolver: ILinkAccountsResolver) {
//                val providers: List<String> = resolver.conflictingAccounts.loginProviders
//                val loginId = resolver.conflictingAccounts.loginID
//
//                _uiStateFlow.value = UiState.ConflictingAccount(
//                    loginId = loginId,
//                    message = "Login via Google.\n\nConflicting Accounts: $loginId\nProviders: ${providers.joinToString()}"
//                )
//            }
//        })
//    }

    @MainThread
    fun onSignInWithGoogle(result: OwnIdSocialViewModel.State?) {
        when (result) {
            is OwnIdSocialViewModel.State.LoggedIn -> {
                try {
                    if (result.sessionPayload == null) {
                        onOwnIdError(OwnIdIntegrationError("No session payload"))
                        return
                    }
                    val dataJson = JSONObject(result.sessionPayload!!).optJSONObject("sessionInfo") ?: run {
                        onOwnIdError(OwnIdIntegrationError("No 'data' in session payload"))
                        return
                    }

                    if (dataJson.has("sessionSecret") && dataJson.has("sessionToken")) {
                        val sessionSecret = dataJson.getString("sessionSecret")
                        val sessionToken = dataJson.getString("sessionToken")

                        val expiresInValue = dataJson.optLong("expires_in")
                        val expirationTimeValue = dataJson.optLong("expirationTime")
                        val expirationTime = when {
                            expiresInValue > 0L -> expiresInValue
                            expirationTimeValue > 0L -> expirationTimeValue
                            else -> 0L
                        }
                        gigya.setSession(SessionInfo(sessionSecret, sessionToken, expirationTime))
                        onOwnIdLogin(null)
                    } else {
                        onOwnIdError(OwnIdIntegrationError("No session payload in 'data'"))
                    }
                } catch (e: JSONException) {
                    _uiStateFlow.value = UiState.Error(e.message ?: e.toString())
                }
            }

            is OwnIdSocialViewModel.State.Error -> {
                onOwnIdError(result.cause)
            }

            null -> Unit
        }
    }

    @MainThread
    fun runOwnIdFlow() {
        OwnId.start {
            events {
                onNativeAction { name, params ->
                    val registerData = PageAction.Native.Register.fromAction(name, params) ?: run {
                        _uiStateFlow.value = UiState.Error("Unknown action: $name")
                        return@onNativeAction
                    }

                    _uiStateFlow.value = UiState.OnAccountNotFound(registerData.loginId, registerData.ownIdData)
                }
                onAccountNotFound { loginId, ownIdData, authToken ->
                    PageAction.Native.Register(loginId, ownIdData, authToken)
                }
                onFinish { loginId: String, authMethod: AuthMethod?, authToken: String? ->
                    onOwnIdLogin(authToken)
                }
                onError { cause: OwnIdException ->
                    onOwnIdError(cause)
                }
            }
        }.also { addCloseable(it) }
    }

    @MainThread
    fun onOwnIdLogin(authToken: String?) {
        gigya.getAccount(true, object : GigyaCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)
        })
    }

    @MainThread
    fun onOwnIdError(error: OwnIdException) {
        if (error is GigyaException) {
            onGigyaError(error.gigyaError)
        } else {
            _uiStateFlow.value = UiState.Error(error.message ?: error.toString())
        }
    }

    @MainThread
    fun clearState() {
        _uiStateFlow.value = null
    }

    @MainThread
    private fun onGigyaLogin(account: GigyaAccount) {
        _uiStateFlow.value = UiState.LoggedIn(
            account.profile?.firstName.orEmpty().ifBlank { "-" },
            account.profile?.email.orEmpty().ifBlank { "-" }
        )
    }

    @MainThread
    private fun onGigyaError(error: GigyaError) {
        val message = (JSONObject(error.data).optJSONArray("validationErrors")?.getJSONObject(0)?.optString("message") ?: "")
            .ifBlank { error.localizedMessage }
        _uiStateFlow.value = UiState.Error(message)
    }
}