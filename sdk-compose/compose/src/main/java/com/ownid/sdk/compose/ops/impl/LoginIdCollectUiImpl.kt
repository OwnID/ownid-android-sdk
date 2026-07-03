package com.ownid.sdk.compose.ops.impl

import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.ui.LoginIdCollectUi

internal class LoginIdCollectUiImpl(
    private val showContainer: (OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) -> Unit,
) : LoginIdCollectUi {

    override fun start(
        controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    ) {
        try {
            showContainer(controller.operationId, onStartError)
        } catch (error: Exception) {
            onStartError(error, "Failed to show container: ${error.message}")
        }
    }
}
