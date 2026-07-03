package com.ownid.sdk.ops

import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken

/**
 * Enrolls a passkey on the server using a proof token.
 *
 * This is an API-only operation with no platform passkey UI. It does not create or retrieve local credentials; it
 * completes the OwnID enrollment boundary for a passkey that was proven by an earlier attestation or verification step.
 *
 * [Params.proofToken] is always caller-supplied. [Params.accessToken] takes precedence over the current OwnID context
 * access token; when neither access token source is available, the operation completes with
 * [OperationResult.Failure]. The proof token and access token are used only for this enrollment run and are not a
 * session creation mechanism.
 *
 * The returned controller represents one operation run and settles once. Calling [OperationController.abort] before
 * settlement cancels the operation with the supplied [Reason]; repeated starts on the same operation object return the
 * same controller and do not start a second enrollment. On success, the passkey is enrolled and the operation completes
 * with `Unit`.
 *
 * [availability] and [isAvailable] are preflight checks for registered dependencies, [Params.proofToken], and a
 * resolvable access token; [start] revalidates the same contract and may still settle with a typed failure or
 * cancellation if runtime state changes.
 */
public interface PasskeyEnrollOperation : OperationCapability<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> {

    /**
     * Parameters for [PasskeyEnrollOperation].
     *
     * @property proofToken The proof token from a prior attestation or verification step. This value must be supplied
     *   directly in params.
     * @property accessToken An existing access token, if available. When `null`, the current OwnID context access token
     *   is used if available. Defaults to `null`.
     * @property headless Reserved for future use and currently has no observable effect. Defaults to `null`.
     */
    public class Params internal constructor(
        public val proofToken: ProofToken,
        public val accessToken: AccessToken? = null,
        public val headless: Boolean? = null,
        internal val traceParent: String?
    ) : Capability.Params {
        public constructor(proofToken: ProofToken, accessToken: AccessToken? = null, headless: Boolean? = null)
                : this(proofToken, accessToken, headless, traceParent = null)
    }
}

/** Failure payload returned by [PasskeyEnrollOperation]. */
public sealed interface PasskeyEnrollOperationFailure : OperationFailure {
    /** Required enrollment input is missing or invalid. */
    public sealed class Input : PasskeyEnrollOperationFailure {
        /**
         * About: The operation could not resolve both tokens required to enroll a passkey.
         *
         * End-user: No direct user action. The app should restart the prerequisite proof or authentication step.
         *
         * Developer action: Pass a proof token and provide an access token through [PasskeyEnrollOperation.Params] or OwnID context.
         */
        public class MissingTokens(override val errorCode: ErrorCode, override val message: String) : Input() {
            override fun toString(): String = "Input.MissingTokens(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The passkey enrollment request was rejected as invalid.
         *
         * End-user: No direct user action unless the app can restart the prerequisite step.
         *
         * Developer action: Check proof-token/access-token pairing and [apiFailure] for the rejected operation invariant.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidRequest(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Input() {
            override fun toString(): String = "Input.InvalidRequest(errorCode=$errorCode, message=$message)"
        }
    }

    /** The account or caller is not allowed to continue. */
    public sealed class Access : PasskeyEnrollOperationFailure {
        /**
         * About: The account referenced by the enrollment tokens was not found.
         *
         * End-user: Direct the user to register or sign in again, based on app state.
         *
         * Developer action: Treat as an expected business outcome unless token/account data is inconsistent.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class UserNotFound(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.UserNotFound(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The caller is not allowed to enroll a passkey for this account/context.
         *
         * End-user: Explain that passkey enrollment is unavailable.
         *
         * Developer action: Check access token claims, proof-token origin, and app policy.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Forbidden(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.Forbidden(errorCode=$errorCode, message=$message)"
        }
    }

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : PasskeyEnrollOperationFailure {
        /**
         * About: A configured backend/provider dependency failed while enrolling the passkey.
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
         * About: A provider capability required by passkey enrollment is not configured.
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
    }

    /**
     * About: The operation stopped because of an unexpected SDK/runtime failure or an internal invariant violation.
     *
     * End-user: Show a generic failure state. Retrying may be reasonable after restarting the enrollment step.
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
    ) : PasskeyEnrollOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
