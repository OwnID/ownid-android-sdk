package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.VerificationMethod
import com.ownid.sdk.namespace.OwnIdApi

/**
 * Starts an email verification challenge.
 *
 * The returned controller owns the challenge and exposes the linked complete, resend, and cancel calls so the app does
 * not pass the challenge ID manually.
 * Linked calls use the challenge returned by [start] and the access token supplied to [start], or the access token
 * available from the current [Context].
 *
 * Coroutine cancellation is propagated through normal Kotlin coroutine cancellation and is not returned as an
 * [ApiResult].
 *
 * Part of [OwnIdApi.Verifications].
 *
 * OpenAPI source: `startEmailVerification` operation.
 */
public interface EmailVerificationApi : ApiCapability {

    /**
     * Parameters for the email verification API request.
     *
     * @property loginId The email address to verify. When `null`, the login ID from the current [Context] is used if
     *   available. It may be omitted when an access token is supplied or available from the current [Context]. If the
     *   current [Context] contains a raw login ID, it must resolve to a supported typed login ID or [start] returns a
     *   typed failure.
     * @property loginIdHintId An optional hint identifier for the login ID. Defaults to `null`.
     * @property accessToken Optional access token for an already-authenticated session. When `null`, the access token
     *   from the current [Context] is used if available. The same token is used by the controller's linked calls.
     * @property verificationMethods Preferred verification methods (`OTP`, magic link). When `null`, the field is
     *   omitted and the server chooses available methods.
     * @property magicLinkRedirectUrl An optional URL to which the user will be redirected after clicking a magic link,
     *   if magic link is requested and used. Defaults to `null`.
     *
     * OpenAPI source: `StartVerificationRequest` schema.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val loginIdHintId: String? = null,
        public val accessToken: AccessToken? = null,
        public val verificationMethods: Set<VerificationMethod>? = null,
        public val magicLinkRedirectUrl: String? = null,
        internal val traceParent: String?
    ) {
        public constructor(
            loginId: LoginId? = null,
            loginIdHintId: String? = null,
            accessToken: AccessToken? = null,
            verificationMethods: Set<VerificationMethod>? = null,
            magicLinkRedirectUrl: String? = null
        ) : this(loginId, loginIdHintId, accessToken, verificationMethods, magicLinkRedirectUrl, traceParent = null)
    }

    /**
     * Controls an active email verification flow.
     *
     * [challenge] includes the available methods, attempt limits, resend policy, and timeout.
     * Call [completeWithCode] to submit a verification code and receive either [AccessToken] or [ProofToken].
     * Call [resend] to request a new verification code, subject to the challenge's resend policy.
     * Call [cancel] to abort the verification.
     * The SDK does not enforce attempt, resend, or timeout limits locally; the server response for each linked call is
     * returned as that call's typed [ApiResult].
     *
     * Keep the controller referenced while the challenge can still complete, resend, or cancel. Releasing or clearing
     * the controller reference only loses the app's handle; it does not cancel the server challenge. Call [cancel]
     * before clearing the last reference when the app abandons this challenge.
     *
     * OpenAPI source: `StartVerificationResponse` success response schema, with linked `completeEmailVerification`,
     * `resendEmailVerification`, and `cancelEmailVerification` operations.
     */
    public interface Controller {
        /**
         * The verification challenge with details such as supported methods and resend policy.
         *
         * OpenAPI source: `StartVerificationResponse` schema.
         */
        public val challenge: VerificationChallenge

        /**
         * Submits a verification code.
         *
         * Returns [AccessOrProofToken] ([AccessToken] or [ProofToken]) on success. Invalid, expired, exhausted, or
         * wrong-code challenges are returned as [EmailVerificationCompleteApiFailure].
         *
         * OpenAPI source: `completeEmailVerification` operation; `CompleteVerificationRequest` request schema.
         */
        public val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure>

        /**
         * Requests a new verification code to be sent.
         *
         * Resend limits and debounce are server-enforced and returned as [EmailVerificationResendApiFailure].
         *
         * OpenAPI source: `resendEmailVerification` operation.
         */
        public val resend: suspend () -> ApiResult<Unit, EmailVerificationResendApiFailure>

        /**
         * Cancels the verification flow with the given [Reason].
         *
         * Canceling the coroutine or releasing the controller does not cancel the server-side challenge; call this
         * function when the app wants to request challenge cancellation.
         *
         * OpenAPI source: `cancelEmailVerification` operation.
         */
        public val cancel: suspend (reason: Reason) -> ApiResult<Unit, EmailVerificationCancelApiFailure>
    }

    /**
     * Starts the email verification flow.
     *
     * @param params Optional verification parameters. Defaults to `null`.
     *
     * @return [ApiResult.Success] with a [Controller], or [ApiResult.Failure] with a typed failure.
     * Coroutine cancellation is propagated and is not represented as an [ApiResult].
     *
     * OpenAPI source: `startEmailVerification` operation.
     */
    public suspend fun start(params: Params? = null): ApiResult<Controller, EmailVerificationStartApiFailure>
}

/**
 * Native failure hierarchy returned by [EmailVerificationApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for starting email verification, plus
 * [EmailVerificationStartApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `startEmailVerification` operation.
 */
public sealed interface EmailVerificationStartApiFailure : ApiFailure {
    /**
     * Bad verification start request.
     *
     * OpenAPI source: `BadVerificationRequestErrorResponse` response component.
     */
    public sealed class BadRequest : EmailVerificationStartApiFailure {
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
         * About: The selected verification channel is unavailable for the login ID.
         *
         * End-user: Offer another authentication method or ask the user to update account contact details.
         *
         * Developer action: Check account provider data and channel configuration before retrying.
         *
         * @property loginId Login ID value whose selected verification channel is unavailable.
         *
         * OpenAPI source: `MissingChannelError` schema.
         */
        public class MissingChannel(override val errorCode: ErrorCode, override val message: String, public val loginId: LoginId) : BadRequest() {
            override fun toString(): String = "BadRequest.MissingChannel(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : EmailVerificationStartApiFailure {
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
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : EmailVerificationStartApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : EmailVerificationStartApiFailure {
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
    public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String) : EmailVerificationStartApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), EmailVerificationStartApiFailure
}

/**
 * Native failure hierarchy returned by [EmailVerificationApi.Controller.completeWithCode].
 *
 * Direct cases correspond to OpenAPI error response components for completing email verification, plus
 * [EmailVerificationCompleteApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `completeEmailVerification` operation.
 */
public sealed interface EmailVerificationCompleteApiFailure : ApiFailure {
    /**
     * Bad request while completing a verification challenge.
     *
     * OpenAPI source: `BadCompleteVerificationRequestErrorResponse` response component.
     */
    public sealed class BadRequest : EmailVerificationCompleteApiFailure {
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
         * About: The submitted verification code or equivalent challenge proof is incorrect.
         *
         * End-user: Ask the user to try again while attempts remain.
         *
         * Developer action: Keep the current challenge active and update remaining-attempt UI if available.
         *
         * @property challengeId Challenge identifier for which the submitted verification code or proof was incorrect.
         *
         * OpenAPI source: `VerificationCodeWrongError` schema.
         */
        public class WrongCode(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) : BadRequest() {
            override fun toString(): String = "BadRequest.WrongCode(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : EmailVerificationCompleteApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), EmailVerificationCompleteApiFailure
}

/**
 * Native failure hierarchy returned by [EmailVerificationApi.Controller.resend].
 *
 * Direct cases correspond to OpenAPI error response components for resending an email verification code, plus
 * [EmailVerificationResendApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `resendEmailVerification` operation.
 */
public sealed interface EmailVerificationResendApiFailure : ApiFailure {
    /**
     * Bad request while resending a verification challenge code.
     *
     * OpenAPI source: `BadResendRequestErrorResponse` response component.
     */
    public sealed class BadRequest : EmailVerificationResendApiFailure {
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
         * About: The verification code resend limit or debounce policy was reached.
         *
         * End-user: Ask the user to wait or restart the challenge.
         *
         * Developer action: Disable resend UI for the current challenge and respect the server resend policy.
         *
         * @property challengeId Challenge identifier for which the resend limit or debounce policy was reached.
         *
         * OpenAPI source: `MaximumResendAttemptsReachedError` schema.
         */
        public class MaximumResendAttemptsReached(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) :
            BadRequest() {
            override fun toString(): String = "BadRequest.MaximumResendAttemptsReached(errorCode=$errorCode, message=$message)"
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
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : EmailVerificationResendApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), EmailVerificationResendApiFailure
}

/**
 * Native failure hierarchy returned by [EmailVerificationApi.Controller.cancel].
 *
 * Direct cases correspond to OpenAPI error response components for canceling email verification, plus
 * [EmailVerificationCancelApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `cancelEmailVerification` operation.
 */
public sealed interface EmailVerificationCancelApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : EmailVerificationCancelApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), EmailVerificationCancelApiFailure
}

/**
 * Starts a phone number verification challenge.
 *
 * The returned controller owns the challenge and exposes the linked complete, resend, and cancel calls so the app does
 * not pass the challenge ID manually.
 * Linked calls use the challenge returned by [start] and the access token supplied to [start], or the access token
 * available from the current [Context].
 *
 * Coroutine cancellation is propagated through normal Kotlin coroutine cancellation and is not returned as an
 * [ApiResult].
 *
 * Part of [OwnIdApi.Verifications].
 *
 * OpenAPI source: `startPhoneVerification` operation.
 */
public interface PhoneVerificationApi : ApiCapability {

    /**
     * Parameters for the phone verification API request.
     *
     * @property loginId The phone number to verify. When `null`, the login ID from the current [Context] is used if
     *   available. It may be omitted when an access token is supplied or available from the current [Context]. If the
     *   current [Context] contains a raw login ID, it must resolve to a supported typed login ID or [start] returns a
     *   typed failure.
     * @property loginIdHintId An optional hint identifier for the login ID. Defaults to `null`.
     * @property accessToken Optional access token for an already-authenticated session. When `null`, the access token
     *   from the current [Context] is used if available. The same token is used by the controller's linked calls.
     * @property verificationMethods Preferred verification methods (`OTP`, magic link). When `null`, the field is
     *   omitted and the server chooses available methods.
     * @property magicLinkRedirectUrl An optional URL to which the user will be redirected after clicking a magic link,
     *   if magic link is requested and used. Defaults to `null`.
     *
     * OpenAPI source: `StartVerificationRequest` schema.
     */
    public class Params internal constructor(
        public val loginId: LoginId? = null,
        public val loginIdHintId: String? = null,
        public val accessToken: AccessToken? = null,
        public val verificationMethods: Set<VerificationMethod>? = null,
        public val magicLinkRedirectUrl: String? = null,
        internal val traceParent: String?
    ) {
        public constructor(
            loginId: LoginId? = null,
            loginIdHintId: String? = null,
            accessToken: AccessToken? = null,
            verificationMethods: Set<VerificationMethod>? = null,
            magicLinkRedirectUrl: String? = null
        ) : this(loginId, loginIdHintId, accessToken, verificationMethods, magicLinkRedirectUrl, traceParent = null)
    }

    /**
     * Controls an active phone verification flow.
     *
     * [challenge] includes the available methods, attempt limits, resend policy, and timeout.
     * Call [completeWithCode] to submit a verification code and receive either [AccessToken] or [ProofToken].
     * Call [resend] to request a new verification code, subject to the challenge's resend policy.
     * Call [cancel] to abort the verification.
     * The SDK does not enforce attempt, resend, or timeout limits locally; the server response for each linked call is
     * returned as that call's typed [ApiResult].
     *
     * Keep the controller referenced while the challenge can still complete, resend, or cancel. Releasing or clearing
     * the controller reference only loses the app's handle; it does not cancel the server challenge. Call [cancel]
     * before clearing the last reference when the app abandons this challenge.
     *
     * OpenAPI source: `StartVerificationResponse` success response schema, with linked `completePhoneVerification`,
     * `resendPhoneVerification`, and `cancelPhoneVerification` operations.
     */
    public interface Controller {
        /**
         * The verification challenge with details such as supported methods and resend policy.
         *
         * OpenAPI source: `StartVerificationResponse` schema.
         */
        public val challenge: VerificationChallenge

        /**
         * Submits a verification code.
         *
         * Returns [AccessOrProofToken] ([AccessToken] or [ProofToken]) on success. Invalid, expired, exhausted, or
         * wrong-code challenges are returned as [PhoneVerificationCompleteApiFailure].
         *
         * OpenAPI source: `completePhoneVerification` operation; `CompleteVerificationRequest` request schema.
         */
        public val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, PhoneVerificationCompleteApiFailure>

        /**
         * Requests a new verification code to be sent.
         *
         * Resend limits and debounce are server-enforced and returned as [PhoneVerificationResendApiFailure].
         *
         * OpenAPI source: `resendPhoneVerification` operation.
         */
        public val resend: suspend () -> ApiResult<Unit, PhoneVerificationResendApiFailure>

        /**
         * Cancels the verification flow with the given [Reason].
         *
         * Canceling the coroutine or releasing the controller does not cancel the server-side challenge; call this
         * function when the app wants to request challenge cancellation.
         *
         * OpenAPI source: `cancelPhoneVerification` operation.
         */
        public val cancel: suspend (reason: Reason) -> ApiResult<Unit, PhoneVerificationCancelApiFailure>
    }

    /**
     * Starts the phone verification flow.
     *
     * @param params Optional verification parameters. Defaults to `null`.
     *
     * @return [ApiResult.Success] with a [Controller], or [ApiResult.Failure] with a typed failure.
     * Coroutine cancellation is propagated and is not represented as an [ApiResult].
     *
     * OpenAPI source: `startPhoneVerification` operation.
     */
    public suspend fun start(params: Params? = null): ApiResult<Controller, PhoneVerificationStartApiFailure>
}

/**
 * Native failure hierarchy returned by [PhoneVerificationApi.start].
 *
 * Direct cases correspond to OpenAPI error response components for starting phone verification, plus
 * [PhoneVerificationStartApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `startPhoneVerification` operation.
 */
public sealed interface PhoneVerificationStartApiFailure : ApiFailure {
    /**
     * Bad verification start request.
     *
     * OpenAPI source: `BadVerificationRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PhoneVerificationStartApiFailure {
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
         * About: The selected verification channel is unavailable for the login ID.
         *
         * End-user: Offer another authentication method or ask the user to update account contact details.
         *
         * Developer action: Check account provider data and channel configuration before retrying.
         *
         * @property loginId Login ID value whose selected verification channel is unavailable.
         *
         * OpenAPI source: `MissingChannelError` schema.
         */
        public class MissingChannel(override val errorCode: ErrorCode, override val message: String, public val loginId: LoginId) : BadRequest() {
            override fun toString(): String = "BadRequest.MissingChannel(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PhoneVerificationStartApiFailure {
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
    public class UserNotFound(override val errorCode: ErrorCode, override val message: String) : PhoneVerificationStartApiFailure {
        override fun toString(): String = "UserNotFound(errorCode=$errorCode, message=$message)"
    }

    /**
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PhoneVerificationStartApiFailure {
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
    public class MaximumChallengesReached(override val errorCode: ErrorCode, override val message: String) : PhoneVerificationStartApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PhoneVerificationStartApiFailure
}

/**
 * Native failure hierarchy returned by [PhoneVerificationApi.Controller.completeWithCode].
 *
 * Direct cases correspond to OpenAPI error response components for completing phone verification, plus
 * [PhoneVerificationCompleteApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `completePhoneVerification` operation.
 */
public sealed interface PhoneVerificationCompleteApiFailure : ApiFailure {
    /**
     * Bad request while completing a verification challenge.
     *
     * OpenAPI source: `BadCompleteVerificationRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PhoneVerificationCompleteApiFailure {
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
         * About: The submitted verification code or equivalent challenge proof is incorrect.
         *
         * End-user: Ask the user to try again while attempts remain.
         *
         * Developer action: Keep the current challenge active and update remaining-attempt UI if available.
         *
         * @property challengeId Challenge identifier for which the submitted verification code or proof was incorrect.
         *
         * OpenAPI source: `VerificationCodeWrongError` schema.
         */
        public class WrongCode(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) : BadRequest() {
            override fun toString(): String = "BadRequest.WrongCode(errorCode=$errorCode, message=$message)"
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
    public class Forbidden(override val errorCode: ErrorCode, override val message: String) : PhoneVerificationCompleteApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PhoneVerificationCompleteApiFailure
}

/**
 * Native failure hierarchy returned by [PhoneVerificationApi.Controller.resend].
 *
 * Direct cases correspond to OpenAPI error response components for resending a phone verification code, plus
 * [PhoneVerificationResendApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `resendPhoneVerification` operation.
 */
public sealed interface PhoneVerificationResendApiFailure : ApiFailure {
    /**
     * Bad request while resending a verification challenge code.
     *
     * OpenAPI source: `BadResendRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PhoneVerificationResendApiFailure {
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
         * About: The verification code resend limit or debounce policy was reached.
         *
         * End-user: Ask the user to wait or restart the challenge.
         *
         * Developer action: Disable resend UI for the current challenge and respect the server resend policy.
         *
         * @property challengeId Challenge identifier for which the resend limit or debounce policy was reached.
         *
         * OpenAPI source: `MaximumResendAttemptsReachedError` schema.
         */
        public class MaximumResendAttemptsReached(override val errorCode: ErrorCode, override val message: String, public val challengeId: ChallengeId) :
            BadRequest() {
            override fun toString(): String = "BadRequest.MaximumResendAttemptsReached(errorCode=$errorCode, message=$message)"
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
     * Provider or capability dependency failed.
     *
     * OpenAPI source: `FailedDependencyErrorResponse` response component.
     */
    public sealed class FailedDependency : PhoneVerificationResendApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PhoneVerificationResendApiFailure
}

/**
 * Native failure hierarchy returned by [PhoneVerificationApi.Controller.cancel].
 *
 * Direct cases correspond to OpenAPI error response components for canceling phone verification, plus
 * [PhoneVerificationCancelApiFailure.Unexpected] for SDK-side failures.
 *
 * OpenAPI source: `cancelPhoneVerification` operation.
 */
public sealed interface PhoneVerificationCancelApiFailure : ApiFailure {
    /**
     * Bad request against an existing challenge.
     *
     * OpenAPI source: `BadChallengeRequestErrorResponse` response component.
     */
    public sealed class BadRequest : PhoneVerificationCancelApiFailure {
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
        ApiUnexpectedFailure(errorCode, message, cause), PhoneVerificationCancelApiFailure
}
