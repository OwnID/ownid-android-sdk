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
import com.ownid.demo.ui.LogSection
import com.ownid.sdk.compose.components.OwnIdBoostButtonPosition
import com.ownid.sdk.compose.widget.OwnIdCreatePasskeyWidget
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.ownIdDataFor
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.launch

@Composable
fun BoostCreatePasskeyScreen(
    onRegister: suspend (name: String, email: String, password: String, ownIdData: String?) -> Result<Unit>
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var boostButtonPosition by rememberSaveable { mutableStateOf(OwnIdBoostButtonPosition.Start) }

    var createPasskeyResponse by retain { mutableStateOf<BoostFlow.Response.CreatePasskey?>(null) }

    val isPasswordRequired = createPasskeyResponse?.ownIdDataFor(currentLoginId = email) == null

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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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
                        OwnIdCreatePasskeyWidget(
                            loginId = email,
                            onLogin = { loginResponse: BoostFlow.Response.Login ->
                                email = loginResponse.loginId.id // Update your form

                                logEntries.add("Boost Create Passkey -> onLogin: $loginResponse")
                            },
                            onNewPasskey = { passkeyResponse: BoostFlow.Response.CreatePasskey ->
                                email = passkeyResponse.loginId.id // Update your form

                                createPasskeyResponse = passkeyResponse // Keep passkey response for submit
                                logEntries.add("Boost Create Passkey -> onNewPasskey: $passkeyResponse")
                            },
                            onReset = {
                                createPasskeyResponse = null // Clear passkey response
                                logEntries.add("Boost Create Passkey -> onReset")
                            },
                            onError = { error ->
                                logEntries.add("Boost Create Passkey -> onError: $error")
                            },
                            onCancel = { reason: Reason -> logEntries.add("Boost Create Passkey -> onCancel: $reason") },
                            position = boostButtonPosition,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = 6.dp)
                        )
                    }

                    val passwordField: @Composable RowScope.() -> Unit = {
                        OutlinedTextField(
                            enabled = isPasswordRequired,
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
                val isSubmitDisabled = name.isBlank() || email.isBlank() || (isPasswordRequired && password.length < 6) || isSubmitting
                val scope = rememberCoroutineScope()

                Button(
                    enabled = !isSubmitDisabled,
                    onClick = {
                        scope.launch {
                            isSubmitting = true

                            // Use OwnID data only when it belongs to the current email; otherwise register with password.
                            val ownIdData = createPasskeyResponse?.ownIdDataFor(currentLoginId = email)
                            val resolvedPassword = if (ownIdData == null) password else "SomeRandomLongAndCrypticPassword"

                            // Register user
                            onRegister(name, email, resolvedPassword, ownIdData)
                                .onSuccess { createPasskeyResponse = null }
                                .onFailure { error -> logEntries.add("Register failed: $error") }

                            isSubmitting = false
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                ) {
                    Text("Submit")
                }
            }
        }

        LogSection(logEntries = logEntries, modifier = Modifier.padding(16.dp))
    }
}
