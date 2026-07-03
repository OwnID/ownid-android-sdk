@file:JvmName("OwnIdEliteFlowContexts")

package com.ownid.sdk.flow.elite

import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import com.ownid.sdk.flow.elite.EliteFlowContext.Companion.Empty
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.ops.WebBridgeOperationOptions

@DslMarker
internal annotation class OwnIdEliteFlowContextDsl

/**
 * Creates an [EliteFlowContext] using a DSL builder.
 *
 * Use [EliteFlowContext.Builder.options] for WebView content and display overrides, and
 * [EliteFlowContext.Builder.events] for hosted-page event callbacks. Omitted values use [EliteFlowContext.Empty]
 * behavior.
 */
@Suppress("NOTHING_TO_INLINE")
@JvmSynthetic
public inline fun EliteFlowContext(noinline block: EliteFlowContext.Builder.() -> Unit): EliteFlowContext =
    EliteFlowContext.Builder().apply(block).build()

/**
 * Immutable configuration for the Elite web-based authentication flow.
 *
 * Carries optional WebView options and event handlers. The Elite flow accepts WebView configuration only; it does not
 * accept an app-provided access token, proof token, or headless flag. Hosted-page callbacks may deliver an access token
 * when the hosted flow sends one.
 *
 * Public contexts are immutable; use the [EliteFlowContext] DSL function or [Builder] to create a new value for each
 * flow run.
 *
 * Build it via the [EliteFlowContext] DSL function or [Builder], or use [Empty] for default behavior.
 *
 * If you omit [EliteFlowEventBuilder.onFinish], [EliteFlowEventBuilder.onError], or [EliteFlowEventBuilder.onClose],
 * the flow installs default no-op hosted-page handlers. [EliteFlowEventBuilder.onNativeAction] is installed only when
 * configured. Hosted-page terminal handlers close the WebView after the handler returns and complete the flow; direct
 * WebView close/cancel events complete as cancellation. Custom handlers run on the main thread and are responsible only
 * for app-side work; the SDK still owns WebView closure. If a custom handler throws, the hosted page receives a bridge
 * error and the flow remains active until another terminal event, cancellation, or WebView failure occurs.
 */
public class EliteFlowContext private constructor(
    private val values: Map<Key<*>, Any>
) {
    private interface Key<T : Any>

    @InternalOwnIdAPI
    private object OptionsKey : Key<WebBridgeOperationOptions>

    @InternalOwnIdAPI
    private object EventsKey : Key<List<WebBridgeOperationEventWrapper>>

    @InternalOwnIdAPI
    internal val options: WebBridgeOperationOptions?
        get() = values[OptionsKey] as? WebBridgeOperationOptions

    @InternalOwnIdAPI
    @Suppress("UNCHECKED_CAST")
    internal val eventsWrappers: List<WebBridgeOperationEventWrapper>?
        get() = values[EventsKey] as? List<WebBridgeOperationEventWrapper>

    public companion object {
        /**
         * A context with no overrides.
         *
         * The flow uses server WebView configuration and SDK defaults, installs no-op finish/error/close handlers, and
         * does not register a native-action callback.
         */
        public val Empty: EliteFlowContext = Builder().build()
    }

    /** DSL builder for [EliteFlowContext]. */
    @OwnIdEliteFlowContextDsl
    public class Builder {
        private val map = linkedMapOf<Key<*>, Any>()

        /**
         * Configures WebView display options.
         *
         * Calling this function replaces any options previously configured on this builder. Values not set in [block]
         * fall back to server configuration or SDK defaults.
         */
        @OptIn(InternalOwnIdAPI::class)
        public fun options(block: EliteFlowOptionsBuilder.() -> Unit) {
            map[OptionsKey] = EliteFlowOptionsBuilder().apply(block).build()
        }

        /**
         * Configures hosted-page event handlers for the Elite flow.
         *
         * Calling this function replaces any handlers previously configured on this builder.
         */
        @OptIn(InternalOwnIdAPI::class)
        public fun events(block: EliteFlowEventBuilder.() -> Unit) {
            map[EventsKey] = EliteFlowEventBuilder().apply(block).build()
        }

        /** Creates an [EliteFlowContext] from the builder's current state. */
        public fun build(): EliteFlowContext = EliteFlowContext(map.toMap())
    }
}

/**
 * Builder for WebView display options used by the Elite flow.
 *
 * All values are optional. Leave a value `null` to use server configuration or SDK defaults for that field.
 */
@OwnIdEliteFlowContextDsl
@OptIn(InternalOwnIdAPI::class)
public class EliteFlowOptionsBuilder internal constructor() {

    /**
     * Base URL for the HTML page. Defaults to null and falls back to server configuration.
     *
     * The flow must be able to derive an origin from the resolved value. Page URLs are accepted; path, query, and
     * fragment are ignored for origin matching. Invalid values fail the flow before WebView presentation.
     */
    public var baseUrl: String? = null

    /** Raw HTML to render in the SDK-managed WebView. Defaults to null and falls back to server configuration or SDK defaults. */
    public var html: String? = null

    /** Custom WebView User-Agent. Defaults to null and falls back to the SDK User-Agent value. */
    public var userAgent: String? = null

    /** Color for the SDK-managed activity status bar. Defaults to null to keep the container default. */
    @ColorInt
    public var statusBarColor: Int? = null

    /** Color for the SDK-managed activity navigation bar. Defaults to null to keep the container default. */
    @ColorInt
    public var navigationBarColor: Int? = null

    internal fun build(): WebBridgeOperationOptions = WebBridgeOperationOptions(baseUrl, html, userAgent, statusBarColor, navigationBarColor)
}

/**
 * Builder for defining hosted-page event handlers for the OwnID Elite flow.
 *
 * All configured handlers run on the main thread. Each event can be set once on this builder; setting the same event
 * again replaces the previous handler. Hosted `onFinish`, `onNativeAction`, `onError`, and `onClose` events are
 * terminal after the configured handler returns successfully.
 */
@OwnIdEliteFlowContextDsl
public class EliteFlowEventBuilder {
    private var onNativeAction: (suspend (loginId: String, ownIdData: String?, accessToken: AccessToken?) -> Unit)? = null
    private var onFinish: (suspend (loginId: String, authMethod: AuthMethod, accessToken: AccessToken?) -> Unit)? = null
    private var onError: (suspend (error: String?) -> Unit)? = null
    private var onClose: (suspend () -> Unit)? = null

    /**
     * Sets the handler for the `onNativeAction` event.
     *
     * This event is triggered when the hosted flow requires app-owned native handling. The handler is terminal; the
     * WebView closes and the flow completes successfully after it returns. The SDK does not create an app session from
     * this event.
     *
     * @param block Suspend function invoked on main thread with the login ID, optional OwnID data, and optional access token
     *   emitted by the hosted page.
     */
    public fun onNativeAction(@MainThread block: suspend (loginId: String, ownIdData: String?, accessToken: AccessToken?) -> Unit) {
        onNativeAction = block
    }

    /**
     * Sets the handler for the `onFinish` event.
     *
     * This event is triggered when the hosted authentication flow completes successfully. The handler is terminal; the
     * WebView closes and the flow completes successfully after it returns. Use the optional access token at the app
     * authentication boundary if your integration creates an app session from this outcome.
     *
     * @param block Suspend function invoked on main thread with the login ID, auth method, and optional access token
     *   emitted by the hosted page.
     */
    public fun onFinish(@MainThread block: suspend (loginId: String, authMethod: AuthMethod, accessToken: AccessToken?) -> Unit) {
        onFinish = block
    }

    /**
     * Sets the handler for the `onError` event.
     *
     * This event is triggered when the hosted flow reports an application-level error. The handler is terminal; the
     * WebView closes and the flow completes successfully after it returns. Treat the optional string as display or
     * diagnostic context from the hosted page, not as an SDK failure payload.
     *
     * @param block Suspend function invoked on main thread with optional error details.
     */
    public fun onError(@MainThread block: suspend (error: String?) -> Unit) {
        onError = block
    }

    /**
     * Sets the handler for the `onClose` event.
     *
     * This event is triggered when the hosted flow reports a close event. The handler is terminal; the WebView closes
     * and the flow completes successfully after it returns. Native user-close controls and controller cancellation
     * complete the flow with cancellation instead.
     *
     * @param block Suspend function invoked on main thread when the flow closes.
     */
    public fun onClose(@MainThread block: suspend () -> Unit) {
        onClose = block
    }

    @InternalOwnIdAPI
    internal fun build(): List<WebBridgeOperationEventWrapper> = buildList {
        onNativeAction?.let { add(WebBridgeOperationEventWrapper.OnNativeAction(it)) }
        onFinish?.let { add(WebBridgeOperationEventWrapper.OnFinish(it)) }
        onError?.let { add(WebBridgeOperationEventWrapper.OnError(it)) }
        onClose?.let { add(WebBridgeOperationEventWrapper.OnClose(it)) }
    }
}
