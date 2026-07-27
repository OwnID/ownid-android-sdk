package com.ownid.demo.flow.boost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.OwnId
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginAuthOperation
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EmailOtpLoginViewModel : ViewModel() {
    private companion object {
        private const val FLOW_CONTROLLER_KEY: String = "com.ownid.demo.email_otp_login_flow_controller_key"
    }

    var isRunning by mutableStateOf(false)
        private set

    fun startLoginFlow(
        email: String,
        onLogin: (BoostFlow.Response.Login) -> Unit,
        onError: (BoostLoginFlowFailure) -> Unit,
        onCancel: (Reason) -> Unit
    ) {
        if (isRunning) return

        viewModelScope.launch {
            isRunning = true

            try {
                val flowContext = BoostFlowContext {
                    loginId(email.trim(), LoginId.Type.Email)
                    allowedAuthOperations = setOf(BoostLoginAuthOperation.EMAIL_VERIFICATION)
                }

                val controller = try {
                    OwnId.flows.boost.login.start(flowContext)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onError(
                        BoostLoginFlowFailure.Unexpected(
                            errorCode = ErrorCode.IntegrationError,
                            message = error.message ?: "Failed to start login flow",
                            error = error
                        )
                    )
                    return@launch
                }

                addCloseable(FLOW_CONTROLLER_KEY, controller)
                controller.whenSettled()
                    .onSuccess(onLogin)
                    .onCanceled(onCancel)
                    .onError(onError)
            } finally {
                isRunning = false
            }
        }
    }
}
