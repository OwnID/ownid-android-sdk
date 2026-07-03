package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialChallenge
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.namespace.OwnIdApi

/**
 * Starts an OpenID Connect authentication flow.
 *
 * Use this API when the app drives the social-provider UI itself and needs OwnID only for the challenge lifecycle.
 * [start] returns a [Controller] that carries the provider challenge, accepts the provider result, and can cancel the
 * challenge when the app abandons the flow.
 *
 * Calling [start] creates a server-side challenge only; the app owns provider UI, provider tokens/codes, and any
 * session exchange after completion.
 *
 * Part of [OwnIdApi.oidc].
 *
 * OpenAPI source: `startOidcChallenge` operation.
 */
public interface OidcApi : ApiCapability {

    /**
     * Parameters for starting an OIDC challenge.
     *
     * The public direct API exposes provider, response type, and optional session access token only. Login ID hints and
     * redirect URI overrides are SDK-owned flow details and are not configurable through this public parameter object.
     *
     * @property provider Social identity provider to use. When `null`, OwnID starts a Google challenge.
     * @property oauthResponseType Provider result type the app will later pass back to the controller. Use
     * [OauthResponseType.ID_TOKEN] with [Controller.completeWithToken] and [OauthResponseType.CODE] with
     * [Controller.completeWithCode].
     * @property accessToken Optional token for an already-authenticated session. The app owns this sensitive value.
     * When `null`, OwnID uses the current [Context] access token when one is available. The same token is used for this
     * challenge's start, complete, and cancel requests and is not persisted by the SDK.
     *
     * OpenAPI source: `StartOidcChallengeRequest` schema and `provider` path parameter.
     */
    public class Params internal constructor(
        public val provider: SocialProvider? = null,
        public val oauthResponseType: OauthResponseType = OauthResponseType.ID_TOKEN,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(
            provider: SocialProvider? = null,
            oauthResponseType: OauthResponseType = OauthResponseType.ID_TOKEN,
            accessToken: AccessToken? = null
        ) : this(provider, oauthResponseType, accessToken, traceParent = null)
    }

    /**
     * Controls an active OIDC authentication flow.
     *
     * Use [challenge] to start the provider flow in your app. After the provider finishes, call the completion method
     * that matches the [Params.oauthResponseType] used to start this controller. A successful completion returns the
     * OwnID [AccessTokenWithUserInfo] for the authenticated user. Call [cancel] when the app stops the provider flow
     * without a provider result.
     *
     * Calling the completion method that does not match the requested response type fails locally with
     * [OidcCompleteApiFailure.BadRequest.InvalidArgument]. Keep the controller strongly referenced while the challenge
     * can still complete or cancel. Releasing or clearing the controller reference only loses the app's handle; it
     * does not cancel the server challenge. Call [cancel] before clearing the last reference when the app abandons this
     * challenge.
     *
     * Canceling the surrounding coroutine is not the same as canceling the OIDC challenge; call [cancel] when the app
     * abandons the provider flow.
     *
     * OpenAPI source: `OidcChallengeResponse` success response schema, with linked `completeOidcChallenge` and
     * `cancelOidcVerification` operations.
     */
    public interface Controller {
        /**
         * The provider challenge the app must use to launch social authentication.
         *
         * The challenge includes the OwnID challenge identifier, expiration timeout, provider client ID, and optional
         * web challenge URL. The app owns presenting or invoking the provider flow with those values.
         *
         * OpenAPI source: `OidcChallengeResponse` schema.
         */
        public val challenge: SocialChallenge

        /**
         * Completes authentication with an ID token returned by the provider.
         *
         * Use this when the controller was started with [OauthResponseType.ID_TOKEN]. On success, the returned
         * [AccessTokenWithUserInfo] contains the OwnID access token plus the login ID, provider, and provider user info.
         * The app owns the provider ID token it passes in and the OwnID token returned on success.
         * When this controller expects an authorization code, this function returns
         * [OidcCompleteApiFailure.BadRequest.InvalidArgument].
         *
         * OpenAPI source: `completeOidcChallenge` operation; `CompleteOidcChallengeRequest` request schema.
         */
        public val completeWithToken: suspend (idToken: String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure>

        /**
         * Completes authentication with an authorization code returned by the provider.
         *
         * Use this when the controller was started with [OauthResponseType.CODE]. On success, the returned
         * [AccessTokenWithUserInfo] contains the OwnID access token plus the login ID, provider, and provider user info.
         * The app owns the provider code it passes in and the OwnID token returned on success.
         * When this controller expects an ID token, this function returns
         * [OidcCompleteApiFailure.BadRequest.InvalidArgument].
         *
         * OpenAPI source: `completeOidcChallenge` operation; `CompleteOidcChallengeRequest` request schema.
         */
        public val completeWithCode: suspend (code: String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure>

        /**
         * Cancels the active OIDC challenge with an app-selected [Reason].
         *
         * Use this when the user closes the provider UI, the app moves to another challenge, or the app otherwise
         * abandons the flow. A successful result means OwnID accepted the cancel request; it does not dismiss provider
         * UI that the app owns. Invalid, expired, or already completed challenges are reported through
         * [OidcCancelApiFailure]. Canceling the coroutine or releasing the controller does not cancel the server-side
         * challenge; call this function when the app wants to request challenge cancellation.
         *
         * OpenAPI source: `cancelOidcVerification` operation.
         */
        public val cancel: suspend (reason: Reason) -> ApiResult<Unit, OidcCancelApiFailure>
    }

    /**
     * Starts the OIDC authentication flow.
     *
     * The app is responsible for driving the social-provider UI using the returned controller's [Controller.challenge].
     * Android direct APIs propagate coroutine cancellation to the caller; cancellation is not encoded as [ApiResult].
     *
     * @param params Optional OIDC parameters. Defaults to `null`.
     *
     * @return [ApiResult.Success] with a [Controller], or [ApiResult.Failure] with a typed failure.
     *
     * OpenAPI source: `startOidcChallenge` operation.
     */
    public suspend fun start(params: Params? = null): ApiResult<Controller, OidcStartApiFailure>
}

/**
 * Native failure hierarchy returned by [OidcApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for starting an OIDC challenge, plus
 * [OidcStartApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `startOidcChallenge` operation.
 */
public sealed interface OidcStartApiFailure : ApiFailure {
    /**
     * Bad request.
     *
     * OpenAPI source: `BadRequestErrorResponse` response component.
     */
    public sealed class BadRequest : OidcStartApiFailure {
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : OidcStartApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : OidcStartApiFailure {
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
     * About: The active challenge limit or concurrency guard was reached.
     *
     * End-user: Ask the user to wait briefly or finish an existing challenge before starting another.
     *
     * Developer action: Rate-limit retries, log repeated occurrences, and inspect challenge cleanup if this persists.
     *
     * OpenAPI source: `MaximumChallengesReachedError` schema.
     */
    public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String) : OidcStartApiFailure {
        override fun toString(): String = "MaximumChallengesReached(errorCode=$errorCode, message=$message)"
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
        ApiUnexpectedFailure(errorCode, message, cause), OidcStartApiFailure
}

/**
 * Native failure hierarchy returned by [OidcApi.Controller.completeWithToken] or [OidcApi.Controller.completeWithCode].
 *
 * Direct cases correspond to OpenAPI error response components for completing an OIDC challenge, plus
 * [OidcCompleteApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `completeOidcChallenge` operation.
 */
public sealed interface OidcCompleteApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : OidcCompleteApiFailure {
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
         * About: The challenge was not found, expired or already completed.
         *
         * End-user: Ask the user to restart the operation.
         *
         * Developer action: Stop polling or completing this challenge ID and create a fresh challenge.
         *
         * @property challengeId Challenge identifier that was not found, expired, or already completed.
         *
         * OpenAPI source: `InvalidChallengeError` schema.
         */
        public class InvalidChallenge(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) : BadRequest() {
            override fun toString(): String = "BadRequest.InvalidChallenge(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The user exhausted the allowed verification attempts for the challenge.
         *
         * End-user: Ask the user to start a new challenge.
         *
         * Developer action: Do not retry the same challenge; clear local challenge state and restart the flow.
         *
         * @property challengeId Challenge identifier for which the allowed verification attempts were exhausted.
         *
         * OpenAPI source: `MaximumAttemptsReachedError` schema.
         */
        public class MaximumAttemptsReached(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) :
            BadRequest() {
            override fun toString(): String = "BadRequest.MaximumAttemptsReached(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : OidcCompleteApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : OidcCompleteApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), OidcCompleteApiFailure
}

/**
 * Native failure hierarchy returned by [OidcApi.Controller.cancel].
 *
 * Direct cases correspond to OpenAPI error response components for canceling an OIDC challenge, plus
 * [OidcCancelApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `cancelOidcVerification` operation.
 */
public sealed interface OidcCancelApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : OidcCancelApiFailure {
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
         * About: The challenge was not found, expired or already completed.
         *
         * End-user: Ask the user to restart the operation.
         *
         * Developer action: Stop polling or completing this challenge ID and create a fresh challenge.
         *
         * @property challengeId Challenge identifier that was not found, expired, or already completed.
         *
         * OpenAPI source: `InvalidChallengeError` schema.
         */
        public class InvalidChallenge(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) : BadRequest() {
            override fun toString(): String = "BadRequest.InvalidChallenge(errorCode=$errorCode, message=$message)"
        }

        /**
         * About: The user exhausted the allowed verification attempts for the challenge.
         *
         * End-user: Ask the user to start a new challenge.
         *
         * Developer action: Do not retry the same challenge; clear local challenge state and restart the flow.
         *
         * @property challengeId Challenge identifier for which the allowed verification attempts were exhausted.
         *
         * OpenAPI source: `MaximumAttemptsReachedError` schema.
         */
        public class MaximumAttemptsReached(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) :
            BadRequest() {
            override fun toString(): String = "BadRequest.MaximumAttemptsReached(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : OidcCancelApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
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
        ApiUnexpectedFailure(errorCode, message, cause), OidcCancelApiFailure
}
