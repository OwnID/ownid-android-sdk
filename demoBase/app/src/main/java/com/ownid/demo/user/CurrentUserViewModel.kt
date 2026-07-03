package com.ownid.demo.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.OwnId
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.onUnavailable
import com.ownid.sdk.namespace.availability
import com.ownid.sdk.namespace.start
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class CurrentUserViewModel : ViewModel() {
    private companion object {
        private const val PASSKEY_ENROLL_CONTROLLER_CLOSEABLE_KEY = "com.ownid.demo.passkey_enroll_flow_controller_key"
    }

    var isRunning by mutableStateOf(false)
        private set

    var enrollmentResultMessage by mutableStateOf<String?>(null)
        private set

    private val _logEntries = mutableStateListOf<String>()
    val logEntries: List<String> = _logEntries

    fun startPasskeyEnrollFlow(accessToken: AccessToken?) {
        if (accessToken == null || isRunning) return

        isRunning = true
        enrollmentResultMessage = null
        _logEntries.add("Starting Passkey Enroll Flow...")

        viewModelScope.launch {
            try {
                val passkeyEnroll = OwnId.headless
                    .withContext { authz = Authz.fromToken(accessToken) }
                    .passkeys.enroll

                passkeyEnroll.availability().onUnavailable { message ->
                    enrollmentResultMessage = "Enrollment failed"
                    _logEntries.add("Passkey Enroll Flow is not available: $message")
                    return@launch
                }

                passkeyEnroll
                    .start()
                    .also { controller -> addCloseable(PASSKEY_ENROLL_CONTROLLER_CLOSEABLE_KEY, controller) }
                    .whenSettled()
                    .onSuccess { response ->
                        enrollmentResultMessage = "Enrollment successful"
                        _logEntries.add("Passkey Enroll Flow succeeded: ${response.loginId}")
                    }
                    .onCanceled { reason -> _logEntries.add("Passkey Enroll Flow canceled: $reason") }
                    .onError { error ->
                        enrollmentResultMessage = "Enrollment failed"
                        _logEntries.add("Passkey Enroll Flow failed: $error")
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                enrollmentResultMessage = "Enrollment failed"
                _logEntries.add("Passkey Enroll Flow failed: $error")
            } finally {
                isRunning = false
            }
        }
    }
}
