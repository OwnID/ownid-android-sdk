package com.ownid.demo.flow.boost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.demo.ui.LogSection
import com.ownid.sdk.compose.components.OwnIdBoostButtonPosition
import com.ownid.sdk.compose.widget.OwnIdLoginWidget
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.launch

@Composable
fun BoostLoginScreen(
    onPasswordLogin: suspend (email: String, password: String) -> Result<Unit>
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var boostButtonPosition by rememberSaveable { mutableStateOf(OwnIdBoostButtonPosition.Start) }

    val emailOtpLoginViewModel: EmailOtpLoginViewModel = viewModel()
    val logEntries = retain { mutableStateListOf<String>() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Widget position:")

            FilterChip(
                selected = boostButtonPosition == OwnIdBoostButtonPosition.Start,
                onClick = { boostButtonPosition = OwnIdBoostButtonPosition.Start },
                label = { Text("Start") }
            )

            FilterChip(
                selected = boostButtonPosition == OwnIdBoostButtonPosition.End,
                onClick = { boostButtonPosition = OwnIdBoostButtonPosition.End },
                label = { Text("End") }
            )
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ownIdWidget: @Composable RowScope.() -> Unit = {
                        OwnIdLoginWidget(
                            loginId = email,
                            onLogin = { loginResponse: BoostFlow.Response.Login ->
                                email = loginResponse.loginId.id // Update your form
                                logEntries.add("Boost Login Widget -> onLogin: $loginResponse")
                            },
                            onError = { error ->
                                logEntries.add("Boost Login Widget -> onError: $error")
                            },
                            onCancel = { reason: Reason -> logEntries.add("Boost Login Widget -> onCancel: $reason") },
                            position = boostButtonPosition,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = 6.dp)
                        )
                    }

                    val passwordField: @Composable RowScope.() -> Unit = {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }

                    when (boostButtonPosition) {
                        OwnIdBoostButtonPosition.Start -> {
                            ownIdWidget()
                            passwordField()
                        }

                        OwnIdBoostButtonPosition.End -> {
                            passwordField()
                            ownIdWidget()
                        }
                    }
                }

                var isSubmitting by retain { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Button(
                    enabled = email.isNotBlank() && password.length >= 6 && !isSubmitting,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            onPasswordLogin(email, password)
                                .onFailure { error -> logEntries.add("Password Login -> Error: $error") }
                            isSubmitting = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("Login")
                }

                Button(
                    enabled = email.isNotBlank() && !emailOtpLoginViewModel.isRunning,
                    onClick = {
                        emailOtpLoginViewModel.startLoginFlow(
                            email = email,
                            onLogin = { response ->
                                logEntries.add("Login via email OTP -> onLogin: $response")
                            },
                            onError = { error ->
                                logEntries.add("Login via email OTP -> onError: $error")
                            },
                            onCancel = { reason ->
                                logEntries.add("Login via email OTP -> onCancel: $reason")
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("Login via email OTP")
                }
            }
        }

        LogSection(logEntries = logEntries, modifier = Modifier.padding(16.dp))
    }
}
