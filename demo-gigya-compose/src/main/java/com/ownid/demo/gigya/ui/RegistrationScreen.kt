package com.ownid.demo.gigya.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownid.demo.gigya.R
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.compose.OwnIdRegisterButton
import com.ownid.sdk.compose.OwnIdRegisterViewModel

@Composable
fun RegistrationScreen(
    onRegistrationClick: (String, String) -> Unit,
    onRegistrationClickWithOwnId: (String, RegistrationParameters?) -> Unit,
) {
    var nameValue by remember { mutableStateOf("") }
    var emailValue by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }

    var registerViaOwnId by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
        OutlinedTextField(
            value = nameValue,
            onValueChange = { nameValue = it },
            placeholder = { Text("Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp)),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
        )

        OutlinedTextField(
            value = emailValue,
            onValueChange = { emailValue = it },
            placeholder = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp)),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            val ownIdRegisterEventState = OwnIdRegisterViewModel.events.observeAsState()
            // Use ownIdRegisterEventState to update your UI if required

            OwnIdRegisterButton(
                loginId = emailValue,
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight(),
                onReadyToRegister = { ownIdEvent ->
                    registerViaOwnId = true
                    if (ownIdEvent.loginId.isNotBlank()) emailValue = ownIdEvent.loginId
                },
                onUndo = { registerViaOwnId = false }
            )

            OutlinedTextField(
                value = passwordValue,
                onValueChange = { passwordValue = it },
                placeholder = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp)),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }

        Button(
            onClick = {
                if (registerViaOwnId) onRegistrationClickWithOwnId.invoke(emailValue, null)
                else onRegistrationClick.invoke(emailValue, passwordValue)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(text = "Create Account", fontSize = 16.sp)
        }

        Text(
            text = stringResource(R.string.terms_and_policy),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}