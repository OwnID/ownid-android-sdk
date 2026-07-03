package com.ownid.demo.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ownid.demo.ui.ApiResponseView

@Composable
fun ApiOidcScreen(
    viewModel: ApiViewModel,
    modifier: Modifier = Modifier
) {
    val screenState = viewModel.screenState
    val oidcState = viewModel.oidcState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = screenState.mode == ApiViewModel.Mode.AccessToken,
                onClick = {
                    val checked = screenState.mode != ApiViewModel.Mode.AccessToken
                    viewModel.onModeSelected(if (checked) ApiViewModel.Mode.AccessToken else ApiViewModel.Mode.LoginId)
                },
                label = { Text("Use Access token") },
                enabled = screenState.accessToken != null
            )
        }

        if (oidcState.hasState) {
            Button(onClick = viewModel::resetOidcState, modifier = Modifier.fillMaxWidth()) {
                Text("Clear State")
            }
        } else {
            Button(onClick = viewModel::startOidcChallenge, modifier = Modifier.fillMaxWidth()) {
                Text("Start OIDC Challenge")
            }
        }

        oidcState.challenge?.let { challenge ->
            ApiResponseView(value = challenge.toString())

            Button(onClick = oidcState.authorize, modifier = Modifier.fillMaxWidth()) {
                Text("Authorize with Google")
            }

            FilledTonalButton(onClick = oidcState.cancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }

        oidcState.result?.let { result ->
            ApiResponseView(value = result.toString())
        }

        oidcState.status?.let { status ->
            ApiResponseView(title = "Result", value = status)
        }
    }
}
