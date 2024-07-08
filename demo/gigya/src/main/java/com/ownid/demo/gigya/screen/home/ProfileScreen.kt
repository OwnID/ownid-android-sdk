package com.ownid.demo.gigya.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.compose.ownIdViewModel
import com.ownid.sdk.defaultAuthTokenProvider
import com.ownid.sdk.defaultLoginIdProvider
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel

@Composable
fun ProfileScreen(
    name: String?,
    email: String?,
    doLogout: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Welcome, $name !",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Name: $name",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = "Email: $email",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Button(
            onClick = doLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "Log Out")
        }

        val context = LocalContext.current
        val ownIdEnrollmentViewModel = ownIdViewModel<OwnIdEnrollmentViewModel>()
        TextButton(
            onClick = {
                ownIdEnrollmentViewModel.enrollCredential(
                    context = context,
                    loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                    authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
                    force = true
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Trigger credential enrollment")
        }

        LaunchedEffect(Unit) {
            ownIdEnrollmentViewModel.enrollCredential(
                context = context,
                loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                authTokenProvider = OwnIdGigya.defaultAuthTokenProvider()
            )
        }
    }
}