package com.ownid.sdk.ops

import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.Reason

/**
 * Authenticates the user with Google through OwnID OIDC.
 *
 * The operation uses the access token from [Params] or the current OwnID context when available, then starts a Google
 * OIDC challenge and delegates Google UI to the registered [SignInWithGoogle] provider. The app/provider integration
 * owns Google SDK setup, UI presentation details, provider session state, and provider-specific failure mapping.
 *
 * On success, the controller completes with [OperationResult.Success] containing [AccessTokenWithUserInfo]. Provider
 * cancellation or user dismissal completes with [OperationResult.Canceled]. Provider, platform UI, access-policy,
 * challenge, and backend errors complete with [OperationResult.Failure].
 *
 * The UI step resolves the current SDK UI context and forwards it to the registered Google provider. For predictable
 * AndroidX Credential Manager behavior, start this operation while the app has an active foreground `Activity`.
 *
 * If the OIDC challenge times out, the operation cancels with [Reason.Timeout]. Calling [OperationController.abort]
 * while the provider UI is active requests best-effort cancellation from both the Google provider and the OwnID
 * challenge.
 *
 * [availability] verifies SDK dependencies and parameter type only; it does not prove that provider UI or the Google
 * account picker will succeed. Requires a registered [SignInWithGoogle] provider in the active OwnID scope.
 */
public interface SignInWithGoogleOperation : OperationCapability<SignInWithGoogleOperation.Params, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> {

    /**
     * Parameters for [SignInWithGoogleOperation].
     *
     * @property accessToken An existing access token, if available. When null, the current OwnID context access token is
     * used if available. Defaults to null.
     */
    public class Params internal constructor(public val accessToken: AccessToken? = null, internal val traceParent: String?) : Capability.Params {
        public constructor(accessToken: AccessToken? = null) : this(accessToken, traceParent = null)
    }
}

/** Failure payload returned by [SignInWithGoogleOperation]. */
public sealed interface SignInWithGoogleOperationFailure : OperationFailure {
    /** Required operation input is missing or invalid. */
    public sealed class Input : SignInWithGoogleOperationFailure {
        /**
         * About: The Google sign-in request was rejected before the operation could continue.
         *
         * End-user: No direct user action unless the app can restart sign-in with corrected context.
         *
         * Developer action: Inspect access-token context and [apiFailure] for the rejected invariant.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class InvalidRequest(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Input() {
            override fun toString(): String = "Input.InvalidRequest(errorCode=$errorCode, message=$message)"
        }
    }

    /** The caller is not allowed to continue. */
    public sealed class Access : SignInWithGoogleOperationFailure {
        /**
         * About: The caller is not allowed to start or complete Google sign-in in this context.
         *
         * End-user: Explain that Google sign-in is unavailable.
         *
         * Developer action: Check access token claims, app policy, and OIDC configuration.
         *
         * @property apiFailure Underlying API failure retained for diagnostics, when available.
         */
        public class Forbidden(override val errorCode: ErrorCode, override val message: String, public val apiFailure: ApiFailure? = null) : Access() {
            override fun toString(): String = "Access.Forbidden(errorCode=$errorCode, message=$message)"
        }
    }

    /** OIDC challenge cannot continue or must be restarted. */
    public sealed class Challenge : SignInWithGoogleOperationFailure {
        /**
         * About: The active OIDC challenge limit was reached.
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
         * About: The OIDC challenge is invalid, expired, or no longer usable.
         *
         * End-user: Ask the user to restart Google sign-in.
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
         * About: The OIDC challenge reached its attempt limit.
         *
         * End-user: Ask the user to start Google sign-in again.
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

    /** SDK, app, backend, provider, or platform integration path failed. */
    public sealed class Integration : SignInWithGoogleOperationFailure {
        /**
         * About: Google provider, backend, or platform integration failed during sign-in.
         *
         * End-user: Show a temporary failure state or let the user try another sign-in method.
         *
         * Developer action: Inspect provider configuration, activity/UI availability, [apiFailure], and [error].
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
         * About: A provider capability required by Google sign-in is not configured.
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
     * End-user: Show a generic failure state. Retrying may be reasonable after restarting Google sign-in.
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
    ) : SignInWithGoogleOperationFailure {
        override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
    }
}
