package com.ownid.sdk.namespace

import com.ownid.sdk.OwnId
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationStartApiFailure
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlow
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowContext
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure

/**
 * UI-less OwnID authentication namespace.
 *
 * Use this namespace when you want direct OwnID APIs, operations, and the passkey enrollment flow without starting
 * from the higher-level flow namespace.
 *
 * Namespace handles remain associated with the SDK instance state from which they were obtained.
 * After [OwnId.destroy] or same-name reinitialization, previously returned namespace handles are invalid and should be
 * reacquired from [OwnId.headless] or the current [OwnIdInstance].
 *
 * Scope it with [withContext] for [Context] values or [withProviders], then launch
 * the selected API, operation, or flow. Nullable parameter and context types support zero-argument start/preflight
 * overloads; the selected entry decides whether omitted values are valid and how the current [Context] is used.
 * Direct API entries return endpoint-specific [ApiResult] values. If the SDK cannot prepare the selected API, the
 * result contains that endpoint's `Unexpected` failure; caller coroutine cancellation propagates to the caller.
 * Operation and flow entries return caller-owned controllers that should be observed until settlement. Reading a
 * namespace entry property does not start work; call start or a preflight method when ready.
 *
 * Accessed via [OwnId.headless] (default instance) or [OwnIdInstance.headless].
 */
public interface OwnIdHeadless : OwnIdNamespace<OwnIdHeadless> {

    public val auth: Auth

    /**
     * Authentication API group.
     *
     * Use [discover] to start from a login ID, or [login] to continue with an access token.
     */
    public interface Auth {
        /** Runs discovery mode for a login ID without an access token. */
        public val discover: APIEntry<DiscoverApi.Params?, LoginResponse, DiscoverApiFailure>

        /** Authenticates a proven login using an access token and returns the login response/session payload. */
        public val login: APIEntry<LoginApi.Params?, LoginResponse, LoginApiFailure>
    }

    public val passkeys: Passkeys

    /**
     * Passkey operation group.
     *
     * Use [auth] to authenticate with a passkey, [create] to register one, and [enroll] to run the full passkey
     * enrollment flow.
     */
    public interface Passkeys {
        /** Authenticates with an existing passkey. */
        public val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure>

        /** Creates (registers) a new passkey. */
        public val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure>

        /**
         * Passkey enrollment flow that uses an access token, runs passkey attestation when no proof token is provided,
         * and completes server enrollment.
         */
        public val enroll: PreflightFlowEntry<PasskeyEnrollFlowContext?, PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>>
    }

    public val verifications: Verifications

    /**
     * Verification API group.
     *
     * Each API starts a verification challenge and returns a controller for code entry, resend, and cancellation.
     */
    public interface Verifications {
        /** Starts an email verification challenge. */
        public val email: APIEntry<EmailVerificationApi.Params?, EmailVerificationApi.Controller, EmailVerificationStartApiFailure>

        /** Starts a phone verification challenge. */
        public val phone: APIEntry<PhoneVerificationApi.Params?, PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure>
    }

}

@InternalOwnIdAPI
internal val DIContainer.headlessNamespace: OwnIdHeadless
    get() = OwnIdHeadlessImpl(this)

@InternalOwnIdAPI
private class OwnIdHeadlessImpl(
    override val container: DIContainer
) : OwnIdHeadless, OwnIdNamespaceSupport<OwnIdHeadless> {

    override fun rebind(container: DIContainer): OwnIdHeadless = container.headlessNamespace

    override val auth: OwnIdHeadless.Auth = object : OwnIdHeadless.Auth {
        override val discover: APIEntry<DiscoverApi.Params?, LoginResponse, DiscoverApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = DiscoverApi::class.java,
                unexpectedFailure = DiscoverApiFailure::Unexpected,
                start = DiscoverApi::start
            )

        override val login: APIEntry<LoginApi.Params?, LoginResponse, LoginApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = LoginApi::class.java,
                unexpectedFailure = LoginApiFailure::Unexpected,
                start = LoginApi::start
            )
    }

    override val passkeys: OwnIdHeadless.Passkeys = object : OwnIdHeadless.Passkeys {
        override val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> =
            operationEntry(
                container = container,
                runtimeClass = PasskeyAssertionOperation::class.java,
                operationType = com.ownid.sdk.model.OperationType.PASSKEY_AUTH
            )

        override val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
            operationEntry(
                container = container,
                runtimeClass = PasskeyAttestationOperation::class.java,
                operationType = com.ownid.sdk.model.OperationType.PASSKEY_CREATION
            )

        override val enroll: PreflightFlowEntry<PasskeyEnrollFlowContext?, PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>> =
            preflightFlowEntry(
                container = container,
                runtimeClass = PasskeyEnrollFlow::class.java,
                availability = PasskeyEnrollFlow::availability,
                start = { context -> start(context) }
            )
    }

    override val verifications: OwnIdHeadless.Verifications = object : OwnIdHeadless.Verifications {
        override val email: APIEntry<EmailVerificationApi.Params?, EmailVerificationApi.Controller, EmailVerificationStartApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = EmailVerificationApi::class.java,
                unexpectedFailure = EmailVerificationStartApiFailure::Unexpected,
                start = EmailVerificationApi::start
            )

        override val phone: APIEntry<PhoneVerificationApi.Params?, PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = PhoneVerificationApi::class.java,
                unexpectedFailure = PhoneVerificationStartApiFailure::Unexpected,
                start = PhoneVerificationApi::start
            )
    }
}
