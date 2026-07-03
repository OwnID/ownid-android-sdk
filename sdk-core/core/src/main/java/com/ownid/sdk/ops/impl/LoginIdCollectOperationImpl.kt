package com.ownid.sdk.ops.impl

import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.appendWithType
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.rawLoginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.toUiError
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ui.capability.ErrorStringsParams
import com.ownid.sdk.ui.capability.ErrorStringsProvider
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Instance-scoped runtime for one [LoginIdCollectOperation] lifecycle.
 *
 * The namespace entry creates a fresh runtime for each launch. This runtime registers its controller while the operation
 * can be presented by SDK UI hosts, owns all state and effect callbacks for that run, and unregisters after the
 * controller settles or cleanup completes. Abort and close requests are operation-owned cancellation inputs; callers must
 * observe settlement through the controller.
 */
@InternalOwnIdAPI
internal class LoginIdCollectOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val loginIdConfigurationProvider: LoginIdConfigurationProvider,
    private val loginIdValidator: LoginIdValidator,
    private val ui: LoginIdCollectUi,
    private val errorStringsProvider: ErrorStringsProvider?,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : LoginIdCollectOperation, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.LoginIdCollectOperation")) {

    private sealed interface Event {
        class Start(val params: LoginIdCollectOperation.Params?) : Event
        class OnUiError(val error: Throwable?, val message: String?) : Event
        class Abort(val reason: Reason) : Event

        sealed interface Ui : Event {
            class OnLoginIdChange(val loginIdValue: String) : Ui
            object OnContinue : Ui
            object OnCancel : Ui
        }

        object ErrorStringsUpdated : Event
    }

    private val resultDeferred = CompletableDeferred<OperationResult<LoginId, LoginIdCollectOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()

    private val controller: LoginIdCollectOperationController = object : LoginIdCollectOperationController {
        override val operationId: OperationID = this@LoginIdCollectOperationImpl.operationId
        override val state: StateFlow<LoginIdCollectOperation.State>
            get() = stateFlow

        override suspend fun whenSettled(): OperationResult<LoginId, LoginIdCollectOperationFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@LoginIdCollectOperationImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val stateFlow = MutableStateFlow<LoginIdCollectOperation.State>(LoginIdCollectOperation.State.Created)

    private var errorStrings: ErrorStrings = ErrorStrings.DEFAULT

    init {
        eventChannel.receiveAsFlow()
            .runningFold(LoginIdCollectOperation.State.Created) { current: LoginIdCollectOperation.State, event -> reduceState(current, event) }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@LoginIdCollectOperationImpl, "Operation error", "$error", error)
                emit(
                    LoginIdCollectOperation.State.Completed(
                        OperationResult.Failure(LoginIdCollectOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
                    )
                )
            }
            .onEach { newState ->
                stateFlow.value = newState
                if (newState is LoginIdCollectOperation.State.Completed) {
                    if (resultDeferred.complete(newState.result)) {
                        newState.result
                            .onError { logger?.logD(this, "Completed with error", it.message) }
                            .onCanceled { logger?.logD(this, "Canceled with reason", "$it") }
                        eventChannel.close()
                        operationRegistry.unregister(operationId)
                        this@LoginIdCollectOperationImpl.cancel()
                    }
                }
            }
            .launchIn(this)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val result = OperationResult.Canceled(Reason.SystemError("Operation canceled"))
                if (resultDeferred.complete(result)) {
                    stateFlow.value = LoginIdCollectOperation.State.Completed(result)
                    eventChannel.close()
                    operationRegistry.unregister(operationId)
                }
            }
        }

        errorStringsProvider?.getStrings(ErrorStringsParams())
            ?.onEach { strings ->
                errorStrings = strings ?: ErrorStrings.DEFAULT
                eventChannel.trySend(Event.ErrorStringsUpdated)
            }
            ?.launchIn(this)
    }

    override fun start(params: LoginIdCollectOperation.Params?): LoginIdCollectOperationController {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }

        val operationParams = when (params) {
            null -> null
            is LoginIdCollectOperation.Params -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val collectableLoginIdTypes = loginIdConfigurationProvider.configuration.supportedTypes.filter { it in COLLECTABLE_LOGIN_ID_TYPES }
        if (collectableLoginIdTypes.isEmpty()) {
            return Availability.Unavailable("No supported LoginId.Types")
        }

        (operationParams?.loginId ?: context?.loginId)?.let { loginId ->
            return if (loginId.type in collectableLoginIdTypes) {
                Availability.Available()
            } else {
                Availability.Unavailable("Unsupported LoginId.Type: ${loginId.type}")
            }
        }

        val rawLoginId = context?.rawLoginId ?: return Availability.Available()
        return try {
            val loginId = loginIdValidator.appendWithType(rawLoginId)
            if (loginId.type in collectableLoginIdTypes) {
                Availability.Available()
            } else {
                Availability.Unavailable("Unsupported LoginId.Type: ${loginId.type}")
            }
        } catch (_: LoginIdValidationException) {
            Availability.Available()
        }
    }

    private suspend fun reduceState(current: LoginIdCollectOperation.State, event: Event): LoginIdCollectOperation.State = when (event) {
        is Event.Start -> when (current) {
            is LoginIdCollectOperation.State.Created -> {
                operationRegistry.register(controller)

                val collectableLoginIdTypes = loginIdConfigurationProvider.configuration.supportedTypes.filter { it in COLLECTABLE_LOGIN_ID_TYPES }.ifEmpty {
                    return LoginIdCollectOperation.State.Completed(
                        OperationResult.Failure(
                            LoginIdCollectOperationFailure.Integration.NoSupportedLoginIdTypes(
                                errorCode = ErrorCode.LoginIdTypeNotSupported,
                                message = "No supported LoginId.Types"
                            )
                        )
                    )
                }

                val loginIdValue = (event.params?.loginId ?: context?.loginId)?.let { loginId ->
                    if (loginId.type !in collectableLoginIdTypes) {
                        return LoginIdCollectOperation.State.Completed(
                            OperationResult.Failure(
                                LoginIdCollectOperationFailure.Input.UnsupportedLoginIdType(
                                    errorCode = ErrorCode.LoginIdTypeNotSupported,
                                    message = "Unsupported LoginId.Type: ${loginId.type}"
                                )
                            )
                        )
                    }

                    try {
                        return LoginIdCollectOperation.State.Completed(OperationResult.Success(loginIdValidator.validate(loginId)))
                    } catch (_: LoginIdValidationException) {
                        // Type is accepted, but value is invalid. Start UI so the user can fix it.
                    }

                    loginId.id
                } ?: context?.rawLoginId?.let { rawLoginId ->
                    try {
                        val loginId = loginIdValidator.appendWithType(rawLoginId)
                        if (loginId.type !in collectableLoginIdTypes) {
                            return LoginIdCollectOperation.State.Completed(
                                OperationResult.Failure(
                                    LoginIdCollectOperationFailure.Input.UnsupportedLoginIdType(
                                        errorCode = ErrorCode.LoginIdTypeNotSupported,
                                        message = "Unsupported LoginId.Type: ${loginId.type}"
                                    )
                                )
                            )
                        }

                        return LoginIdCollectOperation.State.Completed(OperationResult.Success(loginIdValidator.validate(loginId)))
                    } catch (_: LoginIdValidationException) {
                        // Type detection or validation failed. Start UI with the raw value so the user can fix it.
                    }

                    rawLoginId
                }

                ui.start(controller) { error: Throwable?, message: String? ->
                    logger?.logW(this, "Event.Start", "UI start error [$message]: $error", error)
                    eventChannel.trySend(Event.OnUiError(error, message))
                }

                launch {
                    delay(LOGIN_ID_COLLECT_TIMEOUT)
                    if (isActive) eventChannel.trySend(Event.Abort(Reason.Timeout()))
                }

                LoginIdCollectOperation.State.Active(
                    LoginIdCollectUi.UiState(
                        loginIdValue = loginIdValue ?: "",
                        collectableLoginIdTypes = collectableLoginIdTypes,
                        error = null,
                        onLoginIdChange = { eventChannel.trySend(Event.Ui.OnLoginIdChange(it)) },
                        onContinue = {
                            invokeOnUiClick(event.params?.onUiClick)
                            eventChannel.trySend(Event.Ui.OnContinue)
                        },
                        onCancel = {
                            invokeOnUiClick(event.params?.onUiClick)
                            eventChannel.trySend(Event.Ui.OnCancel)
                        }
                    )
                )
            }

            is LoginIdCollectOperation.State.Active -> current

            is LoginIdCollectOperation.State.Completed -> current
        }

        is Event.OnUiError -> when (current) {
            is LoginIdCollectOperation.State.Created,
            is LoginIdCollectOperation.State.Active -> LoginIdCollectOperation.State.Completed(
                OperationResult.Failure(
                    LoginIdCollectOperationFailure.Integration.Ui(
                        errorCode = ErrorCode.IntegrationError,
                        message = event.message ?: event.error?.message ?: event.error?.toString() ?: "LoginIdCollect UI failed",
                        error = event.error
                    )
                )
            )

            is LoginIdCollectOperation.State.Completed -> current
        }

        is Event.Abort -> when (current) {
            is LoginIdCollectOperation.State.Created ->
                LoginIdCollectOperation.State.Completed(OperationResult.Canceled(event.reason))

            is LoginIdCollectOperation.State.Active ->
                LoginIdCollectOperation.State.Completed(OperationResult.Canceled(event.reason))

            is LoginIdCollectOperation.State.Completed -> current
        }

        is Event.Ui -> when (current) {
            is LoginIdCollectOperation.State.Created -> unexpectedEventFailure(event, current)

            is LoginIdCollectOperation.State.Active -> when (event) {
                is Event.Ui.OnLoginIdChange -> current.copy(uiState = current.uiState.copy(loginIdValue = event.loginIdValue, error = null))
                is Event.Ui.OnCancel -> LoginIdCollectOperation.State.Completed(OperationResult.Canceled(Reason.UserClose()))
                is Event.Ui.OnContinue -> {
                    var acceptedLoginId: LoginId? = null
                    var lastValidationErrorCode: ErrorCode? = null
                    for (loginIdType in current.uiState.collectableLoginIdTypes) {
                        try {
                            acceptedLoginId = loginIdValidator.validate(LoginId(current.uiState.loginIdValue, loginIdType))
                        } catch (error: LoginIdValidationException) {
                            lastValidationErrorCode = error.errorCode
                        }
                        if (acceptedLoginId != null) break
                    }

                    acceptedLoginId?.let { loginId ->
                        LoginIdCollectOperation.State.Completed(OperationResult.Success(loginId))
                    } ?: run {
                        val errorCode = lastValidationErrorCode ?: ErrorCode.LoginIdValidationFailed
                        current.copy(uiState = current.uiState.copy(error = errorCode.toUiError(errorStrings)))
                    }
                }
            }

            is LoginIdCollectOperation.State.Completed -> current
        }

        is Event.ErrorStringsUpdated -> when (current) {
            is LoginIdCollectOperation.State.Active -> {
                val currentUiError = current.uiState.error
                if (currentUiError == null) current
                else current.copy(uiState = current.uiState.copy(error = currentUiError.errorCode.toUiError(errorStrings)))
            }

            else -> current
        }
    }

    private fun unexpectedEventFailure(event: Event, current: LoginIdCollectOperation.State): LoginIdCollectOperation.State =
        LoginIdCollectOperation.State.Completed(
            OperationResult.Failure(
                LoginIdCollectOperationFailure.Unexpected(
                    message = "${this::class.simpleName}: Unexpected event [${event::class.simpleName}] for state [${current::class.simpleName}]"
                )
            )
        )

    private fun invokeOnUiClick(callback: ((OperationID) -> Unit)?) {
        if (callback == null) return
        try {
            callback.invoke(operationId)
        } catch (error: Exception) {
            logger?.logI(this, "onUiClick", "Ignored callback failure: ${error.message}", error)
        }
    }

    internal companion object {
        private val LOGIN_ID_COLLECT_TIMEOUT = 5.minutes
        private val COLLECTABLE_LOGIN_ID_TYPES = setOf(LoginId.Type.Email, LoginId.Type.PhoneNumber, LoginId.Type.UserName)

        fun create(resolver: DIContainerResolver): LoginIdCollectOperation = resolver.runCatchingExceptions {
            LoginIdCollectOperationImpl(
                operationType = OperationType.LOGIN_ID_COLLECT,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                loginIdConfigurationProvider = getOrThrow<LoginIdConfigurationProvider>(),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                ui = getOrThrow<LoginIdCollectUi>(),
                errorStringsProvider = getOrNull<ErrorStringsProvider>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(LoginIdCollectOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    /**
     * Unavailable operation returned when construction cannot provide a usable runtime.
     *
     * Starting this object returns a controller that is already completed with [LoginIdCollectOperationFailure.Unexpected],
     * does not register in [OperationRegistry], and reports unavailable with the same diagnostic message.
     */
    private class Failed(error: Throwable) : LoginIdCollectOperation {

        override val operationType: OperationType = OperationType.LOGIN_ID_COLLECT
        private val operationId: OperationID = operationType.createUID()

        private val failure = LoginIdCollectOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureState = MutableStateFlow(LoginIdCollectOperation.State.Completed(OperationResult.Failure(failure)))

        private val failureController: LoginIdCollectOperationController = object : LoginIdCollectOperationController {
            override val operationId: OperationID = this@Failed.operationId
            override val state: StateFlow<LoginIdCollectOperation.State>
                get() = failureState

            override suspend fun whenSettled(): OperationResult<LoginId, LoginIdCollectOperationFailure> = OperationResult.Failure(failure)
            override fun abort(reason: Reason) = Unit
        }

        private val controller: LoginIdCollectOperationController = failureController
        override fun start(params: LoginIdCollectOperation.Params?): LoginIdCollectOperationController = controller
        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
