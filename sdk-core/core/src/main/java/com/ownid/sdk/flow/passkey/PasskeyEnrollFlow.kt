package com.ownid.sdk.flow.passkey

import androidx.annotation.CheckResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.flow.FlowCapability
import com.ownid.sdk.flow.FlowFailure
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OwnIdHeadless
import com.ownid.sdk.ops.OperationFailure

/**
 * Flow that enrolls a passkey for an already-authenticated user.
 *
 * The flow requires an access token from [PasskeyEnrollFlowContext] or the current [Context].
 * The access token is used to resolve the enrolled [LoginId] and to authorize passkey enrollment for that user.
 * It does not sign the user in, create an app session, refresh app tokens, or persist app authentication state.
 *
 * If a proof token is provided, the flow skips passkey attestation and calls the enroll API;
 * otherwise it runs passkey attestation followed by server-side enrollment. In the attestation branch, the
 * [AttestationResponse.proofToken] is consumed by the flow for enrollment and [AttestationResponse.ownIdData] is not
 * returned to the caller. Use the lower-level attestation API or operation when the app must receive `ownIdData` at its
 * own registration boundary.
 *
 * Availability checks follow the same branch: with a proof token they check enrollment readiness, and without one they
 * check local passkey creation readiness.
 *
 * The [PasskeyEnrollFlowContext.headless] flag is reserved and currently has no observable effect. It does not
 * suppress platform passkey UI when local attestation is required.
 *
 * Check [isAvailable] before starting to verify the required access-token path and passkey enrollment support.
 *
 * The returned [Controller] delivers one terminal [FlowResult]: [FlowResult.Success] means the passkey was enrolled for
 * [Response.loginId], [FlowResult.Canceled] means the run stopped with a [Reason], and [FlowResult.Failure] carries a
 * typed [PasskeyEnrollFlowFailure].
 * A flow instance represents one run; keep the controller until [Controller.whenSettled] returns and close it when
 * the owning lifecycle ends.
 *
 * Accessible via [OwnId.headless] using [OwnIdHeadless.Passkeys.enroll].
 */
public interface PasskeyEnrollFlow : FlowCapability {

    /**
     * Controls a running passkey enrollment flow.
     *
     * Call [whenSettled] to await the cached final [FlowResult].
     *
     * Cancellation semantics follow [Capability.Controller]: use [abort] for semantic cancellation, and [close] for
     * owner-driven lifecycle cleanup. Calling either method after settlement has no effect on the returned result.
     */
    public interface Controller<R, F : FlowFailure> : Capability.Controller {
        /** Suspends until the flow completes and returns the [FlowResult]. */
        public suspend fun whenSettled(): FlowResult<R, F>
    }

    /**
     * Returns `true` when passkey enrollment can start with the given [params].
     *
     * Use [availability] when the unavailable reason is needed.
     */
    public override suspend fun isAvailable(params: Capability.Params?): Boolean = availability(params) is Availability.Available

    /**
     * Returns whether passkey enrollment can start with the given [params].
     *
     * Pass `null` to check availability without explicit parameters. When provided, [params] must be a [PasskeyEnrollFlowContext].
     *
     * If unavailable, the result carries a human-readable message explaining what the integrator needs to provide
     * or change before calling [start]. Passing a [PasskeyEnrollFlowContext.proofToken] checks the server enrollment
     * path; omitting it checks whether local passkey creation can start.
     */
    public suspend fun availability(params: Capability.Params?): Availability

    /**
     * Starts passkey enrollment with the given [context].
     *
     * @param context Optional per-run configuration. Pass `null` to use the current [Context] access token, run local
     *     passkey attestation, and omit the reserved headless flag.
     *
     * @return A [Controller] to await the result.
     *
     *     The caller owns the returned controller and should close it when the owner lifecycle ends.
     *     Repeated calls on the same flow instance return the same controller and do not start a second run.
     */
    @CheckResult
    public fun start(context: PasskeyEnrollFlowContext? = null): Controller<Response, PasskeyEnrollFlowFailure>

    /**
     * Successful passkey-enroll response.
     *
     * @property loginId The enrolled user's login identifier.
     */
    public class Response(public val loginId: LoginId) {
        override fun toString(): String = "PasskeyEnrollFlowResponse(loginId=$loginId)"
    }
}

/**
 * Failure payload returned by [PasskeyEnrollFlow].
 *
 * Every failure is terminal for the current passkey enrollment flow run. The flow requires an access token, resolves the
 * login ID from it, optionally runs passkey attestation to obtain a proof token, and then runs passkey enrollment.
 * Canceled runs are reported as [FlowResult.Canceled], not as this failure type.
 */
public sealed interface PasskeyEnrollFlowFailure : FlowFailure {
    /** Flow input or OwnID context required for passkey enrollment was missing or unusable. */
    public sealed class Input : PasskeyEnrollFlowFailure {
        /**
         * About: No access token was available from flow params or current OwnID context.
         *
         * End-user: End this enrollment attempt and ask the user to sign in before starting a new passkey enrollment.
         *
         * Developer action: Start the flow only after authentication, pass [PasskeyEnrollFlowContext.accessToken], or
         * provide an access token through OwnID context.
         */
        public class MissingAccessToken(override val errorCode: ErrorCode, override val message: String) : Input() {
            override fun toString(): String = "Input.MissingAccessToken(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The flow could not resolve a usable login ID from the available access token.
         *
         * End-user: End this enrollment attempt and ask the user to sign in again before retrying.
         *
         * Developer action: Check token contents, token forwarding, and login ID validation setup.
         *
         * @property error Underlying token or validation error retained for diagnostics, when available.
         */
        public class UnresolvedLoginId(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) : Input() {
            override fun toString(): String = "Input.UnresolvedLoginId(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: Passkey attestation or server-side passkey enrollment could not run, was unavailable, or completed with
     * failure.
     *
     * End-user: End this enrollment attempt. The app may show a failure state, offer another account security path, or
     * let the user start a new attempt.
     *
     * Developer action: Do not continue this flow controller. Inspect [operationFailure] when present; otherwise inspect
     * [operationType], [message], and [error] before allowing a retry.
     *
     * @property operationType Operation step that failed or could not start.
     * @property operationId Operation instance ID when an operation was started.
     * @property operationFailure Underlying operation failure retained for diagnostics, when available.
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class OperationFailed(
        public val operationType: OperationType,
        override val errorCode: ErrorCode,
        override val message: String,
        public val operationId: OperationID? = null,
        public val operationFailure: OperationFailure? = null,
        public val error: Throwable? = null
    ) : PasskeyEnrollFlowFailure {
        override fun toString(): String = "OperationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The flow stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: End this enrollment attempt and show a generic enrollment failure state.
     *
     * Developer action: Log flow context and [error]. If [message] mentions a missing proof token, inspect the preceding
     * passkey attestation path before starting a new attempt.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(
        override val errorCode: ErrorCode = ErrorCode.Unknown,
        override val message: String,
        public val error: Throwable? = null
    ) : PasskeyEnrollFlowFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
