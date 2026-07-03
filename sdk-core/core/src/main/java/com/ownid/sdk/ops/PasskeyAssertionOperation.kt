package com.ownid.sdk.ops

import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason

/**
 * Authenticates the user with an existing passkey.
 *
 * This operation owns the full SDK-managed passkey assertion lifecycle: it starts an OwnID challenge, presents the
 * platform passkey UI through the registered [Passkey] capability, verifies the platform
 * assertion result, and settles the returned [OperationController]. Apps that want to own the platform passkey UI
 * should use the direct passkey API instead of this operation.
 *
 * Explicit [Params] values take precedence over the current OwnID context. If [Params.accessToken] or the context
 * access token is available, [Params.loginId] may be omitted. If no access token is available, the operation requires a
 * login ID from [Params.loginId] or the current context; context login ID resolution can produce typed input or
 * integration failures.
 *
 * The platform passkey prompt is owned by AndroidX Credential Manager and the installed credential provider. The SDK
 * supplies the current SDK UI context and converts the resulting passkey capability outcome into operation settlement:
 * successful assertion returns an [AccessToken], user or system cancellation returns [OperationResult.Canceled], no
 * applicable credential returns [PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys], and other platform
 * or provider failures return [OperationResult.Failure]. For predictable prompt behavior, start this operation while
 * your app has an active foreground `Activity`.
 *
 * The returned controller represents one operation run and settles once. Calling [OperationController.abort] before
 * settlement cancels the active operation with the supplied [Reason]; when a server challenge is active, cancellation is
 * also reported to OwnID on a best-effort basis. If the server provides a timeout, the operation cancels with
 * [Reason.Timeout] after it elapses. Repeated starts on the same operation object return the same controller and do not
 * create a new passkey prompt.
 *
 * Requires API 28+ with passkey capabilities registered. [availability] and [isAvailable] are preflight checks for
 * registered dependencies and resolvable login ID/access token input; [start] revalidates the same contract and may
 * still settle with a typed failure or cancellation if runtime state changes.
 */
public interface PasskeyAssertionOperation : OperationCapability<PasskeyAssertionOperation.Params, AccessToken, PasskeyAssertionOperationFailure> {

    /**
     * Parameters for [PasskeyAssertionOperation].
     *
     * @property loginId The user's login identifier. When `null`, the current OwnID context login ID is used if
     *   available and no access token is available. Defaults to `null`.
     * @property accessToken An existing access token, if available. When `null`, the current OwnID context access token
     *   is used if available. A resolved access token can identify the assertion request without a login ID. Defaults
     *   to `null`.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) : Capability.Params {
        public constructor(loginId: LoginId? = null, accessToken: AccessToken? = null) : this(loginId, accessToken, traceParent = null)
    }
}

/** Failure payload returned by [PasskeyAssertionOperation]. */
public sealed interface PasskeyAssertionOperationFailure : OperationFailure {
    /** Login ID or token input is missing or invalid. */
    public sealed class Input : PasskeyAssertionOperationFailure {
        /**
         * About: The operation could not resolve either an access token or a login ID to start passkey assertion.
         *
         * End-user: Ask the user to provide an identifier or restart from an authenticated state.
         *
         * Developer action: Pass [PasskeyAssertionOperation.Params.loginId] or
         * [PasskeyAssertionOperation.Params.accessToken], or provide one through OwnID context.
         */
        public class MissingLoginIdOrAccessToken(override val errorCode: ErrorCode, override val message: String) : Input() {
            override fun toString(): String = "Input.MissingLoginIdOrAccessToken(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The resolved login ID value failed validation.
         *
         * End-user: Ask the user to correct the identifier.
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
         * About: The resolved login ID type is not supported for passkey assertion.
         *
         * End-user: Ask the user for a supported identifier type, if the app lets the user choose one.
         *
         * Developer action: Compare the supplied login ID type with the app's OwnID login ID configuration.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class UnsupportedLoginIdType(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) :
            Input() {
            override fun toString(): String = "Input.UnsupportedLoginIdType(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The passkey assertion start request was rejected as invalid.
         *
         * End-user: No direct user action unless the app can collect corrected input from the user.
         *
         * Developer action: Check the supplied params, resolved context values, and [apiFailure] for the rejected
         * operation invariant.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidRequest(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Input() {
            override fun toString(): String = "Input.InvalidRequest(errorCode=$errorCode, message=$message)"
        }
    }

    /** The account or caller is not allowed to continue. */
    public sealed class Access : PasskeyAssertionOperationFailure {
        /**
         * About: The account associated with the passkey assertion request was not found.
         *
         * End-user: Direct the user to register or use another sign-in method, based on app state.
         *
         * Developer action: Treat as an expected business outcome unless account provider data is inconsistent.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class UserNotFound(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.UserNotFound(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The caller is not allowed to start or complete passkey assertion in this context.
         *
         * End-user: Explain that passkey sign-in is unavailable.
         *
         * Developer action: Check access token claims, app policy, and operation requirements.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Forbidden(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.Forbidden(errorCode=$errorCode, message=$message)"
        }
    }

    /** Passkey assertion challenge cannot continue or must be restarted. */
    public sealed class Challenge : PasskeyAssertionOperationFailure {
        /**
         * About: The active passkey assertion challenge limit was reached.
         *
         * End-user: Ask the user to wait briefly or finish an existing sign-in attempt.
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
         * About: The passkey assertion challenge is invalid, expired, or no longer usable.
         *
         * End-user: Ask the user to restart passkey sign-in.
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
         * About: The passkey assertion challenge reached its attempt limit.
         *
         * End-user: Ask the user to start passkey sign-in again.
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

    /** User or device credential state prevents passkey assertion. */
    public sealed class Credential : PasskeyAssertionOperationFailure {
        /**
         * About: No passkey was available or applicable for this assertion request.
         *
         * End-user: Offer another sign-in method or ask the user to use a different account/device.
         *
         * Developer action: Treat as a normal credential outcome; inspect [error] only for diagnostics.
         *
         * @property error Underlying Credential Manager error retained for diagnostics, when available.
         */
        public class NoApplicablePasskeys(override val errorCode: ErrorCode, override val message: String, public val error: Throwable? = null) :
            Credential() {
            override fun toString(): String = "Credential.NoApplicablePasskeys(errorCode=$errorCode, message=$message)"
        }
    }

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : PasskeyAssertionOperationFailure {
        /**
         * About: A backend/provider/platform dependency failed while processing passkey assertion.
         *
         * End-user: Show a temporary failure state or let the user choose another sign-in method.
         *
         * Developer action: Inspect provider configuration, Credential Manager availability, [apiFailure], and [error].
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         * @property error Underlying provider/platform error retained for diagnostics, when available.
         */
        public class ProviderFailed(
            override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null, public val error: Throwable? = null
        ) : Integration() {
            override fun toString(): String = "Integration.ProviderFailed(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: A provider capability required by passkey assertion is not configured.
         *
         * End-user: No direct user action. The app should offer another available sign-in method.
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
     * End-user: Show a generic failure state. Retrying may be reasonable after restarting passkey sign-in.
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
    ) : PasskeyAssertionOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
