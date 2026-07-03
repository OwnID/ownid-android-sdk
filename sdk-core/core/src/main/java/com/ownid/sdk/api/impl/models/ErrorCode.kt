package com.ownid.sdk.api.impl.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ErrorCodeSerializer::class)
internal enum class ErrorCode(val value: String) {
    ABORTED("aborted"),

    CANCEL_NOT_SUPPORTED("cancel_not_supported"),

    DEVICE_NOT_SUPPORTED("device_not_supported"),

    DOM_ELEMENT_NOT_FOUND("dom_element_not_found"),

    EMPTY_LOGIN_ID("empty_login_id"),

    FORBIDDEN("forbidden"),

    INTEGRATION_ERROR("integration_error"),

    INVALID_ARGUMENT("invalid_argument"),

    INVALID_CHALLENGE("invalid_challenge"),

    LOGIN_ID_TYPE_NOT_SUPPORTED("login_id_type_not_supported"),

    LOGIN_ID_VALIDATION_FAILED("login_id_validation_failed"),

    LOGIN_WITH_PASSWORD_FAILED("login_with_password_failed"),

    MAXIMUM_ATTEMPTS_REACHED("maximum_attempts_reached"),

    MAXIMUM_CHALLENGES_REACHED("maximum_challenges_reached"),

    MAXIMUM_RESEND_ATTEMPTS_REACHED("maximum_resend_attempts_reached"),

    MISSING_CAPABILITY_PROVIDER("missing_capability_provider"),

    MISSING_CHANNEL("missing_channel"),

    NETWORK("network"),

    NO_APPLICABLE_PASSKEYS("no_applicable_passkeys"),

    NOTIFICATION_BLOCKED("notification_blocked"),

    OIDC_FAILED("oidc_failed"),

    PASSKEY_ALREADY_REGISTERED("passkey_already_registered"),

    PASSKEY_NOT_CREATED("passkey_not_created"),

    PASSKEYS_NOT_SUPPORTED("passkeys_not_supported"),

    SCREENS_NOT_READY("screens_not_ready"),

    SESSION_NOT_ESTABLISHED("session_not_established"),

    TIMEOUT("timeout"),

    UNAUTHORIZED("unauthorized"),

    UNKNOWN("unknown"),

    USER_BLOCKED("user_blocked"),

    USER_CHANGED("user_changed"),

    USER_NOT_FOUND("user_not_found"),

    VERIFICATION_CODE_WRONG("verification_code_wrong"),

    WIDGET_ALREADY_EXISTS("widget_already_exists")
}

internal object ErrorCodeSerializer : KSerializer<ErrorCode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ErrorCode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ErrorCode {
        val raw = decoder.decodeString()
        return ErrorCode.values().firstOrNull { it.value == raw } ?: ErrorCode.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: ErrorCode) {
        encoder.encodeString(value.value)
    }
}
