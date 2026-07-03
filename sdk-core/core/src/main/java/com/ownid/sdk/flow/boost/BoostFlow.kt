package com.ownid.sdk.flow.boost

import androidx.annotation.CheckResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.flow.FlowCapability
import com.ownid.sdk.flow.FlowFailure
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.namespace.OwnIdFlows
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.provider.SessionCreate

/**
 * Server-driven authentication flow for Boost login and create-passkey journeys.
 *
 * Boost is a high-level flow layer. It chooses among SDK operations, provider callbacks, current [Context], and stored
 * user state, then settles once with [FlowResult]. Apps that need to control individual challenge APIs or platform
 * passkey UI directly should use the operation or direct API layer instead.
 *
 * Start the flow with an optional [BoostFlowContext] to supply login-ID hints or flow behavior. Boost is token-first
 * when the flow context or current [Context] contains an access token: the SDK derives the login ID from that token and
 * starts the token login path for that run. Without an access token, login ID is resolved from [BoostFlowContext], the
 * current [Context], then the stored last user before login-ID collection is shown. Setting
 * [BoostFlowContext.ignoreLastUser] to `true` only disables the stored last-user fallback; it does not ignore explicit
 * flow-context or current-context login IDs.
 *
 * Use [Login] for sign-in screens and [CreatePasskey] when account creation should include the OwnID create-passkey
 * path. Both variants are asynchronous, may present operation UI, may call configured app providers, and settle through
 * [FlowResult.Success], [FlowResult.Canceled], or [FlowResult.Failure].
 *
 * A flow instance represents one run. Keep the returned [Controller] until [Controller.whenSettled] returns, and call
 * [Capability.Controller.close] when the owning lifecycle ends.
 *
 * Accessible via [OwnId.flows] using [OwnIdFlows.OwnIdBoostFlow.login] and [OwnIdFlows.OwnIdBoostFlow.createPasskey].
 *
 * @param Res The flow response type ([Response.Login] or [Response]).
 * @param F The flow-specific failure type.
 */
public interface BoostFlow<Res, F : FlowFailure> : FlowCapability {

    /**
     * Controls a running Boost flow.
     *
     * Call [whenSettled] to await the cached terminal [FlowResult].
     *
     * Cancellation semantics follow [Capability.Controller]: use [abort] for semantic cancellation, and [close] for
     * owner-driven lifecycle cleanup. Both request cancellation of the active child operation or flow when one is
     * running. Calling either method after settlement has no effect on the returned result.
     */
    public interface Controller<R, F : FlowFailure> : Capability.Controller {
        /** Suspends until the flow completes and returns the [FlowResult]. */
        public suspend fun whenSettled(): FlowResult<R, F>
    }

    /**
     * Starts the Boost flow with the given [context].
     *
     * @param context Configuration for the flow. Defaults to [BoostFlowContext.Empty].
     *
     * @return A [Controller] to await the result.
     *
     *     The caller owns the returned controller and should close it when the owner lifecycle ends.
     *     Repeated calls on the same flow instance return the same controller and do not start a second run.
     */
    @CheckResult
    public fun start(context: BoostFlowContext = BoostFlowContext.Empty): Controller<Res, F>

    /** Terminal response from a Boost flow. */
    public sealed interface Response {
        /**
         * Successful login response.
         *
         * @property loginId The authenticated user's login identifier.
         * @property authMethod The authentication method used, such as passkey or OTP.
         * @property accessToken The server-issued access token.
         * @property sessionPayload Server-provided payload for app session integration. The same value is passed to
         * [SessionCreate] when that provider is configured and available. Structured values remain JSON text. If the
         * server returns a plain string, this property contains that string value.
         * @property session App-defined [SessionCreate] output returned in this response. When no provider is configured
         * or the provider is unavailable for these params, the flow still succeeds and this value is `null`. The SDK
         * does not interpret, serialize, or persist the value. A provider failure is terminal and is reported as a
         * session-creation flow failure instead of a successful login response.
         */
        public class Login(
            public val loginId: LoginId,
            public val authMethod: AuthMethod,
            public val accessToken: AccessToken,
            public val sessionPayload: String,
            public val session: Any?
        ) : Response {
            override fun toString(): String =
                "Login(loginId=$loginId, authMethod=$authMethod, accessToken=$accessToken, sessionPayload='*', session=${session?.let { "'*'" }})"
        }

        /**
         * Successful create-passkey outcome.
         *
         * @property loginId Login identifier used by the create-passkey journey.
         * @property proofToken Token proving that passkey-related operations produced server-side proof, or `null`
         * when no proof is available. A proof token is not a host app session token.
         * @property ownIdData Value to forward to the host app's vendor backend, or `null` when no proof is available.
         * Structured values remain JSON text. If the server returns a plain string, this property contains that string
         * value. The SDK does not interpret or persist this value.
         */
        public class CreatePasskey(
            public val loginId: LoginId,
            public val proofToken: ProofToken?,
            public val ownIdData: String?
        ) : Response {
            override fun toString(): String =
                "CreatePasskey(loginId=$loginId, proofToken=$proofToken, ownIdData=${ownIdData?.let { "'*'" }})"
        }
    }

    /**
     * Boost flow specialized for login; resolves to [Response.Login].
     *
     * It prefers token-based login:
     * - If an access token is available in this flow context or the current [Context], login ID is derived from token
     *   and login operation runs directly (login-ID collection is skipped).
     *   If login ID cannot be resolved from token, the flow fails.
     * - If no access token is available, the flow uses any login-ID hint from [BoostFlowContext], the current
     *   [Context], or the stored last user when [BoostFlowContext.ignoreLastUser] is not `true` before showing
     *   login-ID collection.
     *
     * When the server requires additional authentication, the flow uses available operations to satisfy the
     * requirements. User or owner cancellation settles as [FlowResult.Canceled]. Missing dependencies, unavailable
     * operations, provider failures, account outcomes, and unmet requirements settle as [FlowResult.Failure].
     *
     * On successful login, the flow updates the stored last user when that capability is configured.
     */
    public interface Login : BoostFlow<Response.Login, BoostLoginFlowFailure>

    /**
     * Boost flow specialized for create-passkey journeys during account creation.
     *
     * Use this flow when the app wants OwnID to handle the create-passkey path as part of registration or account
     * creation.
     *
     * The flow prefers access-token login when an access token is available in this flow context or the current
     * [Context]. In that path, login ID is derived from the access token and login-ID collection is skipped. If login
     * ID cannot be resolved from token, the flow fails.
     *
     * When no access token is available, the flow resolves or collects login ID and checks the account state before
     * continuing. [BoostFlowContext.ignoreLastUser] only disables the stored last-user fallback; explicit flow-context
     * and current-context login IDs remain eligible.
     *
     * Depending on the server response, the result may represent an existing account sign-in or a create-passkey
     * outcome. Existing-account sign-in uses the same login response and [SessionCreate] boundary as [Login]. A
     * create-passkey outcome returns proof values from passkey attestation when available; when local passkey creation is
     * unavailable or fails, the flow can still succeed with absent proof values so the app can continue its non-passkey
     * registration path.
     * The flow updates the stored last user only after a successful existing account sign-in, or after a create-passkey
     * result with a proof token.
     *
     * Terminal result:
     * - Sign-in outcome when an existing account is authenticated.
     * - Create-passkey outcome when that path completes. Its proof values may be absent when passkey attestation is
     *   unavailable or fails.
     *
     * User or owner cancellation settles as [FlowResult.Canceled]. Missing dependencies, unavailable required
     * operations, provider failures, account outcomes, and unresolved input settle as [FlowResult.Failure].
     */
    public interface CreatePasskey : BoostFlow<Response, BoostCreatePasskeyFlowFailure>
}

/**
 * Returns [BoostFlow.Response.CreatePasskey.ownIdData] when [currentLoginId] matches this response login ID.
 *
 * The supplied [currentLoginId] is trimmed before comparison. `null`, blank, whitespace-only, and non-matching values
 * return `null`. Use this helper when the app keeps a create-passkey response for later registration submit and the
 * current form login ID may have changed since [BoostFlow.Response.CreatePasskey] was emitted.
 */
public fun BoostFlow.Response.CreatePasskey.ownIdDataFor(currentLoginId: String?): String? {
    val normalizedLoginId = currentLoginId?.trim()?.takeIf { it.isNotEmpty() }
    return if (normalizedLoginId == loginId.id) ownIdData else null
}

/**
 * Failure payload returned by [BoostFlow.Login].
 *
 * Every failure is terminal for the current login flow run. Use the concrete failure type to decide the next app-level
 * step, such as showing a final error, starting a new flow attempt, routing to registration, or inspecting app
 * integration. Use [FlowFailure.errorCode] only as a localization key.
 */
public sealed interface BoostLoginFlowFailure : FlowFailure {
    /** Input or OwnID context required before the first login operation could not be resolved. */
    public sealed class Input : BoostLoginFlowFailure {
        /**
         * About: The flow could not resolve a usable login ID from the available context or access token.
         *
         * End-user: End this sign-in attempt and let the user restart sign-in or enter a valid identifier in a new run.
         *
         * Developer action: Check token contents, [Context] values, [BoostFlowContext], and login ID validation setup.
         *
         * @property error Underlying token or validation error retained for diagnostics, when available.
         */
        public class UnresolvedLoginId(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) : Input() {
            override fun toString(): String = "Input.UnresolvedLoginId(errorCode=$errorCode, message=$message)"
        }
    }

    /** Account state reported by login prevented the flow from completing. */
    public sealed class Account : BoostLoginFlowFailure {
        /**
         * About: The account is blocked and cannot complete login.
         *
         * End-user: End this sign-in attempt and direct the user to the app's recovery or support path.
         *
         * Developer action: Treat as an expected business outcome unless provider data is inconsistent.
         */
        public class Blocked(override val errorCode: ErrorCode, override val message: String) : Account() {
            override fun toString(): String = "Account.Blocked(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The login path could not find an account for the resolved login ID.
         *
         * End-user: End this sign-in attempt and direct the user to registration or another app-level path.
         *
         * Developer action: Treat as an expected business outcome unless account provider data is inconsistent.
         */
        public class NotFound(override val errorCode: ErrorCode, override val message: String) : Account() {
            override fun toString(): String = "Account.NotFound(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: The flow could not satisfy server authentication requirements with the operations available in this SDK
     * instance and context.
     *
     * End-user: End this sign-in attempt and offer a different app-level sign-in path or retry later.
     *
     * Developer action: Check server auth requirements, configured providers, operation availability, and diagnostics in
     * [message] before starting a new attempt. The message may include skipped operation reasons.
     */
    public class InsufficientAuth(override val errorCode: ErrorCode, override val message: String) : BoostLoginFlowFailure {
        override fun toString(): String = "InsufficientAuth(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: OwnID authentication succeeded, but the app's [SessionCreate] provider failed.
     *
     * End-user: End this sign-in attempt and show an app sign-in failure state.
     *
     * Developer action: Inspect the app [SessionCreate] provider, request context, and [error]. Avoid treating this as failed
     * OwnID authentication.
     *
     * @property error Underlying [SessionCreate] provider error retained for diagnostics, when available.
     */
    public class SessionCreationFailed(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) :
        BoostLoginFlowFailure {
        override fun toString(): String = "SessionCreationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: A login flow operation could not run, was unavailable, or completed with failure.
     *
     * End-user: End this sign-in attempt. The app may show a failure state, offer another app-level path, or let the
     * user start a new attempt.
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
    ) : BoostLoginFlowFailure {
        override fun toString(): String = "OperationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The flow stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: End this sign-in attempt and show a generic failure state.
     *
     * Developer action: Log flow context and [error]. Retry only by starting a new flow attempt after the cause is
     * understood or the user explicitly retries.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(override val errorCode: ErrorCode = ErrorCode.Unknown, override val message: String, public val error: Throwable? = null) :
        BoostLoginFlowFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}

/**
 * Failure payload returned by [BoostFlow.CreatePasskey].
 *
 * Every failure is terminal for the current create-passkey flow run. Some create-passkey path outcomes are still
 * successful even when local passkey attestation is unavailable or fails; in that case proof values are absent and the
 * app should continue using its non-passkey registration path.
 */
public sealed interface BoostCreatePasskeyFlowFailure : FlowFailure {
    /** Input or OwnID context required before passkey creation could not be resolved. */
    public sealed class Input : BoostCreatePasskeyFlowFailure {
        /**
         * About: The flow could not resolve a usable login ID from the available context or access token.
         *
         * End-user: End this registration attempt and let the user restart or enter a valid identifier in a new run.
         *
         * Developer action: Check token contents, [Context] values, [BoostFlowContext], and login ID validation setup.
         *
         * @property error Underlying token or validation error retained for diagnostics, when available.
         */
        public class UnresolvedLoginId(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) : Input() {
            override fun toString(): String = "Input.UnresolvedLoginId(errorCode=$errorCode, message=$message)"
        }
    }

    /** Account state prevented the flow from completing. */
    public sealed class Account : BoostCreatePasskeyFlowFailure {
        /**
         * About: The resolved account is blocked and cannot continue.
         *
         * End-user: End this attempt and direct the user to the app's recovery or support path.
         *
         * Developer action: Treat as an expected business outcome unless provider data is inconsistent.
         */
        public class Blocked(override val errorCode: ErrorCode, override val message: String) : Account() {
            override fun toString(): String = "Account.Blocked(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The flow could not find an account for the resolved login ID in a path that requires an existing account.
         *
         * End-user: End this attempt and route the user to the app's registration path or another identifier entry path.
         *
         * Developer action: Treat as an expected account-state outcome unless provider data is inconsistent.
         */
        public class NotFound(override val errorCode: ErrorCode, override val message: String) : Account() {
            override fun toString(): String = "Account.NotFound(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: The flow could not satisfy server authentication requirements with the operations available in this SDK
     * instance and context.
     *
     * End-user: End this attempt and offer a different sign-in or registration path, or retry later.
     *
     * Developer action: Check server auth requirements, configured providers, operation availability, and diagnostics in
     * [message] before starting a new attempt. The message may include skipped operation reasons.
     */
    public class InsufficientAuth(override val errorCode: ErrorCode, override val message: String) : BoostCreatePasskeyFlowFailure {
        override fun toString(): String = "InsufficientAuth(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: OwnID authentication succeeded, but the app's [SessionCreate] provider failed.
     *
     * End-user: End this attempt and show an app sign-in failure state.
     *
     * Developer action: Inspect the app [SessionCreate] provider, request context, and [error]. Avoid treating this as failed
     * OwnID authentication.
     *
     * @property error Underlying [SessionCreate] provider error retained for diagnostics, when available.
     */
    public class SessionCreationFailed(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) :
        BoostCreatePasskeyFlowFailure {
        override fun toString(): String = "SessionCreationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: A required create-passkey flow operation could not run, was unavailable, or completed with failure.
     *
     * End-user: End this attempt. The app may show a failure state, offer another app-level path, or let the user start
     * a new attempt.
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
    ) : BoostCreatePasskeyFlowFailure {
        override fun toString(): String = "OperationFailed(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The flow stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: End this attempt and show a generic failure state.
     *
     * Developer action: Log flow context and [error]. Retry only by starting a new flow attempt after the cause is
     * understood or the user explicitly retries.
     *
     * @property error Underlying runtime error retained for diagnostics, when available.
     */
    public class Unexpected(override val errorCode: ErrorCode = ErrorCode.Unknown, override val message: String, public val error: Throwable? = null) :
        BoostCreatePasskeyFlowFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
