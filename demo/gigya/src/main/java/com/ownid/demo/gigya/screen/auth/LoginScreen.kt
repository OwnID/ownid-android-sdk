package com.ownid.demo.gigya.screen.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ownid.demo.TERMS_POLICY
import com.ownid.demo.gigya.R
import com.ownid.sdk.compose.OwnIdAuthLoginButton
import com.ownid.sdk.compose.OwnIdLoginButton
import com.ownid.sdk.compose.ownIdViewModel
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdSocialViewModel

internal enum class ButtonType { Default, Auth }
internal enum class DefaultButtonPosition { Start, END }

@Composable
fun LoginScreen(
    doLoginWithPassword: (String, String) -> Unit,
    onSignInWithGoogle: (OwnIdSocialViewModel.State?) -> Unit,
    onOwnIdLogin: (String?) -> Unit,
    onOwnIdError: (OwnIdException) -> Unit,
    onNavigateBack: () -> Unit
) {

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val buttonType = remember { mutableStateOf(ButtonType.Default) }
        val defaultButtonPosition = remember { mutableStateOf(DefaultButtonPosition.Start) }

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

            LoginScreenSettings(
                buttonType.value,
                { buttonType.value = it },
                defaultButtonPosition.value,
                { defaultButtonPosition.value = it }
            )
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
                    if (defaultButtonPosition.value == DefaultButtonPosition.Start) {
                        OwnIdLoginButton(
                            loginIdProvider = { email.value },
                            modifier = Modifier.height(56.dp),
                            onLogin = onOwnIdLogin,
                            onError = onOwnIdError,
                            styleRes = R.style.OwnIdButton_Start,
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
                    } else {
                        OutlinedTextField(
                            value = password.value,
                            onValueChange = { password.value = it },
                            placeholder = { Text("Password") },
                            modifier = Modifier
                                .weight(1F)
                                .padding(end = 8.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                        )

                        OwnIdLoginButton(
                            loginIdProvider = { email.value },
                            modifier = Modifier.height(56.dp),
                            onLogin = onOwnIdLogin,
                            onError = onOwnIdError,
                            styleRes = R.style.OwnIdButton_End
                        )
                    }
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

        val context = LocalContext.current
        val ownIdSocialViewModel = ownIdViewModel<OwnIdSocialViewModel>()
        SignInWithGoogleButton( // Can be any UI
            onClick = { ownIdSocialViewModel.startSignInWithGoogle(context) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val result = ownIdSocialViewModel.socialResultFlow.collectAsStateWithLifecycle()
        LaunchedEffect(result.value) {
            onSignInWithGoogle(result.value)
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


@Composable
private fun LoginScreenSettings(
    buttonType: ButtonType,
    onButtonTypeChange: (ButtonType) -> Unit,
    defaultButtonPosition: DefaultButtonPosition,
    onDefaultButtonPositionChange: (DefaultButtonPosition) -> Unit
) {
    Box {
        val showButtonTypeMenu = remember { mutableStateOf(false) }
        val showDefaultButtonPositionMenu = remember { mutableStateOf(false) }

        IconButton(onClick = { showButtonTypeMenu.value = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Parameters")
        }

        ButtonTypeMenu(showButtonTypeMenu.value, buttonType, onButtonTypeChange, { showDefaultButtonPositionMenu.value = true }) {
            showButtonTypeMenu.value = false
        }

        DefaultButtonPositionMenu(showDefaultButtonPositionMenu.value, defaultButtonPosition, onDefaultButtonPositionChange) {
            showDefaultButtonPositionMenu.value = false
        }
    }
}

@Composable
private fun ButtonTypeMenu(
    show: Boolean,
    buttonType: ButtonType,
    onButtonTypeChange: (ButtonType) -> Unit,
    onShowDefaultButtonPositionMenu: () -> Unit,
    onDismissRequest: () -> Unit
) {
    DropdownMenu(expanded = show, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(text = "Default button") },
            onClick = {
                onButtonTypeChange.invoke(ButtonType.Default)
                onDismissRequest.invoke()
            },
            leadingIcon = {
                if (buttonType == ButtonType.Default) Icon(imageVector = Icons.Default.Done, contentDescription = null)
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.clickable { onShowDefaultButtonPositionMenu.invoke() }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(text = "Auth button") },
            onClick = {
                onButtonTypeChange.invoke(ButtonType.Auth)
                onDismissRequest.invoke()
            },
            leadingIcon = {
                if (buttonType == ButtonType.Auth) Icon(imageVector = Icons.Default.Done, contentDescription = null)
            },
        )
    }
}


@Composable
private fun DefaultButtonPositionMenu(
    show: Boolean,
    position: DefaultButtonPosition,
    onPositionChange: (DefaultButtonPosition) -> Unit,
    onDismissRequest: () -> Unit
) {
    DropdownMenu(expanded = show, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(text = "Button position: Start") },
            onClick = {
                onPositionChange.invoke(DefaultButtonPosition.Start)
                onDismissRequest.invoke()
            },
            leadingIcon = {
                if (position == DefaultButtonPosition.Start) Icon(imageVector = Icons.Default.Done, contentDescription = null)
            },
        )
        DropdownMenuItem(
            text = { Text(text = "Button position: End") },
            onClick = {
                onPositionChange.invoke(DefaultButtonPosition.END)
                onDismissRequest.invoke()
            },
            leadingIcon = {
                if (position == DefaultButtonPosition.END) Icon(imageVector = Icons.Default.Done, contentDescription = null)
            },
        )
    }
}