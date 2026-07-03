package com.ownid.sdk.ops.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.withContext
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.SignInWithGoogleOperationFailure
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
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

@InternalOwnIdAPI
internal class SignInWithGoogleOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val ui: SignInWithGoogleUi,
    private val api: OidcApi,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : SignInWithGoogleOperation,
    CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.SignInWithGoogleOperation")) {

    private sealed interface State {
        object Created : State
        data class Preparing(val params: SignInWithGoogleOperation.Params) : State
        data class Active(val apiController: OidcApi.Controller) : State
        data class Completed(val result: OperationResult<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure>) : State
    }

    private sealed interface Event {
        class Start(val params: SignInWithGoogleOperation.Params?) : Event
        class Abort(val reason: Reason) : Event

        class ApiResultStart(val apiResult: ApiResult<OidcApi.Controller, OidcStartApiFailure>) : Event

        sealed interface ApiEvent : Event {
            class Complete(val apiResult: ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure>) : ApiEvent
            class Cancel(val reason: Reason? = null, val systemError: Throwable? = null) : ApiEvent
        }

        class SignIn(val result: SignInWithSocial.Result) : Event

    }

    private val resultDeferred = CompletableDeferred<OperationResult<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()
    private val cleanupScope = CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.SignInWithGoogleOperation.cleanup"))

    private val controller: OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
        object : OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> {
            override val operationId: OperationID = this@SignInWithGoogleOperationImpl.operationId
            override suspend fun whenSettled(): OperationResult<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> = resultDeferred.await()
            override fun abort(reason: Reason) {
                logger?.logD(this@SignInWithGoogleOperationImpl, "abort", "$reason")
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
                logger?.logW(this@SignInWithGoogleOperationImpl, "Operation error", "$error", error)
                emit(
                    State.Completed(
                        OperationResult.Failure(SignInWithGoogleOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
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
                        this@SignInWithGoogleOperationImpl.cancel()
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

    override fun start(params: SignInWithGoogleOperation.Params?): OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }
        if (params != null && params !is SignInWithGoogleOperation.Params) {
            return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }
        return Availability.Available()
    }

    private suspend fun reduceState(current: State, event: Event): State = when (event) {
        is Event.Start -> when (current) {
            is State.Created -> {
                operationRegistry.register(controller)

                val params = SignInWithGoogleOperation.Params(
                    accessToken = event.params?.accessToken ?: context?.accessToken,
                    traceParent = event.params?.traceParent ?: TraceContext.generateTraceparent()
                )

                launch {
                    val newEvent = try {
                        val apiParams = OidcApi.Params(provider = SocialProvider.GOOGLE, accessToken = params.accessToken, traceParent = params.traceParent)
                        Event.ApiResultStart(api.start(apiParams))
                    } catch (_: CancellationException) {
                        Event.Abort(Reason.SystemError("OIDC start canceled"))
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
                        ui.cancel()
                    } catch (error: CancellationException) {
                        logger?.logI(this@SignInWithGoogleOperationImpl, "cancel", "Canceled: ${error.message}", error)
                    } catch (error: Exception) {
                        logger?.logI(this@SignInWithGoogleOperationImpl, "cancel", "Failed: ${error.message}", error)
                    }
                }

                cleanupScope.launch {
                    try {
                        current.apiController.cancel(event.reason)
                    } catch (error: CancellationException) {
                        logger?.logI(this@SignInWithGoogleOperationImpl, "Event.Abort", "API cancel canceled: ${error.message}", error)
                    } catch (error: Exception) {
                        logger?.logI(this@SignInWithGoogleOperationImpl, "Event.Abort", "API cancel failed: ${error.message}", error)
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

                    launch {
                        delay(apiController.challenge.timeout.milliseconds.milliseconds + 2.seconds) // Adding 2 seconds to give chance for system cancellation
                        if (isActive) eventChannel.trySend(Event.Abort(Reason.Timeout()))
                    }

                    launch {
                        val newEvent = try {
                            Event.SignIn(ui.signIn(apiController.challenge.clientId, apiController.challenge.challengeId.value))
                        } catch (_: CancellationException) {
                            Event.SignIn(SignInWithSocial.Result.Canceled(Reason.SystemError("Authorization canceled")))
                        } catch (error: Exception) {
                            Event.SignIn(SignInWithSocial.Result.Fail(error))
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
                    event.systemError != null -> State.Completed(
                        OperationResult.Failure(
                            SignInWithGoogleOperationFailure.Integration.ProviderFailed(
                                errorCode = ErrorCode.OidcFailed,
                                message = event.systemError.toString(),
                                error = event.systemError
                            )
                        )
                    )

                    else -> State.Completed(OperationResult.Canceled(event.reason ?: Reason.Unknown()))
                }
            }

            is State.Completed -> current
        }

        is Event.SignIn -> when (current) {
            is State.Created -> unexpectedEventFailure(event, current)

            is State.Preparing -> unexpectedEventFailure(event, current)

            is State.Active -> {
                launch {
                    val newEvent = when (event.result) {
                        is SignInWithSocial.Result.Fail -> {
                            try {
                                current.apiController.cancel(Reason.SystemError("Authorization failed"))
                            } catch (error: CancellationException) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel canceled after provider failure: ${error.message}",
                                    error
                                )
                            } catch (error: Exception) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel failed after provider failure: ${error.message}",
                                    error
                                )
                            }

                            Event.ApiEvent.Cancel(systemError = event.result.error)
                        }

                        is SignInWithSocial.Result.Canceled -> {
                            try {
                                current.apiController.cancel(event.result.reason)
                            } catch (error: CancellationException) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel canceled after provider cancellation: ${error.message}",
                                    error
                                )
                            } catch (error: Exception) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel failed after provider cancellation: ${error.message}",
                                    error
                                )
                            }

                            Event.ApiEvent.Cancel(reason = event.result.reason)
                        }

                        is SignInWithSocial.Result.Success -> try {
                            Event.ApiEvent.Complete(apiResult = current.apiController.completeWithToken(event.result.idToken))
                        } catch (_: CancellationException) {
                            val reason = Reason.SystemError("Authorization completion canceled")
                            try {
                                current.apiController.cancel(reason)
                            } catch (cancelError: CancellationException) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel canceled after completion cancellation: ${cancelError.message}",
                                    cancelError
                                )
                            } catch (cancelError: Exception) {
                                logger?.logI(
                                    this@SignInWithGoogleOperationImpl,
                                    "Event.Abort",
                                    "API cancel failed after completion cancellation: ${cancelError.message}",
                                    cancelError
                                )
                            }

                            Event.ApiEvent.Cancel(reason = reason)
                        }
                    }
                    eventChannel.trySend(newEvent)
                }
                current
            }

            is State.Completed -> current
        }
    }

    private fun unexpectedEventFailure(event: Event, current: State): State = State.Completed(
        OperationResult.Failure(
            SignInWithGoogleOperationFailure.Unexpected(
                message = "${this::class.simpleName}: Unexpected event [${event::class.simpleName}] for state [${current::class.simpleName}]"
            )
        )
    )

    private fun OidcStartApiFailure.toOperationFailure(): SignInWithGoogleOperationFailure = when (this) {
        is OidcStartApiFailure.BadRequest.InvalidArgument,
        is OidcStartApiFailure.BadRequest.Unknown -> SignInWithGoogleOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is OidcStartApiFailure.Forbidden -> SignInWithGoogleOperationFailure.Access.Forbidden(errorCode, message, this)
        is OidcStartApiFailure.FailedDependency.ProviderFailed -> SignInWithGoogleOperationFailure.Integration.ProviderFailed(errorCode, message, this)
        is OidcStartApiFailure.FailedDependency.MissingProvider ->
            SignInWithGoogleOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is OidcStartApiFailure.MaximumChallengesReached -> SignInWithGoogleOperationFailure.Challenge.MaximumChallengesReached(errorCode, message, this)
        is OidcStartApiFailure.Unexpected -> SignInWithGoogleOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun OidcCompleteApiFailure.toOperationFailure(): SignInWithGoogleOperationFailure = when (this) {
        is OidcCompleteApiFailure.BadRequest.InvalidArgument,
        is OidcCompleteApiFailure.BadRequest.Unknown -> SignInWithGoogleOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is OidcCompleteApiFailure.BadRequest.InvalidChallenge ->
            SignInWithGoogleOperationFailure.Challenge.Invalid(errorCode, message, challengeId, this)

        is OidcCompleteApiFailure.BadRequest.MaximumAttemptsReached ->
            SignInWithGoogleOperationFailure.Challenge.MaximumAttemptsReached(errorCode, message, challengeId, this)

        is OidcCompleteApiFailure.Forbidden -> SignInWithGoogleOperationFailure.Access.Forbidden(errorCode, message, this)
        is OidcCompleteApiFailure.FailedDependency.ProviderFailed -> SignInWithGoogleOperationFailure.Integration.ProviderFailed(errorCode, message, this)
        is OidcCompleteApiFailure.FailedDependency.MissingProvider ->
            SignInWithGoogleOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is OidcCompleteApiFailure.Unexpected -> SignInWithGoogleOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    internal companion object {

        fun create(resolver: DIContainerResolver): SignInWithGoogleOperation = resolver.runCatchingExceptions {
            SignInWithGoogleOperationImpl(
                operationType = OperationType.OIDC_AUTHENTICATION_GOOGLE,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                ui = getOrThrow<SignInWithGoogleUi>(),
                api = (this as DIContainer).withContext(scopeName = "SignInWithGoogleOperation") { }.getOrThrow<OidcApi>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(SignInWithGoogleOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : SignInWithGoogleOperation {

        override val operationType: OperationType = OperationType.OIDC_AUTHENTICATION_GOOGLE
        private val operationId: OperationID = operationType.createUID()

        private val failure = SignInWithGoogleOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureController: OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
            object : OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> {
                override val operationId: OperationID = this@Failed.operationId
                override suspend fun whenSettled(): OperationResult<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
                    OperationResult.Failure(failure)

                override fun abort(reason: Reason) = Unit
            }

        private val controller: OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> = failureController
        override fun start(params: SignInWithGoogleOperation.Params?): OperationController<AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
            controller

        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
