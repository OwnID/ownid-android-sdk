package com.ownid.demo.custom.screen.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ownid.demo.custom.DemoApp
import com.ownid.demo.custom.IdentityPlatform
import com.ownid.demo.custom.User
import com.ownid.sdk.FlowResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.SessionAdapter
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.start
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class AuthViewModel(private val identityPlatform: IdentityPlatform) : ViewModel() {

    @Immutable
    sealed class UiState {
        data class LoggedIn(val user: User) : UiState()
        data class Error(val error: String) : UiState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel((this[APPLICATION_KEY] as DemoApp).identityPlatform) }
        }
    }

    private val _uiStateFlow: MutableStateFlow<UiState?> = MutableStateFlow(null)
    val uiStateFlow: StateFlow<UiState?> = _uiStateFlow.asStateFlow()

    init {
        if (identityPlatform.currentUser != null) {
            onLogin(identityPlatform.currentUser!!)
        }
    }

    private val defaultCallback: Result<User>.() -> Unit = {
        onFailure { onError(it) }
        onSuccess { onLogin(it) }
    }

    @MainThread
    fun doRegisterWithPassword(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiStateFlow.value = UiState.Error("Please enter all fields")
            return
        }
        identityPlatform.register(name, email, password, defaultCallback)
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
    fun runOwnIdFlow() {
        viewModelScope.launch {
            val result = OwnId.firstInstanceOrThrow<OwnIdInstance>().start(
                object : SessionAdapter<String> {
                    override fun transformOrThrow(session: String): String = JSONObject(session).getString("token")
                }
            )
            when (result) {
                is FlowResult.OnAccountNotFound -> Unit
                is FlowResult.OnLogin -> identityPlatform.getProfile(result.session, defaultCallback)
                is FlowResult.OnError -> onOwnIdError(result.cause)
                FlowResult.OnClose -> Unit
            }
        }
    }

    @MainThread
    fun onOwnIdLogin() {
        onLogin(identityPlatform.currentUser!!)
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