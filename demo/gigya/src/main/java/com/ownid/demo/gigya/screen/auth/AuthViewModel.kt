package com.ownid.demo.gigya.screen.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.GigyaDefinitions
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.interruption.link.ILinkAccountsResolver
import com.gigya.android.sdk.network.GigyaError
import com.ownid.sdk.FlowResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.gigya
import com.ownid.sdk.start
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

        val paramsWithOwnIdData = OwnIdGigya.appendWithOwnIdData(params, result.ownIdData)

        gigya.register(email, password, paramsWithOwnIdData, object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)
        })
    }

    @MainThread
    fun runLoginWithGoogle() {
        gigya.login(GigyaDefinitions.Providers.GOOGLE, mutableMapOf(), object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(error: GigyaError) = onGigyaError(error)

            override fun onConflictingAccounts(response: GigyaApiResponse, resolver: ILinkAccountsResolver) {
                val providers: List<String> = resolver.conflictingAccounts.loginProviders
                val loginId = resolver.conflictingAccounts.loginID

                _uiStateFlow.value = UiState.ConflictingAccount(
                    loginId = loginId,
                    message = "Login via Google.\n\nConflicting Accounts: $loginId\nProviders: ${providers.joinToString()}"
                )
            }
        })
    }

    @MainThread
    fun runOwnIdFlow() {
        viewModelScope.launch {
            val result = OwnId.gigya.start()
            when (result) {
                is FlowResult.OnAccountNotFound -> _uiStateFlow.value = UiState.OnAccountNotFound(result.loginId, result.ownIdData)

                is FlowResult.OnLogin -> {
                    gigya.setSession(result.session)
                    onOwnIdLogin()
                }

                is FlowResult.OnError -> onOwnIdError(result.cause)
                FlowResult.OnClose -> Unit
            }
        }
    }

    @MainThread
    fun onOwnIdLogin() {
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