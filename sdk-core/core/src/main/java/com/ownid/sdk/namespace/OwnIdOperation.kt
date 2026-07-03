package com.ownid.sdk.namespace

import com.ownid.sdk.OwnId
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.SignInWithGoogleOperationFailure

/**
 * OwnID operation namespace.
 *
 * Use this namespace when you want to launch one authentication step directly instead of running a composite flow or a
 * raw server API call. Operation entries run SDK operation lifecycles and return caller-owned controllers; direct
 * server API calls belong to [OwnIdApi] instead.
 *
 * Namespace handles are bound views of the SDK instance and context/provider scope they were obtained from.
 * After [OwnId.destroy] or same-name reinitialization, previously returned namespace handles are invalid and should be
 * reacquired from the current [OwnIdInstance].
 *
 * Use [withContext] to scope operations with a [Context] containing a login ID or access token and [withProviders] to
 * register providers. Use [OperationEntry.start] to launch the selected operation.
 * Check [OperationEntry.availability] or [OperationEntry.isAvailable] before starting when the app needs to know
 * whether required providers, UI, passkey support, or inputs are currently available. Entries whose parameter type is
 * nullable support zero-argument availability and start overloads; passkey enrollment requires explicit params.
 *
 * Each [OperationEntry.start] call starts a separate operation lifecycle and returns a caller-owned controller for
 * settlement, cancellation, and lifecycle cleanup. Availability checks and starts use that bound scope at the time they
 * are called. See [OperationEntry] for preflight and missing-dependency behavior.
 */
public interface OwnIdOperation : OwnIdNamespace<OwnIdOperation> {

    public val loginId: LoginId

    public interface LoginId {
        /** Collects a login ID. */
        public val collect: OperationEntry<LoginIdCollectOperation.Params?, com.ownid.sdk.model.LoginId, LoginIdCollectOperationFailure>
    }

    public val verifications: Verifications

    public interface Verifications {
        /** Verifies an email address. */
        public val email: OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure>

        /** Verifies a phone number. */
        public val phone: OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure>
    }

    public val passkeys: Passkeys

    /**
     * Passkey operation group.
     *
     * Use [create] to register a passkey, [auth] to authenticate with an existing passkey, and [enroll] to complete
     * passkey enrollment from a proof token.
     */
    public interface Passkeys {
        /** Creates (registers) a new passkey. */
        public val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure>

        /** Authenticates with an existing passkey. */
        public val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure>

        /** Enrolls a passkey using a proof token. */
        public val enroll: OperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure>
    }

    public val socialLogin: SocialLogin

    public interface SocialLogin {
        /** Signs in with Google via OIDC. */
        public val signInWithGoogle: OperationEntry<SignInWithGoogleOperation.Params?, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure>
    }

    /** Authenticates a user from the current [Context] and returns a [LoginResponse]. */
    public val login: OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure>

}

@InternalOwnIdAPI
internal val DIContainer.opsNamespace: OwnIdOperation
    get() = OwnIdOperationImpl(this)

@InternalOwnIdAPI
private class OwnIdOperationImpl(
    override val container: DIContainer
) : OwnIdOperation, OwnIdNamespaceSupport<OwnIdOperation> {

    override fun rebind(container: DIContainer): OwnIdOperation = container.opsNamespace

    override val loginId: OwnIdOperation.LoginId = object : OwnIdOperation.LoginId {
        override val collect: OperationEntry<LoginIdCollectOperation.Params?, com.ownid.sdk.model.LoginId, LoginIdCollectOperationFailure> =
            scopedOperationEntry(container = container, runtimeClass = LoginIdCollectOperation::class.java, operationType = OperationType.LOGIN_ID_COLLECT)
    }

    override val verifications: OwnIdOperation.Verifications = object : OwnIdOperation.Verifications {
        override val email: OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure> =
            scopedOperationEntry(
                container = container,
                runtimeClass = EmailVerificationOperation::class.java,
                operationType = OperationType.EMAIL_VERIFICATION
            )

        override val phone: OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure> =
            scopedOperationEntry(
                container = container,
                runtimeClass = PhoneVerificationOperation::class.java,
                operationType = OperationType.PHONE_NUMBER_VERIFICATION
            )
    }

    override val passkeys: OwnIdOperation.Passkeys = object : OwnIdOperation.Passkeys {
        override val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
            operationEntry(container = container, runtimeClass = PasskeyAttestationOperation::class.java, operationType = OperationType.PASSKEY_CREATION)

        override val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> =
            operationEntry(container = container, runtimeClass = PasskeyAssertionOperation::class.java, operationType = OperationType.PASSKEY_AUTH)

        override val enroll: OperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> =
            operationEntry(container = container, runtimeClass = PasskeyEnrollOperation::class.java, operationType = OperationType.PASSKEY_ENROLLMENT)
    }

    override val socialLogin: OwnIdOperation.SocialLogin = object : OwnIdOperation.SocialLogin {
        override val signInWithGoogle: OperationEntry<SignInWithGoogleOperation.Params?, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
            operationEntry(
                container = container,
                runtimeClass = SignInWithGoogleOperation::class.java,
                operationType = OperationType.OIDC_AUTHENTICATION_GOOGLE
            )
    }

    override val login: OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure> =
        operationEntry(container = container, runtimeClass = LoginOperation::class.java, operationType = OperationType.SESSION_CREATION)
}
