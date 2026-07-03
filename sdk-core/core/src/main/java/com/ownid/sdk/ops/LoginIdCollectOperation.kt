package com.ownid.sdk.ops

import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects a login ID from the user.
 *
 * Call [start] to launch the operation.
 * The returned controller also implements [LoginIdCollectOperationController], which exposes
 * [LoginIdCollectOperationController.state] for lifecycle and UI state updates.
 *
 * Use this operation when the app needs the user to provide the identifier for the next OwnID step. The SDK first uses
 * the typed [Params.loginId] supplied by the app, then a typed login ID from the current OwnID
 * [Context], then a raw login ID string from the current context. If the resolved typed value is
 * valid and collectable, the operation completes successfully without showing a form. If the value is missing or
 * invalid but can be corrected, the SDK opens login ID collection UI.
 *
 * When multiple login ID types are collectable, the user enters a single value and the SDK validates it against those
 * collectable types in the configured priority order.
 *
 * If the UI is shown, the operation cancels with [Reason.Timeout] after five minutes. User dismissal through the UI
 * cancels with [Reason.UserClose]. Calling [OperationController.abort] cancels with the supplied reason. Calling
 * [Capability.Controller.close] uses the controller cleanup path and cancels with its close reason. Requests made after
 * the operation has settled do not change the terminal result.
 *
 * On success, the result contains the validated [LoginId]. When no collectable types are available, UI startup fails, a
 * resolved typed login ID has an unsupported type, or an unexpected SDK/runtime error occurs, the operation completes
 * with [OperationResult.Failure] carrying [LoginIdCollectOperationFailure].
 *
 * [availability] returns available when at least one collectable login ID type is configured and any supplied typed
 * login ID has a collectable type. Invalid collectable values do not make the operation unavailable because the UI can
 * let the user correct them.
 *
 * This operation collects [LoginId.Type.Email], [LoginId.Type.PhoneNumber], and [LoginId.Type.UserName].
 */
public interface LoginIdCollectOperation : OperationCapability<LoginIdCollectOperation.Params, LoginId, LoginIdCollectOperationFailure> {

    /**
     * Parameters for [LoginIdCollectOperation].
     *
     * @property loginId A typed login ID supplied by the app. If it is valid, the operation completes without showing
     * UI. If the type is supported but the value is invalid, the form opens with [LoginId.id] so the user can correct it.
     * This value takes precedence over typed and raw login ID values from the current OwnID context. When null, a typed
     * context login ID is used first, then a raw context login ID if present; otherwise the form starts blank. Defaults
     * to null.
     * @property onUiClick Optional best-effort callback invoked when the user taps Continue or Cancel, receiving the [OperationID].
     * Exceptions thrown by this callback are ignored and do not change operation settlement. Defaults to null.
     */
    public class Params(
        public val loginId: LoginId? = null,
        public val onUiClick: ((OperationID) -> Unit)? = null
    ) : Capability.Params

    /**
     * State machine for [LoginIdCollectOperation].
     *
     * Exposed by [LoginIdCollectOperationController.state].
     *
     * States progress from [Created] to [Active] only when login ID collection UI is visible, and finally to [Completed].
     * Operations that can resolve a valid typed login ID from input or context may move directly from [Created] to
     * [Completed] with [OperationResult.Success].
     */
    public sealed interface State {
        /** Initial state before the operation starts. */
        public object Created : State

        /**
         * The collection UI is visible.
         *
         * @property uiState Current UI state with the login ID text, collectable types, validation error, and action callbacks.
         */
        public class Active(public val uiState: LoginIdCollectUi.UiState) : State {
            public fun copy(uiState: LoginIdCollectUi.UiState = this.uiState): Active = Active(uiState)
            override fun equals(other: Any?): Boolean = this === other || (other is Active && uiState == other.uiState)
            override fun hashCode(): Int = uiState.hashCode()
        }

        /**
         * The operation finished.
         *
         * @property result [OperationResult.Success] with the validated [LoginId], [OperationResult.Canceled] with the
         * cancellation reason, or [OperationResult.Failure] with a typed failure payload.
         */
        public class Completed(public val result: OperationResult<LoginId, LoginIdCollectOperationFailure>) : State {
            override fun equals(other: Any?): Boolean = this === other || (other is Completed && result == other.result)
            override fun hashCode(): Int = result.hashCode()
        }
    }
}

/**
 * Controller for a running [LoginIdCollectOperation].
 *
 * This controller has the standard [OperationController] contract and adds [state] for UI/state observation.
 */
public interface LoginIdCollectOperationController : OperationController<LoginId, LoginIdCollectOperationFailure> {

    /**
     * Observable state of the running login ID collection operation.
     *
     * Emits [LoginIdCollectOperation.State] transitions from [LoginIdCollectOperation.State.Created]
     * through [LoginIdCollectOperation.State.Active] when UI is needed, then [LoginIdCollectOperation.State.Completed].
     * The latest state remains available to new collectors.
     */
    public val state: StateFlow<LoginIdCollectOperation.State>
}

/**
 * Failure payload returned by [LoginIdCollectOperation].
 *
 * Input failures indicate caller-supplied values that cannot be accepted by this operation. Integration failures
 * indicate missing configuration or UI capability problems. [Unexpected] is reserved for unexpected SDK/runtime
 * failures. Invalid but collectable login ID values are presented in active UI for user correction instead of becoming
 * terminal input failures.
 */
public sealed interface LoginIdCollectOperationFailure : OperationFailure {
    /** Login ID supplied by the app has an unsupported type. Invalid values stay in the active UI for correction. */
    public sealed class Input : LoginIdCollectOperationFailure {
        /**
         * About: The app supplied a login ID type that this collect operation cannot accept.
         *
         * End-user: No direct user action. The app should ask for one of the configured supported identifier types.
         *
         * Developer action: Pass only a collectable [LoginId] type or start the operation without a prefilled login ID.
         */
        public class UnsupportedLoginIdType(override val errorCode: ErrorCode, override val message: String) : Input() {
            override fun toString(): String = "Input.UnsupportedLoginIdType(errorCode=$errorCode, message=$message)"
        }
    }

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : LoginIdCollectOperationFailure {
        /**
         * About: The SDK could not determine any login ID type that can be collected.
         *
         * End-user: No direct user action. The app should offer another sign-in or registration path.
         *
         * Developer action: Check login ID type configuration and operation requirements before starting collection.
         */
        public class NoSupportedLoginIdTypes(override val errorCode: ErrorCode, override val message: String) : Integration() {
            override fun toString(): String = "Integration.NoSupportedLoginIdTypes(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The login ID collection UI failed before the operation could complete.
         *
         * End-user: Show a generic unavailable state or let the user retry opening the collection UI.
         *
         * Developer action: Inspect UI setup, activity/window availability, and [error] for the concrete UI failure.
         *
         * @property error Underlying UI/runtime error retained for diagnostics, when available.
         */
        public class Ui(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) : Integration() {
            override fun toString(): String = "Integration.Ui(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: The operation stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: Show a generic failure state. Retrying may be reasonable if the app can safely restart the operation.
     *
     * Developer action: Log the failure with operation context and inspect [error] before retrying automatically.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(override val errorCode: ErrorCode = ErrorCode.Unknown, override val message: String, public val error: Throwable? = null) :
        LoginIdCollectOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
