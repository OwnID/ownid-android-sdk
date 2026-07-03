package com.ownid.demo.headless

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.demo.ui.LogSection
import com.ownid.sdk.model.UiError
import com.ownid.sdk.model.VerificationChallenge
import kotlinx.coroutines.delay

@Composable
fun HeadlessScreen(
    modifier: Modifier = Modifier,
    viewModel: HeadlessViewModel = viewModel()
) {
    val screenState = viewModel.screenState
    val flowState = screenState.flowState
    val isLoading = flowState is HeadlessViewModel.FlowState.Loading
    val isFlowActive = isLoading || flowState is HeadlessViewModel.FlowState.EmailVerification

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = screenState.email,
                    onValueChange = viewModel::onEmailChanged,
                    enabled = !isFlowActive,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = viewModel::start,
                        enabled = screenState.email.isNotEmpty() && !isFlowActive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Headless Flow")
                    }
                }
            }
        }

        if (flowState is HeadlessViewModel.FlowState.EmailVerification) {
            EmailOtpSection(
                challenge = flowState.challenge,
                resendAvailableAtMs = flowState.resendAvailableAtMs,
                resendCount = flowState.resendCount,
                busy = flowState.busy,
                error = flowState.error,
                onVerify = viewModel::completeEmailVerification,
                onResend = { viewModel.resendEmailVerification() },
                onCancel = { viewModel.cancelEmailVerification() }
            )
        }

        if (flowState is HeadlessViewModel.FlowState.Completed) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Login successful",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        LogSection(logEntries = viewModel.logEntries, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EmailOtpSection(
    challenge: VerificationChallenge,
    resendAvailableAtMs: Long,
    resendCount: Int,
    busy: Boolean,
    error: UiError?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    val otpLength = challenge.methods.otp?.length
    val resendPolicy = challenge.resendPolicy
    var otpCode by rememberSaveable(challenge.challengeId.value) { mutableStateOf("") }
    var debounceUnlocked by remember(resendAvailableAtMs) { mutableStateOf(false) }

    LaunchedEffect(resendAvailableAtMs) {
        debounceUnlocked = false
        val delayMs = resendAvailableAtMs - SystemClock.elapsedRealtime()
        if (delayMs > 0) delay(delayMs)
        debounceUnlocked = true
    }

    val canResend = !busy && resendPolicy.allow && resendCount < resendPolicy.attempts && debounceUnlocked

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = otpCode,
                onValueChange = { value -> otpCode = value.filter(Char::isDigit).let { digits -> otpLength?.let(digits::take) ?: digits } },
                label = { Text("Email OTP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            if (error != null) {
                Text(
                    text = error.localizedMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Button(
                onClick = { onVerify(otpCode) },
                enabled = !busy && otpCode.isNotBlank() && (otpLength == null || otpCode.length == otpLength),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify Email OTP")
            }

            Button(
                onClick = onResend,
                enabled = canResend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resend OTP")
            }

            Button(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Email Verification")
            }
        }
    }
}
