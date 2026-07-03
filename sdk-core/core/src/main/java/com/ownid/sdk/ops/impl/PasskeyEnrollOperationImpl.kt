package com.ownid.sdk.ops.impl

import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.withContext
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

/**
 * Instance-scoped runtime for one [PasskeyEnrollOperation] lifecycle.
 *
 * The namespace entry creates a fresh runtime for each launch. This runtime registers its controller while the operation
 * is active, owns the enroll side effects for that run, and unregisters after the controller settles or cleanup
 * completes. Abort and close requests are operation-owned cancellation inputs; callers must observe settlement through
 * the controller.
 */
@InternalOwnIdAPI
internal class PasskeyEnrollOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val api: PasskeyEnrollApi,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : PasskeyEnrollOperation, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.PasskeyEnrollOperation")) {

    private sealed interface State {
        object Created : State
        object Preparing : State
        data class Completed(val result: OperationResult<Unit, PasskeyEnrollOperationFailure>) : State
    }

    private sealed interface Event {
        class Start(val params: PasskeyEnrollOperation.Params?) : Event
        class Abort(val reason: Reason) : Event
        class ApiResult(val result: OperationResult<Unit, PasskeyEnrollOperationFailure>) : Event
    }

    private val resultDeferred = CompletableDeferred<OperationResult<Unit, PasskeyEnrollOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()

    private val controller: OperationController<Unit, PasskeyEnrollOperationFailure> = object : OperationController<Unit, PasskeyEnrollOperationFailure> {
        override val operationId: OperationID = this@PasskeyEnrollOperationImpl.operationId
        override suspend fun whenSettled(): OperationResult<Unit, PasskeyEnrollOperationFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@PasskeyEnrollOperationImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val stateFlow = MutableStateFlow<State>(State.Created)

    init {
        eventChannel.receiveAsFlow()
            .runningFold(State.Created) { current: State, event -> reduceState(current, event) }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@PasskeyEnrollOperationImpl, "Operation error", "$error", error)
                emit(
                    State.Completed(
                        OperationResult.Failure(PasskeyEnrollOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
                    )
                )
            }
            .onEach { newState ->
                stateFlow.value = newState
                if (newState is State.Completed) {
                    if (resultDeferred.complete(newState.result)) {
                        newState.result
                            .onError { logger?.logD(this, "Completed with error", it.message) }
                            .onCanceled { logger?.logD(this, "Canceled with reason", "$it") }
                        eventChannel.close()
                        operationRegistry.unregister(operationId)
                        this@PasskeyEnrollOperationImpl.cancel()
                    }
                }
            }
            .launchIn(this)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val result = OperationResult.Canceled(Reason.SystemError("Operation canceled"))
                if (resultDeferred.complete(result)) {
                    stateFlow.value = State.Completed(result)
                    eventChannel.close()
                    operationRegistry.unregister(operationId)
                }
            }
        }
    }

    override fun start(params: PasskeyEnrollOperation.Params?): OperationController<Unit, PasskeyEnrollOperationFailure> {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }

        val operationParams = when (params) {
            null -> null
            is PasskeyEnrollOperation.Params -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val accessToken: AccessToken? = operationParams?.accessToken ?: context?.accessToken
        return if (accessToken != null && operationParams?.proofToken != null) {
            Availability.Available()
        } else {
            Availability.Unavailable("AccessToken and ProofToken required")
        }
    }

    private suspend fun reduceState(current: State, event: Event): State {
        return when (event) {
            is Event.Start -> when (current) {
                is State.Created -> {
                    operationRegistry.register(controller)
                    launch { eventChannel.trySend(Event.ApiResult(executeStart(event.params))) }
                    State.Preparing
                }

                else -> current
            }

            is Event.Abort -> when (current) {
                is State.Created -> State.Completed(OperationResult.Canceled(event.reason))
                is State.Preparing -> State.Completed(OperationResult.Canceled(event.reason))
                is State.Completed -> current
            }

            is Event.ApiResult -> when (current) {
                is State.Created -> current
                is State.Preparing -> State.Completed(event.result)
                is State.Completed -> current
            }
        }
    }

    private suspend fun executeStart(params: PasskeyEnrollOperation.Params?): OperationResult<Unit, PasskeyEnrollOperationFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        if (params?.proofToken == null || accessToken == null) {
            return OperationResult.Failure(
                PasskeyEnrollOperationFailure.Input.MissingTokens(ErrorCode.InvalidArgument, "AccessToken and ProofToken required")
            )
        }

        val apiParams = PasskeyEnrollApi.Params(
            proofToken = params.proofToken,
            accessToken = accessToken,
            traceParent = params.traceParent ?: TraceContext.generateTraceparent()
        )

        return try {
            api.start(apiParams).fold(
                onSuccess = { OperationResult.Success(it) },
                onError = { apiError -> OperationResult.Failure(apiError.toOperationFailure()) }
            )
        } catch (_: CancellationException) {
            OperationResult.Canceled(Reason.SystemError("Passkey enrollment canceled"))
        }
    }

    private fun PasskeyEnrollApiFailure.toOperationFailure(): PasskeyEnrollOperationFailure = when (this) {
        is PasskeyEnrollApiFailure.BadRequest.InvalidArgument,
        is PasskeyEnrollApiFailure.BadRequest.Unknown -> PasskeyEnrollOperationFailure.Input.InvalidRequest(errorCode, message, this)
        is PasskeyEnrollApiFailure.Forbidden -> PasskeyEnrollOperationFailure.Access.Forbidden(errorCode, message, this)
        is PasskeyEnrollApiFailure.UserNotFound -> PasskeyEnrollOperationFailure.Access.UserNotFound(errorCode, message, this)
        is PasskeyEnrollApiFailure.FailedDependency.ProviderFailed -> PasskeyEnrollOperationFailure.Integration.ProviderFailed(errorCode, message, this)
        is PasskeyEnrollApiFailure.FailedDependency.MissingProvider ->
            PasskeyEnrollOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is PasskeyEnrollApiFailure.Unexpected -> PasskeyEnrollOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    internal companion object {

        fun create(resolver: DIContainerResolver): PasskeyEnrollOperation = resolver.runCatchingExceptions {
            PasskeyEnrollOperationImpl(
                operationType = OperationType.PASSKEY_ENROLLMENT,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                api = (this as DIContainer).withContext(scopeName = "PasskeyEnrollOperation") { }.getOrThrow<PasskeyEnrollApi>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(PasskeyEnrollOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    /**
     * Unavailable operation returned when construction cannot provide a usable runtime.
     *
     * Starting this object returns a controller that is already completed with [PasskeyEnrollOperationFailure.Unexpected],
     * does not register in [OperationRegistry], and reports unavailable with the same diagnostic message.
     */
    private class Failed(error: Throwable) : PasskeyEnrollOperation {

        override val operationType: OperationType = OperationType.PASSKEY_ENROLLMENT
        private val operationId: OperationID = operationType.createUID()

        private val failure = PasskeyEnrollOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureController: OperationController<Unit, PasskeyEnrollOperationFailure> =
            object : OperationController<Unit, PasskeyEnrollOperationFailure> {
                override val operationId: OperationID = this@Failed.operationId
                override suspend fun whenSettled(): OperationResult<Unit, PasskeyEnrollOperationFailure> = OperationResult.Failure(failure)
                override fun abort(reason: Reason) = Unit
            }

        private val controller: OperationController<Unit, PasskeyEnrollOperationFailure> = failureController
        override fun start(params: PasskeyEnrollOperation.Params?): OperationController<Unit, PasskeyEnrollOperationFailure> = controller
        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
