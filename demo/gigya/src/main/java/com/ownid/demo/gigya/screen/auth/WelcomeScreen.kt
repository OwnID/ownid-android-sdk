package com.ownid.demo.gigya.screen.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onNavigateToRegistration: () -> Unit,
    onNavigateToLogin: () -> Unit,
    runLoginWithGoogle: () -> Unit,
    runOwnIdFlow: () -> Unit,
) {
    Column {
        Text(
            text = "Welcome to OwnID Demo app",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )

        Button(
            onClick = onNavigateToRegistration,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(text = "Register", fontSize = 16.sp)
        }

        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(text = "Login", fontSize = 16.sp)
        }

        Button(
            onClick = runLoginWithGoogle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(text = "Continue with Google", fontSize = 16.sp)
        }

        Button(
            onClick = runOwnIdFlow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(text = "Launch flow", fontSize = 16.sp)
        }
    }
}