package com.ownid.sdk.namespace

import com.ownid.sdk.OwnId
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.EmailEnrollApi
import com.ownid.sdk.api.EmailEnrollApiFailure
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationStartApiFailure
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.api.PhoneEnrollApi
import com.ownid.sdk.api.PhoneEnrollApiFailure
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginResponse

/**
 * OwnID namespace for direct server API access.
 *
 * Use this namespace when you want to call individual OwnID server APIs yourself instead of running a higher-level
 * operation or flow. Direct API entries make one API request and return [ApiResult]; operation lifecycle, UI, and
 * settlement controllers belong to [OwnIdOperation] instead.
 *
 * Namespace handles remain associated with the SDK instance state from which they were obtained.
 * After [OwnId.destroy] or same-name reinitialization, previously returned namespace handles are invalid and should be
 * reacquired from the current [OwnIdInstance].
 *
 * Use [withContext] to scope calls with a [Context] containing a login ID or
 * access token, or [withProviders] to register scoped providers, then call the selected API through
 * [APIEntry.start]. Entries whose parameter type is nullable support the zero-argument `start()` overload; enrollment
 * entries require explicit params.
 *
 * Each [APIEntry.start] call returns the endpoint-specific result. If the SDK cannot prepare the selected API, the
 * result contains that endpoint's `Unexpected` failure. Caller coroutine cancellation propagates to the caller.
 */
public interface OwnIdApi : OwnIdNamespace<OwnIdApi> {

    public val auth: Auth

    /**
     * Authentication API group.
     *
     * Use [discover] to start from a login ID, or [login] to continue with an access token.
     */
    public interface Auth {
        /** Runs discovery mode using a login ID without an access token. */
        public val discover: APIEntry<DiscoverApi.Params?, LoginResponse, DiscoverApiFailure>

        /** Authenticates a proven login using an access token and returns the login response/session payload. */
        public val login: APIEntry<LoginApi.Params?, LoginResponse, LoginApiFailure>
    }

    public val passkeys: Passkeys

    /**
     * Passkey API group.
     *
     * Use these APIs to start passkey attestation or assertion directly.
     */
    public interface Passkeys {
        /** Initiates passkey creation (WebAuthn attestation). */
        public val attestation: APIEntry<PasskeyAttestationApi.Params?, PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure>

        /** Initiates passkey authentication (WebAuthn assertion). */
        public val assertion: APIEntry<PasskeyAssertionApi.Params?, PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure>
    }

    public val verifications: Verifications

    /**
     * Verification challenge API group.
     *
     * Each API starts a verification challenge and returns a controller for code entry, resend, and cancellation.
     */
    public interface Verifications {
        /** Starts an email verification challenge. */
        public val email: APIEntry<EmailVerificationApi.Params?, EmailVerificationApi.Controller, EmailVerificationStartApiFailure>

        /** Starts a phone verification challenge. */
        public val phone: APIEntry<PhoneVerificationApi.Params?, PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure>
    }

    public val enroll: Enroll

    /**
     * Credential enrollment API group.
     *
     * Each API enrolls one credential type using the proof token produced by the matching earlier proof step.
     */
    public interface Enroll {
        /** Enrolls an email address using a proof token. */
        public val email: APIEntry<EmailEnrollApi.Params, Unit, EmailEnrollApiFailure>

        /** Enrolls a phone number using a proof token. */
        public val phone: APIEntry<PhoneEnrollApi.Params, Unit, PhoneEnrollApiFailure>

        /** Enrolls a passkey using a proof token. */
        public val passkey: APIEntry<PasskeyEnrollApi.Params, Unit, PasskeyEnrollApiFailure>
    }

    /** Initiates OpenID Connect authentication. */
    public val oidc: APIEntry<OidcApi.Params?, OidcApi.Controller, OidcStartApiFailure>

}

@InternalOwnIdAPI
internal val DIContainer.apiNamespace: OwnIdApi
    get() = OwnIdApiImpl(this)

@InternalOwnIdAPI
private class OwnIdApiImpl(
    override val container: DIContainer
) : OwnIdApi, OwnIdNamespaceSupport<OwnIdApi> {

    override fun rebind(container: DIContainer): OwnIdApi = container.apiNamespace

    override val auth: OwnIdApi.Auth = object : OwnIdApi.Auth {
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

    override val passkeys: OwnIdApi.Passkeys = object : OwnIdApi.Passkeys {
        override val attestation: APIEntry<PasskeyAttestationApi.Params?, PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = PasskeyAttestationApi::class.java,
                unexpectedFailure = PasskeyAttestationStartApiFailure::Unexpected,
                start = PasskeyAttestationApi::start
            )

        override val assertion: APIEntry<PasskeyAssertionApi.Params?, PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = PasskeyAssertionApi::class.java,
                unexpectedFailure = PasskeyAssertionStartApiFailure::Unexpected,
                start = PasskeyAssertionApi::start
            )
    }

    override val verifications: OwnIdApi.Verifications = object : OwnIdApi.Verifications {
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

    override val enroll: OwnIdApi.Enroll = object : OwnIdApi.Enroll {
        override val email: APIEntry<EmailEnrollApi.Params, Unit, EmailEnrollApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = EmailEnrollApi::class.java,
                unexpectedFailure = EmailEnrollApiFailure::Unexpected,
                start = EmailEnrollApi::start
            )

        override val phone: APIEntry<PhoneEnrollApi.Params, Unit, PhoneEnrollApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = PhoneEnrollApi::class.java,
                unexpectedFailure = PhoneEnrollApiFailure::Unexpected,
                start = PhoneEnrollApi::start
            )

        override val passkey: APIEntry<PasskeyEnrollApi.Params, Unit, PasskeyEnrollApiFailure> =
            apiEntry(
                container = container,
                runtimeClass = PasskeyEnrollApi::class.java,
                unexpectedFailure = PasskeyEnrollApiFailure::Unexpected,
                start = PasskeyEnrollApi::start
            )
    }

    override val oidc: APIEntry<OidcApi.Params?, OidcApi.Controller, OidcStartApiFailure> =
        apiEntry(
            container = container,
            runtimeClass = OidcApi::class.java,
            unexpectedFailure = OidcStartApiFailure::Unexpected,
            start = OidcApi::start
        )
}
