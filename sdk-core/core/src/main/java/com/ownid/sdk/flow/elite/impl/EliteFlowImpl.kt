package com.ownid.sdk.flow.elite.impl

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.elite.EliteFlow
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.flow.elite.EliteFlowFailure
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.ops.WebBridgeOperationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime boundary between Elite flow controller semantics and the SDK-managed WebBridge operation.
 *
 * Maintainer contract: one instance accepts one start and publishes one terminal [FlowResult]. Hosted
 * finish/native-action/error/close events settle as success after callbacks return; owner abort and scope cancellation
 * settle as cancellation with the provided or SDK-generated [Reason]. Controller, WebBridge, and shutdown paths must
 * not publish multiple results for the same run.
 */
@InternalOwnIdAPI
internal class EliteFlowImpl(
    private val webBridgeOperation: WebBridgeOperation?,
    private val userJourney: UserJourney?,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val logger: OwnIdLogRouter?
) : EliteFlow, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.EliteFlow")) {

    internal sealed interface State {
        object Created : State
        data class Active(val controller: OperationController<Unit, WebBridgeOperationFailure>, val flowContext: EliteFlowContext) : State
        data class Completed(val result: FlowResult<Unit, EliteFlowFailure>) : State
    }

    private sealed interface Event {
        data class Start(val flowContext: EliteFlowContext) : Event
        class Abort(val reason: Reason) : Event
        class EliteOpResult(val operationID: OperationID, val result: OperationResult<Unit, WebBridgeOperationFailure>) : Event
    }

    private val resultDeferred = CompletableDeferred<FlowResult<Unit, EliteFlowFailure>>()

    private val controller: EliteFlow.Controller = object : EliteFlow.Controller {
        override suspend fun whenSettled(): FlowResult<Unit, EliteFlowFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@EliteFlowImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var journey: UserJourney.Controller? = null
    private val startAccepted = AtomicBoolean(false)
    private val activeControllerRef = AtomicReference<OperationController<Unit, WebBridgeOperationFailure>?>()

    internal val state: StateFlow<State> = eventChannel.consumeAsFlow()
        .onEach { logger?.logV(this, "Event", it::class.java.simpleName) }
        .runningFold(State.Created) { current: State, event ->
            if (current is State.Active && event is Event.EliteOpResult && current.controller.operationId != event.operationID) {
                logger?.logI(this, "Event", "Ignoring event for inactive operation: $event")
                return@runningFold current
            }

            try {
                when (event) {
                    is Event.Start -> handleStartEvent(current, event)

                    is Event.Abort -> {
                        when (current) {
                            State.Created -> State.Completed(FlowResult.Canceled(event.reason))

                            is State.Active -> {
                                current.controller.abort(event.reason)
                                State.Completed(FlowResult.Canceled(event.reason))
                            }

                            is State.Completed -> current
                        }
                    }

                    is Event.EliteOpResult ->
                        if (current is State.Active) handleEliteOpResult(event) else current

                }
            } catch (error: CancellationException) {
                val reason = Reason.SystemError("Operation canceled")
                if (current is State.Active) {
                    journey?.completeOperation(
                        current.controller.operationId,
                        errorCode = ErrorCode.Aborted,
                        source = "EliteFlowImpl.reducer.canceled",
                        message = "Canceled with reason: $reason"
                    )
                }
                logger?.logI(this@EliteFlowImpl, "Reducer canceled", "$error", error)
                State.Completed(FlowResult.Canceled(reason))
            } catch (error: Exception) {
                if (current is State.Active) {
                    journey?.completeOperation(
                        current.controller.operationId,
                        errorCode = ErrorCode.Unknown,
                        source = "EliteFlowImpl.reducer.failure",
                        message = "Flow error in Elite"
                    )
                }
                logger?.logW(this@EliteFlowImpl, "Reducer error", "$error", error)
                State.Completed(FlowResult.Failure(EliteFlowFailure.Unexpected(message = "Flow error in Elite", error = error)))
            }
        }
        .catch { error ->
            eventChannel.close(error)
            logger?.logW(this@EliteFlowImpl, "Flow error", "$error", error)
            emit(State.Completed(FlowResult.Failure(EliteFlowFailure.Unexpected(message = "Flow error in Elite", error = error))))
        }
        .onEach { newState ->
            logger?.logV(this, "State", "$newState")
            if (newState is State.Active) {
                activeControllerRef.set(newState.controller)
            }
            if (newState is State.Completed) {
                activeControllerRef.set(null)

                if (resultDeferred.complete(newState.result)) {
                    when (val result = newState.result) {
                        is FlowResult.Success ->
                            journey?.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

                        is FlowResult.Canceled ->
                            journey?.completeFlow(
                                UserJourney.Controller.Outcome.Error(
                                    errorCode = ErrorCode.Aborted,
                                    source = "EliteFlowImpl.completeFlow.canceled",
                                    message = "Flow canceled with reason: ${result.reason}"
                                )
                            )

                        is FlowResult.Failure ->
                            journey?.completeFlow(
                                UserJourney.Controller.Outcome.Error(
                                    errorCode = result.failure.errorCode,
                                    source = "EliteFlowImpl.completeFlow.failure",
                                    message = result.failure.message
                                )
                            )
                    }

                    this@EliteFlowImpl.cancel()
                    eventChannel.close()
                }
            }
        }
        .stateIn(this, SharingStarted.Eagerly, State.Created)

    init {
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                // Instance shutdown maps the active WebBridge run to the same canceled result exposed by controller abort.
                val reason = Reason.SystemError("Operation canceled")
                activeControllerRef.getAndSet(null)?.abort(reason)
                val result = FlowResult.Canceled(reason)
                if (resultDeferred.complete(result)) {
                    journey?.completeFlow(
                        UserJourney.Controller.Outcome.Error(
                            errorCode = ErrorCode.Aborted,
                            source = "EliteFlowImpl.scopeCompletion.canceled",
                            message = "Flow canceled with reason: ${result.reason}"
                        )
                    )
                    eventChannel.close()
                }
            }
        }
    }

    override fun start(context: EliteFlowContext): EliteFlow.Controller {
        if (!startAccepted.compareAndSet(false, true)) return controller

        eventChannel.trySend(Event.Start(context))
        return controller
    }

    private suspend fun handleStartEvent(current: State, event: Event.Start): State = when (current) {
        is State.Created -> {
            journey = userJourney?.startFlow(name = "elite", source = FlowInfo.Source.ELITE)

            if (webBridgeOperation == null) {
                State.Completed(
                    FlowResult.Failure(
                        EliteFlowFailure.OperationFailed(OperationType.WEB_BRIDGE, ErrorCode.IntegrationError, "WebBridgeOperation unavailable")
                    )
                )
            } else {
                val eventWrappers = event.flowContext.eventsWrappers ?: emptyList()

                // Always provide no-op hosted terminal handlers so omitted app callbacks still close and settle natively.
                val wrappers = buildList {
                    addAll(eventWrappers)
                    if (eventWrappers.none { it is WebBridgeOperationEventWrapper.OnFinish }) add(WebBridgeOperationEventWrapper.OnFinish.DEFAULT)
                    if (eventWrappers.none { it is WebBridgeOperationEventWrapper.OnError }) add(WebBridgeOperationEventWrapper.OnError.DEFAULT)
                    if (eventWrappers.none { it is WebBridgeOperationEventWrapper.OnClose }) add(WebBridgeOperationEventWrapper.OnClose.DEFAULT)
                }

                val controller = webBridgeOperation.start(
                    WebBridgeOperation.Params(
                        options = event.flowContext.options,
                        eventWrappers = wrappers,
                        onBaseUrlResolved = { url -> launch { journey?.setReferer(url) } }
                    )
                )
                journey?.startOperation(controller.operationId)
                launchOperationObserver(controller) { operationId, result -> Event.EliteOpResult(operationId, result) }
                State.Active(controller, event.flowContext)
            }
        }

        else -> current
    }

    @Suppress("unused")
    private suspend fun handleEliteOpResult(event: Event.EliteOpResult): State =
        when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "EliteFlowImpl.handleEliteOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )
                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "EliteFlowImpl.handleEliteOpResult.failure",
                    message = result.failure.message
                )
                State.Completed(
                    FlowResult.Failure(
                        EliteFlowFailure.OperationFailed(
                            operationType = event.operationID.type,
                            errorCode = result.failure.errorCode,
                            message = "Elite operation failed: ${result.failure.message}",
                            operationId = event.operationID,
                            operationFailure = result.failure
                        )
                    )
                )
            }

            is OperationResult.Success -> {
                journey?.completeOperation(event.operationID)
                State.Completed(FlowResult.Success(Unit))
            }
        }

    private fun launchOperationObserver(
        controller: OperationController<Unit, WebBridgeOperationFailure>,
        eventFactory: (OperationID, OperationResult<Unit, WebBridgeOperationFailure>) -> Event
    ) {
        launch {
            // Awaiting the WebBridge controller must settle this flow once, even if the awaiter itself is canceled.
            val result = try {
                controller.whenSettled()
            } catch (_: CancellationException) {
                OperationResult.Canceled(Reason.SystemError("Operation canceled"))
            } catch (error: Exception) {
                OperationResult.Failure(WebBridgeOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error))
            }
            eventChannel.trySend(eventFactory(controller.operationId, result))
        }
    }

    internal companion object {
        fun create(resolver: DIContainerResolver): EliteFlow = resolver.runCatchingExceptions {
            EliteFlowImpl(
                webBridgeOperation = getOrNull<WebBridgeOperation>(),
                userJourney = getOrNull<UserJourney>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : EliteFlow {
        private val failure = EliteFlowFailure.Unexpected(
            message = (if (error is MissingDependencyException) error.dependencyName else error.message) ?: "Failed to create Elite flow",
            error = error
        )

        private val failureController: EliteFlow.Controller = object : EliteFlow.Controller {
            override suspend fun whenSettled(): FlowResult<Unit, EliteFlowFailure> = FlowResult.Failure(failure)
            override fun abort(reason: Reason) = Unit
        }

        override fun start(context: EliteFlowContext): EliteFlow.Controller = failureController
    }
}
