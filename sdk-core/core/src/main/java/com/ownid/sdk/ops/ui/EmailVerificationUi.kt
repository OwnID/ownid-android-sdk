package com.ownid.sdk.ops.ui

import androidx.annotation.UiThread
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.UiError
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationController
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationUi

/**
 * UI capability for email verification.
 *
 * Presents or binds the verification UI for an active [EmailVerificationOperation].
 *
 * The operation owns the challenge, timeout, final settlement, and controller callbacks. UI implementations render
 * [UiState], invoke its callbacks for code entry, resend, cancel, and "not you", and report startup or presentation
 * failure through [start]. They should not create a separate verification lifecycle.
 *
 * If no UI implementation is registered, startup fails and the operation reports a UI integration failure.
 */
public interface EmailVerificationUi : OperationUi {

    /**
     * Observable state for the email verification UI.
     *
     * [challenge] describes the current challenge, including where the code was sent. [busy] indicates that the operation
     * is processing a user action. [error] is displayable user-facing error state; wrong-code and resend-limit responses
     * stay in this UI state instead of completing the operation.
     *
     * Invoke callbacks only in response to user intent. [onCodeEntered] submits a code. [onResend] requests another code
     * when the UI allows it. [onCancel] dismisses the current verification attempt. [onNotYou] tells the operation to move
     * away from the selected challenge. Callback failures are owned by the operation.
     *
     * @property challenge The current verification challenge.
     * @property busy If `true`, the UI should show a loading indicator and avoid duplicate non-cancel actions.
     * @property error A validation or OwnID error to display, or `null` if none.
     * @property onCodeEntered Callback to invoke when the user submits a verification code.
     * @property onCancel Callback to invoke when the user dismisses the UI.
     * @property onNotYou Callback to invoke when the user indicates this is not their email.
     * @property onResend Callback to invoke when the user requests a new code.
     */
    public class UiState(
        public val challenge: VerificationChallenge,
        public val busy: Boolean = false,
        public val error: UiError? = null,
        public val onCodeEntered: (String) -> Unit = {},
        public val onCancel: () -> Unit = {},
        public val onNotYou: () -> Unit = {},
        public val onResend: () -> Unit = {}
    ) {
        /** Returns a copy with optionally replaced fields. */
        public fun copy(
            challenge: VerificationChallenge = this.challenge,
            busy: Boolean = this.busy,
            error: UiError? = this.error,
            onCodeEntered: (String) -> Unit = this.onCodeEntered,
            onCancel: () -> Unit = this.onCancel,
            onNotYou: () -> Unit = this.onNotYou,
            onResend: () -> Unit = this.onResend
        ): UiState =
            UiState(challenge, busy, error, onCodeEntered, onCancel, onNotYou, onResend)

        override fun equals(other: Any?): Boolean =
            this === other || (other is UiState && challenge == other.challenge && busy == other.busy && error == other.error)

        override fun hashCode(): Int =
            ((challenge.hashCode() * 31 + (if (busy) 1 else 0)) * 31 + (error?.hashCode() ?: 0))
    }

    /**
     * Starts the UI for email verification, binding it to the given [controller].
     *
     * Implementations should present UI for the running operation identified by [controller.operationId] and observe the
     * matching [EmailVerificationOperationController.state] when they need [UiState] updates. The
     * operation remains the owner of challenge completion, cancellation, timeout, and final result settlement.
     *
     * Implementations must not throw from this method. Report recoverable startup or presentation failures through [onStartError] instead.
     *
     * @param controller The operation controller to bind UI actions to.
     * @param onStartError Callback invoked when UI startup fails (for example, container presentation errors).
     */
    @UiThread
    public fun start(
        controller: OperationController<AccessOrProofToken, EmailVerificationOperationFailure>,
        onStartError: (error: Throwable?, details: String?) -> Unit
    )
}
