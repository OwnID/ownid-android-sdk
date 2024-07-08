package com.ownid.demo.gigya.screen.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.GigyaPluginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.ui.plugin.GigyaPluginEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AuthViewModel : ViewModel() {

    @Immutable
    sealed class UiState {
        data class LoggedIn(val name: String, val email: String) : UiState()
        data class Error(val error: String) : UiState()
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
    fun onShowScreenSet(screensSet: String) {
        gigya.showScreenSet(screensSet, false, false, mutableMapOf(), object : GigyaPluginCallback<GigyaAccount>() {
            override fun onLogin(account: GigyaAccount) = onGigyaLogin(account)
            override fun onError(event: GigyaPluginEvent) = onGigyaError(GigyaError.errorFrom(event.eventMap))
            override fun onCanceled() = onError("Operation canceled")
        })
    }

    @MainThread
    fun clearError() {
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

    @MainThread
    private fun onError(message: String) {
        _uiStateFlow.value = UiState.Error(message)
    }
}