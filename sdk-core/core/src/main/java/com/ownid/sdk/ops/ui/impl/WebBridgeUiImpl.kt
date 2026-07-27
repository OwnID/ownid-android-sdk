package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.ui.WebBridgeUi

/**
 * WebBridge UI adapter that requests the Core SDK WebView host Activity.
 *
 * The adapter exposes a no-throw UI startup boundary to the operation. Synchronous failures while requesting the host
 * and asynchronous error results from the host are both reported through `onStartError`, letting the operation settle
 * as a WebBridge UI failure instead of leaking presentation exceptions.
 */
internal class WebBridgeUiImpl(
    private val showContainer: (OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) -> Unit,
) : WebBridgeUi {

    override fun start(
        controller: OperationController<Unit, WebBridgeOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    ) {
        runCatchingExceptions { showContainer(controller.operationId, onStartError) }
            .onFailure { onStartError(it, "Failed to show container: ${it.message}") }
    }
}
