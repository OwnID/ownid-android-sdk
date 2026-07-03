package com.ownid.sdk.compose

import androidx.annotation.UiThread
import androidx.compose.runtime.Stable
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates one app-owned presentation container for [OwnIdOperationUi].
 *
 * Use this controller when you present OwnID operation UI inside your own Compose sheet, dialog, or overlay.
 *
 * Create a new controller for each presentation cycle. Keep the same controller for that whole cycle, pass it to
 * [OwnIdOperationUi], and report container visibility with [markOpened] and [markClosed]. If you store it in Compose
 * state, key it to the current container instance rather than to the whole screen. Removing [OwnIdOperationUi] from
 * composition is not a container-close signal; call [markClosed] from the sheet, dialog, or overlay lifecycle when that
 * container is fully dismissed.
 *
 * [close] invokes [closeAction], but the container is not considered closed until [markClosed] is called. If the
 * container closes before the operation settles, the operation is canceled as a user close. If the operation settles
 * first, the SDK invokes [closeAction] and the later [markClosed] call only completes presentation cleanup.
 *
 * A controller is single-use. Do not reuse a closed controller or replace the controller passed to [OwnIdOperationUi]
 * while the current sheet, dialog, or overlay is still open. Create a new controller for the next presentation.
 * Call its public lifecycle methods from the UI thread.
 *
 * @param closeAction Starts app-owned dismissal, for example by hiding a sheet or setting a dialog-visible flag to
 *   `false`. The SDK invokes it at most once for this controller. The app remains responsible for calling
 *   [markClosed] when the container is fully dismissed.
 */
@Stable
public class OwnIdUiContainerController(
    private val closeAction: () -> Unit
) {
    private sealed interface CloseRequest {
        object DismissWithoutAbort : CloseRequest
        class AbortOnClose(val reason: Reason) : CloseRequest
    }

    private val opened = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()
    private val closeOnce = AtomicBoolean(false)

    private var closeRequest: CloseRequest? = null
    private val closeHandlers = mutableListOf<(Reason?) -> Unit>()

    internal val isClosed: Boolean get() = closed.isCompleted

    /**
     * Marks the container as fully visible and ready for input.
     *
     * Built-in operation content uses this signal to delay one-time initial focus until the surrounding presentation is
     * ready. Repeated calls during the same cycle are ignored.
     */
    @UiThread
    public fun markOpened() {
        if (closed.isCompleted) return
        opened.complete(Unit)
    }

    /**
     * Marks the container as fully dismissed.
     *
     * This is the terminal presentation lifecycle event. If the operation has not settled yet, the SDK treats the
     * close as a user close. If the operation has already settled, this only finalizes presentation cleanup. Repeated
     * calls are ignored. After this call, discard the controller.
     */
    @UiThread
    public fun markClosed() {
        opened.complete(Unit)
        if (closed.complete(Unit)) {
            val abortReason = closeAbortReason()
            val handlers = closeHandlers.toList()
            closeHandlers.clear()
            handlers.forEach { handler -> handler(abortReason) }
        }
    }

    /**
     * Requests app-owned dismissal by invoking the close action passed to the constructor.
     *
     * Use this when your UI wants to close the container before the operation has settled. The SDK cancels the
     * operation with [Reason.UserClose] after [markClosed]. Repeated calls do not invoke the close action again. This
     * method starts dismissal; it does not replace [markClosed].
     */
    @UiThread
    public fun close() {
        requestClose(CloseRequest.AbortOnClose(Reason.UserClose("Operation container closed")))
    }

    internal fun requestDismissWithoutAbort() {
        requestClose(CloseRequest.DismissWithoutAbort)
    }

    internal suspend fun awaitOpened() {
        opened.await()
    }

    internal suspend fun awaitClosed() {
        closed.await()
    }

    @UiThread
    internal fun addClosedHandler(handler: (Reason?) -> Unit): DisposableHandle {
        if (closed.isCompleted) {
            handler(closeAbortReason())
            return DisposableHandle { }
        }

        closeHandlers += handler
        return DisposableHandle { closeHandlers -= handler }
    }

    private fun closeAbortReason(): Reason? {
        return when (val request = closeRequest) {
            CloseRequest.DismissWithoutAbort -> null
            is CloseRequest.AbortOnClose -> request.reason
            null -> Reason.UserClose("Operation container closed")
        }
    }

    private fun requestClose(request: CloseRequest) {
        if (closed.isCompleted) return
        if (!closeOnce.compareAndSet(false, true)) return
        closeRequest = request
        closeAction()
    }
}
