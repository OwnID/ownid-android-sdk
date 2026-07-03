package com.ownid.sdk.ops.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.withContext
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Instance-scoped runtime for one [PasskeyAttestationOperation] lifecycle.
 *
 * The namespace entry creates a fresh runtime for each launch. This runtime registers its controller while the operation
 * can be presented by SDK UI hosts, owns passkey/API side effects for that run, and unregisters after the controller
 * settles or cleanup completes. Abort and close requests are operation-owned cancellation inputs; callers must observe
 * settlement through the controller.
 */
@InternalOwnIdAPI
internal class PasskeyAttestationOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val ui: PasskeyAttestationUi,
    private val api: PasskeyAttestationApi,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : PasskeyAttestationOperation,
    CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.PasskeyAttestationOperation")) {

    private sealed interface State {
        object Created : State
        data class Preparing(val params: PasskeyAttestationOperation.Params) : State
        data class Active(val apiController: PasskeyAttestationApi.Controller) : State
        data class Completed(val result: OperationResult<AttestationResponse, PasskeyAttestationOperationFailure>) : State
    }

    private sealed interface Event {
        class Start(val params: PasskeyAttestationOperation.Params?) : Event
        class Abort(val reason: Reason) : Event

        class ApiResultStart(val apiResult: ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure>) : Event

        sealed interface ApiEvent : Event {
            class Complete(val apiResult: ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure>) : ApiEvent
            class Cancel(val reason: Reason? = null, val systemPasskeyError: Throwable? = null) : ApiEvent
        }

        class Attestation(val attestationResult: Passkey.Result<AttestationResult>) : Event
    }

    private val resultDeferred = CompletableDeferred<OperationResult<AttestationResponse, PasskeyAttestationOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()
    private val cleanupScope = CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.PasskeyAttestationOperation.cleanup"))

    private val controller: OperationController<AttestationResponse, PasskeyAttestationOperationFailure> =
        object : OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
            override val operationId: OperationID = this@PasskeyAttestationOperationImpl.operationId
            override suspend fun whenSettled(): OperationResult<AttestationResponse, PasskeyAttestationOperationFailure> = resultDeferred.await()
            override fun abort(reason: Reason) {
                logger?.logD(this@PasskeyAttestationOperationImpl, "abort", "$reason")
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
                logger?.logW(this@PasskeyAttestationOperationImpl, "Operation error", "$error", error)
                emit(
                    State.Completed(
                        OperationResult.Failure(PasskeyAttestationOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
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
                        this@PasskeyAttestationOperationImpl.cancel()
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

    override fun start(params: PasskeyAttestationOperation.Params?): OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }

        val operationParams = when (params) {
            null -> null
            is PasskeyAttestationOperation.Params -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val accessToken = operationParams?.accessToken ?: context?.accessToken
        val loginId = try {
            operationParams?.loginId ?: context?.loginId(loginIdValidator)
        } catch (error: LoginIdResolutionException) {
            if (accessToken == null) return Availability.Unavailable(error.message)
            null
        }

        return if (loginId != null || accessToken != null) {
            Availability.Available()
        } else {
            Availability.Unavailable("LoginId or AccessToken required")
        }
    }

    private suspend fun reduceState(current: State, event: Event): State {
        return when (event) {
            is Event.Start -> when (current) {
                is State.Created -> {
                    operationRegistry.register(controller)

                    val accessToken = event.params?.accessToken ?: context?.accessToken
                    val loginId = try {
                        event.params?.loginId ?: context?.loginId(loginIdValidator)
                    } catch (error: LoginIdResolutionException) {
                        if (accessToken == null) return State.Completed(OperationResult.Failure(error.toOperationFailure()))
                        null
                    }
                    if (loginId == null && accessToken == null) {
                        return State.Completed(
                            OperationResult.Failure(
                                PasskeyAttestationOperationFailure.Input.MissingLoginIdOrAccessToken(
                                    errorCode = ErrorCode.InvalidArgument,
                                    message = "LoginId or AccessToken required"
                                )
                            )
                        )
                    }
                    val params = PasskeyAttestationOperation.Params(
                        loginId,
                        accessToken = accessToken,
                        isAutoSelectAllowed = event.params?.isAutoSelectAllowed ?: true,
                        traceParent = event.params?.traceParent ?: TraceContext.generateTraceparent()
                    )

                    launch {
                        val newEvent = try {
                            val apiParams = PasskeyAttestationApi.Params(
                                loginId = params.loginId,
                                accessToken = params.accessToken,
                                traceParent = params.traceParent
                            )
                            Event.ApiResultStart(api.start(apiParams))
                        } catch (_: CancellationException) {
                            Event.Abort(Reason.SystemError("Passkey attestation start canceled"))
                        }

                        eventChannel.trySend(newEvent)
                    }

                    State.Preparing(params)
                }

                else -> current
            }

            is Event.Abort -> when (current) {
                is State.Created -> State.Completed(OperationResult.Canceled(event.reason))

                is State.Preparing ->
                    State.Completed(OperationResult.Canceled(event.reason))

                is State.Active -> {
                    cleanupScope.launch {
                        try {
                            current.apiController.cancel(event.reason)
                        } catch (error: CancellationException) {
                            logger?.logI(this@PasskeyAttestationOperationImpl, "Event.Abort", "API cancel canceled: ${error.message}", error)
                        } catch (error: Exception) {
                            logger?.logI(this@PasskeyAttestationOperationImpl, "Event.Abort", "API cancel failed: ${error.message}", error)
                        }
                    }
                    State.Completed(OperationResult.Canceled(event.reason))
                }

                is State.Completed -> current
            }

            is Event.ApiResultStart -> when (current) {
                is State.Created -> unexpectedEventFailure(event, current)

                is State.Preparing -> when (val apiResult = event.apiResult) {
                    is ApiResult.Failure -> State.Completed(OperationResult.Failure(apiResult.failure.toOperationFailure()))

                    is ApiResult.Success -> {
                        val apiController = apiResult.success
                        apiController.attestationOptions.timeout?.let { timeout ->
                            launch {
                                delay(timeout.milliseconds.milliseconds + 2.seconds) // Adding 2 seconds to give chance for system cancellation
                                if (isActive) eventChannel.trySend(Event.Abort(Reason.Timeout()))
                            }
                        }

                        launch {
                            val newEvent = try {
                                Event.Attestation(ui.createCredential(apiController.attestationOptions, current.params.isAutoSelectAllowed))
                            } catch (_: CancellationException) {
                                Event.Attestation(Passkey.Result.Canceled(Reason.SystemError("Passkey attestation canceled")))
                            } catch (error: Exception) {
                                Event.Attestation(Passkey.Result.Fail(error))
                            }

                            eventChannel.trySend(newEvent)
                        }

                        State.Active(apiController)
                    }
                }

                is State.Active -> unexpectedEventFailure(event, current)

                is State.Completed -> current
            }

            is Event.ApiEvent -> when (current) {
                is State.Created -> unexpectedEventFailure(event, current)

                is State.Preparing -> unexpectedEventFailure(event, current)

                is State.Active -> when (event) {
                    is Event.ApiEvent.Complete ->
                        event.apiResult.fold(
                            onSuccess = { State.Completed(OperationResult.Success(it)) },
                            onError = { apiError -> State.Completed(OperationResult.Failure(apiError.toOperationFailure())) }
                        )

                    is Event.ApiEvent.Cancel -> when {
                        event.systemPasskeyError != null -> State.Completed(
                            OperationResult.Failure(
                                PasskeyAttestationOperationFailure.Integration.ProviderFailed(
                                    errorCode = ErrorCode.PasskeyNotCreated,
                                    message = event.systemPasskeyError.toString(),
                                    error = event.systemPasskeyError
                                )
                            )
                        )

                        else -> State.Completed(OperationResult.Canceled(event.reason ?: Reason.Unknown()))
                    }
                }

                is State.Completed -> current
            }

            is Event.Attestation -> when (current) {
                is State.Created -> unexpectedEventFailure(event, current)

                is State.Preparing -> unexpectedEventFailure(event, current)

                is State.Active -> {
                    launch {
                        val newEvent = try {
                            when (event.attestationResult) {
                                is Passkey.Result.Fail -> {
                                    current.apiController.cancel(Reason.SystemError("Passkey attestation failed: ${event.attestationResult.error}"))
                                    Event.ApiEvent.Cancel(systemPasskeyError = event.attestationResult.error)
                                }

                                is Passkey.Result.Canceled -> {
                                    current.apiController.cancel(event.attestationResult.reason)
                                    Event.ApiEvent.Cancel(reason = event.attestationResult.reason)
                                }

                                is Passkey.Result.Success -> Event.ApiEvent.Complete(
                                    apiResult = current.apiController.verify(event.attestationResult.data)
                                )
                            }
                        } catch (_: CancellationException) {
                            Event.Abort(Reason.SystemError("Passkey attestation request canceled"))
                        }
                        eventChannel.trySend(newEvent)
                    }
                    current
                }

                is State.Completed -> current
            }
        }
    }

    private fun unexpectedEventFailure(event: Event, current: State): State = State.Completed(
        OperationResult.Failure(
            PasskeyAttestationOperationFailure.Unexpected(
                message = "${this::class.simpleName}: Unexpected event [${event::class.simpleName}] for state [${current::class.simpleName}]"
            )
        )
    )

    private fun LoginIdResolutionException.toOperationFailure(): PasskeyAttestationOperationFailure = when (this) {
        is LoginIdResolutionException.MissingValidator -> PasskeyAttestationOperationFailure.Integration.MissingProvider(errorCode, message, "LoginIdValidator")
        is LoginIdResolutionException.TypeNotSupported -> PasskeyAttestationOperationFailure.Input.UnsupportedLoginIdType(errorCode, message)
        is LoginIdResolutionException.ValidationFailed -> PasskeyAttestationOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex)
    }

    private fun PasskeyAttestationStartApiFailure.toOperationFailure(): PasskeyAttestationOperationFailure = when (this) {
        is PasskeyAttestationStartApiFailure.BadRequest.InvalidArgument,
        is PasskeyAttestationStartApiFailure.BadRequest.Unknown -> PasskeyAttestationOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is PasskeyAttestationStartApiFailure.BadRequest.InvalidLoginId ->
            PasskeyAttestationOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex, this)

        is PasskeyAttestationStartApiFailure.BadRequest.UnsupportedLoginIdType ->
            PasskeyAttestationOperationFailure.Input.UnsupportedLoginIdType(errorCode, message, this)

        is PasskeyAttestationStartApiFailure.Forbidden -> PasskeyAttestationOperationFailure.Access.Forbidden(errorCode, message, this)
        is PasskeyAttestationStartApiFailure.FailedDependency.ProviderFailed ->
            PasskeyAttestationOperationFailure.Integration.ProviderFailed(errorCode, message, this)

        is PasskeyAttestationStartApiFailure.FailedDependency.MissingProvider ->
            PasskeyAttestationOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is PasskeyAttestationStartApiFailure.MaximumChallengesReached ->
            PasskeyAttestationOperationFailure.Challenge.MaximumChallengesReached(errorCode, message, this)

        is PasskeyAttestationStartApiFailure.Unexpected -> PasskeyAttestationOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun PasskeyAttestationVerifyApiFailure.toOperationFailure(): PasskeyAttestationOperationFailure = when (this) {
        is PasskeyAttestationVerifyApiFailure.BadRequest.InvalidArgument,
        is PasskeyAttestationVerifyApiFailure.BadRequest.Unknown -> PasskeyAttestationOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is PasskeyAttestationVerifyApiFailure.BadRequest.InvalidChallenge ->
            PasskeyAttestationOperationFailure.Challenge.Invalid(errorCode, message, challengeId, this)

        is PasskeyAttestationVerifyApiFailure.BadRequest.MaximumAttemptsReached ->
            PasskeyAttestationOperationFailure.Challenge.MaximumAttemptsReached(errorCode, message, challengeId, this)

        is PasskeyAttestationVerifyApiFailure.Unauthorized -> PasskeyAttestationOperationFailure.Access.Unauthorized(errorCode, message, this)
        is PasskeyAttestationVerifyApiFailure.Forbidden -> PasskeyAttestationOperationFailure.Access.Forbidden(errorCode, message, this)
        is PasskeyAttestationVerifyApiFailure.UserNotFound -> PasskeyAttestationOperationFailure.Access.UserNotFound(errorCode, message, this)
        is PasskeyAttestationVerifyApiFailure.Unexpected -> PasskeyAttestationOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    internal companion object {

        fun create(resolver: DIContainerResolver): PasskeyAttestationOperation = resolver.runCatchingExceptions {
            PasskeyAttestationOperationImpl(
                operationType = OperationType.PASSKEY_CREATION,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                ui = getOrThrow<PasskeyAttestationUi>(),
                api = (this as DIContainer).withContext(scopeName = "PasskeyAttestationOperation") { }.getOrThrow<PasskeyAttestationApi>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(PasskeyAttestationOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    /**
     * Unavailable operation returned when construction cannot provide a usable runtime.
     *
     * Starting this object returns a controller that is already completed with [PasskeyAttestationOperationFailure.Unexpected],
     * does not register in [OperationRegistry], and reports unavailable with the same diagnostic message.
     */
    private class Failed(error: Throwable) : PasskeyAttestationOperation {

        override val operationType: OperationType = OperationType.PASSKEY_CREATION
        private val operationId: OperationID = operationType.createUID()

        private val failure = PasskeyAttestationOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureController: OperationController<AttestationResponse, PasskeyAttestationOperationFailure> =
            object : OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
                override val operationId: OperationID = this@Failed.operationId
                override suspend fun whenSettled(): OperationResult<AttestationResponse, PasskeyAttestationOperationFailure> = OperationResult.Failure(failure)
                override fun abort(reason: Reason) = Unit
            }

        private val controller: OperationController<AttestationResponse, PasskeyAttestationOperationFailure> = failureController
        override fun start(params: PasskeyAttestationOperation.Params?): OperationController<AttestationResponse, PasskeyAttestationOperationFailure> =
            controller

        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
