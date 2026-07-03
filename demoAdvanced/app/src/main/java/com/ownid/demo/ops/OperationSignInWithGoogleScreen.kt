package com.ownid.demo.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ownid.demo.ui.LogSection

@Composable
fun OperationSignInWithGoogleScreen(
    viewModel: OperationViewModel,
    modifier: Modifier = Modifier
) {
    val accessToken = viewModel.screenState.accessToken

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (accessToken == null) {
                "Operation will start without an access token."
            } else {
                "Current access token will be passed to the operation context."
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Button(
            onClick = viewModel::startSignInWithGoogleOperation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Sign In with Google Operation")
        }

        LogSection(logEntries = viewModel.logEntries)
    }
}
