package com.ownid.demo.gigya.screen.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
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
import com.ownid.demo.TERMS_POLICY
import com.ownid.sdk.GigyaRegistrationParameters
import com.ownid.sdk.compose.OwnIdRegisterButton
import com.ownid.sdk.compose.ownIdViewModel
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import org.json.JSONObject

@Composable
fun RegistrationScreen(
    doRegisterWithPassword: (String, String, String) -> Unit,
    onOwnIdLogin: () -> Unit,
    onOwnIdError: (OwnIdException) -> Unit,
    onNavigateBack: () -> Unit,
    ownIdRegisterViewModel: OwnIdRegisterViewModel = ownIdViewModel()
) {
    Column(
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        val name = remember { mutableStateOf("") }
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }

        Row {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = "Create new account",
                modifier = Modifier
                    .weight(1F)
                    .align(Alignment.CenterVertically),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(onClick = { }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Parameters")
            }
        }

        OutlinedTextField(
            value = name.value,
            onValueChange = { name.value = it },
            placeholder = { Text("Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
        )

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

        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OwnIdRegisterButton(
                loginId = email.value,
                modifier = Modifier.height(56.dp),
                ownIdRegisterViewModel = ownIdRegisterViewModel,
                onReadyToRegister = { loginId -> if (loginId.isNotBlank()) email.value = loginId },
                onLogin = onOwnIdLogin,
                onError = onOwnIdError
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
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }

        Button(
            onClick = {
                if (ownIdRegisterViewModel.isReadyToRegister) {
                    val params = mutableMapOf<String, Any>()
                    params["profile"] = JSONObject().put("firstName", name.value).toString()
                    ownIdRegisterViewModel.register(email.value, GigyaRegistrationParameters(params))
                } else {
                    doRegisterWithPassword.invoke(name.value, email.value, password.value)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "Create Account")
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