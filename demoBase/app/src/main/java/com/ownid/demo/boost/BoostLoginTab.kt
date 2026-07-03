package com.ownid.demo.boost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.ownid.sdk.compose.widget.OwnIdLoginWidget
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.launch

@Composable
internal fun BoostLoginTab(
    onPasswordLogin: suspend (email: String, password: String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val logEntries = retain { mutableStateListOf<String>() }

    Column(
        modifier
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
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
                    OwnIdLoginWidget(
                        loginId = email,
                        onLogin = { loginResponse: BoostFlow.Response.Login ->
                            email = loginResponse.loginId.id // Update your form
                            logEntries.add("Boost Login Widget -> onLogin: $loginResponse")
                        },
                        onError = { error -> logEntries.add("Boost Login Widget -> onError: $error") },
                        onCancel = { reason: Reason -> logEntries.add("Boost Login Widget -> onCancel: $reason") },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 6.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
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
            }
        }

        LogSection(logEntries = logEntries, modifier = Modifier.padding(16.dp))
    }
}
