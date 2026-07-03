package com.ownid.sdk.flow.passkey.impl

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlow
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowContext
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowFailure
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.TokenException
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.namespace.DIResolvable
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.namespace.opsNamespace
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(InternalOwnIdAPI::class)
internal class PasskeyEnrollFlowImpl(
    private val ownIdOperation: OwnIdOperation,
    private val serialization: Serialization,
    private val loginIdValidator: LoginIdValidator,
    private val userJourney: UserJourney?,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val logger: OwnIdLogRouter?
) : PasskeyEnrollFlow, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.PasskeyEnrollFlow")) {

    private sealed interface State {
        object Created : State
        data class Active(
            val controller: OperationController<*, *>,
            val loginId: LoginId,
            val flowContext: PasskeyEnrollFlowContext
        ) : State

        data class Completed(val result: FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>) : State
    }

    private sealed interface Event {
        class Start(val flowContext: PasskeyEnrollFlowContext) : Event
        class Abort(val reason: Reason) : Event

        sealed interface OpResult<R, F : OperationFailure> : Event {
            val operationID: OperationID
            val result: OperationResult<R, F>

            class Attestation(
                override val operationID: OperationID,
                override val result: OperationResult<AttestationResponse, PasskeyAttestationOperationFailure>
            ) : OpResult<AttestationResponse, PasskeyAttestationOperationFailure>

            class Enroll(
                override val operationID: OperationID,
                override val result: OperationResult<Unit, PasskeyEnrollOperationFailure>
            ) : OpResult<Unit, PasskeyEnrollOperationFailure>
        }
    }

    private val resultDeferred = CompletableDeferred<FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>>()
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var journey: UserJourney.Controller? = null
    private val startAccepted = AtomicBoolean(false)
    private val activeControllerRef = AtomicReference<OperationController<*, *>?>(null)
    private val controller: PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> =
        object : PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> {
            override suspend fun whenSettled(): FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> = resultDeferred.await()
            override fun abort(reason: Reason) {
                logger?.logD(this@PasskeyEnrollFlowImpl, "abort", "$reason")
                eventChannel.trySend(Event.Abort(reason))
            }
        }

    init {
        eventChannel.consumeAsFlow()
            .onEach { logger?.logV(this, "Event", it::class.java.simpleName) }
            .runningFold(State.Created) { current: State, event ->
                if (current is State.Active && event is Event.OpResult<*, *> && current.controller.operationId != event.operationID) {
                    logger?.logI(this, "Event", "Ignoring event for inactive operation: $event")
                    return@runningFold current
                }

                try {
                    when (event) {
                        is Event.Start -> handleStartEvent(current, event)

                        is Event.Abort -> {
                            if (current is State.Active) current.controller.abort(event.reason)
                            current as? State.Completed ?: State.Completed(FlowResult.Canceled(event.reason))
                        }

                        is Event.OpResult.Attestation -> if (current is State.Active) handleAttestationOpResult(current, event) else current
                        is Event.OpResult.Enroll -> if (current is State.Active) handleEnrollOpResult(current, event) else current
                    }
                } catch (error: CancellationException) {
                    val reason = Reason.SystemError("Operation canceled")
                    if (current is State.Active) {
                        journey?.completeOperation(
                            current.controller.operationId,
                            errorCode = ErrorCode.Aborted,
                            source = "PasskeyEnrollFlowImpl.reducer.canceled",
                            message = "Canceled with reason: $reason"
                        )
                    }
                    logger?.logI(this@PasskeyEnrollFlowImpl, "Reducer canceled", "$error", error)
                    State.Completed(FlowResult.Canceled(reason))
                } catch (error: Exception) {
                    if (current is State.Active) {
                        journey?.completeOperation(
                            current.controller.operationId,
                            errorCode = ErrorCode.Unknown,
                            source = "PasskeyEnrollFlowImpl.reducer.failure",
                            message = "Flow error in Passkey Enroll"
                        )
                    }
                    logger?.logW(this@PasskeyEnrollFlowImpl, "Reducer error", "$error", error)
                    State.Completed(FlowResult.Failure(PasskeyEnrollFlowFailure.Unexpected(message = "Flow error in Passkey Enroll", error = error)))
                }
            }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@PasskeyEnrollFlowImpl, "Flow error", "$error", error)
                emit(State.Completed(FlowResult.Failure(PasskeyEnrollFlowFailure.Unexpected(message = "Flow error in Passkey Enroll", error = error))))
            }
            .onEach { newState ->
                logger?.logV(this, "State", "$newState")
                if (newState is State.Active) {
                    activeControllerRef.set(newState.controller)
                }
                if (newState is State.Completed) {
                    activeControllerRef.set(null)

                    if (resultDeferred.complete(newState.result)) {
                        when (val flowResult = newState.result) {
                            is FlowResult.Success ->
                                journey?.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

                            is FlowResult.Canceled ->
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = ErrorCode.Aborted,
                                        source = "PasskeyEnrollFlowImpl.completeFlow.canceled",
                                        message = "Canceled with reason: ${flowResult.reason}"
                                    )
                                )

                            is FlowResult.Failure ->
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = flowResult.failure.errorCode,
                                        source = "PasskeyEnrollFlowImpl.completeFlow.failure",
                                        message = flowResult.failure.message
                                    )
                                )
                        }

                        this@PasskeyEnrollFlowImpl.cancel()
                        eventChannel.close()
                    }
                }
            }
            .stateIn(this, SharingStarted.Eagerly, State.Created)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val reason = Reason.SystemError("Operation canceled")
                activeControllerRef.getAndSet(null)?.abort(reason)
                val result = FlowResult.Canceled(reason)
                if (resultDeferred.complete(result)) {
                    journey?.completeFlow(
                        UserJourney.Controller.Outcome.Error(
                            errorCode = ErrorCode.Aborted,
                            source = "PasskeyEnrollFlowImpl.scopeCompletion.canceled",
                            message = "Canceled with reason: ${result.reason}"
                        )
                    )
                    eventChannel.close()
                }
            }
        }
    }

    override fun start(context: PasskeyEnrollFlowContext?): PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> {
        if (!startAccepted.compareAndSet(false, true)) return this@PasskeyEnrollFlowImpl.controller

        val ctx = context?.copy {
            if (traceParent == null) traceParent = TraceContext.generateTraceparent()
        } ?: PasskeyEnrollFlowContext { traceParent = TraceContext.generateTraceparent() }

        eventChannel.trySend(Event.Start(ctx))
        return this@PasskeyEnrollFlowImpl.controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        val flowContext = when (params) {
            null -> null
            is PasskeyEnrollFlowContext -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val resolvedAccessToken = flowContext?.accessToken ?: this.context?.accessToken ?: return Availability.Unavailable("AccessToken is required")

        val proofToken = flowContext?.proofToken
        return if (proofToken == null) {
            val operationParams = PasskeyAttestationOperation.Params(accessToken = resolvedAccessToken)
            ownIdOperation.passkeys.create.availability(operationParams)
        } else {
            val operationParams = PasskeyEnrollOperation.Params(proofToken = proofToken, accessToken = resolvedAccessToken, headless = flowContext.headless)
            ownIdOperation.passkeys.enroll.availability(operationParams)
        }
    }

    private suspend fun handleStartEvent(current: State, event: Event.Start): State {
        return when (current) {
            is State.Created -> {
                journey = userJourney?.startFlow(
                    name = "headless-passkey-enroll",
                    source = FlowInfo.Source.EXPLICIT,
                    traceParent = event.flowContext.traceParent
                )

                val resolvedAccessToken = event.flowContext.accessToken ?: context?.accessToken
                ?: return State.Completed(
                    FlowResult.Failure(PasskeyEnrollFlowFailure.Input.MissingAccessToken(ErrorCode.InvalidArgument, "AccessToken is required"))
                )

                val resolvedLoginId = try {
                    resolvedAccessToken.getLoginId(serialization, loginIdValidator)
                } catch (error: TokenException) {
                    return State.Completed(
                        FlowResult.Failure(
                            PasskeyEnrollFlowFailure.Input.UnresolvedLoginId(
                                errorCode = error.errorCode,
                                message = "LoginId cannot be resolved from access token: ${error.message ?: error.toString()}",
                                error = error
                            )
                        )
                    )
                }

                journey?.setUserInfo(resolvedLoginId)

                val updatedFlowContext = event.flowContext.copy {
                    accessToken = resolvedAccessToken
                }

                if (updatedFlowContext.proofToken == null) {
                    val passkeyAttestationOperation = ownIdOperation.passkeys.create
                    passkeyAttestationOperation.getUnsatisfiedDependencies()?.let { deps ->
                        val message = "PasskeyAttestationOperation unavailable. Missing dependencies: ${deps.joinToString()}"
                        return State.Completed(
                            FlowResult.Failure(PasskeyEnrollFlowFailure.OperationFailed(OperationType.PASSKEY_CREATION, ErrorCode.IntegrationError, message))
                        )
                    }
                    val params = PasskeyAttestationOperation.Params(
                        accessToken = updatedFlowContext.accessToken,
                        traceParent = updatedFlowContext.traceParent
                    )

                    when (val availability = passkeyAttestationOperation.availability(params)) {
                        is Availability.Available -> {
                            val controller = passkeyAttestationOperation.start(params)
                            journey?.startOperation(controller.operationId)
                            launchOperationObserver(
                                controller,
                                { error ->
                                    PasskeyAttestationOperationFailure.Unexpected(
                                        message = error.message ?: "Failed to await operation settlement",
                                        error = error
                                    )
                                }
                            ) { operationId, result -> Event.OpResult.Attestation(operationId, result) }
                            State.Active(controller, resolvedLoginId, updatedFlowContext)
                        }

                        is Availability.Unavailable -> State.Completed(
                            FlowResult.Failure(
                                PasskeyEnrollFlowFailure.OperationFailed(OperationType.PASSKEY_CREATION, ErrorCode.IntegrationError, availability.message)
                            )
                        )
                    }
                } else {
                    startEnroll(updatedFlowContext, resolvedLoginId)
                }
            }

            else -> current
        }
    }

    private suspend fun handleAttestationOpResult(current: State.Active, event: Event.OpResult.Attestation): State = when (val result = event.result) {
        is OperationResult.Canceled -> {
            journey?.completeOperation(
                event.operationID,
                errorCode = ErrorCode.Aborted,
                source = "PasskeyEnrollFlowImpl.handleAttestationOpResult.canceled",
                message = "Canceled with reason: ${result.reason}"
            )
            State.Completed(FlowResult.Canceled(result.reason))
        }

        is OperationResult.Failure -> {
            journey?.completeOperation(
                event.operationID,
                errorCode = result.failure.errorCode,
                source = "PasskeyEnrollFlowImpl.handleAttestationOpResult.failure",
                message = result.failure.message
            )
            State.Completed(
                FlowResult.Failure(
                    PasskeyEnrollFlowFailure.OperationFailed(
                        operationType = event.operationID.type,
                        errorCode = result.failure.errorCode,
                        message = "Passkey attestation failed: ${result.failure.message}",
                        operationId = event.operationID,
                        operationFailure = result.failure
                    )
                )
            )
        }

        is OperationResult.Success -> {
            journey?.completeOperation(event.operationID)
            val updatedFlowContext = current.flowContext.copy { proofToken = result.success.proofToken }
            startEnroll(updatedFlowContext, current.loginId)
        }
    }

    private suspend fun startEnroll(flowContext: PasskeyEnrollFlowContext, loginId: LoginId): State {
        val passkeyEnrollOperation = ownIdOperation.passkeys.enroll
        passkeyEnrollOperation.getUnsatisfiedDependencies()?.let { deps ->
            val message = "PasskeyEnrollOperation unavailable. Missing dependencies: ${deps.joinToString()}"
            return State.Completed(
                FlowResult.Failure(PasskeyEnrollFlowFailure.OperationFailed(OperationType.PASSKEY_ENROLLMENT, ErrorCode.IntegrationError, message))
            )
        }
        val proofToken = flowContext.proofToken ?: return State.Completed(
            FlowResult.Failure(PasskeyEnrollFlowFailure.Unexpected(message = "ProofToken is required before passkey enroll start"))
        )
        val params = PasskeyEnrollOperation.Params(
            proofToken = proofToken,
            accessToken = flowContext.accessToken,
            headless = flowContext.headless,
            traceParent = flowContext.traceParent
        )

        return when (val availability = passkeyEnrollOperation.availability(params)) {
            is Availability.Available -> {
                val controller = passkeyEnrollOperation.start(params)
                journey?.startOperation(controller.operationId)
                launchOperationObserver(
                    controller,
                    { error -> PasskeyEnrollOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                ) { operationId, result -> Event.OpResult.Enroll(operationId, result) }
                State.Active(controller, loginId, flowContext)
            }

            is Availability.Unavailable -> State.Completed(
                FlowResult.Failure(
                    PasskeyEnrollFlowFailure.OperationFailed(OperationType.PASSKEY_ENROLLMENT, ErrorCode.IntegrationError, availability.message)
                )
            )
        }
    }

    private fun <R, F : OperationFailure> launchOperationObserver(
        controller: OperationController<R, F>,
        unexpectedFailure: (Throwable) -> F,
        eventFactory: (OperationID, OperationResult<R, F>) -> Event
    ) {
        launch {
            val result = try {
                controller.whenSettled()
            } catch (_: CancellationException) {
                OperationResult.Canceled(Reason.SystemError("Operation canceled"))
            } catch (error: Exception) {
                OperationResult.Failure(unexpectedFailure(error))
            }
            eventChannel.trySend(eventFactory(controller.operationId, result))
        }
    }

    private fun OperationEntry<*, *, *>.getUnsatisfiedDependencies(): List<String>? = (this as DIResolvable).getUnsatisfiedDependencies()

    private suspend fun handleEnrollOpResult(current: State.Active, event: Event.OpResult.Enroll): State = when (val result = event.result) {
        is OperationResult.Canceled -> {
            journey?.completeOperation(
                event.operationID,
                errorCode = ErrorCode.Aborted,
                source = "PasskeyEnrollFlowImpl.handleEnrollOpResult.canceled",
                message = "Canceled with reason: ${result.reason}"
            )
            State.Completed(FlowResult.Canceled(result.reason))
        }

        is OperationResult.Failure -> {
            journey?.completeOperation(
                event.operationID,
                errorCode = result.failure.errorCode,
                source = "PasskeyEnrollFlowImpl.handleEnrollOpResult.failure",
                message = result.failure.message
            )
            State.Completed(
                FlowResult.Failure(
                    PasskeyEnrollFlowFailure.OperationFailed(
                        operationType = event.operationID.type,
                        errorCode = result.failure.errorCode,
                        message = "Passkey enroll failed: ${result.failure.message}",
                        operationId = event.operationID,
                        operationFailure = result.failure
                    )
                )
            )
        }

        is OperationResult.Success -> {
            journey?.completeOperation(event.operationID)
            State.Completed(FlowResult.Success(PasskeyEnrollFlow.Response(current.loginId)))
        }
    }

    internal companion object {
        fun create(resolver: DIContainerResolver): PasskeyEnrollFlow = resolver.runCatchingExceptions {
            PasskeyEnrollFlowImpl(
                ownIdOperation = (this as DIContainer).opsNamespace.withContext(scopeName = "PasskeyEnrollFlow") { },
                serialization = getOrThrow<Serialization>(),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                userJourney = getOrNull<UserJourney>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : PasskeyEnrollFlow {
        private val failureError = PasskeyEnrollFlowFailure.Unexpected(
            message = error.message ?: "Failed to create PasskeyEnroll flow",
            error = error
        )

        override suspend fun availability(params: Capability.Params?): Availability =
            Availability.Unavailable(failureError.message)

        override fun start(context: PasskeyEnrollFlowContext?): PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> =
            object : PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> {
                override suspend fun whenSettled(): FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure> = FlowResult.Failure(failureError)
                override fun abort(reason: Reason) = Unit
            }
    }
}
