package com.ownid.demo.gigya.screen.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.compose.OwnIdLoginButton
import com.ownid.sdk.exception.OwnIdException

@Composable
fun ConflictingAccountScreen(
    conflictMessage: String,
    conflictLoginId: String,
    onOwnIdLogin: (String?) -> Unit,
    onOwnIdError: (OwnIdException) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
            Text(
                text = "Account duplication detected",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = conflictMessage,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )

            OwnIdLoginButton(
                loginIdProvider = { conflictLoginId },
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.CenterHorizontally),
                loginType = OwnIdLoginType.LinkSocialAccount,
                onLogin = onOwnIdLogin,
                onError = onOwnIdError,
            )
        }
    }
}