package com.ownid.demo.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.demo.DemoAuthIntegration
import com.ownid.demo.ui.LogSection
import com.ownid.sdk.model.AccessToken

@Composable
internal fun CurrentUserScreen(
    loadCurrentUser: suspend () -> Result<DemoAuthIntegration.CurrentUser>,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CurrentUserViewModel = viewModel()
) {
    val currentUserResult by produceState<Result<DemoAuthIntegration.CurrentUser>?>(initialValue = null, loadCurrentUser) {
        value = loadCurrentUser()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val result = currentUserResult) {
            null -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            else -> result.fold(
                onSuccess = { currentDemoUser ->
                    CurrentUserCard(
                        currentUser = currentDemoUser,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )

                    PasskeyEnrollAction(
                        accessToken = currentDemoUser.accessToken,
                        isRunning = viewModel.isRunning,
                        onStart = viewModel::startPasskeyEnrollFlow,
                        modifier = Modifier.fillMaxWidth()
                    )

                    viewModel.enrollmentResultMessage?.let { message ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                },
                onFailure = { error ->
                    Text(
                        text = error.message ?: error.toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            )
        }

        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }

        LogSection(logEntries = viewModel.logEntries, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CurrentUserCard(
    currentUser: DemoAuthIntegration.CurrentUser,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Current User:", style = MaterialTheme.typography.titleLarge)
            Text("Name: ${currentUser.name.orEmpty()}")
            Text("Email: ${currentUser.email}")
        }
    }
}

@Composable
private fun PasskeyEnrollAction(
    accessToken: AccessToken?,
    isRunning: Boolean,
    onStart: (AccessToken?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (accessToken == null) {
            Text(
                text = "Access token required for Passkey Enroll Flow",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = { onStart(accessToken) },
            enabled = accessToken != null && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Passkey Enroll Flow")
        }
    }
}
