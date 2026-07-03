package com.ownid.sdk.compose.ops.impl

import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.ui.PhoneVerificationUi

internal class PhoneVerificationUiImpl(
    private val showContainer: (OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) -> Unit,
) : PhoneVerificationUi {

    override fun start(
        controller: OperationController<AccessOrProofToken, PhoneVerificationOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    ) {
        try {
            showContainer(controller.operationId, onStartError)
        } catch (error: Exception) {
            onStartError(error, "Failed to show container: ${error.message}")
        }
    }
}
