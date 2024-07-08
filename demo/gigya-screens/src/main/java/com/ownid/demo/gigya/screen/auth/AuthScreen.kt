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
fun AuthScreen(
    onShowScreenSet: (String) -> Unit
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
            onClick = { onShowScreenSet.invoke("Default-RegistrationLogin") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(text = "Launch Screen-Set", fontSize = 16.sp)
        }
    }
}