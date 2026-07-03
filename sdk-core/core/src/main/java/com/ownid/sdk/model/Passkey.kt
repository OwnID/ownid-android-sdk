package com.ownid.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public-key credential type accepted by WebAuthn.
 *
 * The SDK currently supports only the WebAuthn `"public-key"` value. Android exposes it as [PUBLIC_KEY].
 */
@Serializable
public enum class CredentialType(public val value: String) {
    @SerialName(value = "public-key")
    PUBLIC_KEY("public-key");

    override fun toString(): String = value
}

/**
 * Authenticator attachment value exchanged with WebAuthn.
 *
 * Values encode as the WebAuthn strings `"platform"` and `"cross-platform"`, and map to the platform provider's
 * attachment value when the provider reports one.
 */
@Serializable
public enum class AuthenticatorAttachment(public val value: String) {
    @SerialName(value = "platform")
    PLATFORM("platform"),

    @SerialName(value = "cross-platform")
    CROSS_PLATFORM("cross-platform");

    override fun toString(): String = value
}

/**
 * Relying party preference for attestation conveyance during credential creation.
 *
 * The SDK forwards the preference to the platform provider. Actual attestation data depends on the provider and
 * authenticator.
 */
@Serializable
public enum class AttestationConveyancePreference(public val value: String) {
    @SerialName(value = "none")
    NONE("none"),

    @SerialName(value = "direct")
    DIRECT("direct"),

    @SerialName(value = "indirect")
    INDIRECT("indirect"),

    @SerialName(value = "enterprise")
    ENTERPRISE("enterprise");

    override fun toString(): String = value
}

/**
 * COSE public-key algorithm identifier supported by SDK passkey mapping.
 *
 * Attestation options returned by the direct API default to [ES256] and [RS256] when OwnID returns no supported
 * algorithms.
 */
@Serializable
public enum class KeyAlgorithmType(public val value: Int) {
    @SerialName(value = "-7")
    ES256(-7),

    @SerialName(value = "-257")
    RS256(-257);

    override fun toString(): String = value.toString()
}

/**
 * Relying party preference for a resident, also called discoverable, credential.
 *
 * The value is forwarded in attestation authenticator-selection options when present.
 */
@Serializable
public enum class ResidentKey(public val value: String) {
    @SerialName(value = "required")
    REQUIRED("required"),

    @SerialName(value = "preferred")
    PREFERRED("preferred"),

    @SerialName(value = "discouraged")
    DISCOURAGED("discouraged");

    override fun toString(): String = value
}

/**
 * Authenticator transport hint reported by or sent to WebAuthn.
 *
 * Transport values are hints for credential selection and reporting. They do not guarantee that the platform provider
 * used a particular transport.
 */
@Serializable
public enum class TransportType(public val value: String) {
    @SerialName(value = "usb")
    USB("usb"),

    @SerialName(value = "nfc")
    NFC("nfc"),

    @SerialName(value = "ble")
    BLE("ble"),

    @SerialName(value = "smart-card")
    SMART_CARD("smart-card"),

    @SerialName(value = "hybrid")
    HYBRID("hybrid"),

    @SerialName(value = "internal")
    INTERNAL("internal"),

    @SerialName(value = "cable")
    CABLE("cable");

    override fun toString(): String = value
}

/**
 * Relying party user-verification requirement.
 *
 * The SDK forwards the value to the platform provider when present; the provider decides whether verification can be
 * satisfied.
 */
@Serializable
public enum class UserVerification(public val value: String) {
    @SerialName(value = "preferred")
    PREFERRED("preferred"),

    @SerialName(value = "required")
    REQUIRED("required"),

    @SerialName(value = "discouraged")
    DISCOURAGED("discouraged");

    override fun toString(): String = value
}

/**
 * Identifies an existing credential by its [id], [type], and optional [transports].
 *
 * The [id] is an opaque Base64url-encoded credential ID. The constructor does not validate the encoding or size. Direct
 * API option responses validate descriptor IDs before exposing them; manually constructed descriptors are passed to
 * Android Credential Manager and may fail there if malformed.
 *
 * @param type Credential type for the descriptor.
 * @param id Base64url-encoded credential ID.
 * @param transports Optional authenticator transport hints.
 */
@Serializable
public class PublicKeyCredentialDescriptor(
    @SerialName(value = "type") public val type: CredentialType,
    @SerialName(value = "id") public val id: String,
    @SerialName(value = "transports") public val transports: List<TransportType>? = null
) {
    override fun toString(): String = "PublicKeyCredentialDescriptor(type=$type, id=${id.shorten()}, transports=$transports)"
}

/**
 * Server-provided options for a WebAuthn assertion (get credential) request.
 *
 * The SDK passes these options to the platform passkey provider and sends the resulting [AssertionResult] back to OwnID.
 * Base64url fields are opaque WebAuthn values; do not decode, normalize, or persist them unless your backend explicitly
 * owns that behavior.
 *
 * Direct API success responses validate [challenge] and [allowCredentials] IDs as Base64url before exposing this model.
 * Constructors and deserialization still do not validate blank strings, relying-party format, or manually supplied
 * Base64url values. When the SDK sends these options to Android Credential Manager, default and `null` values are omitted
 * from the WebAuthn JSON payload.
 *
 * @param challenge Base64url-encoded challenge that the authenticator must sign.
 * @param rpId Relying Party identifier (the passkey's domain).
 * @param allowCredentials Optional allowlist of credential descriptors; when absent, discoverable credentials may be used.
 * @param userVerification Preferred user-verification policy for the authenticator.
 * @param timeout Timeout in milliseconds for both the server challenge and the client UI.
 */
@Serializable
public class AssertionOptions(
    @SerialName(value = "challenge") public val challenge: ChallengeId,
    @SerialName(value = "rpId") public val rpId: String,
    @SerialName(value = "allowCredentials") public val allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
    @SerialName(value = "userVerification") public val userVerification: UserVerification? = null,
    @SerialName(value = "timeout") public val timeout: Timeout? = null
) {
    override fun toString(): String =
        "AssertionOptions(challenge=$challenge, rpId=$rpId, allowCredentials=$allowCredentials, userVerification=$userVerification, timeout=$timeout)"
}

/**
 * Result of a successful WebAuthn assertion (get credential) operation.
 *
 * The fields are encoded for transport back to OwnID and should be treated as opaque WebAuthn response data. When
 * [authenticatorAttachment] is `null`, assertion verification sends `"platform"` to OwnID for compatibility.
 * [toString] shortens [id], but nested response output can still include full response values.
 */
@Serializable
public class AssertionResult(
    @SerialName(value = "id") public val id: String,
    @SerialName(value = "type") public val type: CredentialType,
    @SerialName(value = "response") public val response: AuthenticatorResponse,
    @SerialName(value = "authenticatorAttachment") public val authenticatorAttachment: AuthenticatorAttachment? = null,
) {
    /**
     * Authenticator output for a successful assertion.
     *
     * Values are Base64url-encoded WebAuthn response fields. [userHandle] is optional and opaque.
     */
    @Serializable
    public class AuthenticatorResponse(
        @SerialName(value = "clientDataJSON") public val clientDataJSON: String,
        @SerialName(value = "authenticatorData") public val authenticatorData: String,
        @SerialName(value = "signature") public val signature: String,
        @SerialName(value = "userHandle") public val userHandle: String? = null
    ) {
        override fun toString(): String =
            "AuthenticatorResponse(clientDataJSON=$clientDataJSON, authenticatorData=$authenticatorData, signature=$signature, userHandle=${userHandle.shorten()})"
    }

    override fun toString(): String = "AssertionResult(id=${id.shorten()}, type=$type, response=$response, authenticatorAttachment=$authenticatorAttachment)"
}

/**
 * Server-provided options for a WebAuthn attestation (create credential) request.
 *
 * The SDK passes these options to the platform passkey provider and sends the resulting [AttestationResult] back to OwnID.
 * Base64url fields are opaque WebAuthn values; do not decode, normalize, or persist them unless your backend explicitly
 * owns that behavior.
 *
 * Direct API success responses validate [challenge], [user] ID, and [excludeCredentials] IDs as Base64url with WebAuthn
 * size limits before exposing this model. Constructors and deserialization still do not validate blank strings,
 * relying-party format, or manually supplied Base64url values. When the SDK sends these options to Android Credential
 * Manager, default and `null` values are omitted from the WebAuthn JSON payload. When OwnID returns no supported
 * algorithms, SDK API mapping uses [KeyAlgorithmType.ES256] and [KeyAlgorithmType.RS256].
 *
 * @param rp Relying party information for the credential.
 * @param user User account information for the credential.
 * @param challenge Base64url-encoded challenge that the authenticator must sign.
 * @param pubKeyCredParams Supported public key algorithms for credential creation.
 * @param attestation Relying party attestation preference.
 * @param authenticatorSelection Optional authenticator selection criteria.
 * @param timeout Timeout in milliseconds for both the server challenge and the client UI.
 * @param excludeCredentials Optional credentials that should not be re-registered.
 */
@Serializable
public class AttestationOptions(
    @SerialName(value = "rp") public val rp: RelayingParty,
    @SerialName(value = "user") public val user: User,
    @SerialName(value = "challenge") public val challenge: ChallengeId,
    @SerialName(value = "pubKeyCredParams") public val pubKeyCredParams: List<PubKeyCredParams>,
    @SerialName(value = "attestation") public val attestation: AttestationConveyancePreference? = null,
    @SerialName(value = "authenticatorSelection") public val authenticatorSelection: AuthenticatorSelection? = null,
    @SerialName(value = "timeout") public val timeout: Timeout? = null,
    @SerialName(value = "excludeCredentials") public val excludeCredentials: List<PublicKeyCredentialDescriptor>? = null
) {
    /**
     * WebAuthn Relying Party with its [id], usually the passkey domain, and display [name].
     *
     * Values are forwarded as provided and are not validated by this data holder.
     */
    @Serializable
    public class RelayingParty(
        @SerialName(value = "id") public val id: String,
        @SerialName(value = "name") public val name: String
    ) {
        override fun toString(): String = "RelayingParty(id=$id, name=$name)"
    }

    /**
     * WebAuthn user account with a Base64url-encoded [id], [name], and [displayName].
     *
     * [id] is an opaque user handle. This data holder does not validate encoding or size. [toString] shortens [id] and
     * masks [name] and [displayName].
     */
    @Serializable
    public class User(
        @SerialName(value = "id") public val id: String,
        @SerialName(value = "name") public val name: String,
        @SerialName(value = "displayName") public val displayName: String
    ) {
        override fun toString(): String = "User(id=${id.shorten()}, name='*', displayName='*')"
    }

    @Serializable
    public class PubKeyCredParams(
        @SerialName(value = "type") public val type: CredentialType,
        @SerialName(value = "alg") public val alg: KeyAlgorithmType
    ) {
        override fun toString(): String = "PubKeyCredParams(type=$type, alg=$alg)"
    }

    /**
     * Optional authenticator-selection criteria for credential creation.
     *
     * `null` properties leave the corresponding WebAuthn preference unspecified.
     */
    @Serializable
    public class AuthenticatorSelection(
        @SerialName(value = "authenticatorAttachment") public val authenticatorAttachment: AuthenticatorAttachment? = null,
        @SerialName(value = "userVerification") public val userVerification: UserVerification? = null,
        @SerialName(value = "residentKey") public val residentKey: ResidentKey? = null
    ) {
        override fun toString(): String =
            "AuthenticatorSelection(authenticatorAttachment=$authenticatorAttachment, userVerification=$userVerification, residentKey=$residentKey)"
    }

    override fun toString(): String =
        "AttestationOptions(rp=$rp, user=$user, challenge=$challenge, pubKeyCredParams=$pubKeyCredParams, attestation=$attestation, authenticatorSelection=$authenticatorSelection, timeout=$timeout, excludeCredentials=$excludeCredentials)"
}

/**
 * Result of a successful WebAuthn attestation (create credential) operation.
 *
 * The fields are encoded for transport back to OwnID and should be treated as opaque WebAuthn response data. The SDK
 * does not persist the result. `null` [authenticatorAttachment] values are omitted during attestation verification.
 * [toString] shortens [id] and the attestation object.
 */
@Serializable
public class AttestationResult(
    @SerialName(value = "id") public val id: String,
    @SerialName(value = "type") public val type: CredentialType,
    @SerialName(value = "response") public val response: AuthenticatorResponse,
    @SerialName(value = "authenticatorAttachment") public val authenticatorAttachment: AuthenticatorAttachment? = null
) {
    /**
     * Authenticator output for a successful attestation.
     *
     * Values are Base64url-encoded WebAuthn response fields. [transports] contains transport hints from the provider response.
     */
    @Serializable
    public class AuthenticatorResponse(
        @SerialName(value = "clientDataJSON") public val clientDataJSON: String,
        @SerialName(value = "attestationObject") public val attestationObject: String,
        @SerialName(value = "transports") public val transports: List<TransportType>
    ) {
        override fun toString(): String =
            "AuthenticatorResponse(clientDataJSON=$clientDataJSON, attestationObject=${attestationObject.shorten()}, transports=$transports)"
    }

    override fun toString(): String = "AttestationResult(id=${id.shorten()}, type=$type, response=$response, authenticatorAttachment=$authenticatorAttachment)"
}

/**
 * Server response after a successful attestation.
 *
 * This is an OwnID server response, not a WebAuthn provider payload. [toString] masks [ownIdData].
 *
 * @param proofToken Proof token for subsequent enrollment.
 * @param ownIdData Value to store or forward to your vendor backend. Structured values remain JSON text. If the server
 *     returns a plain string, this property contains that string value.
 */
public class AttestationResponse(
    public val proofToken: ProofToken,
    public val ownIdData: String
) {
    override fun toString(): String = "AttestationResponse(proofToken=$proofToken, ownIdData='*')"
}
