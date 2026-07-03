package com.ownid.demo.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ownid.demo.ui.DropdownField
import com.ownid.demo.ui.LogSection
import com.ownid.demo.ui.displayTitle
import com.ownid.demo.ui.keyboardType
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.model.LoginId

@Composable
fun OperationLoginIdCollectScreen(
    viewModel: OperationViewModel,
    modifier: Modifier = Modifier
) {
    val operationUiController = viewModel.loginIdOperationUiController

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = viewModel.screenState.loginId,
            onValueChange = viewModel::onLoginIdChanged,
            label = { Text("Login ID") },
            placeholder = { Text("Email, phone number, or username") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = viewModel.screenState.loginIdType.keyboardType),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DropdownField(
                title = "Login ID Type",
                selectedOption = viewModel.screenState.loginIdType,
                options = listOf(null, LoginId.Type.Email, LoginId.Type.PhoneNumber, LoginId.Type.UserName),
                optionLabel = { option -> option?.displayTitle ?: "Auto" },
                onOptionSelected = viewModel::onLoginIdTypeSelected,
                modifier = Modifier.weight(1f)
            )

            DropdownField(
                title = "UI Mode",
                selectedOption = viewModel.screenState.uiMode,
                options = OperationViewModel.UiMode.entries,
                optionLabel = { option -> option.title },
                onOptionSelected = viewModel::onUiModeSelected,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = viewModel::startLoginIdCollectOperation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Login ID Collect Operation")
        }

        if (viewModel.screenState.uiMode == OperationViewModel.UiMode.Embedded && operationUiController != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                OwnIdOperationUi(operationUiController)
            }
        }

        LogSection(logEntries = viewModel.logEntries)
    }

    if (viewModel.screenState.uiMode == OperationViewModel.UiMode.Dialog && operationUiController != null) {
        DemoOperationDialog(operationUiController)
    }
}
