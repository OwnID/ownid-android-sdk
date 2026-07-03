package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.namespace.OwnIdApi

/**
 * Starts login for a user that already has an OwnID access token.
 *
 * Use this direct API after the app or an OwnID flow has obtained an [AccessToken]. The token is taken from
 * [Params.accessToken], or from the scoped [Context] when the parameter is omitted.
 *
 * A successful API result contains [LoginResponse]. [LoginResponse.Success] gives the app-owned data needed to create
 * the app session. [LoginResponse.NoSession] outcomes mean OwnID did not return a session payload; handle the specific
 * outcome by continuing the required authentication, account-not-found, or account-blocked path.
 *
 * Calling [start] sends a direct API request only; it does not create an operation, present UI, persist tokens, or
 * create the app session.
 *
 * Part of [OwnIdApi.Auth].
 *
 * OpenAPI source: `login` operation.
 */
public interface LoginApi : ApiCapability {

    /**
     * Parameters for token-backed login.
     *
     * @property accessToken Access token proving login ID ownership. The app owns this sensitive value. When `null`,
     *   the SDK uses the access token from the scoped [Context] if one is available. Calling login without a resolvable
     *   access token completes with [LoginApiFailure.BadRequest.InvalidArgument].
     *
     * OpenAPI source: `LoginRequest` schema.
     */
    public class Params internal constructor(public val accessToken: AccessToken? = null, internal val traceParent: String?) {
        public constructor(accessToken: AccessToken? = null) : this(accessToken, traceParent = null)
    }

    /**
     * Starts token-backed login.
     *
     * @param params Optional parameters. Omit only when the scoped [Context] contains the access token to use.
     *
     * @return [ApiResult.Success] with the login outcome, or [ApiResult.Failure] with a typed failure. Coroutine
     *   cancellation is propagated to the caller and is not converted to [ApiResult].
     *
     * OpenAPI source: `login` operation; `LoginResponse` and `AuthRequiredResponse` success response schemas.
     */
    public suspend fun start(params: Params? = null): ApiResult<LoginResponse, LoginApiFailure>
}

/**
 * Native failure hierarchy returned by [LoginApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for the login operation, plus
 * [LoginApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `login` operation.
 */
public sealed interface LoginApiFailure : ApiFailure {
    /**
     * Bad request.
     *
     * OpenAPI source: `BadLoginIdRequestErrorResponse` response component.
     */
    public sealed class BadRequest : LoginApiFailure {
        /**
         * About: The request payload, route parameter, query parameter, or operation state is invalid.
         *
         * End-user: Not Applicable
         *
         * Developer action: Validate the request has all the required fields in their expected format.
         *
         * OpenAPI source: `InvalidArgumentError` schema.
         */
        public class InvalidArgument(override val errorCode: ErrorCode, override val message: String) : BadRequest() {
            override fun toString(): String = "BadRequest.InvalidArgument(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The login ID value does not match the validation rule configured for its type.
         *
         * End-user: Ask the user to correct the identifier.
         *
         * Developer action: Surface the configured regex only in diagnostics and keep client validation aligned with server config.
         *
         * @property loginId Login ID value that failed validation.
         * @property regex Validation regex configured for the login ID type.
         *
         * OpenAPI source: `LoginIdValidationError` schema.
         */
        public class InvalidLoginId(override val errorCode: ErrorCode, override val message: String, public val loginId: LoginId, public val regex: String) :
            BadRequest() {
            override fun toString(): String = "BadRequest.InvalidLoginId(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The supplied login ID type is not supported by the app or operation.
         *
         * End-user: Ask for a supported identifier such as email or phone, based on app configuration.
         *
         * Developer action: Compare the requested login ID type with the app's login ID configuration.
         *
         * OpenAPI source: `LoginIdTypeNotSupportedError` schema.
         */
        public class UnsupportedLoginIdType(override val errorCode: ErrorCode, override val message: String) : BadRequest() {
            override fun toString(): String = "BadRequest.UnsupportedLoginIdType(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The server could not map the failure to a more specific public error code.
         *
         * End-user: Show a generic failure message and suggest retrying.
         *
         * Developer action: Log the correlation context, inspect server logs, and escalate if the issue repeats.
         *
         * OpenAPI source: `UnknownError` schema.
         */
        public class Unknown(override val errorCode: ErrorCode, override val message: String) : BadRequest() {
            override fun toString(): String = "BadRequest.Unknown(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: The caller is authenticated but not allowed to perform the operation.
     *
     * End-user: Explain that the action is unavailable or expired.
     *
     * Developer action: Check access token claims or the operation's policy for the required claims.
     *
     * OpenAPI source: `ForbiddenError` schema.
     */
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : LoginApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : LoginApiFailure {
        /**
         * About: A configured provider failed while OwnID was processing the operation, e.g. integration-endpoint failed
         * to fetch the user, email-server failed to send the email.
         *
         * End-user: Show a temporary failure message.
         *
         * Developer action: Log provider request context, monitor the provider integration, and escalate repeated failures.
         *
         * @property scope Provider or capability scope associated with the dependency failure.
         *
         * OpenAPI source: `ProviderError` schema.
         */
        public class ProviderFailed(override val errorCode: ErrorCode, override val message: String, public val scope: ApiFailureScope) : FailedDependency() {
            override fun toString(): String = "FailedDependency.ProviderFailed(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: No provider is configured for the capability required by the operation, e.g. send SMS for
         * phone-verification.
         *
         * End-user: Not Applicable.
         *
         * Developer action: Configure the missing provider capability for the app and deployment environment.
         *
         * @property capability Missing provider capability required by the operation.
         * @property scope Provider or capability scope associated with the missing capability.
         *
         * OpenAPI source: `MissingProviderError` schema.
         */
        public class MissingProvider(
            override val errorCode: ErrorCode, override val message: String, public val capability: String, public val scope: ApiFailureScope
        ) : FailedDependency() {
            override fun toString(): String = "FailedDependency.MissingProvider(errorCode=$errorCode, message=$message)"
        }
    }

    /**
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error,
     * unhandled HTTP status, or response mapping failure.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for transport, status handling, or
     * response mapping issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), LoginApiFailure
}
