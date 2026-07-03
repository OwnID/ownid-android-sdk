package com.ownid.sdk.flow.elite

import androidx.annotation.CheckResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.flow.FlowCapability
import com.ownid.sdk.flow.FlowFailure
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.namespace.OwnIdFlows
import com.ownid.sdk.ops.OperationFailure

/**
 * Web-based authentication flow that renders OwnID's hosted page inside an SDK-managed WebView.
 *
 * Start the flow with an optional [EliteFlowContext] to configure WebView content options and event handlers. The SDK
 * resolves the WebView content from the context, server configuration, and SDK defaults, then bridges hosted-page
 * events to native handlers.
 *
 * The app does not provide an access token, proof token, or headless flag when starting this flow. Tokens are
 * caller-visible only when the hosted page sends an access token to [EliteFlowContext] callbacks. The SDK does not
 * create an app session from those callbacks; handle any session handoff at the app authentication boundary.
 *
 * The SDK owns WebView presentation and dismissal. The app owns the returned [Controller] and any native callback work
 * started from the context.
 *
 * Functional outcomes are delivered through hosted-page event handlers. The returned [Controller] is a high-level
 * handle for awaiting settlement, canceling the running WebView flow, or detecting infrastructure failures.
 *
 * Terminal event handlers close the WebView and settle the controller with [FlowResult.Success] after the handler
 * returns, including hosted `onError` and `onClose` events. This success result means the hosted terminal event was
 * handled; it does not mean hosted authentication succeeded. [FlowResult.Failure] is reserved for SDK or WebBridge
 * failures, such as a missing WebBridge operation or a WebView-level failure. [FlowResult.Canceled] is used when the
 * app aborts the controller, the SDK is shut down, or the WebView is canceled before a terminal hosted-page event
 * completes.
 *
 * A flow instance represents one run. Keep the returned [Controller] until [Controller.whenSettled] returns, and call
 * [Capability.Controller.close] when the owning lifecycle ends.
 *
 * Accessible via [OwnId.flows] using [OwnIdFlows.elite].
 */
public interface EliteFlow : FlowCapability {

    /**
     * Controls a running Elite flow.
     *
     * Call [whenSettled] to await the cached terminal [FlowResult].
     *
     * Cancellation semantics follow [Capability.Controller]: use [abort] for semantic cancellation, and [close] for
     * owner-driven lifecycle cleanup. Calling either method after settlement has no effect on the returned result.
     *
     * Handle functional outcomes in [EliteFlowContext] event handlers (`onFinish`, `onNativeAction`, `onError`,
     * `onClose`). [whenSettled] is most useful for cleanup and for reacting to [FlowResult.Failure] or
     * [FlowResult.Canceled]. Do not create an app session from controller success; use `onFinish` or `onNativeAction`
     * when applicable.
     */
    public interface Controller : Capability.Controller {
        /**
         * Suspends until the flow completes and returns the [FlowResult].
         *
         * A success result means a hosted terminal event handler completed. It can follow hosted `onFinish`,
         * `onNativeAction`, `onError`, or `onClose`; it does not mean hosted authentication succeeded.
         */
        public suspend fun whenSettled(): FlowResult<Unit, EliteFlowFailure>
    }

    /**
     * Starts the Elite flow with the given [context].
     *
     * @param context Configuration for Elite Flow options and event handlers. Defaults to [EliteFlowContext.Empty].
     *
     * @return A [Controller] to await settlement, cancel the flow, or detect SDK/WebBridge failures.
     *
     *     The caller owns the returned controller and should close it when the owner lifecycle ends.
     *     Repeated calls on the same flow instance return the same controller and do not start a second run.
     */
    @CheckResult
    public fun start(context: EliteFlowContext = EliteFlowContext.Empty): Controller
}

/**
 * Failure payload returned by [EliteFlow].
 *
 * Every failure is terminal for the current Elite flow run. Hosted-page business outcomes are delivered through
 * [EliteFlowContext] event handlers. Flow failures are reserved for SDK/WebBridge setup, operation, or runtime failures
 * that prevent the native WebBridge flow from completing normally.
 */
public sealed interface EliteFlowFailure : FlowFailure {
    /**
     * About: The WebBridge operation could not run, was unavailable, or completed with failure.
     *
     * End-user: End this web authentication attempt and show an unavailable state or a new-start retry option.
     *
     * Developer action: Do not continue this flow controller. Inspect WebBridge configuration, container/UI setup,
     * allowed origins, and [operationFailure] when present. If [operationId] is absent, inspect [message] for startup or
     * missing dependency diagnostics.
     *
     * @property operationType Operation step that failed or could not start. Elite currently uses [OperationType.WEB_BRIDGE].
     * @property operationId Operation instance ID when the WebBridge operation was started.
     * @property operationFailure Underlying WebBridge operation failure retained for diagnostics, when available.
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class OperationFailed(
        public val operationType: OperationType = OperationType.WEB_BRIDGE,
        override val errorCode: ErrorCode,
        override val message: String,
        public val operationId: OperationID? = null,
        public val operationFailure: OperationFailure? = null,
        public val error: Throwable? = null
    ) : EliteFlowFailure {
        override fun toString(): String = "OperationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The flow stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: End this web authentication attempt and show a generic failure state.
     *
     * Developer action: Log flow context and [error], then inspect WebBridge setup and hosted event handling before
     * starting a new attempt.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(
        override val errorCode: ErrorCode = ErrorCode.Unknown,
        override val message: String,
        public val error: Throwable? = null
    ) : EliteFlowFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
