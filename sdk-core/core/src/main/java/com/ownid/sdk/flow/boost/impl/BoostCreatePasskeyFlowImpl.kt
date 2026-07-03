package com.ownid.sdk.flow.boost.impl

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.TokenException
import com.ownid.sdk.model.User
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.rawLoginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.namespace.DIResolvable
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.namespace.opsNamespace
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.provider.SessionCreate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime for one [BoostFlow.CreatePasskey] run.
 *
 * Maintainer invariant: keep this runtime one-shot and preserve the public [BoostFlow.CreatePasskey] result mapping.
 * Source and trace metadata must not affect the developer-facing flow result, proof handoff, or [SessionCreate]
 * boundary.
 */
@OptIn(InternalOwnIdAPI::class)
internal class BoostCreatePasskeyFlowImpl(
    private val userRepository: UserRepository?,
    private val ownIdOperation: OwnIdOperation,
    private val boostFlowLogin: BoostFlow.Login,
    private val loginIdValidator: LoginIdValidator,
    private val serialization: Serialization,
    private val userJourney: UserJourney?,
    private val sessionCreate: SessionCreate?,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val logger: OwnIdLogRouter?
) : BoostFlow.CreatePasskey, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.BoostFlow.CreatePasskey")) {

    private sealed interface State {
        object Created : State
        data class Active(val controller: Capability.Controller, val operationID: OperationID?, val flowContext: BoostFlowContext) : State
        data class Completed(val result: FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure>) : State
    }

    private sealed interface Event {
        class Start(val flowContext: BoostFlowContext) : Event
        class Abort(val reason: Reason) : Event
        class LoginFlowResult(val result: FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>) : Event

        sealed interface OpResult<R, F : OperationFailure> : Event {
            val operationID: OperationID
            val result: OperationResult<R, F>

            class LoginIdCollect(
                override val operationID: OperationID,
                override val result: OperationResult<LoginId, LoginIdCollectOperationFailure>
            ) : OpResult<LoginId, LoginIdCollectOperationFailure>

            class Login(
                override val operationID: OperationID,
                override val result: OperationResult<LoginResponse, LoginOperationFailure>
            ) : OpResult<LoginResponse, LoginOperationFailure>

            class PasskeyAttestation(
                override val operationID: OperationID,
                override val result: OperationResult<AttestationResponse, PasskeyAttestationOperationFailure>
            ) : OpResult<AttestationResponse, PasskeyAttestationOperationFailure>
        }
    }

    private val resultDeferred = CompletableDeferred<FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure>>()
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var journey: UserJourney.Controller? = null
    private val startAccepted = AtomicBoolean(false)
    private val loginFlowStarted = AtomicBoolean(false)
    private val activeControllerRef = AtomicReference<Capability.Controller?>(null)
    private val controller: BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> =
        object : BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> {
            override suspend fun whenSettled(): FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure> = resultDeferred.await()
            override fun abort(reason: Reason) {
                logger?.logD(this@BoostCreatePasskeyFlowImpl, "abort", "$reason")
                eventChannel.trySend(Event.Abort(reason))
            }
        }

    init {
        eventChannel.consumeAsFlow()
            .onEach { logger?.logV(this, "Event", "${it::class.java.simpleName}") }
            .runningFold(State.Created) { current: State, event ->
                if (current is State.Active && current.operationID != null && event is Event.OpResult<*, *> && current.operationID != event.operationID) {
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

                        is Event.LoginFlowResult ->
                            if (current is State.Active) {
                                State.Completed(
                                    when (val result = event.result) {
                                        is FlowResult.Success -> FlowResult.Success(result.success)
                                        is FlowResult.Canceled -> result
                                        is FlowResult.Failure -> FlowResult.Failure(result.failure.toCreatePasskeyFailure())
                                    }
                                )
                            } else current

                        is Event.OpResult.LoginIdCollect ->
                            if (current is State.Active) handleLoginIdCollectOpResult(current, event) else current

                        is Event.OpResult.Login ->
                            if (current is State.Active) handleLoginOpResult(current, event) else current

                        is Event.OpResult.PasskeyAttestation ->
                            if (current is State.Active) handlePasskeyAttestationOpResult(current, event) else current
                    }
                } catch (error: CancellationException) {
                    val reason = Reason.SystemError("Operation canceled")
                    if (current is State.Active && current.operationID != null) {
                        journey?.completeOperation(
                            current.operationID,
                            errorCode = ErrorCode.Aborted,
                            source = "BoostCreatePasskeyFlowImpl.reducer.canceled",
                            message = "Canceled with reason: $reason"
                        )
                    }
                    logger?.logI(this@BoostCreatePasskeyFlowImpl, "Reducer canceled", "$error", error)
                    State.Completed(FlowResult.Canceled(reason))
                } catch (error: Exception) {
                    if (current is State.Active && current.operationID != null) {
                        journey?.completeOperation(
                            current.operationID,
                            errorCode = ErrorCode.Unknown,
                            source = "BoostCreatePasskeyFlowImpl.reducer.failure",
                            message = "Flow error in Boost Create Passkey"
                        )
                    }
                    logger?.logW(this@BoostCreatePasskeyFlowImpl, "Reducer error", "$error", error)
                    State.Completed(FlowResult.Failure(BoostCreatePasskeyFlowFailure.Unexpected(message = "Flow error in Boost Create Passkey", error = error)))
                }
            }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@BoostCreatePasskeyFlowImpl, "Flow error", "$error", error)
                emit(
                    State.Completed(FlowResult.Failure(BoostCreatePasskeyFlowFailure.Unexpected(message = "Flow error in Boost Create Passkey", error = error)))
                )
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
                            is FlowResult.Success -> {
                                when (result.success) {
                                    is BoostFlow.Response.CreatePasskey ->
                                        journey?.completeFlow(UserJourney.Controller.Outcome.Registered(null))

                                    is BoostFlow.Response.Login -> if (!loginFlowStarted.get()) {
                                        journey?.completeFlow(UserJourney.Controller.Outcome.LoggedIn(result.success.authMethod))
                                    }
                                }
                            }

                            is FlowResult.Canceled -> if (!loginFlowStarted.get()) {
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = ErrorCode.Aborted,
                                        source = "BoostCreatePasskeyFlowImpl.completeFlow.canceled",
                                        message = "Flow canceled with reason: ${result.reason}"
                                    )
                                )
                            }

                            is FlowResult.Failure -> if (!loginFlowStarted.get()) {
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = result.failure.errorCode,
                                        source = "BoostCreatePasskeyFlowImpl.completeFlow.failure",
                                        message = result.failure.message
                                    )
                                )
                            }
                        }

                        if (newState.result is FlowResult.Success) {
                            if (newState.result.success is BoostFlow.Response.CreatePasskey && newState.result.success.proofToken != null) {
                                val user = User(newState.result.success.loginId, AuthMethod.Passkey)
                                runCatching { userRepository?.setLastUser(user) }.onFailure { cause ->
                                    if (cause is CancellationException) throw cause
                                    logger?.logI(this@BoostCreatePasskeyFlowImpl, "UserRepository.setLastUser", "Failed: ${cause.message}", cause)
                                }
                            }
                            if (newState.result.success is BoostFlow.Response.Login && loginFlowStarted.get().not()) {
                                val user = User(newState.result.success.loginId, newState.result.success.authMethod)
                                runCatching { userRepository?.setLastUser(user) }.onFailure { cause ->
                                    if (cause is CancellationException) throw cause
                                    logger?.logI(this@BoostCreatePasskeyFlowImpl, "UserRepository.setLastUser", "Failed: ${cause.message}", cause)
                                }
                            }
                        }

                        this@BoostCreatePasskeyFlowImpl.cancel()
                        eventChannel.close()
                    }
                }
            }
            .stateIn(this, SharingStarted.Eagerly, State.Created)
    }

    init {
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val reason = Reason.SystemError("Operation canceled")
                activeControllerRef.getAndSet(null)?.abort(reason)
                val result = FlowResult.Canceled(reason)
                if (resultDeferred.complete(result)) {
                    if (!loginFlowStarted.get()) {
                        journey?.completeFlow(
                            UserJourney.Controller.Outcome.Error(
                                errorCode = ErrorCode.Aborted,
                                source = "BoostCreatePasskeyFlowImpl.scopeCompletion.canceled",
                                message = "Flow canceled with reason: ${result.reason}"
                            )
                        )
                    }
                    eventChannel.close()
                }
            }
        }
    }

    override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> {
        if (!startAccepted.compareAndSet(false, true)) return this@BoostCreatePasskeyFlowImpl.controller

        val ctx = context.copy {
            if (traceParent == null) traceParent = TraceContext.generateTraceparent()
        }
        eventChannel.trySend(Event.Start(ctx))
        return this@BoostCreatePasskeyFlowImpl.controller
    }

    private suspend fun handleStartEvent(current: State, event: Event.Start): State {
        if (current !is State.Created) return current

        loginFlowStarted.set(false)
        var flowContext = event.flowContext

        journey = userJourney?.startFlow(
            name = "create-passkey",
            source = flowContext.source ?: FlowInfo.Source.EXPLICIT,
            traceParent = flowContext.traceParent
        )

        val resolvedAccessToken = flowContext.accessToken ?: context?.accessToken
        if (resolvedAccessToken != null) {
            val resolvedLoginId = try {
                resolvedAccessToken.getLoginId(serialization, loginIdValidator)
            } catch (error: TokenException) {
                return State.Completed(
                    FlowResult.Failure(
                        BoostCreatePasskeyFlowFailure.Input.UnresolvedLoginId(
                            errorCode = error.errorCode,
                            message = "LoginId cannot be resolved from access token: ${error.message ?: error.toString()}",
                            error = error
                        )
                    )
                )
            }

            val loginOperation = ownIdOperation.login
            return loginOperation.getUnsatisfiedDependencies()?.let { deps ->
                val message = "Login operation unavailable. Missing dependencies: ${deps.joinToString()}"
                State.Completed(
                    FlowResult.Failure(BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, message))
                )
            } ?: run {
                journey?.setUserInfo(resolvedLoginId)
                flowContext = flowContext.copy {
                    accessToken = resolvedAccessToken
                    loginId = resolvedLoginId
                }
                val params = LoginOperation.Params(accessToken = resolvedAccessToken, traceParent = flowContext.traceParent)
                when (val availability = loginOperation.availability(params)) {
                    is Availability.Available -> {
                        val controller = loginOperation.start(params)
                        journey?.startOperation(controller.operationId)
                        launchOperationObserver(
                            controller,
                            { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                        ) { operationId, result -> Event.OpResult.Login(operationId, result) }
                        State.Active(controller, controller.operationId, flowContext)
                    }

                    is Availability.Unavailable -> State.Completed(
                        FlowResult.Failure(
                            BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
                        )
                    )
                }
            }
        }

        if (flowContext.loginId == null && flowContext.rawLoginId == null) {
            context?.loginId?.let { loginId ->
                flowContext = flowContext.copy {
                    this.loginId = loginId
                }
            } ?: context?.rawLoginId?.let { rawLoginId ->
                flowContext = flowContext.copy {
                    this.rawLoginId = rawLoginId
                }
            }
        }

        if (flowContext.ignoreLastUser != true && flowContext.loginId == null && flowContext.rawLoginId == null) {
            runCatching { userRepository?.getLastUser()?.loginId }.onFailure { cause ->
                if (cause is CancellationException) throw cause
                logger?.logI(this@BoostCreatePasskeyFlowImpl, "UserRepository.getLastUser", "Failed: ${cause.message}", cause)
            }.getOrNull()?.let { lastUserLoginId ->
                flowContext = flowContext.copy {
                    loginId = lastUserLoginId
                }
            }
        }

        val loginIdCollectOperation = ownIdOperation.withContext("BoostFlow.CreatePasskey.LoginIdCollectOperation") {
            authz = flowContext.loginId?.let { Authz.start(it) } ?: flowContext.rawLoginId?.let { Authz.start(it) }
        }.loginId.collect

        return loginIdCollectOperation.getUnsatisfiedDependencies()?.let { deps ->
            val message = "LoginIdCollect operation unavailable. Missing dependencies: ${deps.joinToString()}"
            State.Completed(
                FlowResult.Failure(BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.LOGIN_ID_COLLECT, ErrorCode.IntegrationError, message))
            )
        } ?: run {
            val params = LoginIdCollectOperation.Params(
                onUiClick = journey?.let { { operationId -> launch { journey?.addOperationClick(operationId) } } }
            )

            when (val availability = loginIdCollectOperation.availability(params)) {
                is Availability.Available -> {
                    val controller = loginIdCollectOperation.start(params)
                    journey?.startOperation(controller.operationId)
                    launchOperationObserver(
                        controller,
                        { error -> LoginIdCollectOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                    ) { operationId, result -> Event.OpResult.LoginIdCollect(operationId, result) }
                    State.Active(controller, controller.operationId, flowContext)
                }

                is Availability.Unavailable -> State.Completed(
                    FlowResult.Failure(
                        BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.LOGIN_ID_COLLECT, ErrorCode.IntegrationError, availability.message)
                    )
                )
            }
        }
    }

    private suspend fun handleLoginIdCollectOpResult(current: State.Active, event: Event.OpResult.LoginIdCollect): State =
        when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostCreatePasskeyFlowImpl.handleLoginIdCollectOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostCreatePasskeyFlowImpl.handleLoginIdCollectOpResult.failure",
                    message = result.failure.message
                )

                State.Completed(
                    FlowResult.Failure(
                        BoostCreatePasskeyFlowFailure.OperationFailed(
                            operationType = event.operationID.type,
                            errorCode = result.failure.errorCode,
                            message = "LoginIdCollect operation failed: ${result.failure.message}",
                            operationId = event.operationID,
                            operationFailure = result.failure
                        )
                    )
                )
            }

            is OperationResult.Success -> {
                journey?.setUserInfo(result.success)
                journey?.completeOperation(event.operationID)

                val loginOperation = ownIdOperation.login
                loginOperation.getUnsatisfiedDependencies()?.let { deps ->
                    val message = "Login operation unavailable. Missing dependencies: ${deps.joinToString()}"
                    State.Completed(
                        FlowResult.Failure(BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, message))
                    )
                } ?: run {
                    val updatedContext = current.flowContext.copy {
                        loginId = result.success
                    }
                    val params = LoginOperation.Params(
                        loginId = updatedContext.loginId,
                        accessToken = updatedContext.accessToken,
                        traceParent = updatedContext.traceParent
                    )

                    when (val availability = loginOperation.availability(params)) {
                        is Availability.Available -> {
                            val controller = loginOperation.start(params)
                            journey?.startOperation(controller.operationId)
                            launchOperationObserver(
                                controller,
                                { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                            ) { operationId, result -> Event.OpResult.Login(operationId, result) }
                            State.Active(controller, controller.operationId, updatedContext)
                        }

                        is Availability.Unavailable -> State.Completed(
                            FlowResult.Failure(
                                BoostCreatePasskeyFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
                            )
                        )
                    }
                }
            }
        }

    private suspend fun handleLoginOpResult(current: State.Active, event: Event.OpResult.Login): State =
        when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostCreatePasskeyFlowImpl.handleLoginOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostCreatePasskeyFlowImpl.handleLoginOpResult.failure",
                    message = result.failure.message
                )
                State.Completed(
                    FlowResult.Failure(
                        BoostCreatePasskeyFlowFailure.OperationFailed(
                            operationType = event.operationID.type,
                            errorCode = result.failure.errorCode,
                            message = "Login operation failed: ${result.failure.message}",
                            operationId = event.operationID,
                            operationFailure = result.failure
                        )
                    )
                )
            }

            is OperationResult.Success -> {
                when (val loginResponse = result.success) {
                    is LoginResponse.Success -> {
                        journey?.completeOperation(event.operationID)
                        State.Completed(
                            current.flowContext.loginId?.let { loginId ->
                                finalizeLoginWithSessionCreate(
                                    loginId = loginId,
                                    authMethod = AuthMethod.Immediate,
                                    accessToken = loginResponse.accessToken,
                                    sessionPayload = loginResponse.sessionPayload
                                )
                            } ?: FlowResult.Failure(
                                BoostCreatePasskeyFlowFailure.Unexpected(
                                    errorCode = ErrorCode.InvalidArgument,
                                    message = "LoginId is required for create-passkey login success context"
                                )
                            )
                        )
                    }

                    is LoginResponse.AccountBlocked -> {
                        val message = loginResponse.reason ?: "Account is blocked"
                        journey?.completeOperation(
                            event.operationID,
                            errorCode = ErrorCode.UserBlocked,
                            source = "BoostCreatePasskeyFlowImpl.handleLoginOpResult.accountBlocked",
                            message = message
                        )
                        State.Completed(FlowResult.Failure(BoostCreatePasskeyFlowFailure.Account.Blocked(errorCode = ErrorCode.UserBlocked, message = message)))
                    }

                    is LoginResponse.AccountNotFound -> {
                        val updatedContext = current.flowContext.copy {
                            accessToken = null
                            authRequiredResponse = null
                        }

                        journey?.completeOperation(event.operationID)

                        val controller = createPasskeyAttestationController(updatedContext)
                        journey?.startOperation(controller.operationId)
                        launchOperationObserver(
                            controller,
                            { error ->
                                PasskeyAttestationOperationFailure.Unexpected(
                                    message = error.message ?: "Failed to await operation settlement",
                                    error = error
                                )
                            }
                        ) { operationId, operationResult -> Event.OpResult.PasskeyAttestation(operationId, operationResult) }
                        State.Active(controller, controller.operationId, updatedContext)
                    }

                    is LoginResponse.AuthRequired -> {
                        val updatedContext = current.flowContext.copy {
                            accessToken = null
                            authRequiredResponse = loginResponse
                        }

                        when {
                            loginResponse.authRequirements.operations.any { it.type == OperationType.PASSKEY_AUTH } -> {
                                journey?.completeOperation(event.operationID)
                                loginFlowStarted.set(true)
                                journey?.switchToFlow(name = "login", source = updatedContext.source ?: FlowInfo.Source.EXPLICIT)
                                val loginFlowController = boostFlowLogin.start(updatedContext)
                                launchFlowObserver(loginFlowController) { flowResult -> Event.LoginFlowResult(flowResult) }
                                State.Active(loginFlowController, null, updatedContext)
                            }

                            else -> {
                                journey?.completeOperation(event.operationID)

                                val controller = createPasskeyAttestationController(updatedContext)
                                journey?.startOperation(controller.operationId)
                                launchOperationObserver(
                                    controller,
                                    { error ->
                                        PasskeyAttestationOperationFailure.Unexpected(
                                            message = error.message ?: "Failed to await operation settlement",
                                            error = error
                                        )
                                    }
                                ) { operationId, operationResult -> Event.OpResult.PasskeyAttestation(operationId, operationResult) }
                                State.Active(controller, controller.operationId, updatedContext)
                            }
                        }
                    }
                }
            }
        }

    private suspend fun handlePasskeyAttestationOpResult(current: State.Active, event: Event.OpResult.PasskeyAttestation): State {
        val loginId = current.flowContext.loginId ?: return State.Completed(
            FlowResult.Failure(
                BoostCreatePasskeyFlowFailure.Unexpected(errorCode = ErrorCode.InvalidArgument, message = "LoginId is required for create-passkey response")
            )
        )

        return when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostCreatePasskeyFlowImpl.handlePasskeyAttestationOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )
                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostCreatePasskeyFlowImpl.handlePasskeyAttestationOpResult.failure",
                    message = result.failure.message
                )

                val createPasskeyResponse = BoostFlow.Response.CreatePasskey(loginId = loginId, proofToken = null, ownIdData = null)
                State.Completed(FlowResult.Success(createPasskeyResponse))
            }

            is OperationResult.Success -> {
                journey?.completeOperation(event.operationID)

                val createPasskeyResponse = BoostFlow.Response.CreatePasskey(
                    loginId = loginId,
                    proofToken = result.success.proofToken,
                    ownIdData = result.success.ownIdData
                )
                State.Completed(FlowResult.Success(createPasskeyResponse))
            }
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

    private fun launchFlowObserver(
        controller: BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure>,
        eventFactory: (FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>) -> Event
    ) {
        launch {
            val result = try {
                controller.whenSettled()
            } catch (_: CancellationException) {
                FlowResult.Canceled(Reason.SystemError("Operation canceled"))
            } catch (error: Exception) {
                FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = error.message ?: "Failed to await flow settlement", error = error))
            }
            eventChannel.trySend(eventFactory(result))
        }
    }

    private suspend fun createPasskeyAttestationController(flowContext: BoostFlowContext): OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
        val passkeyAttestationOperation = ownIdOperation.passkeys.create
        passkeyAttestationOperation.getUnsatisfiedDependencies()?.let { deps ->
            val unavailableDetails = "PasskeyAttestationOperation unavailable. Missing dependencies: ${deps.joinToString()}"
            return object : OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
                override val operationId: OperationID = OperationType.PASSKEY_CREATION.createUID()
                override suspend fun whenSettled(): OperationResult<AttestationResponse, PasskeyAttestationOperationFailure> =
                    CompletableDeferred(
                        OperationResult.Failure(
                            PasskeyAttestationOperationFailure.Unexpected(errorCode = ErrorCode.IntegrationError, message = unavailableDetails)
                        )
                    ).await()

                override fun abort(reason: Reason) = Unit
            }
        }

        val params = PasskeyAttestationOperation.Params(loginId = flowContext.loginId, traceParent = flowContext.traceParent)

        return when (val availability = passkeyAttestationOperation.availability(params)) {
            is Availability.Available -> passkeyAttestationOperation.start(params)
            is Availability.Unavailable ->
                object : OperationController<AttestationResponse, PasskeyAttestationOperationFailure> {
                    override val operationId: OperationID = OperationType.PASSKEY_CREATION.createUID()
                    override suspend fun whenSettled(): OperationResult<AttestationResponse, PasskeyAttestationOperationFailure> =
                        CompletableDeferred(
                            OperationResult.Failure(
                                PasskeyAttestationOperationFailure.Unexpected(errorCode = ErrorCode.IntegrationError, message = availability.message)
                            )
                        ).await()

                    override fun abort(reason: Reason) = Unit
                }
        }
    }

    private suspend fun finalizeLoginWithSessionCreate(
        loginId: LoginId, authMethod: AuthMethod, accessToken: AccessToken, sessionPayload: String
    ): FlowResult<BoostFlow.Response.Login, BoostCreatePasskeyFlowFailure> {
        val baseResponse = BoostFlow.Response.Login(
            loginId = loginId, authMethod = authMethod, accessToken = accessToken, sessionPayload = sessionPayload, session = null
        )

        val sessionCreate = this.sessionCreate ?: return FlowResult.Success(baseResponse)
        val params = SessionCreate.Params(loginId, accessToken, authMethod, sessionPayload)

        return runCatching {
            withContext(Dispatchers.Main.immediate) {
                if (sessionCreate.isAvailable(params)) sessionCreate.create(params).getOrThrow() else return@withContext null
            }
        }.fold(
            onSuccess = { session ->
                FlowResult.Success(
                    BoostFlow.Response.Login(
                        loginId = loginId, authMethod = authMethod, accessToken = accessToken, sessionPayload = sessionPayload, session = session?.session
                    )
                )
            },
            onFailure = { error ->
                if (error is CancellationException) {
                    FlowResult.Canceled(Reason.SystemError("Session creation canceled"))
                } else {
                    FlowResult.Failure(
                        BoostCreatePasskeyFlowFailure.SessionCreationFailed(
                            errorCode = ErrorCode.IntegrationError,
                            message = "Session creation failed: ${error.message}",
                            error = error
                        )
                    )
                }
            }
        )
    }

    private fun OperationEntry<*, *, *>.getUnsatisfiedDependencies(): List<String>? = (this as DIResolvable).getUnsatisfiedDependencies()

    private fun BoostLoginFlowFailure.toCreatePasskeyFailure(): BoostCreatePasskeyFlowFailure = when (this) {
        is BoostLoginFlowFailure.Input.UnresolvedLoginId -> BoostCreatePasskeyFlowFailure.Input.UnresolvedLoginId(errorCode, message, error)
        is BoostLoginFlowFailure.Account.Blocked -> BoostCreatePasskeyFlowFailure.Account.Blocked(errorCode, message)
        is BoostLoginFlowFailure.Account.NotFound -> BoostCreatePasskeyFlowFailure.Account.NotFound(errorCode, message)
        is BoostLoginFlowFailure.OperationFailed -> BoostCreatePasskeyFlowFailure.OperationFailed(
            operationType = operationType,
            errorCode = errorCode,
            message = message,
            operationId = operationId,
            operationFailure = operationFailure,
            error = error
        )

        is BoostLoginFlowFailure.InsufficientAuth -> BoostCreatePasskeyFlowFailure.InsufficientAuth(errorCode, message)
        is BoostLoginFlowFailure.SessionCreationFailed -> BoostCreatePasskeyFlowFailure.SessionCreationFailed(errorCode, message, error)
        is BoostLoginFlowFailure.Unexpected -> BoostCreatePasskeyFlowFailure.Unexpected(errorCode, message, error)
    }

    internal companion object {
        fun create(resolver: DIContainerResolver): BoostFlow.CreatePasskey = resolver.runCatchingExceptions {
            val sharedJourney = getOrNull<UserJourney>()
            BoostCreatePasskeyFlowImpl(
                userRepository = getOrNull<UserRepository>(),
                ownIdOperation = (this as DIContainer).opsNamespace.withContext(scopeName = "BoostFlow.CreatePasskey") { },
                boostFlowLogin = BoostLoginFlowImpl.create(this, userJourneyOverride = sharedJourney),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                serialization = getOrThrow<Serialization>(),
                userJourney = sharedJourney,
                sessionCreate = getOrNull<SessionCreate>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    @InternalOwnIdAPI
    private class Failed(error: Throwable) : BoostFlow.CreatePasskey {
        private val failure = BoostCreatePasskeyFlowFailure.Unexpected(
            message = (if (error is MissingDependencyException) error.dependencyName else error.message) ?: "Failed to create Boost Create Passkey flow",
            error = error
        )

        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> =
            object : BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> {
                override suspend fun whenSettled(): FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure> = FlowResult.Failure(failure)
                override fun abort(reason: Reason) = Unit
            }
    }
}
