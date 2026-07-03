package com.ownid.sdk.ops.impl

import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationController
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.ui.WebBridgeUi
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgeElitePlugin
import com.ownid.sdk.ui.webbridge.impl.OriginNormalizer
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold

/**
 * Owns the WebBridge operation state machine.
 *
 * Start resolves WebView content and options in caller, server-config, local-runtime, then SDK-default order; validates
 * the resolved page origin used for bridge attachment; and publishes a [WebBridgeUi.UiState] for SDK-managed WebView
 * presentation.
 *
 * The state machine settles once. Successful terminal hosted callbacks settle with
 * [OperationResult.Success], UI startup or WebView runtime errors settle with [OperationResult.Failure], and user close,
 * WebView detach, abort, and parent-scope cancellation settle with [OperationResult.Canceled]. Completion unregisters
 * the controller and cancels operation-scoped work so late WebView callbacks cannot produce another terminal result.
 */
@InternalOwnIdAPI
internal class WebBridgeOperationImpl internal constructor(
    override val operationType: OperationType,
    private val operationRegistry: OperationRegistryImpl,
    private val configuration: OwnIdConfiguration,
    private val appConfigProvider: AppConfigProvider,
    private val localInfo: LocalInfo,
    private val ui: WebBridgeUi,
    private val webBridge: WebBridge,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val unsatisfiedDependencies: List<String>?,
    private val logger: OwnIdLogRouter?
) : WebBridgeOperation, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.WebBridgeOperation")) {

    private sealed interface Event {
        class Start(val params: WebBridgeOperation.Params?) : Event
        class Abort(val reason: Reason) : Event

        sealed interface Ui : Event {
            object OnClose : Ui
            class OnError(val error: Throwable?, val message: String?) : Ui
            class OnCancel(val reason: Reason) : Ui
        }
    }

    private val resultDeferred = CompletableDeferred<OperationResult<Unit, WebBridgeOperationFailure>>()

    private val operationId: OperationID = operationType.createUID()

    private val controller: WebBridgeOperationController = object : WebBridgeOperationController {
        override val operationId: OperationID = this@WebBridgeOperationImpl.operationId
        override val state: StateFlow<WebBridgeOperation.State>
            get() = stateFlow

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = resultDeferred.await()
        override fun abort(reason: Reason) {
            logger?.logD(this@WebBridgeOperationImpl, "abort", "$reason")
            eventChannel.trySend(Event.Abort(reason))
        }
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)

    private val stateFlow = MutableStateFlow<WebBridgeOperation.State>(WebBridgeOperation.State.Created)

    init {
        eventChannel.receiveAsFlow()
            .runningFold(WebBridgeOperation.State.Created) { current: WebBridgeOperation.State, event -> reduceState(current, event) }
            .catch { error ->
                if (error is CancellationException) throw error
                eventChannel.close(error)
                logger?.logW(this@WebBridgeOperationImpl, "Operation error", "$error", error)
                emit(
                    WebBridgeOperation.State.Completed(
                        OperationResult.Failure(WebBridgeOperationFailure.Unexpected(message = "Operation error in $operationType", error = error))
                    )
                )
            }
            .onEach { newState ->
                stateFlow.value = newState
                if (newState is WebBridgeOperation.State.Completed) {
                    if (resultDeferred.complete(newState.result)) {
                        newState.result
                            .onError { logger?.logD(this, "Completed with error", it.message) }
                            .onCanceled { logger?.logD(this, "Canceled with reason", "$it") }
                        eventChannel.close()
                        operationRegistry.unregister(operationId)
                        this@WebBridgeOperationImpl.cancel()
                    }
                }
            }
            .launchIn(this)

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException && !resultDeferred.isCompleted) {
                val result = OperationResult.Canceled(Reason.SystemError("Operation canceled"))
                if (resultDeferred.complete(result)) {
                    stateFlow.value = WebBridgeOperation.State.Completed(result)
                    eventChannel.close()
                    operationRegistry.unregister(operationId)
                }
            }
        }
    }

    override fun start(params: WebBridgeOperation.Params?): WebBridgeOperationController {
        eventChannel.trySend(Event.Start(params))
        return controller
    }

    override suspend fun availability(params: Capability.Params?): Availability {
        unsatisfiedDependencies?.let { deps ->
            return Availability.Unavailable("Missing dependencies: ${deps.joinToString()}")
        }
        if (params != null && params !is WebBridgeOperation.Params) {
            return Availability.Unavailable("Unsupported params type: ${params::class.qualifiedName}")
        }
        return Availability.Available()
    }

    private suspend fun reduceState(current: WebBridgeOperation.State, event: Event): WebBridgeOperation.State = when (event) {
        is Event.Start -> when (current) {
            is WebBridgeOperation.State.Created -> {
                operationRegistry.register(controller)

                val registeredPlugins = webBridge.plugins.snapshot()
                val elitePlugin = registeredPlugins.firstOrNull { it is WebBridgeElitePlugin } as? WebBridgeElitePlugin

                elitePlugin?.apply {
                    addEventWrappers(event.params?.eventWrappers ?: emptyList())
                    setWrapperSideEffect { wrapper -> if (wrapper.isTerminal) eventChannel.trySend(Event.Ui.OnClose) }
                }

                val appConfig = appConfigProvider.getOrFetchConfig().webView
                val baseUrl = event.params?.options?.baseUrl ?: appConfig?.baseUrl ?: WebBridgeUi.WEBVIEW_URL
                try {
                    event.params?.onBaseUrlResolved?.invoke(baseUrl)
                } catch (error: Exception) {
                    logger?.logI(this@WebBridgeOperationImpl, "onBaseUrlResolved", "Ignored callback failure: ${error.message}", error)
                }
                val html = event.params?.options?.html ?: appConfig?.html ?: WebBridgeUi.getHTML(configuration)
                val userAgent = event.params?.options?.userAgent ?: localInfo.userAgent
                val pageOriginRule = OriginNormalizer.pageUrlToOriginOrNull(baseUrl)

                if (pageOriginRule == null) {
                    WebBridgeOperation.State.Completed(
                        OperationResult.Failure(
                            WebBridgeOperationFailure.Precondition(
                                errorCode = ErrorCode.Unknown,
                                message = "Invalid baseUrl for WebBridge origin derivation: $baseUrl"
                            )
                        )
                    )
                } else {
                    ui.start(controller) { error: Throwable?, message: String? ->
                        logger?.logW(this, "Event.Start", "UI start error [$message]: $error", error)
                        eventChannel.trySend(Event.Ui.OnError(error, message))
                    }

                    WebBridgeOperation.State.Active(
                        WebBridgeUi.UiState(
                            baseUrl, html, userAgent,
                            statusBarColor = event.params?.options?.statusBarColor,
                            navigationBarColor = event.params?.options?.navigationBarColor,
                            doWebViewBridgeInject = { webView ->
                                webBridge.attach(webView, setOf(pageOriginRule))?.let { error ->
                                    logger?.logW(this@WebBridgeOperationImpl, "Event.Start", "WebBridge inject failed: ${error.message}", error)
                                    eventChannel.trySend(Event.Ui.OnError(error, "WebBridge injection failed: ${error.message}"))
                                }
                            },
                            onWebViewDetach = {
                                runCatchingExceptions { webBridge.detach() }.onFailure { error ->
                                    logger?.logW(this@WebBridgeOperationImpl, "Event.Start", "WebBridge detach failed: ${error.message}", error)
                                }
                                eventChannel.trySend(Event.Ui.OnCancel(Reason.UserClose("Elite UI detached")))
                            },
                            onWebViewTerminalError = { error, message -> eventChannel.trySend(Event.Ui.OnError(error, message)) },
                            onWebViewCancel = { reason -> eventChannel.trySend(Event.Ui.OnCancel(reason)) }
                        )
                    )
                }
            }

            is WebBridgeOperation.State.Active -> current

            is WebBridgeOperation.State.Completed -> current
        }

        is Event.Abort -> when (current) {
            is WebBridgeOperation.State.Created ->
                WebBridgeOperation.State.Completed(OperationResult.Canceled(event.reason))

            is WebBridgeOperation.State.Active ->
                WebBridgeOperation.State.Completed(OperationResult.Canceled(event.reason))

            is WebBridgeOperation.State.Completed -> current
        }

        is Event.Ui -> when (current) {
            is WebBridgeOperation.State.Created -> unexpectedEventFailure(event, current)

            is WebBridgeOperation.State.Active -> when (event) {
                is Event.Ui.OnClose -> WebBridgeOperation.State.Completed(OperationResult.Success(Unit))

                is Event.Ui.OnError -> WebBridgeOperation.State.Completed(
                    OperationResult.Failure(
                        WebBridgeOperationFailure.Ui(
                            errorCode = ErrorCode.Unknown,
                            message = event.message ?: event.error?.message ?: event.error?.toString() ?: "WebBridge UI failed",
                            error = event.error
                        )
                    )
                )

                is Event.Ui.OnCancel -> WebBridgeOperation.State.Completed(OperationResult.Canceled(event.reason))
            }

            is WebBridgeOperation.State.Completed -> current
        }
    }

    private fun unexpectedEventFailure(event: Event, current: WebBridgeOperation.State): WebBridgeOperation.State =
        WebBridgeOperation.State.Completed(
            OperationResult.Failure(
                WebBridgeOperationFailure.Unexpected(
                    message = "${this::class.simpleName}: Unexpected event [${event::class.simpleName}] for state [${current::class.simpleName}]"
                )
            )
        )

    internal companion object {

        fun create(resolver: DIContainerResolver): WebBridgeOperation = resolver.runCatchingExceptions {
            WebBridgeOperationImpl(
                operationType = OperationType.WEB_BRIDGE,
                operationRegistry = getOrThrow<OperationRegistry>() as OperationRegistryImpl,
                configuration = getOrThrow<OwnIdConfiguration>(),
                appConfigProvider = getOrThrow<AppConfigProvider>(),
                localInfo = getOrThrow<LocalInfo>(),
                ui = getOrThrow<WebBridgeUi>(),
                webBridge = getOrThrow<WebBridge>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                unsatisfiedDependencies = getUnsatisfiedDependencies(WebBridgeOperation::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> Failed(error) }
    }

    private class Failed(error: Throwable) : WebBridgeOperation {

        override val operationType: OperationType = OperationType.WEB_BRIDGE
        private val operationId: OperationID = operationType.createUID()

        private val failure = WebBridgeOperationFailure.Unexpected(
            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error.toString(),
            error = error
        )

        private val failureState = MutableStateFlow(WebBridgeOperation.State.Completed(OperationResult.Failure(failure)))

        private val failureController: WebBridgeOperationController = object : WebBridgeOperationController {
            override val operationId: OperationID = this@Failed.operationId
            override val state: StateFlow<WebBridgeOperation.State>
                get() = failureState

            override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Failure(failure)
            override fun abort(reason: Reason) = Unit
        }

        private val controller: WebBridgeOperationController = failureController
        override fun start(params: WebBridgeOperation.Params?): WebBridgeOperationController = controller
        override suspend fun availability(params: Capability.Params?): Availability = Availability.Unavailable(failure.message)
    }
}
