package com.ownid.sdk.compose.capability

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ownid.sdk.compose.OwnIdUiContainerController
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Internal lifecycle bridge between Compose presentation and one operation controller.
 *
 * Embedded UI aborts when its host is logically removed. During configuration recreation, it keeps the operation alive
 * while disposing observation owned by the old host. Container-backed UI waits for the container to report its final
 * close, because app-owned sheets or dialogs can remove the operation composable before their dismissal lifecycle has
 * finished.
 * Operation settlement requests container dismissal without converting the later close signal into cancellation.
 * Rendering and content callbacks stay outside this helper; this boundary only coordinates cancellation and close
 * signals for one operation controller.
 */
internal class OperationUiLifecycle(
    private val operationController: OperationController<*, *>,
    private val containerController: OwnIdUiContainerController?,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("OwnId.OperationUiLifecycle"))
) {
    internal var shouldRender: Boolean by mutableStateOf(true)
        private set

    private var started: Boolean = false
    private var canceled: Boolean = false
    private var settled: Boolean = false
    private var closeRegistration: DisposableHandle? = null

    internal fun start() {
        if (started) return
        started = true

        closeRegistration = containerController?.addClosedHandler { abortReason ->
            closeRegistration?.dispose()
            closeRegistration = null
            if (abortReason != null) {
                cancel(abortReason)
            }
        }

        coroutineScope.launch {
            operationController.whenSettled()
            settled = true
            shouldRender = false
            closeRegistration?.dispose()
            closeRegistration = null

            if (containerController != null && !containerController.isClosed) {
                containerController.requestDismissWithoutAbort()
            }

            coroutineScope.cancel()
        }
    }

    internal fun onHostDisposed(isChangingConfigurations: Boolean = false) {
        if (containerController != null) return

        if (!isChangingConfigurations) {
            cancel(Reason.UserClose())
        }
        closeRegistration?.dispose()
        closeRegistration = null
        coroutineScope.cancel()
    }

    internal fun cancelMissingInstance() {
        shouldRender = false
        cancel(Reason.SystemError("OwnID SDK instance is no longer available"))
        containerController?.requestDismissWithoutAbort()
    }

    internal fun cancel(reason: Reason) {
        if (canceled || settled) return
        canceled = true
        operationController.abort(reason)
    }
}
