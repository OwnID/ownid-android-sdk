package com.ownid.sdk.ops.impl

import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.withContext
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
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

@InternalOwnIdAPI
internal class LoginOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val loginIdValidator: LoginIdValidator,
    private val loginApi: LoginApi,
    private val discoverApi: DiscoverApi,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : LoginOperation, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.LoginOperation")) {

    private sealed interface State {
        object Created : State
        object Preparing : State
        data class Completed(val result: OperationResult<LoginResponse, LoginOperationFailure>) : State
    }

    private sealed interface Event {
        class Start(val params: LoginOperation.Params?) : Event
        class Abort(val reason: Reason) : Event
        class ApiResult(val result: OperationResult<LoginResponse, LoginOperationFailure>) : Event
    }

    private val resultDeferred = CompletableDeferred<OperationResult<LoginResponse, LoginOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()

    private val controller: OperationController<LoginResponse, LoginOperationFailure> = object : OperationController<LoginResponse, LoginOperationFailure> {
        override val operationId: OperationID = this@LoginOperationImpl.operationId
        override suspend fun whenSettled(): OperationResult<LoginResponse, LoginOperationFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@LoginOperationImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val state = MutableStateFlow<State>(State.Created)

    init {
        eventChannel.receiveAsFlow()
            .runningFold(State.Created) { current: State, event -> reduceState(current, event) }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@LoginOperationImpl, "Operation error", "$error", error)
                emit(
                    State.Completed(
                        OperationResult.Failure(LoginOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
                    )
                )
            }
            .onEach { newState ->
                state.value = newState
                if (newState is State.Completed) {
                    if (resultDeferred.complete(newState.result)) {
                        newState.result
                            .onError { logger?.logD(this, "Completed with error", it.message) }
                            .onCanceled { logger?.logD(this, "Canceled with reason", "$it") }
                        eventChannel.close()
                        operationRegistry.unregister(operationId)
                        this@LoginOperationImpl.cancel()
                    }
                }
            }
            .launchIn(this)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val result = OperationResult.Canceled(Reason.SystemError("Operation canceled"))
                if (resultDeferred.complete(result)) {
                    state.value = State.Completed(result)
                    eventChannel.close()
                    operationRegistry.unregister(operationId)
                }
            }
        }
    }

    override fun start(params: LoginOperation.Params?): OperationController<LoginResponse, LoginOperationFailure> {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }

        val operationParams = when (params) {
            null -> null
            is LoginOperation.Params -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val accessToken = operationParams?.accessToken ?: context?.accessToken
        if (accessToken != null) return Availability.Available()

        val loginId = try {
            operationParams?.loginId ?: context?.loginId(loginIdValidator)
        } catch (error: LoginIdResolutionException) {
            return Availability.Unavailable(error.message)
        }

        if (loginId == null) {
            return Availability.Unavailable("AccessToken or LoginId required")
        }

        return try {
            loginIdValidator.validate(loginId)
            Availability.Available()
        } catch (error: LoginIdValidationException) {
            Availability.Unavailable(error.message)
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

    private suspend fun executeStart(params: LoginOperation.Params?): OperationResult<LoginResponse, LoginOperationFailure> {
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val accessToken = params?.accessToken ?: context?.accessToken
        if (accessToken != null) {
            return try {
                loginApi.start(LoginApi.Params(accessToken = accessToken, traceParent = traceParent)).fold(
                    onSuccess = { OperationResult.Success(it) },
                    onError = { apiError -> OperationResult.Failure(apiError.toOperationFailure()) }
                )
            } catch (_: CancellationException) {
                OperationResult.Canceled(Reason.SystemError("Login canceled"))
            }
        }

        val loginId = try {
            params?.loginId ?: context?.loginId(loginIdValidator)
        } catch (error: LoginIdResolutionException) {
            return OperationResult.Failure(error.toOperationFailure())
        }

        if (loginId == null) {
            return OperationResult.Failure(
                LoginOperationFailure.Input.MissingLoginIdOrAccessToken(
                    errorCode = ErrorCode.InvalidArgument,
                    message = "AccessToken or LoginId required"
                )
            )
        }

        try {
            loginIdValidator.validate(loginId)
        } catch (error: LoginIdValidationException) {
            return OperationResult.Failure(error.toOperationFailure())
        }

        return try {
            discoverApi.start(DiscoverApi.Params(loginId = loginId, traceParent = traceParent)).fold(
                onSuccess = { OperationResult.Success(it) },
                onError = { apiError -> OperationResult.Failure(apiError.toOperationFailure()) }
            )
        } catch (_: CancellationException) {
            OperationResult.Canceled(Reason.SystemError("Login discovery canceled"))
        }
    }

    private fun LoginIdResolutionException.toOperationFailure(): LoginOperationFailure = when (this) {
        is LoginIdResolutionException.MissingValidator -> LoginOperationFailure.Integration.MissingProvider(errorCode, message, "LoginIdValidator")
        is LoginIdResolutionException.TypeNotSupported -> LoginOperationFailure.Input.UnsupportedLoginIdType(errorCode, message)
        is LoginIdResolutionException.ValidationFailed -> LoginOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex)
    }

    private fun LoginIdValidationException.toOperationFailure(): LoginOperationFailure = when (this) {
        is LoginIdValidationException.TypeNotSupported -> LoginOperationFailure.Input.UnsupportedLoginIdType(errorCode, message)
        is LoginIdValidationException.ValidationFailed -> LoginOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex)
    }

    private fun LoginApiFailure.toOperationFailure(): LoginOperationFailure = when (this) {
        is LoginApiFailure.BadRequest.InvalidArgument,
        is LoginApiFailure.BadRequest.Unknown -> LoginOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is LoginApiFailure.BadRequest.InvalidLoginId -> LoginOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex, this)
        is LoginApiFailure.BadRequest.UnsupportedLoginIdType -> LoginOperationFailure.Input.UnsupportedLoginIdType(errorCode, message, this)
        is LoginApiFailure.Forbidden -> LoginOperationFailure.Access.Forbidden(errorCode, message, this)
        is LoginApiFailure.FailedDependency.ProviderFailed -> LoginOperationFailure.Integration.ProviderFailed(errorCode, message, this)
        is LoginApiFailure.FailedDependency.MissingProvider -> LoginOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)
        is LoginApiFailure.Unexpected -> LoginOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun DiscoverApiFailure.toOperationFailure(): LoginOperationFailure = when (this) {
        is DiscoverApiFailure.BadRequest.InvalidArgument,
        is DiscoverApiFailure.BadRequest.Unknown -> LoginOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is DiscoverApiFailure.BadRequest.InvalidLoginId -> LoginOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex, this)
        is DiscoverApiFailure.BadRequest.UnsupportedLoginIdType -> LoginOperationFailure.Input.UnsupportedLoginIdType(errorCode, message, this)
        is DiscoverApiFailure.Forbidden -> LoginOperationFailure.Access.Forbidden(errorCode, message, this)
        is DiscoverApiFailure.FailedDependency.ProviderFailed -> LoginOperationFailure.Integration.ProviderFailed(errorCode, message, this)
        is DiscoverApiFailure.FailedDependency.MissingProvider -> LoginOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)
        is DiscoverApiFailure.Unexpected -> LoginOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    internal companion object {

        fun create(resolver: DIContainerResolver): LoginOperation = resolver.runCatchingExceptions {
            val resolverWithContext = (this as DIContainer).withContext(scopeName = "LoginOperation") { }
            LoginOperationImpl(
                operationType = OperationType.SESSION_CREATION,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                loginApi = resolverWithContext.getOrThrow<LoginApi>(),
                discoverApi = resolverWithContext.getOrThrow<DiscoverApi>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(LoginOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : LoginOperation {

        override val operationType: OperationType = OperationType.SESSION_CREATION
        private val operationId: OperationID = operationType.createUID()

        private val failure = LoginOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureController: OperationController<LoginResponse, LoginOperationFailure> =
            object : OperationController<LoginResponse, LoginOperationFailure> {
                override val operationId: OperationID = this@Failed.operationId
                override suspend fun whenSettled(): OperationResult<LoginResponse, LoginOperationFailure> = OperationResult.Failure(failure)
                override fun abort(reason: Reason) = Unit
            }

        private val controller: OperationController<LoginResponse, LoginOperationFailure> = failureController
        override fun start(params: LoginOperation.Params?): OperationController<LoginResponse, LoginOperationFailure> = controller
        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
