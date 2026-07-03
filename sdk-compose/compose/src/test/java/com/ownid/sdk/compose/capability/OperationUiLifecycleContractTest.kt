package com.ownid.sdk.compose.capability

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.compose.OwnIdUiContainerController
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: UI-100
internal class OperationUiLifecycleContractTest {

    @Test
    fun settledOperationRequestsDismissWithoutAbortAndStopsRendering(): Unit = runTest {
        var closeCalls = 0
        val controller = RecordingOperationController()
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        val lifecycle = OperationUiLifecycle(
            operationController = controller,
            containerController = containerController,
            coroutineScope = backgroundScope
        )

        lifecycle.start()
        controller.settled.complete(OperationResult.Success(Unit))
        runCurrent()

        assertThat(lifecycle.shouldRender).isFalse()
        assertThat(closeCalls).isEqualTo(1)

        containerController.markClosed()
        runCurrent()

        assertThat(controller.abortReasons).isEmpty()
    }

    @Test
    fun containerCloseBeforeSettlementAbortsWithCloseReason(): Unit = runTest {
        val controller = RecordingOperationController()
        val containerController = OwnIdUiContainerController(closeAction = {})
        val lifecycle = OperationUiLifecycle(
            operationController = controller,
            containerController = containerController,
            coroutineScope = backgroundScope
        )

        lifecycle.start()
        containerController.close()
        containerController.markClosed()
        runCurrent()

        assertThat(controller.abortReasons).hasSize(1)
        val reason = controller.abortReasons.single()
        assertThat(reason).isInstanceOf(Reason.UserClose::class.java)
        assertThat((reason as Reason.UserClose).details).isEqualTo("Operation container closed")
    }

    @Test
    fun hostDisposeWithoutContainerAbortsAsUserClose(): Unit = runTest {
        val controller = RecordingOperationController()
        val lifecycle = OperationUiLifecycle(
            operationController = controller,
            containerController = null,
            coroutineScope = backgroundScope
        )

        lifecycle.start()
        lifecycle.onHostDisposed()
        runCurrent()

        assertThat(controller.abortReasons).hasSize(1)
        assertThat(controller.abortReasons.single()).isInstanceOf(Reason.UserClose::class.java)
    }

    @Test
    fun missingInstanceCancelsAndDismissesWithoutUserCloseReason(): Unit = runTest {
        var closeCalls = 0
        val controller = RecordingOperationController()
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        val lifecycle = OperationUiLifecycle(
            operationController = controller,
            containerController = containerController,
            coroutineScope = backgroundScope
        )

        lifecycle.cancelMissingInstance()
        runCurrent()

        assertThat(lifecycle.shouldRender).isFalse()
        assertThat(closeCalls).isEqualTo(1)
        assertThat(controller.abortReasons).hasSize(1)
        assertThat(controller.abortReasons.single()).isInstanceOf(Reason.SystemError::class.java)

        val closeReasons = mutableListOf<Reason?>()
        containerController.addClosedHandler { reason -> closeReasons += reason }
        containerController.markClosed()

        assertThat(closeReasons).containsExactly(null)
    }

    private class RecordingOperationController : OperationController<Unit, TestFailure> {
        val settled = CompletableDeferred<OperationResult<Unit, TestFailure>>()
        val abortReasons = mutableListOf<Reason>()

        override val operationId: OperationID = OperationID(OperationType.LOGIN_ID_COLLECT, "operation")

        override suspend fun whenSettled(): OperationResult<Unit, TestFailure> = settled.await()

        override fun abort(reason: Reason) {
            abortReasons += reason
        }
    }

    private object TestFailure : OperationFailure {
        override val errorCode: ErrorCode = ErrorCode.Unknown
        override val message: String = "test failure"
    }
}
