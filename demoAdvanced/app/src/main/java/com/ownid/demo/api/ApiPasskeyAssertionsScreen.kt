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
fun ApiPasskeyAssertionsScreen(
    viewModel: ApiViewModel,
    modifier: Modifier = Modifier
) {
    val screenState = viewModel.screenState
    val passkeyAssertionState = viewModel.passkeyAssertionsState
    val hasAccessToken = screenState.accessToken != null
    val isContextLocked = passkeyAssertionState.isActive

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

        if (passkeyAssertionState.isActive) {
            Button(onClick = viewModel::resetPasskeyAssertionsState, modifier = Modifier.fillMaxWidth()) {
                Text("Clear State")
            }
        } else {
            Button(onClick = viewModel::startPasskeyAssertionChallenge, modifier = Modifier.fillMaxWidth()) {
                Text("Start Passkey Assertion Challenge")
            }
        }

        passkeyAssertionState.assertionOptions?.let { assertionOptions ->
            ApiResponseView(value = assertionOptions.toString())

            Button(onClick = passkeyAssertionState.runAssertion, modifier = Modifier.fillMaxWidth(), enabled = passkeyAssertionState.assertionResult == null) {
                Text("Run Passkey Assertion")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = passkeyAssertionState.cancelAssertion, modifier = Modifier.weight(1f)) {
                    Text("Cancel Assertion")
                }

                FilledTonalButton(
                    onClick = { passkeyAssertionState.verifyAssertion(passkeyAssertionState.assertionResult!!) },
                    modifier = Modifier.weight(1f),
                    enabled = passkeyAssertionState.assertionResult != null && passkeyAssertionState.accessTokenResult == null
                ) {
                    Text("Verify Assertion")
                }
            }
        }

        passkeyAssertionState.assertionResult?.let { assertionResult ->
            ApiResponseView(value = assertionResult.toString())
        }

        passkeyAssertionState.accessTokenResult?.let { accessTokenResult ->
            ApiResponseView(value = accessTokenResult.toString())
        }

        passkeyAssertionState.status?.let { status ->
            ApiResponseView(title = "Result", value = status)
        }
    }
}
