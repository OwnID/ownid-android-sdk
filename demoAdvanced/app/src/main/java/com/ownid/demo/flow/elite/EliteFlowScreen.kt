package com.ownid.demo.flow.elite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.demo.ui.LogSection

@Composable
fun EliteFlowScreen(
    viewModel: EliteFlowViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Button(
            onClick = { viewModel.startEliteFlow() },
            enabled = !viewModel.isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Start Elite Flow")
        }

        LogSection(logEntries = viewModel.logEntries, modifier = Modifier.padding(16.dp))
    }

    viewModel.pendingRegistration?.let {
        CompleteRegistrationDialog(
            registration = it,
            onDismiss = viewModel::cancelRegistration,
            onSubmit = viewModel::completeRegistration
        )
    }
}

@Composable
private fun CompleteRegistrationDialog(
    registration: EliteFlowViewModel.PendingRegistration,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var name by rememberSaveable(registration.email) { mutableStateOf("") }
    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete registration") },
        text = {
            Column {
                OutlinedTextField(
                    value = registration.email,
                    onValueChange = {},
                    label = { Text("Email") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotBlank(),
                onClick = { onSubmit(trimmedName) }
            ) {
                Text("Create user")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
