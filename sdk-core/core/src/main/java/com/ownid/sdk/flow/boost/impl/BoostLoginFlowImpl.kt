package com.ownid.sdk.flow.boost.impl

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
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
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessOrProofToken
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
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.TokenException
import com.ownid.sdk.model.User
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.onAvailable
import com.ownid.sdk.model.onUnavailable
import com.ownid.sdk.model.rawLoginId
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.namespace.DIResolvable
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.namespace.opsNamespace
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
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
 * Runtime for one [BoostFlow.Login] run.
 *
 * Maintainer invariant: keep this runtime one-shot and preserve the public [BoostFlow.Login] result mapping. Source and
 * trace metadata must not affect the developer-facing flow result or [SessionCreate] boundary.
 */
@OptIn(InternalOwnIdAPI::class)
internal class BoostLoginFlowImpl(
    private val userRepository: UserRepository?,
    private val ownIdOperation: OwnIdOperation,
    private val userJourney: UserJourney?,
    private val sessionCreate: SessionCreate?,
    private val loginIdValidator: LoginIdValidator,
    private val serialization: Serialization,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val context: Context?,
    private val logger: OwnIdLogRouter?
) : BoostFlow.Login, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.BoostFlow.Login")) {

    private sealed interface State {
        object Created : State
        data class Active(val controller: OperationController<*, *>, val flowContext: BoostFlowContext) : State
        data class Completed(val result: FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>) : State
    }

    private sealed interface Event {
        class Start(val flowContext: BoostFlowContext) : Event
        class Abort(val reason: Reason) : Event

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

            class PasskeyAssertion(
                override val operationID: OperationID,
                override val result: OperationResult<AccessToken, PasskeyAssertionOperationFailure>
            ) : OpResult<AccessToken, PasskeyAssertionOperationFailure>

            class PasskeyAttestation(
                override val operationID: OperationID,
                override val result: OperationResult<AttestationResponse, PasskeyAttestationOperationFailure>
            ) : OpResult<AttestationResponse, PasskeyAttestationOperationFailure>

            class PasskeyEnroll(
                override val operationID: OperationID,
                override val result: OperationResult<Unit, PasskeyEnrollOperationFailure>
            ) : OpResult<Unit, PasskeyEnrollOperationFailure>

            class Verification(
                override val operationID: OperationID,
                override val result: OperationResult<AccessOrProofToken, OperationFailure>
            ) : OpResult<AccessOrProofToken, OperationFailure>
        }
    }

    private val resultDeferred = CompletableDeferred<FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>>()
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var journey: UserJourney.Controller? = null
    private val startAccepted = AtomicBoolean(false)
    private val activeControllerRef = AtomicReference<OperationController<*, *>?>(null)
    private val controller: BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> =
        object : BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
            override suspend fun whenSettled(): FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> = resultDeferred.await()
            override fun abort(reason: Reason) {
                logger?.logD(this@BoostLoginFlowImpl, "abort", "$reason")
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

                        is Event.OpResult.LoginIdCollect ->
                            if (current is State.Active) handleLoginIdCollectOpResult(current, event) else current

                        is Event.OpResult.Login ->
                            if (current is State.Active) handleLoginOpResult(current, event) else current

                        is Event.OpResult.PasskeyAssertion ->
                            if (current is State.Active) handlePasskeyAssertionOpResult(current, event) else current

                        is Event.OpResult.PasskeyAttestation ->
                            if (current is State.Active) handlePasskeyAttestationOpResult(current, event) else current

                        is Event.OpResult.PasskeyEnroll ->
                            if (current is State.Active) handlePasskeyEnrollOpResult(current, event) else current

                        is Event.OpResult.Verification ->
                            if (current is State.Active) handleVerificationOpResult(current, event) else current
                    }
                } catch (error: CancellationException) {
                    val reason = Reason.SystemError("Operation canceled")
                    if (current is State.Active) {
                        journey?.completeOperation(
                            current.controller.operationId,
                            errorCode = ErrorCode.Aborted,
                            source = "BoostLoginFlowImpl.reducer.canceled",
                            message = "Canceled with reason: $reason"
                        )
                    }
                    logger?.logI(this@BoostLoginFlowImpl, "Reducer canceled", "$error", error)
                    State.Completed(FlowResult.Canceled(reason))
                } catch (error: Exception) {
                    if (current is State.Active) {
                        journey?.completeOperation(
                            current.controller.operationId,
                            errorCode = ErrorCode.Unknown,
                            source = "BoostLoginFlowImpl.reducer.failure",
                            message = "Flow error in Boost Login"
                        )
                    }
                    logger?.logW(this@BoostLoginFlowImpl, "Reducer error", "$error", error)
                    State.Completed(FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = "Flow error in Boost Login", error = error)))
                }
            }
            .catch { error ->
                eventChannel.close(error)
                logger?.logW(this@BoostLoginFlowImpl, "Flow error", "$error", error)
                emit(State.Completed(FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = "Flow error in Boost Login", error = error))))
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
                                journey?.completeFlow(UserJourney.Controller.Outcome.LoggedIn(flowResult.success.authMethod))

                            is FlowResult.Canceled ->
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = ErrorCode.Aborted,
                                        source = "BoostLoginFlowImpl.completeFlow.canceled",
                                        message = "Canceled with reason: ${flowResult.reason}"
                                    )
                                )

                            is FlowResult.Failure ->
                                journey?.completeFlow(
                                    UserJourney.Controller.Outcome.Error(
                                        errorCode = flowResult.failure.errorCode,
                                        source = "BoostLoginFlowImpl.completeFlow.failure",
                                        message = flowResult.failure.message
                                    )
                                )
                        }

                        if (newState.result is FlowResult.Success) {
                            val user = User(newState.result.success.loginId, newState.result.success.authMethod)
                            runCatching { userRepository?.setLastUser(user) }.onFailure { cause ->
                                if (cause is CancellationException) throw cause
                                logger?.logI(this@BoostLoginFlowImpl, "UserRepository.setLastUser", "Failed: ${cause.message}", cause)
                            }
                        }

                        this@BoostLoginFlowImpl.cancel()
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
                            source = "BoostLoginFlowImpl.scopeCompletion.canceled",
                            message = "Canceled with reason: ${result.reason}"
                        )
                    )
                    eventChannel.close()
                }
            }
        }
    }

    override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
        if (!startAccepted.compareAndSet(false, true)) return this@BoostLoginFlowImpl.controller

        val ctx = context.copy {
            if (traceParent == null) traceParent = TraceContext.generateTraceparent()
        }
        eventChannel.trySend(Event.Start(ctx))
        return this@BoostLoginFlowImpl.controller
    }

    private suspend fun handleStartEvent(current: State, event: Event.Start): State {
        if (current !is State.Created) return current

        var flowContext = event.flowContext

        journey = userJourney?.startFlow(
            name = "login",
            source = flowContext.source ?: FlowInfo.Source.EXPLICIT,
            traceParent = flowContext.traceParent
        )

        val loginForbidden = flowContext.authRequiredResponse
        flowContext = flowContext.copy { authRequiredResponse = null }

        if (loginForbidden != null) {
            val controller = object : OperationController<LoginResponse, LoginOperationFailure> {
                override val operationId: OperationID = OperationType.SESSION_CREATION.createUID()
                override suspend fun whenSettled(): OperationResult<LoginResponse, LoginOperationFailure> = OperationResult.Success(loginForbidden)
                override fun abort(reason: Reason) = Unit
            }
            journey?.startOperation(controller.operationId)
            launchOperationObserver(
                controller,
                { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
            ) { operationId, result -> Event.OpResult.Login(operationId, result) }
            return State.Active(controller, flowContext)
        }

        val resolvedAccessToken = flowContext.accessToken ?: context?.accessToken
        if (resolvedAccessToken != null) {
            val resolvedLoginId = try {
                resolvedAccessToken.getLoginId(serialization, loginIdValidator)
            } catch (error: TokenException) {
                return State.Completed(
                    FlowResult.Failure(
                        BoostLoginFlowFailure.Input.UnresolvedLoginId(
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
                    FlowResult.Failure(BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, message))
                )
            } ?: run {
                journey?.setUserInfo(resolvedLoginId)
                val updatedContext = flowContext.copy {
                    accessToken = resolvedAccessToken
                    loginId = resolvedLoginId
                    authMethod = AuthMethod.Immediate
                }
                val params = LoginOperation.Params(accessToken = updatedContext.accessToken, traceParent = updatedContext.traceParent)
                when (val availability = loginOperation.availability(params)) {
                    is Availability.Available -> {
                        val controller = loginOperation.start(params)
                        journey?.startOperation(controller.operationId)
                        launchOperationObserver(
                            controller,
                            { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                        ) { operationId, result -> Event.OpResult.Login(operationId, result) }
                        State.Active(controller, updatedContext)
                    }

                    is Availability.Unavailable -> State.Completed(
                        FlowResult.Failure(
                            BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
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
                logger?.logI(this@BoostLoginFlowImpl, "UserRepository.getLastUser", "Failed: ${cause.message}", cause)
            }.getOrNull()?.let { lastUserLoginId ->
                flowContext = flowContext.copy {
                    loginId = lastUserLoginId
                }
            }
        }

        val loginIdCollectOperation = ownIdOperation.withContext("BoostFlow.Login.LoginIdCollectOperation") {
            authz = flowContext.loginId?.let { Authz.start(it) } ?: flowContext.rawLoginId?.let { Authz.start(it) }
        }.loginId.collect

        return loginIdCollectOperation.getUnsatisfiedDependencies()?.let { deps ->
            val message = "LoginIdCollect operation unavailable. Missing dependencies: ${deps.joinToString()}"
            State.Completed(FlowResult.Failure(BoostLoginFlowFailure.OperationFailed(OperationType.LOGIN_ID_COLLECT, ErrorCode.IntegrationError, message)))
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
                    State.Active(controller, flowContext)
                }

                is Availability.Unavailable -> State.Completed(
                    FlowResult.Failure(BoostLoginFlowFailure.OperationFailed(OperationType.LOGIN_ID_COLLECT, ErrorCode.IntegrationError, availability.message))
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
                    source = "BoostLoginFlowImpl.handleLoginIdCollectOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handleLoginIdCollectOpResult.failure",
                    message = result.failure.message
                )

                State.Completed(
                    FlowResult.Failure(
                        BoostLoginFlowFailure.OperationFailed(
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
                        FlowResult.Failure(BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, message))
                    )
                } ?: run {
                    val updatedContext = current.flowContext.copy {
                        loginId = result.success
                        authMethod = AuthMethod.Immediate
                    }
                    val params = LoginOperation.Params(
                        loginId = result.success,
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
                            State.Active(controller, updatedContext)
                        }

                        is Availability.Unavailable -> State.Completed(
                            FlowResult.Failure(
                                BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
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
                    source = "BoostLoginFlowImpl.handleLoginOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handleLoginOpResult.failure",
                    message = result.failure.message
                )

                State.Completed(
                    FlowResult.Failure(
                        BoostLoginFlowFailure.OperationFailed(
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
                        val pToken = current.flowContext.proofToken

                        val updatedContext = current.flowContext.copy {
                            accessToken = loginResponse.accessToken
                            sessionPayload = loginResponse.sessionPayload
                            authRequiredResponse = null
                            proofToken = null
                        }

                        val passkeyEnrollOperation = ownIdOperation.passkeys.enroll
                        if (pToken == null || !passkeyEnrollOperation.canResolve()) {
                            val loginId = updatedContext.loginId
                            if (loginId == null) {
                                State.Completed(
                                    FlowResult.Failure(
                                        BoostLoginFlowFailure.Unexpected(message = "Missing required loginId in login success context")
                                    )
                                )
                            } else {
                                State.Completed(
                                    finalizeLoginWithSessionCreate(
                                        loginId = loginId,
                                        authMethod = updatedContext.authMethod ?: AuthMethod.Unknown,
                                        accessToken = loginResponse.accessToken,
                                        sessionPayload = loginResponse.sessionPayload
                                    )
                                )
                            }
                        } else {
                            val params = PasskeyEnrollOperation.Params(
                                accessToken = loginResponse.accessToken,
                                proofToken = pToken,
                                traceParent = updatedContext.traceParent
                            )

                            when (passkeyEnrollOperation.availability(params)) {
                                is Availability.Available -> {
                                    val controller = passkeyEnrollOperation.start(params)
                                    journey?.startOperation(controller.operationId)
                                    launchOperationObserver(
                                        controller,
                                        { error ->
                                            PasskeyEnrollOperationFailure.Unexpected(
                                                message = error.message ?: "Failed to await operation settlement",
                                                error = error
                                            )
                                        }
                                    ) { operationId, operationResult -> Event.OpResult.PasskeyEnroll(operationId, operationResult) }
                                    State.Active(controller, updatedContext)
                                }

                                is Availability.Unavailable -> {
                                    val loginId = updatedContext.loginId
                                    if (loginId == null) {
                                        State.Completed(
                                            FlowResult.Failure(
                                                BoostLoginFlowFailure.Unexpected(message = "Missing required loginId in login success context")
                                            )
                                        )
                                    } else {
                                        State.Completed(
                                            finalizeLoginWithSessionCreate(
                                                loginId = loginId,
                                                authMethod = updatedContext.authMethod ?: AuthMethod.Unknown,
                                                accessToken = loginResponse.accessToken,
                                                sessionPayload = loginResponse.sessionPayload
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is LoginResponse.AccountBlocked -> {
                        val message = loginResponse.reason ?: "Account is blocked"
                        journey?.completeOperation(
                            event.operationID,
                            errorCode = ErrorCode.UserBlocked,
                            source = "BoostLoginFlowImpl.handleLoginOpResult.accountBlocked",
                            message = message
                        )
                        State.Completed(FlowResult.Failure(BoostLoginFlowFailure.Account.Blocked(errorCode = ErrorCode.UserBlocked, message = message)))
                    }

                    is LoginResponse.AccountNotFound -> {
                        val message = loginResponse.reason ?: "Account not found"
                        journey?.completeOperation(
                            event.operationID,
                            errorCode = ErrorCode.UserNotFound,
                            source = "BoostLoginFlowImpl.handleLoginOpResult.accountNotFound",
                            message = message
                        )
                        State.Completed(FlowResult.Failure(BoostLoginFlowFailure.Account.NotFound(errorCode = ErrorCode.UserNotFound, message = message)))
                    }

                    is LoginResponse.AuthRequired -> {
                        val updatedContext = current.flowContext.copy {
                            authRequiredResponse = loginResponse
                            accessToken = null
                            authMethod = null
                        }

                        when {
                            loginResponse.authRequirements.isTargetScoreAchievable().not() -> {
                                journey?.completeOperation(event.operationID)
                                State.Completed(
                                    FlowResult.Failure(BoostLoginFlowFailure.InsufficientAuth(ErrorCode.Unknown, "Target score is not achievable"))
                                )
                            }

                            else -> {
                                journey?.completeOperation(event.operationID)
                                selectAndRunNextAuthOperation(updatedContext)
                            }
                        }
                    }
                }
            }
        }

    private suspend fun handlePasskeyAssertionOpResult(current: State.Active, event: Event.OpResult.PasskeyAssertion): State =
        when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostLoginFlowImpl.handlePasskeyAssertionOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

                val updatedContext = current.flowContext.copy {
                    addFailedOperation(OperationType.PASSKEY_AUTH)
                    addFailedOperation(OperationType.PASSKEY_CREATION)
                }
                selectAndRunNextAuthOperation(updatedContext)
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handlePasskeyAssertionOpResult.failure",
                    message = result.failure.message
                )

                val flowContext = current.flowContext.copy {
                    addFailedOperation(OperationType.PASSKEY_AUTH)
                    if (result.failure !is PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys) {
                        addFailedOperation(OperationType.PASSKEY_CREATION)
                    }
                }
                selectAndRunNextAuthOperation(flowContext)
            }

            is OperationResult.Success -> {
                journey?.completeOperation(event.operationID)

                val updatedContext = current.flowContext.copy {
                    accessToken = result.success
                    authMethod = AuthMethod.Passkey
                    addSucceedOperation(OperationType.PASSKEY_AUTH)
                }

                if (updatedContext.isTargetScoreAchieved()) {
                    val clearedContext = updatedContext.copy { authRequiredResponse = null }
                    val params = LoginOperation.Params(accessToken = result.success, traceParent = clearedContext.traceParent)
                    val loginOperation = ownIdOperation.login

                    when (val availability = loginOperation.availability(params)) {
                        is Availability.Available -> {
                            val controller = loginOperation.start(params)
                            journey?.startOperation(controller.operationId)
                            launchOperationObserver(
                                controller,
                                { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                            ) { operationId, operationResult -> Event.OpResult.Login(operationId, operationResult) }
                            State.Active(controller, clearedContext)
                        }

                        is Availability.Unavailable -> State.Completed(
                            FlowResult.Failure(
                                BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
                            )
                        )
                    }
                } else {
                    selectAndRunNextAuthOperation(updatedContext)
                }
            }
        }

    private suspend fun handlePasskeyAttestationOpResult(current: State.Active, event: Event.OpResult.PasskeyAttestation): State {
        // Optional passkey creation must not affect auth score, but must not be retried.
        var flowContext = current.flowContext.copy { addFailedOperation(OperationType.PASSKEY_CREATION) }

        when (val result = event.result) {
            is OperationResult.Canceled ->
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostLoginFlowImpl.handlePasskeyAttestationOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

            is OperationResult.Failure ->
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handlePasskeyAttestationOpResult.failure",
                    message = result.failure.message
                )

            is OperationResult.Success -> {
                journey?.completeOperation(event.operationID)
                flowContext = flowContext.copy {
                    proofToken = result.success.proofToken
                }
            }
        }

        return selectAndRunNextAuthOperation(flowContext)
    }

    private suspend fun handlePasskeyEnrollOpResult(current: State.Active, event: Event.OpResult.PasskeyEnroll): State {
        when (val result = event.result) {
            is OperationResult.Canceled ->
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostLoginFlowImpl.handlePasskeyEnrollOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )

            is OperationResult.Failure ->
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handlePasskeyEnrollOpResult.failure",
                    message = result.failure.message
                )

            is OperationResult.Success ->
                journey?.completeOperation(event.operationID)
        }

        val sessionPayload = current.flowContext.sessionPayload ?: return State.Completed(
            FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = "Missing required sessionPayload in login success context"))
        )
        val loginId = current.flowContext.loginId ?: return State.Completed(
            FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = "Missing required loginId in login success context"))
        )
        val accessToken = current.flowContext.accessToken ?: return State.Completed(
            FlowResult.Failure(BoostLoginFlowFailure.Unexpected(message = "Missing required accessToken in login success context"))
        )

        return State.Completed(
            finalizeLoginWithSessionCreate(
                loginId = loginId,
                authMethod = current.flowContext.authMethod ?: AuthMethod.Unknown,
                accessToken = accessToken,
                sessionPayload = sessionPayload
            )
        )
    }

    private suspend fun handleVerificationOpResult(current: State.Active, event: Event.OpResult.Verification): State {
        return when (val result = event.result) {
            is OperationResult.Canceled -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = ErrorCode.Aborted,
                    source = "BoostLoginFlowImpl.handleVerificationOpResult.canceled",
                    message = "Canceled with reason: ${result.reason}"
                )
                State.Completed(FlowResult.Canceled(result.reason))
            }

            is OperationResult.Failure -> {
                journey?.completeOperation(
                    event.operationID,
                    errorCode = result.failure.errorCode,
                    source = "BoostLoginFlowImpl.handleVerificationOpResult.failure",
                    message = result.failure.message
                )
                State.Completed(
                    FlowResult.Failure(
                        BoostLoginFlowFailure.OperationFailed(
                            operationType = event.operationID.type,
                            errorCode = result.failure.errorCode,
                            message = "Verification operation failed: ${result.failure.message}",
                            operationId = event.operationID,
                            operationFailure = result.failure
                        )
                    )
                )
            }

            is OperationResult.Success -> {
                journey?.completeOperation(event.operationID)

                val accessToken = when (val token = result.success) {
                    is AccessToken -> token
                    is ProofToken -> {
                        return State.Completed(
                            FlowResult.Failure(
                                BoostLoginFlowFailure.OperationFailed(
                                    operationType = event.operationID.type,
                                    errorCode = ErrorCode.IntegrationError,
                                    operationId = event.operationID,
                                    message = "Verification operation returned proofToken, but accessToken is required for Boost Login"
                                )
                            )
                        )
                    }
                }

                val updatedContext = current.flowContext.copy {
                    this.accessToken = accessToken
                    authMethod = AuthMethod.Otp
                    addSucceedOperation(event.operationID.type)
                }

                if (updatedContext.isTargetScoreAchieved()) {
                    val clearedContext = updatedContext.copy { authRequiredResponse = null }
                    val loginOperation = ownIdOperation.login
                    val params = LoginOperation.Params(accessToken = accessToken, traceParent = clearedContext.traceParent)

                    when (val availability = loginOperation.availability(params)) {
                        is Availability.Available -> {
                            val controller = loginOperation.start(params)
                            journey?.startOperation(controller.operationId)
                            launchOperationObserver(
                                controller,
                                { error -> LoginOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                            ) { operationId, operationResult -> Event.OpResult.Login(operationId, operationResult) }
                            State.Active(controller, clearedContext)
                        }

                        is Availability.Unavailable -> State.Completed(
                            FlowResult.Failure(
                                BoostLoginFlowFailure.OperationFailed(OperationType.SESSION_CREATION, ErrorCode.IntegrationError, availability.message)
                            )
                        )
                    }
                } else {
                    selectAndRunNextAuthOperation(updatedContext)
                }
            }
        }
    }

    private suspend fun selectAndRunNextAuthOperation(initialContext: BoostFlowContext): State {
        initialContext.authRequiredResponse ?: return State.Completed(
            FlowResult.Failure(BoostLoginFlowFailure.InsufficientAuth(errorCode = ErrorCode.Unknown, message = "No next operation available"))
        )

        val passedTypes = initialContext.requestedOps?.keys.orEmpty()
        val serverNextOpTypes = initialContext.getNextOperationType().orEmpty()
        val passkeyCreationAttempted = OperationType.PASSKEY_CREATION in passedTypes
        val passkeyAuthAttempted = OperationType.PASSKEY_AUTH in passedTypes
        val shouldTryPasskeyCreationBeforeFallback =
            !passkeyCreationAttempted &&
                    initialContext.isAuthOperationAllowed(OperationType.PASSKEY_CREATION) &&
                    OperationType.PASSKEY_CREATION !in serverNextOpTypes &&
                    OperationType.PASSKEY_AUTH !in serverNextOpTypes &&
                    (passedTypes.isEmpty() || passkeyAuthAttempted)

        val nextOpTypes = if (passkeyCreationAttempted) {
            serverNextOpTypes.filter { it == OperationType.EMAIL_VERIFICATION || it == OperationType.PHONE_NUMBER_VERIFICATION }
        } else if (shouldTryPasskeyCreationBeforeFallback) {
            listOf(OperationType.PASSKEY_CREATION) + serverNextOpTypes
        } else {
            serverNextOpTypes
        }

        if (nextOpTypes.isEmpty()) return State.Completed(
            FlowResult.Failure(BoostLoginFlowFailure.InsufficientAuth(errorCode = ErrorCode.Unknown, message = "No next operation available"))
        )

        val unavailableReasons = mutableListOf<String>()
        var flowContext = initialContext

        for (opType in nextOpTypes) {
            when (opType) {
                OperationType.PASSKEY_CREATION -> {
                    startPasskeyCreationIfAvailable(flowContext)?.let { return it }
                    flowContext = flowContext.copy { addFailedOperation(OperationType.PASSKEY_CREATION) }
                }

                OperationType.PASSKEY_AUTH -> {
                    val passkeyAssertionOperation = ownIdOperation.passkeys.auth
                    passkeyAssertionOperation.getUnsatisfiedDependencies()?.let { deps ->
                        unavailableReasons += "${OperationType.PASSKEY_AUTH}: missing dependencies: ${deps.joinToString()}"
                        flowContext = flowContext.copy {
                            addFailedOperation(OperationType.PASSKEY_AUTH)
                        }
                        return selectAndRunNextAuthOperation(flowContext)
                    } ?: run {
                        val params = PasskeyAssertionOperation.Params(
                            loginId = flowContext.loginId,
                            accessToken = flowContext.accessToken,
                            traceParent = flowContext.traceParent
                        )

                        passkeyAssertionOperation.availability(params)
                            .onAvailable {
                                val controller = passkeyAssertionOperation.start(params)
                                journey?.startOperation(controller.operationId)
                                launchOperationObserver(
                                    controller,
                                    { error ->
                                        PasskeyAssertionOperationFailure.Unexpected(
                                            message = error.message ?: "Failed to await operation settlement",
                                            error = error
                                        )
                                    }
                                ) { operationId, result -> Event.OpResult.PasskeyAssertion(operationId, result) }
                                return State.Active(controller, flowContext)
                            }
                            .onUnavailable { message ->
                                unavailableReasons += "${OperationType.PASSKEY_AUTH}: $message"
                                flowContext = flowContext.copy {
                                    addFailedOperation(OperationType.PASSKEY_AUTH)
                                }
                                return selectAndRunNextAuthOperation(flowContext)
                            }
                    }
                }

                OperationType.EMAIL_VERIFICATION -> {
                    val emailVerificationOperation = ownIdOperation.verifications.email
                    emailVerificationOperation.getUnsatisfiedDependencies()?.let { deps ->
                        unavailableReasons += "${OperationType.EMAIL_VERIFICATION}: missing dependencies: ${deps.joinToString()}"
                        flowContext = flowContext.copy { addFailedOperation(OperationType.EMAIL_VERIFICATION) }
                    } ?: run {
                        val operationRequirement = flowContext.authRequiredResponse?.authRequirements?.operations
                            ?.firstOrNull { it.type == OperationType.EMAIL_VERIFICATION }

                        val params = EmailVerificationOperation.Params(
                            loginId = flowContext.loginId,
                            loginIdHintId = operationRequirement?.channels?.firstOrNull()?.id,
                            accessToken = flowContext.accessToken,
                            onUiClick = journey?.let { { operationId -> launch { journey?.addOperationClick(operationId) } } },
                            traceParent = flowContext.traceParent
                        )

                        emailVerificationOperation.availability(params)
                            .onAvailable {
                                val controller = emailVerificationOperation.start(params)
                                journey?.startOperation(controller.operationId)
                                launchOperationObserver(
                                    controller,
                                    { error ->
                                        EmailVerificationOperationFailure.Unexpected(
                                            message = error.message ?: "Failed to await operation settlement",
                                            error = error
                                        )
                                    }
                                ) { operationId, result -> Event.OpResult.Verification(operationId, result) }
                                return State.Active(controller, flowContext)
                            }
                            .onUnavailable { message ->
                                unavailableReasons += "${OperationType.EMAIL_VERIFICATION}: $message"
                                flowContext = flowContext.copy { addFailedOperation(OperationType.EMAIL_VERIFICATION) }
                            }
                    }
                }

                OperationType.PHONE_NUMBER_VERIFICATION -> {
                    val phoneVerificationOperation = ownIdOperation.verifications.phone
                    phoneVerificationOperation.getUnsatisfiedDependencies()?.let { deps ->
                        unavailableReasons += "${OperationType.PHONE_NUMBER_VERIFICATION}: missing dependencies: ${deps.joinToString()}"
                        flowContext = flowContext.copy { addFailedOperation(OperationType.PHONE_NUMBER_VERIFICATION) }
                    } ?: run {
                        val operationRequirement = flowContext.authRequiredResponse?.authRequirements?.operations
                            ?.firstOrNull { it.type == OperationType.PHONE_NUMBER_VERIFICATION }

                        val params = PhoneVerificationOperation.Params(
                            loginId = flowContext.loginId,
                            loginIdHintId = operationRequirement?.channels?.firstOrNull()?.id,
                            accessToken = flowContext.accessToken,
                            onUiClick = journey?.let { { operationId -> launch { journey?.addOperationClick(operationId) } } },
                            traceParent = flowContext.traceParent
                        )

                        phoneVerificationOperation.availability(params)
                            .onAvailable {
                                val controller = phoneVerificationOperation.start(params)
                                journey?.startOperation(controller.operationId)
                                launchOperationObserver(
                                    controller,
                                    { error ->
                                        PhoneVerificationOperationFailure.Unexpected(
                                            message = error.message ?: "Failed to await operation settlement",
                                            error = error
                                        )
                                    }
                                ) { operationId, result -> Event.OpResult.Verification(operationId, result) }
                                return State.Active(controller, flowContext)
                            }
                            .onUnavailable { message ->
                                unavailableReasons += "${OperationType.PHONE_NUMBER_VERIFICATION}: $message"
                                flowContext = flowContext.copy { addFailedOperation(OperationType.PHONE_NUMBER_VERIFICATION) }
                            }
                    }
                }

                else -> Unit
            }
        }

        val unavailableDetails = unavailableReasons.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = ". Unavailable operations: ", separator = "; ")
            .orEmpty()

        return State.Completed(
            FlowResult.Failure(
                BoostLoginFlowFailure.InsufficientAuth(
                    errorCode = ErrorCode.Unknown,
                    message = "No operation available in runtime from: [${nextOpTypes.joinToString()}]$unavailableDetails"
                )
            )
        )
    }

    private suspend fun startPasskeyCreationIfAvailable(flowContext: BoostFlowContext): State.Active? {
        val passkeyAttestationOperation = ownIdOperation.passkeys.create
        if (!passkeyAttestationOperation.canResolve()) return null

        val params = PasskeyAttestationOperation.Params(
            loginId = flowContext.loginId,
            accessToken = flowContext.accessToken,
            traceParent = flowContext.traceParent
        )

        return when (passkeyAttestationOperation.availability(params)) {
            is Availability.Available -> {
                val controller = passkeyAttestationOperation.start(params)
                journey?.startOperation(controller.operationId)
                launchOperationObserver(
                    controller,
                    { error -> PasskeyAttestationOperationFailure.Unexpected(message = error.message ?: "Failed to await operation settlement", error = error) }
                ) { operationId, result -> Event.OpResult.PasskeyAttestation(operationId, result) }
                State.Active(controller, flowContext)
            }

            is Availability.Unavailable -> null
        }
    }

    private suspend fun finalizeLoginWithSessionCreate(
        loginId: LoginId, authMethod: AuthMethod, accessToken: AccessToken, sessionPayload: String
    ): FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> {
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
                        BoostLoginFlowFailure.SessionCreationFailed(ErrorCode.IntegrationError, "Session creation failed: ${error.message}", error)
                    )
                }
            }
        )
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

    private fun OperationEntry<*, *, *>.canResolve(): Boolean = (this as DIResolvable).canResolve()

    private fun OperationEntry<*, *, *>.getUnsatisfiedDependencies(): List<String>? = (this as DIResolvable).getUnsatisfiedDependencies()

    internal companion object {
        fun create(resolver: DIContainerResolver, userJourneyOverride: UserJourney? = null): BoostFlow.Login = resolver.runCatchingExceptions {
            BoostLoginFlowImpl(
                userRepository = getOrNull<UserRepository>(),
                ownIdOperation = (this as DIContainer).opsNamespace.withContext(scopeName = "BoostFlow.Login") { },
                userJourney = userJourneyOverride ?: getOrNull<UserJourney>(),
                sessionCreate = getOrNull<SessionCreate>(),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                serialization = getOrThrow<Serialization>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                context = getOrNull<Context>(),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : BoostFlow.Login {
        private val failure: FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> = FlowResult.Failure(
            BoostLoginFlowFailure.Unexpected(
                message = (if (error is MissingDependencyException) error.dependencyName else error.message) ?: "Failed to create Boost Login flow",
                error = error
            )
        )

        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> =
            object : BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
                override suspend fun whenSettled(): FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> = CompletableDeferred(failure).await()
                override fun abort(reason: Reason) = Unit
            }
    }
}
