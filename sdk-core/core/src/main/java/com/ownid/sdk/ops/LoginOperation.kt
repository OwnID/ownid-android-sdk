package com.ownid.sdk.ops

import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse

/**
 * Authenticates a user and returns a [LoginResponse].
 *
 * Routing is token-first:
 * - If an access token is supplied in [Params] or the current OwnID context, the operation performs token login and
 *   ignores any login ID.
 * - Otherwise, if a login ID is supplied in [Params] or the current OwnID context, the operation validates it and
 *   performs login discovery.
 * - If neither value can be resolved, the operation completes with [OperationResult.Failure].
 *
 * [availability] mirrors that input precedence as a preflight check: it is available when a token is present, or when a
 * resolvable login ID validates successfully. The operation rechecks inputs after [start], so callers should still
 * handle terminal failure or cancellation.
 *
 * On API success, the controller completes with [OperationResult.Success] containing the [LoginResponse] returned by
 * OwnID, including [LoginResponse.Success] and [LoginResponse.NoSession] outcomes. Validation, access-policy,
 * integration, and backend errors complete with [OperationResult.Failure]. Caller or SDK cancellation completes with
 * [OperationResult.Canceled].
 */
public interface LoginOperation : OperationCapability<LoginOperation.Params, LoginResponse, LoginOperationFailure> {

    /**
     * Parameters for [LoginOperation].
     *
     * @property loginId User login identifier used only when no access token is resolved. When null, context login ID
     * is used if available.
     * @property accessToken Access token proving ownership for login API. When null, context access token is used if
     * available.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) : Capability.Params {
        public constructor(loginId: LoginId? = null, accessToken: AccessToken? = null) : this(loginId, accessToken, traceParent = null)
    }
}

/** Failure payload returned by [LoginOperation]. */
public sealed interface LoginOperationFailure : OperationFailure {
    /** Login ID or token input is missing or invalid. */
    public sealed class Input : LoginOperationFailure {
        /**
         * About: The operation could not resolve either an access token or a login ID to start login.
         *
         * End-user: Ask the user to provide an identifier or sign in through another available path.
         *
         * Developer action: Pass [LoginOperation.Params.loginId] or [LoginOperation.Params.accessToken], or provide one
         * through OwnID context.
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
         * About: The resolved login ID type is not supported for login discovery.
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
         * About: Login input was rejected before the operation could be routed to a successful outcome.
         *
         * End-user: No direct user action unless the app can collect corrected input from the user.
         *
         * Developer action: Inspect the supplied token/login-ID context and [apiFailure] to determine which input invariant was violated.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidRequest(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Input() {
            override fun toString(): String = "Input.InvalidRequest(errorCode=$errorCode, message=$message)"
        }
    }

    /** The account or caller is not allowed to continue. */
    public sealed class Access : LoginOperationFailure {
        /**
         * About: The caller is authenticated but not allowed to perform this login operation.
         *
         * End-user: Explain that the requested action is unavailable.
         *
         * Developer action: Check access token claims, app policy, and operation requirements.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Forbidden(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.Forbidden(errorCode=$errorCode, message=$message)"
        }
    }

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : LoginOperationFailure {
        /**
         * About: A configured backend/provider dependency failed while processing login.
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
         * About: A provider capability required by login is not configured.
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
     * End-user: Show a generic failure state. Retrying may be reasonable if the app can safely restart login.
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
    ) : LoginOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
