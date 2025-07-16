package com.ownid.demo.custom.screen.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ownid.demo.custom.R
import com.ownid.demo.TERMS_POLICY
import com.ownid.sdk.compose.OwnIdAuthLoginButton
import com.ownid.sdk.compose.OwnIdLoginButton
import com.ownid.sdk.exception.OwnIdException

internal enum class ButtonType { Default, Auth }

@Composable
fun LoginScreen(
    doLoginWithPassword: (String, String) -> Unit,
    onOwnIdLogin: (String?) -> Unit,
    onOwnIdError: (OwnIdException) -> Unit,
    onNavigateBack: () -> Unit
) {

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val buttonType = remember { mutableStateOf(ButtonType.Default) }

        Row {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = "Login",
                modifier = Modifier
                    .weight(1F)
                    .align(Alignment.CenterVertically),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )

            Box {
                val showDropDownMenu = remember { mutableStateOf(false) }

                IconButton(onClick = { showDropDownMenu.value = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Parameters")
                }

                DropdownMenu(
                    expanded = showDropDownMenu.value,
                    onDismissRequest = { showDropDownMenu.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Default button") },
                        onClick = {
                            buttonType.value = ButtonType.Default
                            showDropDownMenu.value = false
                        },
                        trailingIcon = {
                            if (buttonType.value == ButtonType.Default) Icon(imageVector = Icons.Default.Done, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Auth button") },
                        onClick = {
                            buttonType.value = ButtonType.Auth
                            showDropDownMenu.value = false
                        },
                        trailingIcon = {
                            if (buttonType.value == ButtonType.Auth) Icon(imageVector = Icons.Default.Done, contentDescription = null)
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = email.value,
            onValueChange = { email.value = it },
            placeholder = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )

        when (buttonType.value) {
            ButtonType.Default -> {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OwnIdLoginButton(
                        loginIdProvider = { email.value },
                        modifier = Modifier.height(56.dp),
                        onLogin = onOwnIdLogin,
                        onError = onOwnIdError,
                        styleRes = R.style.OwnIdButton_Custom,
                    )

                    OutlinedTextField(
                        value = password.value,
                        onValueChange = { password.value = it },
                        placeholder = { Text("Password") },
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 8.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                    )
                }

                Button(
                    onClick = { doLoginWithPassword.invoke(email.value, password.value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = "Sign in")
                }
            }

            ButtonType.Auth -> {
                OwnIdAuthLoginButton(
                    loginIdProvider = { email.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onLogin = onOwnIdLogin,
                    onError = onOwnIdError,
                )
            }
        }

        Text(
            text = TERMS_POLICY,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
}