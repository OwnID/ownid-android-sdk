package com.ownid.demo.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ownid.demo.ui.ApiResponseView
import com.ownid.demo.ui.displayTitle
import com.ownid.demo.ui.keyboardType
import com.ownid.sdk.model.LoginId

@Composable
fun ApiVerificationEnrollScreen(
    viewModel: ApiViewModel,
    modifier: Modifier = Modifier
) {
    val screenState = viewModel.screenState
    val verificationState = viewModel.verificationState
    val hasAccessToken = screenState.accessToken != null
    val isContextLocked = verificationState.isActive

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mode:")

            ApiViewModel.Mode.entries.forEach { mode ->
                FilterChip(
                    selected = screenState.mode == mode,
                    onClick = { viewModel.onModeSelected(mode) },
                    label = { Text(mode.title) },
                    enabled = !isContextLocked && (mode != ApiViewModel.Mode.AccessToken || hasAccessToken)
                )
            }
        }

        if (screenState.mode == ApiViewModel.Mode.LoginId) {
            OutlinedTextField(
                value = screenState.loginId,
                onValueChange = viewModel::onLoginIdChanged,
                label = { Text("Login ID") },
                placeholder = { Text("Email or phone number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = screenState.loginIdType.keyboardType),
                singleLine = true,
                enabled = !isContextLocked
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Login ID Type:")

            FilterChip(
                selected = screenState.loginIdType == LoginId.Type.Email,
                onClick = { viewModel.onLoginIdTypeSelected(LoginId.Type.Email) },
                label = { Text(LoginId.Type.Email.displayTitle) },
                enabled = !isContextLocked
            )

            FilterChip(
                selected = screenState.loginIdType == LoginId.Type.PhoneNumber,
                onClick = { viewModel.onLoginIdTypeSelected(LoginId.Type.PhoneNumber) },
                label = { Text(LoginId.Type.PhoneNumber.displayTitle) },
                enabled = !isContextLocked
            )
        }

        if (verificationState.isActive) {
            Button(onClick = viewModel::resetVerificationState, modifier = Modifier.fillMaxWidth()) {
                Text("Clear State")
            }
        } else {
            Button(onClick = viewModel::startVerificationChallenge, modifier = Modifier.fillMaxWidth(), enabled = screenState.canStartOperation) {
                Text("Start Verification Challenge")
            }
        }

        verificationState.challenge?.let { challenge ->
            var code by rememberSaveable(challenge.challengeId.value) { mutableStateOf("") }
            val otpLength = challenge.methods.otp?.length ?: 4

            ApiResponseView(value = challenge.toString())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(otpLength) },
                    label = { Text("Code") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                FilledTonalButton(onClick = { verificationState.completeWithCode(code) }, enabled = code.isNotBlank()) {
                    Text("Verify")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = verificationState.resend, modifier = Modifier.weight(1f)) {
                    Text("Resend")
                }

                FilledTonalButton(onClick = verificationState.cancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
        }

        verificationState.proofToken?.let { proofToken ->
            ApiResponseView(title = "Proof Token", value = proofToken.toString())
        }

        verificationState.accessTokenResult?.let { accessTokenResult ->
            ApiResponseView(value = accessTokenResult.toString())
        }

        verificationState.status?.let { status ->
            ApiResponseView(title = "Result", value = status)
        }

        verificationState.proofToken?.let {
            Button(onClick = viewModel::startVerificationEnrollment, modifier = Modifier.fillMaxWidth(), enabled = hasAccessToken) {
                Text("Enroll")
            }

            if (!hasAccessToken) {
                Text("Access token required for enrollment")
            }
        }

        verificationState.enrollResult?.let { result ->
            ApiResponseView(title = "Enroll Result", value = result)
        }
    }
}
