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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
fun ApiDiscoverLoginScreen(
    viewModel: ApiViewModel,
    modifier: Modifier = Modifier
) {
    val screenState = viewModel.screenState
    val discoverState = viewModel.discoverState
    val loginState = viewModel.loginState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Discover", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = screenState.loginId,
                    onValueChange = viewModel::onLoginIdChanged,
                    label = { Text("Login ID") },
                    placeholder = { Text("Email or phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = screenState.loginIdType.keyboardType),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Login ID Type:")

                    FilterChip(
                        selected = screenState.loginIdType == LoginId.Type.Email,
                        onClick = { viewModel.onLoginIdTypeSelected(LoginId.Type.Email) },
                        label = { Text(LoginId.Type.Email.displayTitle) }
                    )

                    FilterChip(
                        selected = screenState.loginIdType == LoginId.Type.PhoneNumber,
                        onClick = { viewModel.onLoginIdTypeSelected(LoginId.Type.PhoneNumber) },
                        label = { Text(LoginId.Type.PhoneNumber.displayTitle) }
                    )
                }

                if (discoverState.hasState) {
                    Button(onClick = viewModel::resetDiscoverState, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear State")
                    }
                } else {
                    Button(onClick = viewModel::startDiscoverLoginDiscover, modifier = Modifier.fillMaxWidth(), enabled = screenState.loginId.isNotBlank()) {
                        Text("Start Discover")
                    }
                }

                discoverState.discoverResponse?.let { response ->
                    ApiResponseView(value = response.toString())
                }

                discoverState.discoverStatus?.let { status ->
                    ApiResponseView(title = "Discover Error", value = status)
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Login", style = MaterialTheme.typography.titleMedium)

                screenState.accessToken?.let { accessToken ->
                    ApiResponseView(title = "Access Token", value = accessToken.toString())
                }

                if (screenState.accessToken == null) {
                    Text(
                        text = "Access token required for Login",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (loginState.hasState) {
                    Button(onClick = viewModel::resetLoginState, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear State")
                    }
                } else {
                    Button(
                        onClick = viewModel::startDiscoverLoginLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = screenState.accessToken != null
                    ) {
                        Text("Start Login")
                    }
                }

                loginState.loginResponse?.let { response ->
                    ApiResponseView(title = "Login Result", value = response.toString())
                }

                loginState.loginStatus?.let { status ->
                    ApiResponseView(title = "Login Error", value = status)
                }
            }
        }
    }
}
