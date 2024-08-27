package com.ownid.demo.custom.screen.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ownid.demo.custom.DemoApp
import com.ownid.demo.custom.IdentityPlatform
import com.ownid.demo.custom.User
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.compose.OwnIdFlowResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdUserError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AuthViewModel(private val identityPlatform: IdentityPlatform) : ViewModel() {

    @Immutable
    sealed class UiState {
        data class LoggedIn(val user: User) : UiState()
        data class Error(val error: String) : UiState()
        data class OnAccountNotFound(val loginId: String, val ownIdData: String?, val authToken: String?) : UiState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel((this[APPLICATION_KEY] as DemoApp).identityPlatform) }
        }
    }

    private val _uiStateFlow: MutableStateFlow<UiState?> = MutableStateFlow(null)
    val uiStateFlow: StateFlow<UiState?> = _uiStateFlow.asStateFlow()

    private val defaultCallback: Result<User>.() -> Unit = {
        onFailure { onError(it) }
        onSuccess { onLogin(it) }
    }

    init {
        if (identityPlatform.currentUser != null) {
            onLogin(identityPlatform.currentUser!!)
        }
    }

    private var ownIdFlowResponse: OwnIdFlowResponse? = null

    @MainThread
    fun onOwnIdResponse(response: OwnIdFlowResponse) {
        when (response.payload.type) {
            OwnIdPayload.Type.Registration -> ownIdFlowResponse = response
            OwnIdPayload.Type.Login -> doLoginWithOwnId(response)
        }
    }

    @MainThread
    fun doRegistration(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank()) {
            _uiStateFlow.value = UiState.Error("Please enter name and email")
            return
        }

        when {
            ownIdFlowResponse != null -> doRegisterWithOwnId(name, email, ownIdFlowResponse!!.payload.data)
            password.isNotBlank() -> doRegisterWithPassword(name, email, password)
            else -> _uiStateFlow.value = UiState.Error("Please enter password or register with OwnID")
        }

    }

    @MainThread
    fun doRegisterWithPassword(name: String, email: String, password: String) {
        identityPlatform.register(name, email, password) {
            onFailure { onError(it) }
            onSuccess { identityPlatform.getProfile(JSONObject(it).getString("token"), null, defaultCallback) }
        }
    }

    @MainThread
    fun doRegisterWithOwnId(name: String, email: String, ownIdData: String) {
        ownIdFlowResponse = null
        identityPlatform.registerWithOwnId(name, email, ownIdData) {
            onFailure { onError(it) }
            onSuccess { identityPlatform.getProfile(JSONObject(it).getString("token"), null, defaultCallback) }
        }
    }

    @MainThread
    fun finishRegisterWithOwnId(name: String) {
        val result = _uiStateFlow.value as UiState.OnAccountNotFound
        identityPlatform.registerWithOwnId(name, result.loginId, result.ownIdData!!) {
            onFailure { onError(it) }
            onSuccess { identityPlatform.getProfile(JSONObject(it).getString("token"), result.authToken, defaultCallback) }
        }
    }

    @MainThread
    fun doLoginWithPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiStateFlow.value = UiState.Error("Please enter all fields")
            return
        }

        identityPlatform.login(email, password, defaultCallback)
    }

    @MainThread
    private fun doLoginWithOwnId(response: OwnIdFlowResponse) {
        val token = JSONObject(response.payload.data).getString("token")
        ownIdFlowResponse = null
        identityPlatform.getProfile(token, null, defaultCallback)
    }

    @MainThread
    fun onOwnIdError(error: OwnIdException) {
        _uiStateFlow.value = UiState.Error(error.message ?: error.toString())
    }

    @MainThread
    fun clearState() {
        _uiStateFlow.value = null
    }

    @MainThread
    private fun onLogin(user: User) {
        _uiStateFlow.value = UiState.LoggedIn(user)
    }

    @MainThread
    private fun onError(error: Throwable) {
        val message = when (error) {
            is OwnIdUserError -> "[${error.code}]\n${error.userMessage}"
            else -> error.message ?: error.cause?.message ?: "Unknown error"
        }
        _uiStateFlow.value = UiState.Error(message)
    }
}