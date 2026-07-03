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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ownid.demo.ui.ApiResponseView
import com.ownid.demo.ui.displayTitle
import com.ownid.demo.ui.keyboardType
import com.ownid.sdk.model.LoginId

@Composable
fun ApiPasskeyCreateEnrollScreen(
    viewModel: ApiViewModel,
    modifier: Modifier = Modifier
) {
    val screenState = viewModel.screenState
    val passkeyCreateState = viewModel.passkeyCreateState
    val hasAccessToken = screenState.accessToken != null
    val isContextLocked = passkeyCreateState.isActive

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

        if (passkeyCreateState.isActive) {
            Button(onClick = viewModel::resetPasskeyCreateState, modifier = Modifier.fillMaxWidth()) {
                Text("Clear State")
            }
        } else {
            Button(onClick = viewModel::startPasskeyCreateChallenge, modifier = Modifier.fillMaxWidth(), enabled = screenState.canStartOperation) {
                Text("Start Passkey Create Challenge")
            }
        }

        passkeyCreateState.attestationOptions?.let { attestationOptions ->
            ApiResponseView(value = attestationOptions.toString())

            Button(onClick = passkeyCreateState.createPasskey, modifier = Modifier.fillMaxWidth(), enabled = passkeyCreateState.attestationResult == null) {
                Text("Create Passkey")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = passkeyCreateState.cancelPasskey, modifier = Modifier.weight(1f)) {
                    Text("Cancel Passkey")
                }

                FilledTonalButton(
                    onClick = { passkeyCreateState.verifyAttestation(passkeyCreateState.attestationResult!!) },
                    modifier = Modifier.weight(1f),
                    enabled = passkeyCreateState.attestationResult != null && passkeyCreateState.attestationResponse == null
                ) {
                    Text("Verify Attestation")
                }
            }
        }

        passkeyCreateState.attestationResult?.let { attestationResult ->
            ApiResponseView(value = attestationResult.toString())
        }

        passkeyCreateState.attestationResponse?.let { attestationResponse ->
            ApiResponseView(value = attestationResponse.toString())
        }

        passkeyCreateState.status?.let { status ->
            ApiResponseView(title = "Result", value = status)
        }

        if (passkeyCreateState.attestationResponse != null) {
            Button(
                onClick = viewModel::startPasskeyEnrollment,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasAccessToken && passkeyCreateState.enrollResult == null
            ) {
                Text("Enroll Passkey")
            }

            if (!hasAccessToken) {
                Text("Access token required for enrollment")
            }
        }

        passkeyCreateState.enrollResult?.let { result ->
            ApiResponseView(title = "Enroll Result", value = result)
        }
    }
}
