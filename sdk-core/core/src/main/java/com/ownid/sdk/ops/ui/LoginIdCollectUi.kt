package com.ownid.sdk.ops.ui

import androidx.annotation.UiThread
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationUi

/**
 * UI capability for collecting a login ID.
 *
 * Drives a UI where the user enters or confirms a login identifier of type [LoginId.Type.Email],
 * [LoginId.Type.PhoneNumber], or [LoginId.Type.UserName].
 *
 * The operation owns settlement and validation. UI implementations own presentation, dismissal controls, input
 * rendering, and forwarding user actions through [UiState] callbacks. Calling the cancel callback settles the operation
 * as canceled with user-close semantics.
 *
 * If no UI implementation is registered, startup fails and the operation reports a UI integration failure.
 */
public interface LoginIdCollectUi : OperationUi {

    /**
     * Observable state for the login ID collection UI.
     *
     * Custom UI should display [loginIdValue] in the input field, use [collectableLoginIdTypes] to choose appropriate
     * labels or keyboard hints, display [error] when present, and invoke the callbacks when the user edits, submits,
     * or dismisses the form. The operation may update this state after validation, including clearing [error] when the
     * user edits the value.
     *
     * @property loginIdValue The current login ID text entered by the user.
     * @property collectableLoginIdTypes The login ID types this form can collect, in validation priority order.
     * @property error A validation or server error to display, or `null` if none.
     * @property onLoginIdChange Callback to invoke when the user changes the login ID text.
     * @property onContinue Callback to invoke when the user submits the login ID for validation.
     * @property onCancel Callback to invoke when the user dismisses the UI; the operation cancels with [Reason.UserClose].
     */
    public class UiState(
        public val loginIdValue: String,
        public val collectableLoginIdTypes: List<LoginId.Type>,
        public val error: UiError? = null,
        public val onLoginIdChange: (loginIdValue: String) -> Unit,
        public val onContinue: () -> Unit,
        public val onCancel: () -> Unit
    ) {
        /** Returns a copy with optionally replaced fields. */
        public fun copy(
            loginIdValue: String = this.loginIdValue,
            collectableLoginIdTypes: List<LoginId.Type> = this.collectableLoginIdTypes,
            error: UiError? = this.error,
            onLoginIdChange: (loginIdValue: String) -> Unit = this.onLoginIdChange,
            onContinue: () -> Unit = this.onContinue,
            onCancel: () -> Unit = this.onCancel
        ): UiState =
            UiState(loginIdValue, collectableLoginIdTypes, error, onLoginIdChange, onContinue, onCancel)

        override fun equals(other: Any?): Boolean =
            this === other || (other is UiState && loginIdValue == other.loginIdValue && collectableLoginIdTypes == other.collectableLoginIdTypes && error == other.error)

        override fun hashCode(): Int =
            ((loginIdValue.hashCode() * 31 + collectableLoginIdTypes.hashCode()) * 31 + (error?.hashCode() ?: 0))
    }

    /**
     * Starts the UI for login ID collection, binding it to the given [controller].
     *
     * Implementations should present UI for the running operation identified by [controller.operationId] and bind user
     * actions to the controller-backed operation state exposed elsewhere by the SDK. The UI should observe
     * [LoginIdCollectOperationController.state] when it needs ongoing state updates.
     *
     * Implementations must not throw from this method. Report recoverable startup or presentation failures through [onStartError] instead.
     *
     * @param controller The operation controller to bind UI actions to.
     * @param onStartError Callback invoked when UI startup fails (for example, container presentation errors).
     */
    @UiThread
    public fun start(
        controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    )
}
