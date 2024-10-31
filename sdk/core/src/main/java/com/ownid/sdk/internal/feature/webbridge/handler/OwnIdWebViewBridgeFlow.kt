package com.ownid.sdk.internal.feature.webbridge.handler

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import com.ownid.sdk.internal.feature.webflow.OnErrorEvent
import com.ownid.sdk.internal.feature.webflow.OnErrorWrapper
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowAction
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowEvent
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowEventBus
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeFlow : OwnIdWebViewBridgeImpl.NamespaceHandler {

    @VisibleForTesting
    internal class Config(
        val actions: Array<String>,
        val actionWrapperMap: Map<String, OwnIdFlowWrapper<JsonSerializable>>,
        val eventBus: OwnIdFlowEventBus.EventBus
    ) {
        @Throws(IllegalArgumentException::class, NoSuchElementException::class)
        internal fun getFlowEvent(action: String?, params: String?, webViewCallback: (String?) -> Unit): OwnIdFlowEvent {
            val wrapper = actionWrapperMap.firstNotNullOfOrNull { (key, value) ->
                if (key.equals(action, ignoreCase = true)) value else null
            } ?: throw IllegalArgumentException("OwnIdWebViewBridgeFlow: Unsupported action: $action")

            val flowAction = OwnIdFlowAction.values().first { it.webAction.equals(action, ignoreCase = true) }

            return flowAction.eventFactory.create(wrapper, params, webViewCallback)
        }

        internal fun send(flowEvent: OwnIdFlowEvent) = eventBus.send(flowEvent)

        internal fun addInvokeOnClose(action: () -> Unit) = eventBus.addInvokeOnClose(action)

        internal companion object {
            @Suppress("UNCHECKED_CAST")
            internal fun create(wrappers: List<OwnIdFlowWrapper<JsonSerializable>>, eventBus: OwnIdFlowEventBus.EventBus): Config {
                val actionWrapperMap = wrappers.associateBy { wrapper ->
                    OwnIdFlowAction.values().firstOrNull { it.wrapperKlass == wrapper::class }?.webAction
                }.filterKeys { it != null } as Map<String, OwnIdFlowWrapper<JsonSerializable>>

                return Config(actionWrapperMap.keys.toTypedArray(), actionWrapperMap, eventBus)
            }
        }
    }

    private val config: AtomicReference<Config?> = AtomicReference(null)

    override val namespace: OwnIdWebViewBridge.Namespace = OwnIdWebViewBridge.Namespace.FLOW

    override val actions: Array<String>
        get() = config.get()?.actions ?: emptyArray()

    @UiThread
    override fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        bridgeContext.launch {
            try {
                val currentConfig = config.get() ?: throw IllegalArgumentException("OwnIdWebViewBridgeFlow: Not initialized")

                val flowEvent = currentConfig.getFlowEvent(action, params) { wrapperResult: String? ->
                    bridgeContext.launch { bridgeContext.finishWithSuccess(wrapperResult ?: "{}") }
                }

                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFlow, "handle", flowEvent::class.java.simpleName)
                currentConfig.send(flowEvent)
            } catch (cause: CancellationException) {
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeFlow, cause)
                throw cause
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeFlow, "handle: $action", cause.message, cause)
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeFlow, cause)
            }
        }
    }

    internal fun sendCloseEvent() {
        config.get()?.run {
            send(getFlowEvent(OwnIdFlowAction.ON_CLOSE.webAction, null, {}))
        } ?: OwnIdInternalLogger.logW(this, "sendCloseEvent", "No config available")
    }

    internal fun sendErrorEvent(error: OwnIdException) {
        config.get()?.run {
            send(getFlowEvent(OwnIdFlowAction.ON_ERROR.webAction, error.message, {}))
        } ?: OwnIdInternalLogger.logW(this, "sendErrorEvent", "No config available")
    }

    internal fun addInvokeOnClose(action: () -> Unit) {
        config.get()?.addInvokeOnClose(action) ?: OwnIdInternalLogger.logW(this, "addInvokeOnClose", "No config available")
    }

    internal fun setWrappers(
        ownIdCore: OwnIdCoreImpl,
        wrappers: List<OwnIdFlowWrapper<JsonSerializable>>,
    ): AutoCloseable {
        val flowJob = Job()

        flowJob.invokeOnCompletion {
            OwnIdInternalLogger.logD(this, "start.invokeOnClose", "Cleaning actions")
            config.set(null)
        }

        val bus = OwnIdFlowEventBus.create(flowJob)

        config.set(Config.create(wrappers, bus))

        bus.consumeAsHotFlow()
            .onEach { flowEvent ->
                OwnIdInternalLogger.logD(this, "start:onEach", flowEvent::class.simpleName)

                flowEvent.onReceiveSideEffect.invoke(ownIdCore)

                if (flowEvent.isTerminal.not()) {
                    val wrapperResult = flowEvent.wrapper.invoke(flowEvent.payload)
                    flowEvent.webViewCallback.invoke(wrapperResult?.toJson())
                } else {
                    // Dispatch wrapper invocation independently from flow scope as it will be canceled now
                    // Extract wrapper and payload only not to hold reference to full flowEvent
                    val wrapper = flowEvent.wrapper
                    val payload = flowEvent.payload
                    val errorWrapper = wrappers.filterIsInstance<OnErrorWrapper>().firstOrNull()
                    CoroutineScope(Job() + Dispatchers.Main).launch {
                        runCatching {
                            wrapper.invoke(payload)
                        }.recoverCatching { cause ->
                            if (cause is CancellationException) return@launch
                            val error = OwnIdException.map("OwnID flow error: ${cause.message}", cause)
                            OwnIdInternalLogger.logW(this, "start.terminal.recoverCatching", error.message, error)
                            errorWrapper?.invoke(OnErrorEvent.Payload(error))
                        }.onFailure { cause ->
                            if (cause is CancellationException) return@launch
                            OwnIdInternalLogger.logW(this, "start.terminal.recoverCatching.onFailure", cause.message, cause)
                        }
                    }

                    throw CancellationException()
                }
            }
            .onCompletion {
                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFlow, "start", "onCompletion")
                bus.close()
            }
            .catch { cause ->
                if (cause is CancellationException) return@catch

                val error = OwnIdException.map("OwnID flow error: ${cause.message}", cause)
                OwnIdInternalLogger.logW(this, "start.catch", error.message, error)

                wrappers.filterIsInstance<OnErrorWrapper>().firstOrNull()?.invoke(OnErrorEvent.Payload(error))
            }
            .catch { cause ->
                if (cause is CancellationException) return@catch
                OwnIdInternalLogger.logW(this, "start.catch.catch", cause.message, cause)
            }
            .launchIn(CoroutineScope(flowJob + Dispatchers.Main.immediate))

        return AutoCloseable {
            if (flowJob.isActive) {
                OwnIdInternalLogger.logD(this, "start", "Flow Request Canceled")
                ownIdCore.eventsService.sendMetric(Metric.Category.General, Metric.EventType.Track, "Flow Request Canceled")

                bus.close()
            }
        }
    }
}