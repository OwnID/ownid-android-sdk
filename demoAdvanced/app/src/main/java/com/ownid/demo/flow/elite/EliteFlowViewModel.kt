package com.ownid.demo.flow.elite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.demo.DemoAuthIntegrationProvider
import com.ownid.sdk.OwnId
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EliteFlowViewModel : ViewModel() {
    private companion object {
        private const val FLOW_CONTROLLER_CLOSEABLE_KEY: String = "com.ownid.demo.elite_flow_controller_key"
    }

    data class PendingRegistration(val email: String, val ownIdData: String?)

    var pendingRegistration by mutableStateOf<PendingRegistration?>(null)
        private set

    private val _logEntries = mutableStateListOf<String>()
    val logEntries: List<String> = _logEntries

    var isRunning by mutableStateOf(false)
        private set

    fun startEliteFlow() {
        if (isRunning) return

        viewModelScope.launch {
            isRunning = true
            _logEntries.add("Starting Elite Flow...")

            val flowContext = EliteFlowContext {
                events {
                    onNativeAction { loginId, ownIdData, accessToken ->
                        _logEntries.add("Elite Flow: onNativeAction: $loginId, ownIdData=$ownIdData, accessToken=$accessToken")
                        pendingRegistration = PendingRegistration(email = loginId, ownIdData = ownIdData)
                    }
                    onFinish { loginId, authMethod, accessToken ->
                        _logEntries.add("Elite Flow: onFinish: $loginId, authMethod=$authMethod, accessToken=$accessToken")
                    }
                    onError { error ->
                        _logEntries.add("Elite Flow: onError: $error")
                    }
                    onClose {
                        _logEntries.add("Elite Flow: onClose")
                    }
                }
            }

            try {
                OwnId.flows.elite.start(flowContext)
                    .also { controller -> addCloseable(FLOW_CONTROLLER_CLOSEABLE_KEY, controller) }
                    .whenSettled()
                    .onSuccess { _logEntries.add("Elite Flow settled") }
                    .onCanceled { reason -> _logEntries.add("Elite Flow Canceled: $reason") }
                    .onError { error -> _logEntries.add("Elite Flow Failed: $error") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _logEntries.add("Elite Flow failed: $error")
            } finally {
                isRunning = false
            }
        }
    }

    fun cancelRegistration() {
        pendingRegistration = null
        _logEntries.add("Elite Flow: registration canceled")
    }

    fun completeRegistration(name: String) {
        val registration = pendingRegistration ?: return
        pendingRegistration = null

        viewModelScope.launch {
            DemoAuthIntegrationProvider.integration.registerAndSignIn(
                name = name,
                email = registration.email,
                password = "SomeRandomLongAndCrypticPassword",
                ownIdData = registration.ownIdData
            )
                .onSuccess { _logEntries.add("Elite Flow: Registration succeeded") }
                .onFailure { error -> _logEntries.add("Elite Flow: Registration failed: $error") }
        }
    }
}
