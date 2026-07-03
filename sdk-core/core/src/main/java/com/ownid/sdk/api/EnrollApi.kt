package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.namespace.OwnIdApi

/**
 * Enrolls a passkey for an authenticated user using a proof token.
 *
 * The proof token comes from a successful passkey attestation verification. [Params.accessToken], when supplied, is
 * used as the authenticated account token; otherwise the SDK uses the access token from the current [Context] when one
 * is available. If neither source has an access token, [start] returns
 * [PasskeyEnrollApiFailure.BadRequest.InvalidArgument] before sending the request.
 *
 * Success means the backend accepted enrollment and returned no response payload.
 *
 * Part of [OwnIdApi.Enroll].
 *
 * OpenAPI source: `attestationEnroll` operation.
 */
public interface PasskeyEnrollApi : ApiCapability {

    /**
     * Parameters for the passkey enrollment API request.
     *
     * @property proofToken Proof token returned by passkey attestation verification.
     * @property accessToken Access token for the authenticated account. When `null`, the SDK uses the access token from
     *   the current [Context] if available.
     *
     * OpenAPI source: `AttestationEnrollRequest` schema.
     */
    public class Params internal constructor(
        public val proofToken: ProofToken,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(proofToken: ProofToken, accessToken: AccessToken? = null) : this(proofToken, accessToken, traceParent = null)
    }

    /**
     * Starts the passkey enrollment.
     *
     * @param params Enrollment parameters including the required proof token.
     *
     * @return [ApiResult.Success] when enrollment is accepted, or [ApiResult.Failure] with a typed failure. Coroutine
     *   cancellation is propagated to the caller and is not converted to [ApiResult].
     *
     * OpenAPI source: `attestationEnroll` operation.
     */
    public suspend fun start(params: Params): ApiResult<Unit, PasskeyEnrollApiFailure>
}

/**
 * Native failure hierarchy returned by [PasskeyEnrollApi.start].
 *
 * Endpoint-defined failures include invalid enrollment input, forbidden enrollment, missing user, and provider or
 * capability dependency failures. [PasskeyEnrollApiFailure.Unexpected] represents transport failures, local runtime
 * failures, and endpoint responses that cannot be represented by a typed case.
 *
 * OpenAPI source: `attestationEnroll` operation.
 */
public sealed interface PasskeyEnrollApiFailure : ApiFailure {
    /**
     * Bad request while validating the passkey enrollment proof token or request.
     *
     * OpenAPI source: `BadEnrollRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyEnrollApiFailure {
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
     * About: The endpoint rejected authorization for this enrollment.
     *
     * End-user: Explain that the action is unavailable or expired.
     *
     * Developer action: Check that a valid access token is supplied by parameters or [Context] and has the claims
     * required by the operation's policy.
     *
     * OpenAPI source: `ForbiddenError` schema.
     */
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PasskeyEnrollApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The account provider did not find an account for the requested login ID.
     *
     * End-user: Direct the user to register an account.
     *
     * Developer action: Treat this as an expected business outcome; do not escalate unless provider data is inconsistent.
     *
     * OpenAPI source: `UserNotFoundError` schema.
     */
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : PasskeyEnrollApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PasskeyEnrollApiFailure {
        /**
         * About: A configured backend provider failed while OwnID was processing the operation.
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
         * About: No provider is configured for a capability required by the operation.
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
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error, or unexpected
     * endpoint response.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for network, endpoint contract, or
     * SDK runtime issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyEnrollApiFailure
}

/**
 * Enrolls an email address for an authenticated user using a proof token.
 *
 * The proof token comes from a completed email verification challenge. [Params.accessToken], when supplied, is used as
 * the authenticated account token; otherwise the SDK uses the access token from the current [Context] when one is
 * available. If neither source has an access token, [start] returns
 * [EmailEnrollApiFailure.BadRequest.InvalidArgument] before sending the request.
 *
 * Success means the backend accepted enrollment and returned no response payload.
 *
 * Part of [OwnIdApi.Enroll].
 *
 * OpenAPI source: `enrollEmailAddress` operation.
 */
public interface EmailEnrollApi : ApiCapability {

    /**
     * Parameters for the email enrollment API request.
     *
     * @property proofToken Proof token returned by completing an email verification challenge.
     * @property accessToken Access token for the authenticated account. When `null`, the SDK uses the access token from
     *   the current [Context] if available.
     *
     * OpenAPI source: `EnrollRequest` schema.
     */
    public class Params internal constructor(
        public val proofToken: ProofToken,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(proofToken: ProofToken, accessToken: AccessToken? = null) : this(proofToken, accessToken, traceParent = null)
    }

    /**
     * Starts the email enrollment.
     *
     * @param params Enrollment parameters including the required proof token.
     *
     * @return [ApiResult.Success] when enrollment is accepted, or [ApiResult.Failure] with a typed failure. Coroutine
     *   cancellation is propagated to the caller and is not converted to [ApiResult].
     *
     * OpenAPI source: `enrollEmailAddress` operation.
     */
    public suspend fun start(params: Params): ApiResult<Unit, EmailEnrollApiFailure>
}

/**
 * Native failure hierarchy returned by [EmailEnrollApi.start].
 *
 * Endpoint-defined failures include invalid enrollment input, forbidden enrollment, missing user, and provider or
 * capability dependency failures. [EmailEnrollApiFailure.Unexpected] represents transport failures, local runtime
 * failures, and endpoint responses that cannot be represented by a typed case.
 *
 * OpenAPI source: `enrollEmailAddress` operation.
 */
public sealed interface EmailEnrollApiFailure : ApiFailure {
    /**
     * Bad request while validating the email enrollment proof token or request.
     *
     * OpenAPI source: `BadEnrollRequestErrorResponse` response component.
     */
    public sealed class BadRequest : EmailEnrollApiFailure {
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
     * About: The endpoint rejected authorization for this enrollment.
     *
     * End-user: Explain that the action is unavailable or expired.
     *
     * Developer action: Check that a valid access token is supplied by parameters or [Context] and has the claims
     * required by the operation's policy.
     *
     * OpenAPI source: `ForbiddenError` schema.
     */
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : EmailEnrollApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The account provider did not find an account for the requested login ID.
     *
     * End-user: Direct the user to register an account.
     *
     * Developer action: Treat this as an expected business outcome; do not escalate unless provider data is inconsistent.
     *
     * OpenAPI source: `UserNotFoundError` schema.
     */
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : EmailEnrollApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : EmailEnrollApiFailure {
        /**
         * About: A configured backend provider failed while OwnID was processing the operation.
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
         * About: No provider is configured for a capability required by the operation.
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
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error, or unexpected
     * endpoint response.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for network, endpoint contract, or
     * SDK runtime issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), EmailEnrollApiFailure
}

/**
 * Enrolls a phone number for an authenticated user using a proof token.
 *
 * The proof token comes from a completed phone verification challenge. [Params.accessToken], when supplied, is used as
 * the authenticated account token; otherwise the SDK uses the access token from the current [Context] when one is
 * available. If neither source has an access token, [start] returns
 * [PhoneEnrollApiFailure.BadRequest.InvalidArgument] before sending the request.
 *
 * Success means the backend accepted enrollment and returned no response payload.
 *
 * Part of [OwnIdApi.Enroll].
 *
 * OpenAPI source: `enrollPhoneNumber` operation.
 */
public interface PhoneEnrollApi : ApiCapability {

    /**
     * Parameters for the phone enrollment API request.
     *
     * @property proofToken Proof token returned by completing a phone verification challenge.
     * @property accessToken Access token for the authenticated account. When `null`, the SDK uses the access token from
     *   the current [Context] if available.
     *
     * OpenAPI source: `EnrollRequest` schema.
     */
    public class Params internal constructor(
        public val proofToken: ProofToken,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(proofToken: ProofToken, accessToken: AccessToken? = null) : this(proofToken, accessToken, traceParent = null)
    }

    /**
     * Starts the phone enrollment.
     *
     * @param params Enrollment parameters including the required proof token.
     *
     * @return [ApiResult.Success] when enrollment is accepted, or [ApiResult.Failure] with a typed failure. Coroutine
     *   cancellation is propagated to the caller and is not converted to [ApiResult].
     *
     * OpenAPI source: `enrollPhoneNumber` operation.
     */
    public suspend fun start(params: Params): ApiResult<Unit, PhoneEnrollApiFailure>
}

/**
 * Native failure hierarchy returned by [PhoneEnrollApi.start].
 *
 * Endpoint-defined failures include invalid enrollment input, forbidden enrollment, missing user, and provider or
 * capability dependency failures. [PhoneEnrollApiFailure.Unexpected] represents transport failures, local runtime
 * failures, and endpoint responses that cannot be represented by a typed case.
 *
 * OpenAPI source: `enrollPhoneNumber` operation.
 */
public sealed interface PhoneEnrollApiFailure : ApiFailure {
    /**
     * Bad request while validating the phone enrollment proof token or request.
     *
     * OpenAPI source: `BadEnrollRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PhoneEnrollApiFailure {
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
     * About: The endpoint rejected authorization for this enrollment.
     *
     * End-user: Explain that the action is unavailable or expired.
     *
     * Developer action: Check that a valid access token is supplied by parameters or [Context] and has the claims
     * required by the operation's policy.
     *
     * OpenAPI source: `ForbiddenError` schema.
     */
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PhoneEnrollApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * About: The account provider did not find an account for the requested login ID.
     *
     * End-user: Direct the user to register an account.
     *
     * Developer action: Treat this as an expected business outcome; do not escalate unless provider data is inconsistent.
     *
     * OpenAPI source: `UserNotFoundError` schema.
     */
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : PhoneEnrollApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PhoneEnrollApiFailure {
        /**
         * About: A configured backend provider failed while OwnID was processing the operation.
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
         * About: No provider is configured for a capability required by the operation.
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
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error, or unexpected
     * endpoint response.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for network, endpoint contract, or
     * SDK runtime issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), PhoneEnrollApiFailure
}
