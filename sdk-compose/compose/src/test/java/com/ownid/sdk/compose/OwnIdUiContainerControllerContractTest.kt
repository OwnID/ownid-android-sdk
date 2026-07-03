package com.ownid.sdk.compose

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Covers: UI-090
internal class OwnIdUiContainerControllerContractTest {

    @Test
    fun markClosedWithoutCloseReportsUserCloseToRegisteredHandlers(): Unit = runTest {
        val closeReasons = mutableListOf<Reason?>()
        val controller = OwnIdUiContainerController(closeAction = { error("close action should not run") })
        controller.addClosedHandler { reason -> closeReasons += reason }

        controller.markOpened()
        controller.awaitOpened()
        controller.markClosed()
        controller.awaitClosed()
        controller.markClosed()

        assertThat(closeReasons).hasSize(1)
        val reason = closeReasons.single()
        assertThat(reason).isInstanceOf(Reason.UserClose::class.java)
        assertThat((reason as Reason.UserClose).details).isEqualTo("Operation container closed")
    }

    @Test
    fun closeInvokesDismissActionAtMostOnceAndCompletesAfterMarkClosed(): Unit = runTest {
        var closeCalls = 0
        val closeReasons = mutableListOf<Reason?>()
        val controller = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        controller.addClosedHandler { reason -> closeReasons += reason }

        controller.close()
        controller.close()

        assertThat(closeCalls).isEqualTo(1)
        assertThat(closeReasons).isEmpty()

        controller.markClosed()
        controller.awaitClosed()

        assertThat(closeReasons).hasSize(1)
        assertThat(closeReasons.single()).isInstanceOf(Reason.UserClose::class.java)
    }

    @Test
    fun dismissWithoutAbortClosesContainerWithoutOperationCancellationReason(): Unit = runTest {
        var closeCalls = 0
        val closeReasons = mutableListOf<Reason?>()
        val controller = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        controller.addClosedHandler { reason -> closeReasons += reason }

        controller.requestDismissWithoutAbort()
        controller.requestDismissWithoutAbort()
        controller.markClosed()
        controller.awaitClosed()

        assertThat(closeCalls).isEqualTo(1)
        assertThat(closeReasons).containsExactly(null)
    }

    @Test
    fun disposedClosedHandlerIsNotCalledWhenContainerCloses(): Unit = runTest {
        val closeReasons = mutableListOf<Reason?>()
        val controller = OwnIdUiContainerController(closeAction = { error("close action should not run") })
        val disposable = controller.addClosedHandler { reason -> closeReasons += reason }

        disposable.dispose()
        controller.markClosed()
        controller.awaitClosed()

        assertThat(closeReasons).isEmpty()
    }

    @Test
    fun closedHandlerAddedAfterCloseReceivesTerminalReasonImmediately(): Unit = runTest {
        val closeReasons = mutableListOf<Reason?>()
        val controller = OwnIdUiContainerController(closeAction = { error("close action should not run") })

        controller.markClosed()
        controller.awaitClosed()
        controller.addClosedHandler { reason -> closeReasons += reason }

        assertThat(closeReasons).hasSize(1)
        assertThat(closeReasons.single()).isInstanceOf(Reason.UserClose::class.java)
    }
}
