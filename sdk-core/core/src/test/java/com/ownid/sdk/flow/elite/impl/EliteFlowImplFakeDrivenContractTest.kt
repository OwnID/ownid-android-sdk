package com.ownid.sdk.flow.elite.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.flow.RecordingUserJourney
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.flow.elite.EliteFlowFailure
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: FLOW-100, FLOW-110, FLOW-ORCH-030
internal class EliteFlowImplFakeDrivenContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repeatedStartReturnsSameControllerAndStartsWebBridgeOnce(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val operation = RecordingWebBridgeOperation()
        val flow = eliteFlow(operation)

        val firstController = flow.start(EliteFlowContext.Empty)
        val secondController = flow.start(
            EliteFlowContext {
                options {
                    baseUrl = "https://ignored.example"
                }
            }
        )
        runCurrent()

        assertThat(secondController).isSameInstanceAs(firstController)
        assertThat(operation.startParams).hasSize(1)
        assertThat(operation.controllers).hasSize(1)
    }

    @Test
    fun startInstallsDefaultHostedTerminalHandlers(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val operation = RecordingWebBridgeOperation()
        val flow = eliteFlow(operation)

        assertThat(flow.start(EliteFlowContext.Empty)).isNotNull()
        runCurrent()

        assertThat(operation.startParams.single()?.eventWrappers?.map { it.webBridgePluginAction })
            .containsExactly("onFinish", "onError", "onClose")
            .inOrder()
    }

    @Test
    fun webBridgeSuccessRecordsEliteJourneyMetadataRefererOperationAndCompletedOutcome(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val baseUrl = "https://elite.example/login"
        val operation = RecordingWebBridgeOperation(resolvedBaseUrl = baseUrl)
        val userJourney = RecordingUserJourney()
        val flow = eliteFlow(operation, userJourney)
        val context = EliteFlowContext {
            options {
                this.baseUrl = "https://configured.example/page"
            }
        }

        val controller = flow.start(context)
        runCurrent()
        val webBridgeController = operation.controllers.single()

        webBridgeController.complete(OperationResult.Success(Unit))
        runCurrent()
        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat(userJourney.startedFlows)
            .containsExactly(RecordingUserJourney.StartedFlow(name = "elite", source = FlowInfo.Source.ELITE, traceParent = null))
        assertThat(userJourney.referers).containsExactly(baseUrl)
        assertThat(userJourney.startedOperations).containsExactly(webBridgeController.operationId)
        assertThat(userJourney.completedOperations)
            .containsExactly(RecordingUserJourney.CompletedOperation(webBridgeController.operationId, null, null, null))
        assertThat(userJourney.completedOutcomes.single()).isInstanceOf(UserJourney.Controller.Outcome.Completed::class.java)
    }

    @Test
    fun webBridgeSuccessSettlesOnceAndIgnoresLateFailureAndAbort(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val operation = RecordingWebBridgeOperation()
        val flow = eliteFlow(operation)

        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()
        val webBridgeController = operation.controllers.single()
        val lateFailure = WebBridgeOperationFailure.Ui(ErrorCode.ScreensNotReady, "late UI failure")

        webBridgeController.complete(OperationResult.Success(Unit))
        runCurrent()
        val firstResult = controller.whenSettled()

        webBridgeController.complete(OperationResult.Failure(lateFailure))
        controller.abort(Reason.UserClose("late abort"))
        runCurrent()
        val repeatedResult = controller.whenSettled()

        assertThat(firstResult).isInstanceOf(FlowResult.Success::class.java)
        assertThat(repeatedResult).isSameInstanceAs(firstResult)
        assertThat(webBridgeController.abortReasons).isEmpty()
    }

    @Test
    fun webBridgeCancelRecordsEliteJourneyOperationAndAbortedOutcomeWithoutChangingSettlement(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val reason = Reason.UserClose("WebView closed")
        val operation = RecordingWebBridgeOperation()
        val userJourney = RecordingUserJourney()
        val flow = eliteFlow(operation, userJourney)

        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()
        val webBridgeController = operation.controllers.single()

        webBridgeController.complete(OperationResult.Canceled(reason))
        runCurrent()
        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Canceled::class.java)
        assertThat((result as FlowResult.Canceled).reason).isSameInstanceAs(reason)
        assertThat(userJourney.startedOperations).containsExactly(webBridgeController.operationId)
        val operationCompletion = userJourney.completedOperations.single()
        assertThat(operationCompletion.operationId).isEqualTo(webBridgeController.operationId)
        assertThat(operationCompletion.errorCode).isEqualTo(ErrorCode.Aborted)
        assertThat(operationCompletion.source).isEqualTo("EliteFlowImpl.handleEliteOpResult.canceled")
        assertThat(operationCompletion.message).contains("Canceled with reason: $reason")
        val outcome = userJourney.completedOutcomes.single()
        assertThat(outcome).isInstanceOf(UserJourney.Controller.Outcome.Error::class.java)
        outcome as UserJourney.Controller.Outcome.Error
        assertThat(outcome.errorCode).isEqualTo(ErrorCode.Aborted)
        assertThat(outcome.source).isEqualTo("EliteFlowImpl.completeFlow.canceled")
    }

    @Test
    fun webBridgeFailureMapsToEliteOperationFailed(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val failure = WebBridgeOperationFailure.Ui(ErrorCode.ScreensNotReady, "UI could not start")

        val result = settleWith(OperationResult.Failure(failure))

        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val flowFailure = (result as FlowResult.Failure).failure
        assertThat(flowFailure).isInstanceOf(EliteFlowFailure.OperationFailed::class.java)
        flowFailure as EliteFlowFailure.OperationFailed
        assertThat(flowFailure.operationType).isEqualTo(OperationType.WEB_BRIDGE)
        assertThat(flowFailure.operationId).isEqualTo(OperationID(OperationType.WEB_BRIDGE, "web-bridge-1"))
        assertThat(flowFailure.operationFailure).isSameInstanceAs(failure)
        assertThat(flowFailure.errorCode).isEqualTo(ErrorCode.ScreensNotReady)
        assertThat(flowFailure.message).isEqualTo("Elite operation failed: UI could not start")
    }

    @Test
    fun webBridgeFailureRecordsEliteJourneyOperationAndFailedOutcomeWithoutChangingSettlement(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val failure = WebBridgeOperationFailure.Ui(ErrorCode.ScreensNotReady, "UI could not start")
        val operation = RecordingWebBridgeOperation()
        val userJourney = RecordingUserJourney()
        val flow = eliteFlow(operation, userJourney)

        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()
        val webBridgeController = operation.controllers.single()

        webBridgeController.complete(OperationResult.Failure(failure))
        runCurrent()
        val result = controller.whenSettled()

        val flowFailure = (result as FlowResult.Failure).failure as EliteFlowFailure.OperationFailed
        assertThat(flowFailure.operationFailure).isSameInstanceAs(failure)
        assertThat(userJourney.startedOperations).containsExactly(webBridgeController.operationId)
        assertThat(userJourney.completedOperations)
            .containsExactly(
                RecordingUserJourney.CompletedOperation(
                    operationId = webBridgeController.operationId,
                    errorCode = ErrorCode.ScreensNotReady,
                    source = "EliteFlowImpl.handleEliteOpResult.failure",
                    message = "UI could not start"
                )
            )
        val outcome = userJourney.completedOutcomes.single()
        assertThat(outcome).isInstanceOf(UserJourney.Controller.Outcome.Error::class.java)
        outcome as UserJourney.Controller.Outcome.Error
        assertThat(outcome.errorCode).isEqualTo(ErrorCode.ScreensNotReady)
        assertThat(outcome.source).isEqualTo("EliteFlowImpl.completeFlow.failure")
        assertThat(outcome.message).isEqualTo("Elite operation failed: UI could not start")
    }

    @Test
    fun webBridgeCancelMapsToEliteCanceledWithSameReason(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val reason = Reason.UserClose("WebView closed")

        val result = settleWith(OperationResult.Canceled(reason))

        assertThat(result).isInstanceOf(FlowResult.Canceled::class.java)
        assertThat((result as FlowResult.Canceled).reason).isSameInstanceAs(reason)
    }

    @Test
    fun missingWebBridgeOperationFailsBeforeStartingRuntime(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val flow = eliteFlow(webBridgeOperation = null)

        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()
        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val flowFailure = (result as FlowResult.Failure).failure
        assertThat(flowFailure).isInstanceOf(EliteFlowFailure.OperationFailed::class.java)
        flowFailure as EliteFlowFailure.OperationFailed
        assertThat(flowFailure.operationType).isEqualTo(OperationType.WEB_BRIDGE)
        assertThat(flowFailure.operationId).isNull()
        assertThat(flowFailure.operationFailure).isNull()
        assertThat(flowFailure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(flowFailure.message).isEqualTo("WebBridgeOperation unavailable")
    }

    @Test
    fun abortCancelsActiveWebBridgeAndSettlesWithExpectedReason(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val reason = Reason.SystemError("test abort")
        val operation = RecordingWebBridgeOperation()
        val flow = eliteFlow(operation)

        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()
        val webBridgeController = operation.controllers.single()

        controller.abort(reason)
        runCurrent()
        val result = controller.whenSettled()

        webBridgeController.complete(OperationResult.Success(Unit))
        runCurrent()
        val repeatedResult = controller.whenSettled()

        assertThat(webBridgeController.abortReasons).containsExactly(reason)
        assertThat(result).isInstanceOf(FlowResult.Canceled::class.java)
        assertThat((result as FlowResult.Canceled).reason).isSameInstanceAs(reason)
        assertThat(repeatedResult).isSameInstanceAs(result)
    }

    private suspend fun TestScope.settleWith(
        operationResult: OperationResult<Unit, WebBridgeOperationFailure>
    ): FlowResult<Unit, EliteFlowFailure> {
        val operation = RecordingWebBridgeOperation()
        val flow = eliteFlow(operation)
        val controller = flow.start(EliteFlowContext.Empty)
        runCurrent()

        operation.controllers.single().complete(operationResult)
        runCurrent()

        return controller.whenSettled()
    }

    private fun TestScope.eliteFlow(webBridgeOperation: WebBridgeOperation?): EliteFlowImpl =
        eliteFlow(webBridgeOperation, userJourney = null)

    private fun TestScope.eliteFlow(webBridgeOperation: WebBridgeOperation?, userJourney: UserJourney?): EliteFlowImpl =
        EliteFlowImpl(
            webBridgeOperation = webBridgeOperation,
            userJourney = userJourney,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = Job(),
            logger = null
        )

    private class RecordingWebBridgeOperation(
        private val resolvedBaseUrl: String? = null
    ) : WebBridgeOperation {
        override val operationType: OperationType = OperationType.WEB_BRIDGE
        val startParams = mutableListOf<WebBridgeOperation.Params?>()
        val controllers = mutableListOf<FakeWebBridgeController>()

        override suspend fun availability(params: Capability.Params?): Availability = Availability.Available()

        override fun start(params: WebBridgeOperation.Params?): OperationController<Unit, WebBridgeOperationFailure> {
            startParams += params
            resolvedBaseUrl?.let { params?.onBaseUrlResolved?.invoke(it) }
            return FakeWebBridgeController(OperationID(OperationType.WEB_BRIDGE, "web-bridge-${controllers.size + 1}"))
                .also { controllers += it }
        }
    }

    private class FakeWebBridgeController(
        override val operationId: OperationID
    ) : OperationController<Unit, WebBridgeOperationFailure> {
        private val result = CompletableDeferred<OperationResult<Unit, WebBridgeOperationFailure>>()
        val abortReasons = mutableListOf<Reason>()

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = result.await()

        override fun abort(reason: Reason) {
            abortReasons += reason
            result.complete(OperationResult.Canceled(reason))
        }

        fun complete(operationResult: OperationResult<Unit, WebBridgeOperationFailure>) {
            result.complete(operationResult)
        }
    }
}
