package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OwnIdApi

/**
 * Starts passkey authentication and returns an assertion challenge controller.
 *
 * This is the direct API surface for apps that own their platform passkey UI. [start] asks OwnID for WebAuthn
 * [AssertionOptions], validates the server-provided WebAuthn fields that the SDK must decode, and returns a controller
 * tied to that challenge. The SDK does not call Android Credential Manager from this API and does not check device
 * passkey availability; pass [PasskeyAssertionApi.Controller.assertionOptions] to your platform passkey layer, then pass
 * the resulting [AssertionResult] to [PasskeyAssertionApi.Controller.verify].
 *
 * The controller owns the challenge returned by [start]. Verification completes the challenge and returns an
 * [AccessToken]. Cancellation reports that the challenge was abandoned; it is best-effort and may fail if the challenge
 * already expired, was completed, or was canceled.
 *
 * Coroutine cancellation is not converted to [ApiResult]. If the coroutine running [start], `verify`, or `cancel` is
 * canceled, cancellation propagates to the caller.
 *
 * Part of [OwnIdApi.Passkeys].
 *
 * OpenAPI source: `assertionOptions` operation.
 */
public interface PasskeyAssertionApi : ApiCapability {

    /**
     * Parameters for the passkey assertion API request.
     *
     * @property loginId The user's login identifier. When `null`, the login ID from the current [Context] is used if
     *   available. Raw context login IDs are resolved through the configured login ID validator; resolution failures are
     *   returned as typed start failures. It may be omitted when an access token is supplied or available from the
     *   current [Context].
     * @property accessToken Optional access token for an already-authenticated session. When `null`, the access token
     *   from the current [Context] is used if available. The token resolved by [start] is reused by the returned
     *   controller's verification call; later [Context] changes do not update an existing controller.
     *
     * OpenAPI source: `AssertionOptionsRequest` schema.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(loginId: LoginId? = null, accessToken: AccessToken? = null) : this(loginId, accessToken, traceParent = null)
    }

    /**
     * Controls an active passkey assertion (authentication) flow.
     *
     * [assertionOptions] contains the challenge, relying party ID, allowed credentials, user verification preference,
     * and timeout.
     * Call [verify] with the platform credential result for this challenge to complete authentication and receive an
     * [AccessToken]. Call [cancel] when the app abandons the challenge before verification.
     *
     * Keep the controller referenced while the challenge can still verify or cancel. Releasing or clearing the
     * controller reference only loses the app's handle; it does not cancel the server challenge. Call [cancel] before
     * clearing the last reference when the app abandons this challenge.
     *
     * OpenAPI source: `AssertionOptionsResponse` success response schema, with linked `assertionResult` and
     * `assertionCancel` operations.
     */
    public interface Controller {
        /**
         * WebAuthn assertion options to pass to the platform credential manager.
         *
         * OpenAPI source: `AssertionOptionsResponse` schema.
         */
        public val assertionOptions: AssertionOptions

        /**
         * Verifies the assertion result from the platform passkey provider. Returns [AccessToken] on success.
         *
         * The result must correspond to this controller's [assertionOptions]. A stale, expired, already-completed, or
         * mismatched challenge is returned as [PasskeyAssertionVerifyApiFailure.BadRequest.InvalidChallenge] when the
         * backend can classify it.
         *
         * OpenAPI source: `assertionResult` operation; `AssertionResultRequest` request schema.
         */
        public val verify: suspend (assertion: AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure>

        /**
         * Cancels this assertion challenge with the given [Reason].
         *
         * A successful result means OwnID accepted the cancellation. If the challenge is already terminal or unknown,
         * the backend may return a typed bad-request failure. Canceling the coroutine or releasing the controller does
         * not cancel the server-side challenge; call this function when the app wants to request challenge
         * cancellation.
         *
         * OpenAPI source: `assertionCancel` operation.
         */
        public val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure>
    }

    /**
     * Starts the passkey assertion flow.
     *
     * The SDK resolves optional values from [params] first, then from the current [Context]. On success, the returned
     * controller captures the resolved access token and challenge; subsequent context changes do not affect it.
     *
     * @param params Optional assertion parameters. Defaults to `null`.
     *
     * @return [ApiResult.Success] with a [Controller], or [ApiResult.Failure] with a typed failure.
     *
     * OpenAPI source: `assertionOptions` operation.
     */
    public suspend fun start(params: Params? = null): ApiResult<Controller, PasskeyAssertionStartApiFailure>
}

/**
 * Native failure hierarchy returned by [PasskeyAssertionApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for starting a passkey assertion, plus
 * [PasskeyAssertionStartApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `assertionOptions` operation.
 */
public sealed interface PasskeyAssertionStartApiFailure : ApiFailure {
    /**
     * Bad request.
     *
     * OpenAPI source: `BadLoginIdRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAssertionStartApiFailure {
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PasskeyAssertionStartApiFailure {
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
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : PasskeyAssertionStartApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PasskeyAssertionStartApiFailure {
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
    public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String) : PasskeyAssertionStartApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAssertionStartApiFailure
}

/**
 * Native failure hierarchy returned by [PasskeyAssertionApi.Controller.verify].
 *
 * Direct cases correspond to OpenAPI error response components for verifying a passkey assertion, plus
 * [PasskeyAssertionVerifyApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `assertionResult` operation.
 */
public sealed interface PasskeyAssertionVerifyApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAssertionVerifyApiFailure {
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PasskeyAssertionVerifyApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAssertionVerifyApiFailure
}

/**
 * Native failure hierarchy returned by [PasskeyAssertionApi.Controller.cancel].
 *
 * Direct cases correspond to OpenAPI error response components for canceling a passkey assertion, plus
 * [PasskeyAssertionCancelApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `assertionCancel` operation.
 */
public sealed interface PasskeyAssertionCancelApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAssertionCancelApiFailure {
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
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error,
     * unhandled HTTP status, or response mapping failure.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for transport, status handling, or
     * response mapping issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAssertionCancelApiFailure
}

/**
 * Starts passkey creation and returns an attestation challenge controller.
 *
 * This is the direct API surface for apps that own their platform passkey UI. [start] asks OwnID for WebAuthn
 * [AttestationOptions], validates the server-provided WebAuthn fields that the SDK must decode, and returns a
 * controller tied to that challenge. The SDK does not call Android Credential Manager from this API and does not check
 * device passkey availability; pass [PasskeyAttestationApi.Controller.attestationOptions] to your platform passkey
 * layer, then pass the resulting [AttestationResult] to [PasskeyAttestationApi.Controller.verify].
 *
 * The controller owns the challenge returned by [start]. Verification completes the challenge and returns an
 * [AttestationResponse] containing OwnID data and a proof token for the app's registration boundary. Cancellation
 * reports that the challenge was abandoned; it is best-effort and may fail if the challenge already expired, was
 * completed, or was canceled.
 *
 * Coroutine cancellation is not converted to [ApiResult]. If the coroutine running [start], `verify`, or `cancel` is
 * canceled, cancellation propagates to the caller.
 *
 * Part of [OwnIdApi.Passkeys].
 *
 * OpenAPI source: `attestationOptions` operation.
 */
public interface PasskeyAttestationApi : ApiCapability {

    /**
     * Parameters for the passkey attestation API request.
     *
     * @property loginId The user's login identifier to register the passkey for. When `null`, the login ID from the
     *   current [Context] is used if available. Raw context login IDs are resolved through the configured login ID
     *   validator; resolution failures are returned as typed start failures. It may be omitted when an access token is
     *   supplied or available from the current [Context].
     * @property accountDisplayName A human-readable name for the account, shown in credential manager UI. When `null`,
     *   the account display name from the current [Context] is used if available. Defaults to `null`.
     * @property accessToken Optional access token for an already-authenticated session. When `null`, the access token
     *   from the current [Context] is used if available. The token resolved by [start] is reused by the returned
     *   controller's verification call; later [Context] changes do not update an existing controller.
     *
     * OpenAPI source: `AttestationOptionsRequest` schema.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val accountDisplayName: String? = null,
        public val accessToken: AccessToken? = null,
        internal val traceParent: String?
    ) {
        public constructor(
            loginId: LoginId? = null,
            accountDisplayName: String? = null,
            accessToken: AccessToken? = null
        ) : this(loginId, accountDisplayName, accessToken, traceParent = null)
    }

    /**
     * Controls an active passkey attestation (creation) flow.
     *
     * [attestationOptions] contains relying party info, user info, challenge, supported algorithms, attestation
     * preference, authenticator selection criteria, and timeout.
     * Call [verify] with the platform credential result for this challenge to complete registration and receive an
     * [AttestationResponse]. Call [cancel] when the app abandons the challenge before verification.
     *
     * Keep the controller referenced while the challenge can still verify or cancel. Releasing or clearing the
     * controller reference only loses the app's handle; it does not cancel the server challenge. Call [cancel] before
     * clearing the last reference when the app abandons this challenge.
     *
     * OpenAPI source: `AttestationOptionsResponse` success response schema, with linked `attestationResult` and
     * `attestationCancel` operations.
     */
    public interface Controller {
        /**
         * WebAuthn attestation options to pass to the platform credential manager.
         *
         * OpenAPI source: `AttestationOptionsResponse` schema.
         */
        public val attestationOptions: AttestationOptions

        /**
         * Verifies the attestation result from the platform passkey provider. Returns [AttestationResponse] on success.
         *
         * The result must correspond to this controller's [attestationOptions]. A stale, expired, already-completed, or
         * mismatched challenge is returned as [PasskeyAttestationVerifyApiFailure.BadRequest.InvalidChallenge] when the
         * backend can classify it.
         *
         * OpenAPI source: `attestationResult` operation; `AttestationResultRequest` request schema.
         */
        public val verify: suspend (attestation: AttestationResult) -> ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure>

        /**
         * Cancels this attestation challenge with the given [Reason].
         *
         * A successful result means OwnID accepted the cancellation. If the challenge is already terminal or unknown,
         * the backend may return a typed bad-request failure. Canceling the coroutine or releasing the controller does
         * not cancel the server-side challenge; call this function when the app wants to request challenge
         * cancellation.
         *
         * OpenAPI source: `attestationCancel` operation.
         */
        public val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAttestationCancelApiFailure>
    }

    /**
     * Starts the passkey attestation flow.
     *
     * The SDK resolves optional values from [params] first, then from the current [Context]. On success, the returned
     * controller captures the resolved access token and challenge; subsequent context changes do not affect it.
     *
     * @param params Optional attestation parameters. Defaults to `null`.
     *
     * @return [ApiResult.Success] with a [Controller], or [ApiResult.Failure] with a typed failure.
     *
     * OpenAPI source: `attestationOptions` operation.
     */
    public suspend fun start(params: Params? = null): ApiResult<Controller, PasskeyAttestationStartApiFailure>
}

/**
 * Native failure hierarchy returned by [PasskeyAttestationApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for starting passkey attestation, plus
 * [PasskeyAttestationStartApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `attestationOptions` operation.
 */
public sealed interface PasskeyAttestationStartApiFailure : ApiFailure {
    /**
     * Bad request.
     *
     * OpenAPI source: `BadLoginIdRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAttestationStartApiFailure {
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PasskeyAttestationStartApiFailure {
        override fun toString(): String = "Forbidden(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PasskeyAttestationStartApiFailure {
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
    public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String) : PasskeyAttestationStartApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAttestationStartApiFailure
}

/**
 * Native failure hierarchy returned by [PasskeyAttestationApi.Controller.verify].
 *
 * Direct cases correspond to OpenAPI error response components for verifying passkey attestation, plus
 * [PasskeyAttestationVerifyApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `attestationResult` operation.
 */
public sealed interface PasskeyAttestationVerifyApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAttestationVerifyApiFailure {
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
     * About: The caller is missing valid authentication for the operation.
     *
     * End-user: Prompt the user to authenticate again.
     *
     * Developer action: Refresh credentials, verify token forwarding, and avoid retry loops without new credentials.
     *
     * OpenAPI source: `UnauthorizedError` schema.
     */
    public class Unauthorized(override val errorCode: ErrorCode, override val message: String) : PasskeyAttestationVerifyApiFailure {
        override fun toString(): String = "Unauthorized(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PasskeyAttestationVerifyApiFailure {
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
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : PasskeyAttestationVerifyApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
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
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAttestationVerifyApiFailure
}

/**
 * Native failure hierarchy returned by [PasskeyAttestationApi.Controller.cancel].
 *
 * Direct cases correspond to OpenAPI error response components for canceling passkey attestation, plus
 * [PasskeyAttestationCancelApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `attestationCancel` operation.
 */
public sealed interface PasskeyAttestationCancelApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PasskeyAttestationCancelApiFailure {
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
     * About: The SDK could not produce a typed API failure because of a transport failure, runtime error,
     * unhandled HTTP status, or response mapping failure.
     *
     * End-user: Show a generic failure message and suggest retrying when appropriate.
     *
     * Developer action: Log the failure with request and correlation context. Check for transport, status handling, or
     * response mapping issues. Escalate repeated occurrences.
     */
    public class Unexpected internal constructor(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) :
        ApiUnexpectedFailure(errorCode, message, cause), PasskeyAttestationCancelApiFailure
}
