package com.ownid.sdk.compose.ops.impl

import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.ui.EmailVerificationUi

internal class EmailVerificationUiImpl(
    private val showContainer: (OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) -> Unit,
) : EmailVerificationUi {

    override fun start(
        controller: OperationController<AccessOrProofToken, EmailVerificationOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    ) {
        try {
            showContainer(controller.operationId, onStartError)
        } catch (error: Exception) {
            onStartError(error, "Failed to show container: ${error.message}")
        }
    }
}
