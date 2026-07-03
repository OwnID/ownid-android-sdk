package com.ownid.sdk.ops

import android.webkit.WebView
import androidx.annotation.ColorInt
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ops.ui.WebBridgeUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runs the OwnID Elite web-based authentication flow inside a WebView.
 *
 * Call [start] to launch the operation. The returned [WebBridgeOperationController] exposes
 * [WebBridgeOperationController.state] so SDK-owned UI and callers can observe the operation lifecycle.
 *
 * The operation resolves the WebView content, loads it into SDK-owned UI, and bridges hosted flow events to
 * app-owned native callbacks through [WebBridgeOperationEventWrapper]. Callback failures are reported to the web flow
 * as bridge errors; the native operation is settled only after a terminal callback completes successfully.
 *
 * Hosted terminal events such as `onFinish`, `onError`, `onClose`, and `onNativeAction` complete the operation with
 * [OperationResult.Success] after the app callback returns. User close, WebView detach, [WebBridgeOperationController.abort],
 * and parent-scope cancellation complete with [OperationResult.Canceled]. Invalid resolved base URL/origin, UI startup
 * failures, bridge injection failures, JavaScript load/exception callbacks, WebView load failures, and WebView
 * render-process termination complete with [OperationResult.Failure].
 *
 * Base URL, HTML, and User-Agent values are resolved from [Params.options], server WebView configuration, local runtime
 * information, and SDK defaults. [Params.onBaseUrlResolved] receives the resolved base URL before UI startup; exceptions
 * thrown by that callback are ignored.
 *
 * The resolved base URL must produce an origin. That origin is used as an explicit WebBridge origin rule and is combined
 * with server-configured allowed origins by the WebBridge. Messages from untrusted origins are not accepted. The
 * SDK-managed WebView keeps the hosted flow document in the WebView, blocks local/script schemes, and opens external
 * navigations outside the embedded flow.
 */
@InternalOwnIdAPI
internal interface WebBridgeOperation : OperationCapability<WebBridgeOperation.Params, Unit, WebBridgeOperationFailure> {

    /**
     * Parameters for [WebBridgeOperation].
     *
     * @property options Display and content options for the WebView. Defaults to `null`, which lets the operation use
     *   server WebView configuration, local runtime information, and SDK defaults.
     * @property eventWrappers App-owned event handlers that map hosted WebBridge actions to native callbacks. Defaults
     *   to `null`, which registers no operation-supplied wrappers.
     * @property onBaseUrlResolved Optional best-effort callback invoked with the resolved base URL for the HTML page.
     *   Exceptions thrown by this callback are ignored and do not change operation settlement. Defaults to `null`.
     */
    class Params(
        val options: WebBridgeOperationOptions? = null,
        val eventWrappers: List<WebBridgeOperationEventWrapper>? = null,
        val onBaseUrlResolved: ((String) -> Unit)? = null
    ) : Capability.Params

    /**
     * State machine for [WebBridgeOperation].
     *
     * Exposed by [WebBridgeOperationController.state].
     *
     * States progress from [Created] to [Active] and then [Completed] with an [OperationResult]. A settled operation
     * does not emit another terminal state.
     */
    sealed interface State {
        /** Initial state before the operation starts. */
        object Created : State

        /** The WebView UI has been requested and the web-based flow is in progress. */
        data class Active(val uiState: WebBridgeUi.UiState) : State

        /** The operation finished with a success, cancellation, or failure result. */
        data class Completed(val result: OperationResult<Unit, WebBridgeOperationFailure>) : State
    }

}

internal sealed interface WebBridgeOperationFailure : OperationFailure {
    /**
     * About: The web bridge operation could not start because required local configuration or runtime preconditions are missing.
     *
     * End-user: No direct user action. The app should offer another available path.
     *
     * Developer action: Check WebView options, resolved base URL, HTML configuration, and bridge setup before starting the operation.
     */
    class Precondition(override val errorCode: ErrorCode, override val message: String) : WebBridgeOperationFailure {
        override fun toString(): String = "Precondition(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The WebView UI failed while loading or running the web bridge flow.
     *
     * End-user: Show a generic unavailable state or let the user retry opening the web flow.
     *
     * Developer action: Inspect WebView lifecycle, render-process state, bridge events, and [error].
     *
     * @property error Underlying WebView/runtime error retained for diagnostics, when available.
     */
    class Ui(override val errorCode: ErrorCode, override val message: String, val error: Throwable? = null) : WebBridgeOperationFailure {
        override fun toString(): String = "Ui(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The operation stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: Show a generic failure state. Retrying may be reasonable if the app can safely restart the web flow.
     *
     * Developer action: Log operation context and inspect [error] before retrying automatically.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    class Unexpected(override val errorCode: ErrorCode = ErrorCode.Unknown, override val message: String, val error: Throwable? = null) :
        WebBridgeOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}

/** Controller for a running [WebBridgeOperation]. */
@InternalOwnIdAPI
internal interface WebBridgeOperationController : OperationController<Unit, WebBridgeOperationFailure> {

    /**
     * Observable state of the running web bridge operation.
     *
     * Emits [WebBridgeOperation.State] transitions from [WebBridgeOperation.State.Created]
     * through [WebBridgeOperation.State.Active] to [WebBridgeOperation.State.Completed].
     */
    val state: StateFlow<WebBridgeOperation.State>
}

/**
 * Display and content options for the [WebBridgeOperation] WebView.
 *
 * Use these options to override HTML content, base URL, User-Agent string, and SDK-owned activity system bar colors.
 *
 * All properties default to `null`. Content values fall back to server WebView configuration, local runtime
 * information, and then SDK defaults. Color values fall back to the SDK-owned container defaults.
 */
@InternalOwnIdAPI
internal class WebBridgeOperationOptions(
    /**
     * Base URL for the HTML page.
     *
     * The operation must be able to derive an origin from this value. Page URLs are accepted; path, query, and fragment
     * are ignored for origin matching. Invalid values fail the operation before UI presentation.
     */
    val baseUrl: String? = null,

    /** Raw HTML to render with [WebView.loadDataWithBaseURL], using the resolved [baseUrl]. */
    val html: String? = null,

    /** Custom WebView User-Agent. */
    val userAgent: String? = null,

    /** Color for the SDK-owned activity status bar, or `null` for the container default. */
    @ColorInt val statusBarColor: Int? = null,

    /** Color for the SDK-owned activity navigation bar, or `null` for the container default. */
    @ColorInt val navigationBarColor: Int? = null
)

/**
 * Wraps a customer-provided callback for a single WebView event in the OwnID Elite flow.
 *
 * Each wrapper maps a [webBridgePluginAction] name from the hosted flow to a native suspend function owned by the app
 * or SDK operation. Built-in operation wrappers include [OnNativeAction], [OnFinish], [OnError], and [OnClose].
 *
 * When [isTerminal] is `true`, a successful callback result closes the WebView and completes the operation with
 * [OperationResult.Success]. If the callback throws or its payload cannot be decoded, the hosted flow receives a bridge
 * error and the native operation remains active until another terminal event, cancellation, or failure occurs.
 */
@InternalOwnIdAPI
internal interface WebBridgeOperationEventWrapper {

    /** The WebView bridge action name this wrapper handles. */
    val webBridgePluginAction: String

    /** If `true`, the WebView closes after this event completes. */
    val isTerminal: Boolean

    /**
     * Invokes the wrapped callback with the given JSON parameters.
     *
     * Built-in wrappers dispatch app callbacks on [Dispatchers.Main.immediate].
     *
     * @param params JSON-encoded event payload, or `null` if the event carries no data.
     * @param serialization JSON encoder/decoder for payload serialization.
     *
     * @return A [JsonElement] result passed back to the WebView.
     */
    @Throws
    suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement

    /**
     * Handles the `onNativeAction` event.
     *
     * This terminal event is triggered when the hosted flow requires app-owned native handling. The operation closes
     * the WebView and settles successfully after [onNativeAction] returns.
     *
     * @param onNativeAction Suspend function invoked with the login ID, optional OwnID data, and optional access token.
     */
    class OnNativeAction(
        private val onNativeAction: suspend (loginId: String, ownIdData: String?, accessToken: AccessToken?) -> Unit
    ) : WebBridgeOperationEventWrapper {

        @Serializable
        internal class Payload(
            internal val loginId: String,
            internal val ownIdData: String? = null,
            internal val authToken: String? = null
        )

        override val webBridgePluginAction: String = "onNativeAction"
        override val isTerminal: Boolean = true

        @Throws
        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
            val payload = serialization.decodeFromString(Payload.serializer(), requireNotNull(params) { "Unexpected: params=null" })
            withContext(Dispatchers.Main.immediate) {
                onNativeAction.invoke(payload.loginId, payload.ownIdData, payload.authToken?.let { AccessToken(it) })
            }
            return JsonPrimitive(true)
        }
    }

    /**
     * Handles the `onFinish` event.
     *
     * This terminal event is triggered when the hosted authentication flow completes successfully. The operation closes
     * the WebView and settles successfully after [onFinish] returns.
     *
     * @property onFinish Suspend function invoked with the login ID, auth method, and optional access token.
     */

    class OnFinish(
        private val onFinish: suspend (loginId: String, authMethod: AuthMethod, accessToken: AccessToken?) -> Unit
    ) : WebBridgeOperationEventWrapper {

        internal companion object {
            internal val DEFAULT = OnFinish { _, _, _ -> }
        }

        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        internal class Payload(
            internal val loginId: String,
            // Unused internal val source: String, // 'mobile' : 'desktop'
            // Unused internal val context: String? = null, // OwnIdContext
            @JsonNames("authType", "authMethod")
            internal val authMethod: AuthMethod,
            internal val authToken: String? = null
        )

        override val webBridgePluginAction: String = "onFinish"
        override val isTerminal: Boolean = true

        @Throws
        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
            val payload = serialization.decodeFromString(Payload.serializer(), requireNotNull(params) { "Unexpected: params=null" })
            val accessToken = payload.authToken?.let { AccessToken(it) }
            withContext(Dispatchers.Main.immediate) {
                onFinish.invoke(payload.loginId, payload.authMethod, accessToken)
            }
            return JsonPrimitive(true)
        }
    }

    /**
     * Handles the `onError` event.
     *
     * This terminal event is triggered when the hosted flow reports an application-level error. The operation closes
     * the WebView and settles successfully after [onError] returns; the error string is delivered to the app callback.
     *
     * @property onError Suspend function invoked with the optional error details.
     */
    class OnError(
        private val onError: suspend (error: String?) -> Unit
    ) : WebBridgeOperationEventWrapper {
        internal companion object {
            internal val DEFAULT = OnError { }
        }

        override val webBridgePluginAction: String = "onError"
        override val isTerminal: Boolean = true

        @Throws
        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
            withContext(Dispatchers.Main.immediate) {
                onError.invoke(params)
            }
            return JsonPrimitive(true)
        }
    }

    /**
     * Handles the `onClose` event.
     *
     * This terminal event is triggered when the hosted flow reports a close event. The operation closes the WebView and
     * settles successfully after [onClose] returns. Native user-close controls use [OperationResult.Canceled] instead.
     *
     * @property onClose Suspend function invoked when the OwnID flow is closed.
     */
    class OnClose(
        private val onClose: suspend () -> Unit
    ) : WebBridgeOperationEventWrapper {

        internal companion object {
            internal val DEFAULT = OnClose { }
        }

        override val webBridgePluginAction: String = "onClose"
        override val isTerminal: Boolean = true

        @Throws
        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
            withContext(Dispatchers.Main.immediate) {
                onClose.invoke()
            }
            return JsonPrimitive(true)
        }
    }
}
