package com.ownid.sdk.model

/**
 * A delivery channel for a verification operation.
 *
 * OwnID returns the channel together with a channel [id] that identifies it for operation selection. The SDK keeps both
 * strings as supplied; it does not validate, normalize, or unmask them.
 *
 * [toString] redacts [channel] and includes only [id]. The SDK does not provide a public serializer for this type.
 *
 * @property channel Channel value, such as an email address or phone number.
 * @property id Channel identifier used for operation selection.
 */
public class OperationChannel(
    public val channel: String,
    public val id: String
) {
    override fun toString(): String = "OperationChannel(channel='*', id='$id')"
    override fun equals(other: Any?): Boolean = this === other || (other is OperationChannel && channel == other.channel && id == other.id)
    override fun hashCode(): Int = 31 * channel.hashCode() + id.hashCode()
}

/**
 * A recommended operation that can contribute to the current authentication requirements.
 *
 * Operation requirements are returned by OwnID as part of [AuthRequirements]. The SDK keeps the returned [score] and
 * [channels] as supplied; it does not reject negative scores, duplicate channels, or empty lists when this model is
 * constructed directly.
 *
 * The SDK does not provide a public serializer for this type. If an app serializes it, use the public properties and
 * [OperationType.value] for the operation type string.
 *
 * @property type Operation type to perform.
 * @property score Score this operation adds toward [AuthRequirements.targetScore].
 * @property channels Optional available channels with IDs for performing the operation.
 */
public class OperationRequirement(
    public val type: OperationType,
    public val score: Int,
    public val channels: List<OperationChannel>? = null
) {
    override fun toString(): String = "$type(score=$score, channels=[${channels?.joinToString()}])"
}

/**
 * The kind of authentication, enrollment, or account operation.
 *
 * [value] is the OwnID operation type string used by API payloads and diagnostics. Enum constant names are Kotlin names;
 * use [value] or [toString] when the OwnID string is required. Invalid enum names passed to `valueOf` throw
 * [IllegalArgumentException].
 */
public enum class OperationType(public val value: String) {
    LOGIN_ID_COLLECT("LoginIdCollect"),

    EMAIL_VERIFICATION("EmailVerification"),

    EMAIL_ENROLLMENT("EmailEnrollment"),

    PHONE_NUMBER_VERIFICATION("PhoneNumberVerification"),

    PHONE_NUMBER_ENROLLMENT("PhoneNumberEnrollment"),

    PASSKEY_CREATION("PasskeyCreation"),

    PASSKEY_AUTH("PasskeyAuth"),

    PASSKEY_ENROLLMENT("PasskeyEnrollment"),

    SESSION_CREATION("SessionCreation"),

    DEFERRED_AUTHENTICATION("DeferredAuthentication"),

    EXTERNAL_AUTHENTICATION("ExternalAuthentication"),

    PROFILE_COLLECTION("ProfileCollection"),

    PASSWORD_AUTHENTICATION("PasswordAuthentication"),

    OIDC_AUTHENTICATION_APPLE("OidcAuthenticationApple"),

    OIDC_AUTHENTICATION_GOOGLE("OidcAuthenticationGoogle"),

    REGISTRATION("Registration"),

    PROFILE_UPDATE("ProfileUpdate"),

    SESSION_MANAGEMENT("SessionManagement"),

    WEB_BRIDGE("WebBridge"),

    FACE_KEY_VERIFICATION("FaceKeyVerification"),

    FACE_KEY_CREATION("FaceKeyCreation"),

    FACE_KEY_ENROLLMENT("FaceKeyEnrollment");

    override fun toString(): String = value

    internal fun createUID(): OperationID = OperationID(type = this, secureRandom().encodeToBase64UrlSafe())
}

/**
 * Unique identifier for one operation lifecycle.
 *
 * The SDK creates operation IDs when operations start and uses them to correlate controllers, state, UI presentation,
 * analytics, and results for the same lifecycle. Use [type] to inspect the operation kind and [id] only as an opaque
 * per-operation identifier. Do not parse [id] or treat it as stable beyond the operation run that produced it.
 *
 * [toString] returns the public `type:id` form using [OperationType.value]. The constructor keeps [id] as supplied and
 * does not reject blank, reused, or externally generated identifiers. The SDK does not provide a public serializer for
 * this type.
 *
 * @property type Operation kind associated with this identifier.
 * @property id Opaque per-operation identifier.
 */
public class OperationID(public val type: OperationType, public val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OperationID) return false
        return type == other.type && id == other.id
    }

    override fun hashCode(): Int = 31 * type.hashCode() + id.hashCode()

    override fun toString(): String = "$type:$id"
}
