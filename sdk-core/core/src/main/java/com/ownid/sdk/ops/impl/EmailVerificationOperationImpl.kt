package com.ownid.sdk.ops.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.EmailVerificationResendApiFailure
import com.ownid.sdk.api.EmailVerificationStartApiFailure
import com.ownid.sdk.api.fold
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.VerificationMethod
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.toUiError
import com.ownid.sdk.model.withContext
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationController
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.ui.EmailVerificationUi
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@InternalOwnIdAPI
internal class EmailVerificationOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val ui: EmailVerificationUi,
    private val errorStringsProvider: ErrorStringsProvider?,
    private val api: EmailVerificationApi,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?,
) : EmailVerificationOperation,
    CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.EmailVerificationOperation")) {

    private sealed interface Event {
        class Start(val params: EmailVerificationOperation.Params?) : Event
        class OnUiError(val error: Throwable?, val message: String?) : Event
        class Abort(val reason: Reason) : Event
        class CompleteAfterError(val result: OperationResult<AccessOrProofToken, EmailVerificationOperationFailure>) : Event

        class ApiResultStart(
            val apiResult: ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure>,
            val onUiClick: ((OperationID) -> Unit)?
        ) : Event

        sealed interface ApiEvent : Event {
            class Complete(val apiResult: ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure>) : ApiEvent
            class Resend(val apiResult: ApiResult<Unit, EmailVerificationResendApiFailure>) : ApiEvent
            class Cancel(val reason: Reason) : ApiEvent
        }

        sealed interface Ui : Event {
            class OnCodeEntered(val code: String) : Ui
            object OnResend : Ui
            object OnCancel : Ui
            object OnNotYou : Ui
        }

        object ErrorStringsUpdated : Event
    }

    private val resultDeferred = CompletableDeferred<OperationResult<AccessOrProofToken, EmailVerificationOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()
    private val cleanupScope = CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.EmailVerificationOperation.cleanup"))

    private val controller: EmailVerificationOperationController = object : EmailVerificationOperationController {
        override val operationId: OperationID = this@EmailVerificationOperationImpl.operationId
        override val state: StateFlow<EmailVerificationOperation.State>
            get() = stateFlow

        override suspend fun whenSettled(): OperationResult<AccessOrProofToken, EmailVerificationOperationFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@EmailVerificationOperationImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val stateFlow = MutableStateFlow<EmailVerificationOperation.State>(EmailVerificationOperation.State.Created)

    private var errorStrings: ErrorStrings = ErrorStrings.DEFAULT
    private var timeoutJob: Job? = null

    init {
        eventChannel.receiveAsFlow()
            .runningFold(EmailVerificationOperation.State.Created) { current: EmailVerificationOperation.State, event ->
                reduceState(current, event)
            }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@EmailVerificationOperationImpl, "Operation error", "$error", error)
                emit(
                    EmailVerificationOperation.State.Completed(
                        OperationResult.Failure(EmailVerificationOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
                    )
                )
            }
            .onEach { newState ->
                stateFlow.value = newState
                if (newState is EmailVerificationOperation.State.Completed) {
                    if (resultDeferred.complete(newState.result)) {
                        newState.result
                            .onError { logger?.logD(this, "Completed with error", it.message) }
                            .onCanceled { logger?.logD(this, "Canceled with reason", "$it") }
                        eventChannel.close()
                        operationRegistry.unregister(operationId)
                        this@EmailVerificationOperationImpl.cancel()
                    }
                }
            }
            .launchIn(this)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val result = OperationResult.Canceled(Reason.SystemError("Operation canceled"))
                if (resultDeferred.complete(result)) {
                    stateFlow.value = EmailVerificationOperation.State.Completed(result)
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

    override fun start(params: EmailVerificationOperation.Params?): EmailVerificationOperationController {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }

        val operationParams = when (params) {
            null -> null
            is EmailVerificationOperation.Params -> params
            else -> return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }

        val accessToken = operationParams?.accessToken ?: context?.accessToken
        val loginId = try {
            operationParams?.loginId ?: context?.loginId(loginIdValidator)
        } catch (error: LoginIdResolutionException) {
            if (accessToken == null) return Availability.Unavailable(error.message)
            null
        }

        if (loginId == null && accessToken == null) {
            return Availability.Unavailable("LoginId or AccessToken required")
        }

        if (!loginId.isSupportedVerificationTarget(LoginId.Type.Email, operationParams?.loginIdHintId)) {
            return Availability.Unavailable("LoginId.Type.Email or LoginId.Type.UserName with loginIdHintId required")
        }

        return Availability.Available()
    }

    private suspend fun reduceState(current: EmailVerificationOperation.State, event: Event): EmailVerificationOperation.State {
        return when (event) {
            is Event.Start -> when (current) {
                is EmailVerificationOperation.State.Created -> {
                    operationRegistry.register(controller)

                    val accessToken = event.params?.accessToken ?: context?.accessToken
                    val loginId = try {
                        event.params?.loginId ?: context?.loginId(loginIdValidator)
                    } catch (error: LoginIdResolutionException) {
                        if (accessToken == null) return EmailVerificationOperation.State.Completed(OperationResult.Failure(error.toOperationFailure()))
                        null
                    }

                    if (loginId == null && accessToken == null) {
                        EmailVerificationOperation.State.Completed(
                            OperationResult.Failure(
                                EmailVerificationOperationFailure.Input.MissingLoginIdOrAccessToken(
                                    errorCode = ErrorCode.InvalidArgument,
                                    message = "LoginId or AccessToken required"
                                )
                            )
                        )
                    } else if (!loginId.isSupportedVerificationTarget(LoginId.Type.Email, event.params?.loginIdHintId)) {
                        EmailVerificationOperation.State.Completed(
                            OperationResult.Failure(
                                EmailVerificationOperationFailure.Input.UnsupportedLoginIdType(
                                    errorCode = ErrorCode.LoginIdTypeNotSupported,
                                    message = "LoginId.Type.Email or LoginId.Type.UserName with loginIdHintId required"
                                )
                            )
                        )
                    } else {
                        val params = EmailVerificationOperation.Params(
                            loginId,
                            loginIdHintId = event.params?.loginIdHintId,
                            accessToken = accessToken,
                            onUiClick = event.params?.onUiClick,
                            traceParent = event.params?.traceParent ?: TraceContext.generateTraceparent()
                        )

                        ui.start(controller) { error: Throwable?, message: String? ->
                            logger?.logW(this, "Event.Start", "UI start error [$message]: $error", error)
                            eventChannel.trySend(Event.OnUiError(error, message))
                        }

                        launch {
                            val newEvent = try {
                                val apiParams = EmailVerificationApi.Params(
                                    loginId = loginId,
                                    loginIdHintId = params.loginIdHintId,
                                    accessToken = params.accessToken,
                                    verificationMethods = setOf(VerificationMethod.OTP),
                                    magicLinkRedirectUrl = null,
                                    traceParent = params.traceParent
                                )
                                Event.ApiResultStart(api.start(apiParams), params.onUiClick)
                            } catch (_: CancellationException) {
                                Event.Abort(Reason.SystemError("Email verification start canceled"))
                            }

                            eventChannel.trySend(newEvent)
                        }

                        EmailVerificationOperation.State.Preparing(params)
                    }
                }

                else -> current
            }

            is Event.OnUiError -> when (current) {
                is EmailVerificationOperation.State.Created,
                is EmailVerificationOperation.State.Preparing,
                is EmailVerificationOperation.State.Active -> EmailVerificationOperation.State.Completed(
                    OperationResult.Failure(
                        EmailVerificationOperationFailure.Integration.Ui(
                            errorCode = ErrorCode.IntegrationError,
                            message = event.message ?: event.error?.message ?: event.error?.toString() ?: "EmailVerification UI failed",
                            error = event.error
                        )
                    )
                )

                is EmailVerificationOperation.State.Completed -> current
            }

            is Event.Abort -> when (current) {
                is EmailVerificationOperation.State.Created ->
                    EmailVerificationOperation.State.Completed(OperationResult.Canceled(event.reason))

                is EmailVerificationOperation.State.Preparing ->
                    EmailVerificationOperation.State.Completed(OperationResult.Canceled(event.reason))

                is EmailVerificationOperation.State.Active -> {
                    cleanupScope.launch {
                        try {
                            current.apiController.cancel(event.reason)
                        } catch (error: CancellationException) {
                            logger?.logI(this@EmailVerificationOperationImpl, "Event.Abort", "API cancel canceled: ${error.message}", error)
                        } catch (error: Exception) {
                            logger?.logI(this@EmailVerificationOperationImpl, "Event.Abort", "API cancel failed: ${error.message}", error)
                        }
                    }
                    EmailVerificationOperation.State.Completed(OperationResult.Canceled(event.reason))
                }

                is EmailVerificationOperation.State.Completed -> current
            }

            is Event.CompleteAfterError -> when (current) {
                is EmailVerificationOperation.State.Completed -> current
                else -> EmailVerificationOperation.State.Completed(event.result)
            }

            is Event.ApiResultStart -> when (current) {
                is EmailVerificationOperation.State.Created -> unexpectedEventFailure(event, current)

                is EmailVerificationOperation.State.Preparing -> when (val apiResult = event.apiResult) {
                    is ApiResult.Failure -> EmailVerificationOperation.State.Completed(OperationResult.Failure(apiResult.failure.toOperationFailure()))

                    is ApiResult.Success -> {
                        val apiController = apiResult.success
                        if (apiController.challenge.methods.otp == null) {
                            EmailVerificationOperation.State.Completed(
                                OperationResult.Failure(
                                    EmailVerificationOperationFailure.Unexpected(
                                        errorCode = ErrorCode.IntegrationError,
                                        message = "OTP challenge method missing; availableChallengeMethods=${apiController.challenge.methods}"
                                    )
                                )
                            )
                        } else {
                            timeoutJob = launch {
                                delay(apiController.challenge.timeout.milliseconds.milliseconds)
                                if (isActive) eventChannel.trySend(Event.Abort(Reason.Timeout()))
                            }

                            EmailVerificationOperation.State.Active(
                                uiState = EmailVerificationUi.UiState(
                                    challenge = VerificationChallenge(
                                        challengeId = apiController.challenge.challengeId,
                                        resendPolicy = apiController.challenge.resendPolicy,
                                        timeout = apiController.challenge.timeout,
                                        attempts = apiController.challenge.attempts,
                                        methods = apiController.challenge.methods,
                                        channel = apiController.challenge.channel
                                    ),
                                    busy = false,
                                    error = null,
                                    onCodeEntered = {
                                        invokeOnUiClick(event.onUiClick)
                                        eventChannel.trySend(Event.Ui.OnCodeEntered(it))
                                    },
                                    onResend = {
                                        invokeOnUiClick(event.onUiClick)
                                        eventChannel.trySend(Event.Ui.OnResend)
                                    },
                                    onCancel = {
                                        invokeOnUiClick(event.onUiClick)
                                        eventChannel.trySend(Event.Ui.OnCancel)
                                    },
                                    onNotYou = {
                                        invokeOnUiClick(event.onUiClick)
                                        eventChannel.trySend(Event.Ui.OnNotYou)
                                    }
                                ),
                                apiController = apiController
                            )
                        }
                    }
                }

                is EmailVerificationOperation.State.Active -> unexpectedEventFailure(event, current)
                is EmailVerificationOperation.State.Completed -> current
            }

            is Event.ApiEvent -> when (current) {
                is EmailVerificationOperation.State.Created -> unexpectedEventFailure(event, current)
                is EmailVerificationOperation.State.Preparing -> unexpectedEventFailure(event, current)

                is EmailVerificationOperation.State.Active -> when (event) {
                    is Event.ApiEvent.Complete ->
                        event.apiResult.fold(
                            onSuccess = { EmailVerificationOperation.State.Completed(OperationResult.Success(it)) },
                            onError = { apiError ->
                                if (apiError is EmailVerificationCompleteApiFailure.BadRequest.WrongCode) {
                                    current.copy(uiState = current.uiState.copy(busy = false, error = apiError.errorCode.toUiError(errorStrings)))
                                } else {
                                    timeoutJob?.cancel()
                                    val maximumAttemptsReached =
                                        apiError is EmailVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached
                                    launch {
                                        delay(1.seconds)
                                        if (maximumAttemptsReached) {
                                            cleanupScope.launch {
                                                current.apiController.cancel(Reason.UserClose("Maximum attempts reached"))
                                            }
                                        }
                                        eventChannel.trySend(Event.CompleteAfterError(OperationResult.Failure(apiError.toOperationFailure())))
                                    }
                                    val uiErrorCode = if (maximumAttemptsReached) ErrorCode.MaximumAttemptsReached else ErrorCode.Unknown
                                    current.copy(
                                        uiState = current.uiState.copy(
                                            busy = false,
                                            error = uiErrorCode.toUiError(errorStrings),
                                            onCodeEntered = {},
                                            onCancel = {},
                                            onNotYou = {},
                                            onResend = {}
                                        )
                                    )
                                }
                            }
                        )

                    is Event.ApiEvent.Resend ->
                        event.apiResult.fold(
                            onSuccess = { current.copy(uiState = current.uiState.copy(busy = false, error = null)) },
                            onError = { apiError ->
                                if (apiError is EmailVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached) {
                                    val resendPolicy = current.uiState.challenge.resendPolicy
                                    current.copy(
                                        uiState = current.uiState.copy(
                                            challenge = VerificationChallenge(
                                                challengeId = current.uiState.challenge.challengeId,
                                                resendPolicy = VerificationChallenge.ResendPolicy(
                                                    allow = false,
                                                    attempts = resendPolicy.attempts,
                                                    debounce = resendPolicy.debounce
                                                ),
                                                timeout = current.uiState.challenge.timeout,
                                                attempts = current.uiState.challenge.attempts,
                                                methods = current.uiState.challenge.methods,
                                                channel = current.uiState.challenge.channel
                                            ),
                                            busy = false,
                                            error = apiError.errorCode.toUiError(errorStrings)
                                        )
                                    )
                                } else {
                                    timeoutJob?.cancel()
                                    launch {
                                        delay(1.seconds)
                                        eventChannel.trySend(Event.CompleteAfterError(OperationResult.Failure(apiError.toOperationFailure())))
                                    }
                                    current.copy(
                                        uiState = current.uiState.copy(
                                            busy = false,
                                            error = ErrorCode.Unknown.toUiError(errorStrings),
                                            onCodeEntered = {},
                                            onCancel = {},
                                            onNotYou = {},
                                            onResend = {}
                                        )
                                    )
                                }
                            }
                        )

                    is Event.ApiEvent.Cancel ->
                        EmailVerificationOperation.State.Completed(OperationResult.Canceled(event.reason))

                }

                is EmailVerificationOperation.State.Completed -> current
            }

            is Event.ErrorStringsUpdated -> when (current) {
                is EmailVerificationOperation.State.Active -> {
                    val currentUiError = current.uiState.error
                    if (currentUiError == null) current
                    else current.copy(uiState = current.uiState.copy(error = currentUiError.errorCode.toUiError(errorStrings)))
                }

                else -> current
            }

            is Event.Ui -> when (current) {
                is EmailVerificationOperation.State.Created -> unexpectedEventFailure(event, current)
                is EmailVerificationOperation.State.Preparing -> unexpectedEventFailure(event, current)

                is EmailVerificationOperation.State.Active -> {
                    if (current.uiState.busy && event !is Event.Ui.OnCancel) {
                        current
                    } else {
                        launch {
                            val newEvent = try {
                                when (event) {
                                    is Event.Ui.OnCodeEntered -> Event.ApiEvent.Complete(current.apiController.completeWithCode(event.code))

                                    is Event.Ui.OnResend -> Event.ApiEvent.Resend(current.apiController.resend())

                                    is Event.Ui.OnCancel -> {
                                        current.apiController.cancel(Reason.UserClose())
                                        Event.ApiEvent.Cancel(Reason.UserClose())
                                    }

                                    is Event.Ui.OnNotYou -> {
                                        current.apiController.cancel(Reason.MoveToOtherChallenge())
                                        Event.ApiEvent.Cancel(Reason.MoveToOtherChallenge())
                                    }
                                }
                            } catch (_: CancellationException) {
                                Event.Abort(Reason.SystemError("Email verification request canceled"))
                            }

                            eventChannel.trySend(newEvent)
                        }
                        current.copy(uiState = current.uiState.copy(busy = true, error = null))
                    }
                }

                is EmailVerificationOperation.State.Completed -> current
            }
        }
    }

    private fun unexpectedEventFailure(event: Event, current: EmailVerificationOperation.State): EmailVerificationOperation.State =
        EmailVerificationOperation.State.Completed(
            OperationResult.Failure(
                EmailVerificationOperationFailure.Unexpected(
                    message = "${this::class.simpleName}: Unexpected event [${event::class.simpleName}] for state [${current::class.simpleName}]"
                )
            )
        )

    private fun LoginIdResolutionException.toOperationFailure(): EmailVerificationOperationFailure = when (this) {
        is LoginIdResolutionException.MissingValidator -> EmailVerificationOperationFailure.Integration.MissingProvider(errorCode, message, "LoginIdValidator")
        is LoginIdResolutionException.TypeNotSupported -> EmailVerificationOperationFailure.Input.UnsupportedLoginIdType(errorCode, message)
        is LoginIdResolutionException.ValidationFailed -> EmailVerificationOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex)
    }

    private fun EmailVerificationStartApiFailure.toOperationFailure(): EmailVerificationOperationFailure = when (this) {
        is EmailVerificationStartApiFailure.BadRequest.InvalidArgument,
        is EmailVerificationStartApiFailure.BadRequest.Unknown -> EmailVerificationOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is EmailVerificationStartApiFailure.BadRequest.InvalidLoginId ->
            EmailVerificationOperationFailure.Input.InvalidLoginId(errorCode, message, loginId, regex, this)

        is EmailVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType ->
            EmailVerificationOperationFailure.Input.UnsupportedLoginIdType(errorCode, message, this)

        is EmailVerificationStartApiFailure.BadRequest.MissingChannel ->
            EmailVerificationOperationFailure.Integration.MissingChannel(errorCode, message, loginId, this)

        is EmailVerificationStartApiFailure.Forbidden -> EmailVerificationOperationFailure.Access.Forbidden(errorCode, message, this)
        is EmailVerificationStartApiFailure.UserNotFound -> EmailVerificationOperationFailure.Access.UserNotFound(errorCode, message, this)
        is EmailVerificationStartApiFailure.FailedDependency.ProviderFailed ->
            EmailVerificationOperationFailure.Integration.ProviderFailed(errorCode, message, this)

        is EmailVerificationStartApiFailure.FailedDependency.MissingProvider ->
            EmailVerificationOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is EmailVerificationStartApiFailure.MaximumChallengesReached ->
            EmailVerificationOperationFailure.Challenge.MaximumChallengesReached(errorCode, message, this)

        is EmailVerificationStartApiFailure.Unexpected -> EmailVerificationOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun EmailVerificationCompleteApiFailure.toOperationFailure(): EmailVerificationOperationFailure = when (this) {
        is EmailVerificationCompleteApiFailure.BadRequest.InvalidArgument,
        is EmailVerificationCompleteApiFailure.BadRequest.Unknown ->
            EmailVerificationOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is EmailVerificationCompleteApiFailure.BadRequest.WrongCode -> error("WrongCode is handled as active UI state")
        is EmailVerificationCompleteApiFailure.BadRequest.InvalidChallenge ->
            EmailVerificationOperationFailure.Challenge.Invalid(errorCode, message, challengeId, this)

        is EmailVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached ->
            EmailVerificationOperationFailure.Challenge.MaximumAttemptsReached(errorCode, message, challengeId, this)

        is EmailVerificationCompleteApiFailure.Forbidden -> EmailVerificationOperationFailure.Access.Forbidden(errorCode, message, this)
        is EmailVerificationCompleteApiFailure.Unexpected -> EmailVerificationOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun EmailVerificationResendApiFailure.toOperationFailure(): EmailVerificationOperationFailure = when (this) {
        is EmailVerificationResendApiFailure.BadRequest.InvalidArgument,
        is EmailVerificationResendApiFailure.BadRequest.Unknown -> EmailVerificationOperationFailure.Input.InvalidRequest(errorCode, message, this)

        is EmailVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached -> error("MaximumResendAttemptsReached is handled as active UI state")
        is EmailVerificationResendApiFailure.BadRequest.InvalidChallenge ->
            EmailVerificationOperationFailure.Challenge.Invalid(errorCode, message, challengeId, this)

        is EmailVerificationResendApiFailure.FailedDependency.ProviderFailed ->
            EmailVerificationOperationFailure.Integration.ProviderFailed(errorCode, message, this)

        is EmailVerificationResendApiFailure.FailedDependency.MissingProvider ->
            EmailVerificationOperationFailure.Integration.MissingProvider(errorCode, message, capability, this)

        is EmailVerificationResendApiFailure.Unexpected -> EmailVerificationOperationFailure.Unexpected(errorCode, message, apiFailure = this)
    }

    private fun invokeOnUiClick(callback: ((OperationID) -> Unit)?) {
        if (callback == null) return
        try {
            callback.invoke(operationId)
        } catch (error: Exception) {
            logger?.logI(this, "onUiClick", "Ignored callback failure: ${error.message}", error)
        }
    }

    private fun LoginId?.isSupportedVerificationTarget(targetType: LoginId.Type, loginIdHintId: String?): Boolean = when (this?.type) {
        null, targetType -> true
        LoginId.Type.UserName -> !loginIdHintId.isNullOrBlank()
        else -> false
    }

    internal companion object {

        fun create(resolver: DIContainerResolver): EmailVerificationOperation = resolver.runCatchingExceptions {
            EmailVerificationOperationImpl(
                operationType = OperationType.EMAIL_VERIFICATION,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                ui = getOrThrow<EmailVerificationUi>(),
                errorStringsProvider = getOrNull<ErrorStringsProvider>(),
                api = (this as DIContainer).withContext(scopeName = "EmailVerificationOperation") { }.getOrThrow<EmailVerificationApi>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(EmailVerificationOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : EmailVerificationOperation {

        override val operationType: OperationType = OperationType.EMAIL_VERIFICATION
        private val operationId: OperationID = operationType.createUID()

        private val failure = EmailVerificationOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureState = MutableStateFlow(EmailVerificationOperation.State.Completed(OperationResult.Failure(failure)))

        private val failureController: EmailVerificationOperationController = object : EmailVerificationOperationController {
            override val operationId: OperationID = this@Failed.operationId
            override val state: StateFlow<EmailVerificationOperation.State>
                get() = failureState

            override suspend fun whenSettled(): OperationResult<AccessOrProofToken, EmailVerificationOperationFailure> = OperationResult.Failure(failure)
            override fun abort(reason: Reason) = Unit
        }

        private val controller: EmailVerificationOperationController = failureController
        override fun start(params: EmailVerificationOperation.Params?): EmailVerificationOperationController = controller
        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
