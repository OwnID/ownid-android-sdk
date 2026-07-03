package com.ownid.demo.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.RetainedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.compose.OwnIdOperationUiController
import com.ownid.sdk.compose.OwnIdUiContainerController

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DemoOperationDialog(
    operationUiController: OwnIdOperationUiController<*, *>
) {
    // Keep dialog visibility tied to this SDK operation. A new operation gets
    // a new operationId and therefore a fresh dialog/container lifecycle.
    var isVisible by retain(operationUiController.operationId, "isVisible") {
        mutableStateOf(true)
    }

    // The operation UI controller drives the SDK operation; the container controller
    // reports this app-owned dialog lifecycle and handles SDK-requested close actions.
    val dialogContainerController = retain(operationUiController.operationId, "containerController") {
        OwnIdUiContainerController(
            closeAction = {
                // closeAction starts app-owned dismissal. markClosed() below reports
                // final teardown after the dialog leaves the composition.
                isVisible = false
            }
        )
    }

    if (!isVisible) return

    RetainedEffect(dialogContainerController) {
        // markOpened() tells the SDK that this app-owned container is visible
        // and ready for focus/user input.
        dialogContainerController.markOpened()
        // markClosed() is the terminal lifecycle signal for this container. If
        // the operation has not settled, the SDK treats it as user close.
        onRetire { dialogContainerController.markClosed() }
    }

    // User-initiated dialog dismissal goes through the container controller so
    // the SDK can distinguish app close from normal operation settlement.
    BasicAlertDialog(onDismissRequest = dialogContainerController::close) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            // Pass both controllers: operationUiController renders the SDK
            // operation; uiContainerController binds it to this dialog lifecycle.
            OwnIdOperationUi(
                operationUiController = operationUiController,
                uiContainerController = dialogContainerController
            )
        }
    }
}
