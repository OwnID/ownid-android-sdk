package com.ownid.sdk.ops

import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import kotlinx.coroutines.flow.StateFlow

/**
 * Verifies user ownership of a phone number via a one-time code.
 *
 * Call [start] to launch one operation run. The returned controller also implements
 * [PhoneVerificationOperationController]; keep it while the run is active, use
 * [PhoneVerificationOperationController.state] for lifecycle and UI state, and call
 * [Capability.Controller.abort] to stop the run with an explicit [Reason].
 *
 * Explicit [Params] values take precedence over values from the current OwnID context. If [Params.loginId] or
 * [Params.accessToken] is `null`, the operation uses the matching context value when available. An access token is enough
 * to start without a login ID. When a login ID is resolved, it must be [LoginId.Type.PhoneNumber], or
 * [LoginId.Type.UserName] with a nonblank [Params.loginIdHintId] that identifies the selected phone channel. Unsupported
 * input is reported through [availability] as unavailable and through [start] as [OperationResult.Failure].
 *
 * The operation requests OTP verification. If OwnID returns a challenge without OTP support, the run completes with
 * [OperationResult.Failure]. In the active state, [State.Active.uiState] carries the current challenge, loading/error
 * state, and callbacks for code entry, resend, cancel, and "not you". The challenge includes the phone destination for
 * the code. [State.Active.apiController] is exposed for advanced integrations that need direct access to the challenge
 * controller for the same operation run.
 *
 * A correct code completes with [OperationResult.Success] containing [AccessOrProofToken], which is either [AccessToken] or
 * a proof token. A wrong code keeps the run active and updates [State.Active.uiState] with an error. Reaching the resend
 * limit keeps the run active and marks resend unavailable for the current challenge. Terminal challenge, access, input,
 * integration, and unexpected failures complete with [OperationResult.Failure]. User cancel, "not you",
 * [Capability.Controller.abort], lifecycle cancellation, and challenge timeout complete with [OperationResult.Canceled];
 * when possible, an active challenge is canceled as part of that settlement.
 */
public interface PhoneVerificationOperation : OperationCapability<PhoneVerificationOperation.Params, AccessOrProofToken, PhoneVerificationOperationFailure> {

    /**
     * Parameters for [PhoneVerificationOperation].
     *
     * @property loginId The phone number to verify, or a username when [loginIdHintId] identifies an associated phone
     * channel. When `null`, the current OwnID context login ID is used if available. Defaults to `null`.
     * @property loginIdHintId An optional hint identifier for the phone channel associated with [loginId]. Required when
     * [loginId] is [LoginId.Type.UserName]. Blank values are treated as missing. Defaults to `null`.
     * @property accessToken An existing access token, if available. When `null`, the current OwnID context access token is used if available.
     * Defaults to `null`.
     * @property onUiClick Optional best-effort callback invoked when the user triggers a UI action (code entry, resend, cancel, or "not you").
     * Receives the [OperationID] for this operation. Exceptions thrown by this callback are ignored and do not change operation settlement.
     * Defaults to `null`.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val loginIdHintId: String? = null,
        public val accessToken: AccessToken? = null,
        public val onUiClick: ((OperationID) -> Unit)? = null,
        internal val traceParent: String?
    ) : Capability.Params {
        public constructor(
            loginId: LoginId? = null,
            loginIdHintId: String? = null,
            accessToken: AccessToken? = null,
            onUiClick: ((OperationID) -> Unit)? = null
        ) : this(loginId, loginIdHintId, accessToken, onUiClick, traceParent = null)
    }

    /**
     * State machine for [PhoneVerificationOperation].
     *
     * Exposed by [PhoneVerificationOperationController.state].
     *
     * States progress from [Created] through [Preparing] and, when challenge startup succeeds, [Active] to [Completed] with
     * an [OperationResult] containing [AccessOrProofToken].
     */
    public sealed interface State {
        /** Initial state before the operation starts. */
        public object Created : State

        /**
         * The operation is initializing.
         *
         * @property params The parameters used to start the operation.
         */
        public class Preparing(public val params: Params) : State {
            override fun equals(other: Any?): Boolean = this === other || (other is Preparing && params == other.params)
            override fun hashCode(): Int = params.hashCode()
        }

        /**
         * The verification UI is visible.
         *
         * @property uiState Current UI state with the selected challenge, display hint, busy flag, validation error, and action callbacks.
         * @property apiController Controller that owns the selected challenge for this operation run.
         */
        public class Active(
            public val uiState: PhoneVerificationUi.UiState,
            public val apiController: PhoneVerificationApi.Controller,
        ) : State {
            public fun copy(uiState: PhoneVerificationUi.UiState = this.uiState, apiController: PhoneVerificationApi.Controller = this.apiController): Active =
                Active(uiState, apiController)

            override fun equals(other: Any?): Boolean = this === other || (other is Active && uiState == other.uiState && apiController == other.apiController)
            override fun hashCode(): Int = 31 * uiState.hashCode() + apiController.hashCode()
        }

        /**
         * The operation finished.
         *
         * @property result [OperationResult.Success] with [AccessOrProofToken], [OperationResult.Canceled] with a [Reason], or
         * [OperationResult.Failure] with [PhoneVerificationOperationFailure].
         */
        public class Completed(public val result: OperationResult<AccessOrProofToken, PhoneVerificationOperationFailure>) : State {
            override fun equals(other: Any?): Boolean = this === other || (other is Completed && result == other.result)
            override fun hashCode(): Int = result.hashCode()
        }
    }
}

/** Controller for one running [PhoneVerificationOperation]. */
public interface PhoneVerificationOperationController : OperationController<AccessOrProofToken, PhoneVerificationOperationFailure> {

    /**
     * Observable state of the running phone verification operation.
     *
     * Emits [PhoneVerificationOperation.State] transitions from [PhoneVerificationOperation.State.Created]
     * through [PhoneVerificationOperation.State.Preparing] and [PhoneVerificationOperation.State.Active]
     * to [PhoneVerificationOperation.State.Completed].
     */
    public val state: StateFlow<PhoneVerificationOperation.State>
}

/**
 * Failure payload returned by [PhoneVerificationOperation].
 *
 * Every failure is terminal for the current operation run. Recoverable wrong-code responses stay in active UI state and are
 * not emitted as failures. Branch on the category to decide whether to ask for corrected input, stop the OTP challenge,
 * offer another authentication path, or fix integration. Use [OperationFailure.errorCode] as a localization key; use
 * diagnostic fields such as `apiFailure`, `error`, `challengeId`, `loginId`, `capability`, and `regex` for logging.
 */
public sealed interface PhoneVerificationOperationFailure : OperationFailure {
    /** Operation input is missing, unsupported, or invalid. */
    public sealed class Input : PhoneVerificationOperationFailure {
        /**
         * About: The operation could not resolve either an access token or a phone login ID to start verification.
         *
         * End-user: Ask the user to provide a phone number or restart from an authenticated state.
         *
         * Developer action: Pass [PhoneVerificationOperation.Params.loginId] or [PhoneVerificationOperation.Params.accessToken],
         * or provide one through OwnID context.
         */
        public class MissingLoginIdOrAccessToken(override val errorCode: ErrorCode, override val message: String) : Input() {
            override fun toString(): String = "Input.MissingLoginIdOrAccessToken(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The resolved login ID is not a phone number, or is a username without a phone channel hint.
         *
         * End-user: Ask the user to provide a phone number or restart this step.
         *
         * Developer action: Start phone verification with [LoginId.Type.PhoneNumber], or [LoginId.Type.UserName] plus
         * [PhoneVerificationOperation.Params.loginIdHintId] from the server-recommended phone channel.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class UnsupportedLoginIdType(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) :
            Input() {
            override fun toString(): String = "Input.UnsupportedLoginIdType(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The resolved phone login ID failed validation.
         *
         * End-user: Ask the user to correct the phone number.
         *
         * Developer action: Keep client-side validation aligned with OwnID configuration and use [regex] only for diagnostics.
         *
         * @property loginId Login ID that failed validation.
         * @property regex Validation pattern associated with the failure.
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidLoginId(
            override val errorCode: ErrorCode,
            override val message: String,
            public val loginId: LoginId,
            public val regex: String,
            public val apiFailure: ApiFailure? = null
        ) : Input() {
            override fun toString(): String = "Input.InvalidLoginId(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The verification start request was rejected as invalid.
         *
         * End-user: No direct user action unless the app can collect corrected input from the user.
         *
         * Developer action: Inspect the provided params, resolved context, and [apiFailure] for the rejected invariant.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidRequest(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Input() {
            override fun toString(): String = "Input.InvalidRequest(errorCode=$errorCode, message=$message)"
        }
    }

    /** The account or caller is not allowed to continue. */
    public sealed class Access : PhoneVerificationOperationFailure {
        /**
         * About: The account associated with the verification request was not found.
         *
         * End-user: Direct the user to register or use another phone number, based on app state.
         *
         * Developer action: Treat as an expected business outcome unless account provider data is inconsistent.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class UserNotFound(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.UserNotFound(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The caller is not allowed to start or complete phone verification in this context.
         *
         * End-user: Explain that phone verification is unavailable.
         *
         * Developer action: Check access token claims, app policy, and operation requirements.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Forbidden(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.Forbidden(errorCode=$errorCode, message=$message)"
        }
    }

    /** Verification challenge cannot continue or must be restarted. */
    public sealed class Challenge : PhoneVerificationOperationFailure {
        /**
         * About: The active phone verification challenge limit was reached.
         *
         * End-user: Ask the user to wait briefly or finish an existing verification attempt.
         *
         * Developer action: Rate-limit retries and ensure abandoned challenges are canceled when possible.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) :
            Challenge() {
            override fun toString(): String = "Challenge.MaximumChallengesReached(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The phone verification challenge is invalid, expired, or no longer usable.
         *
         * End-user: Ask the user to restart phone verification.
         *
         * Developer action: Stop using this [challengeId], clear local challenge state, and start a fresh operation.
         *
         * @property challengeId Challenge that can no longer continue.
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Invalid(
            override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId, public val apiFailure: ApiFailure? = null
        ) : Challenge() {
            override fun toString(): String = "Challenge.Invalid(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The phone verification challenge reached its attempt limit.
         *
         * End-user: Ask the user to start phone verification again.
         *
         * Developer action: Do not retry this [challengeId]; clear local challenge state and create a new challenge.
         *
         * @property challengeId Challenge that reached the attempt limit.
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class MaximumAttemptsReached(
            override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId, public val apiFailure: ApiFailure? = null
        ) : Challenge() {
            override fun toString(): String = "Challenge.MaximumAttemptsReached(errorCode=$errorCode, message=$message)"
        }
    }

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : PhoneVerificationOperationFailure {
        /**
         * About: The requested phone verification channel is unavailable for the resolved login ID.
         *
         * End-user: Offer another authentication method or ask the user to update account contact details, if supported by the app.
         *
         * Developer action: Check account provider data, phone channel configuration, and [apiFailure].
         *
         * @property loginId Login ID for which the phone channel was unavailable.
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class MissingChannel(
            override val errorCode: ErrorCode, override val message: String, public val loginId: LoginId, public val apiFailure: ApiFailure? = null
        ) : Integration() {
            override fun toString(): String = "Integration.MissingChannel(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: A configured backend/provider dependency failed while processing phone verification.
         *
         * End-user: Show a temporary failure state.
         *
         * Developer action: Log provider context and monitor the integration before retrying aggressively.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class ProviderFailed(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) :
            Integration() {
            override fun toString(): String = "Integration.ProviderFailed(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: A provider capability required by phone verification is not configured.
         *
         * End-user: No direct user action. The app should offer another available path.
         *
         * Developer action: Configure the missing [capability] for the app and deployment environment.
         *
         * @property capability Missing provider capability required by the operation.
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class MissingProvider(
            override val errorCode: ErrorCode, override val message: String, public val capability: String, public val apiFailure: ApiFailure? = null
        ) : Integration() {
            override fun toString(): String = "Integration.MissingProvider(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The phone verification UI failed before the operation could complete.
         *
         * End-user: Show a generic unavailable state or let the user retry opening verification.
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
     * End-user: Show a generic failure state. Retrying may be reasonable after restarting phone verification.
     *
     * Developer action: Log operation context, inspect [apiFailure] or [error], and avoid automatic retry loops.
     *
     * @property apiFailure Underlying API failure retained for diagnostics, when available.
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(
        override val errorCode: ErrorCode = ErrorCode.Unknown,
        override val message: String,
        public val apiFailure: ApiFailure? = null,
        public val error: Throwable? = null
    ) : PhoneVerificationOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
