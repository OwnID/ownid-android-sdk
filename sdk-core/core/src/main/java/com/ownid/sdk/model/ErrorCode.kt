package com.ownid.sdk.model

import com.ownid.sdk.OwnId
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.ui.capability.ErrorStringsResolver

/**
 * Stable OwnID error key used for localization and cross-surface failure labels.
 *
 * OwnID result and failure types carry this key so app UI can resolve consistent copy. Branch on typed API, operation,
 * or flow failures for semantic handling; use this value as the display-message key after the app has chosen to show an
 * OwnID error.
 */
public enum class ErrorCode(public val value: String) {
    Aborted("aborted"),

    CancelNotSupported("cancel_not_supported"),

    DeviceNotSupported("device_not_supported"),

    DomElementNotFound("dom_element_not_found"),

    EmptyLoginId("empty_login_id"),

    Forbidden("forbidden"),

    IntegrationError("integration_error"),

    InvalidArgument("invalid_argument"),

    InvalidChallenge("invalid_challenge"),

    LoginIdTypeNotSupported("login_id_type_not_supported"),

    LoginIdValidationFailed("login_id_validation_failed"),

    LoginWithPasswordFailed("login_with_password_failed"),

    MaximumAttemptsReached("maximum_attempts_reached"),

    MaximumChallengesReached("maximum_challenges_reached"),

    MaximumResendAttemptsReached("maximum_resend_attempts_reached"),

    MissingCapabilityProvider("missing_capability_provider"),

    MissingChannel("missing_channel"),

    Network("network"),

    NoApplicablePasskeys("no_applicable_passkeys"),

    NotificationBlocked("notification_blocked"),

    OidcFailed("oidc_failed"),

    PasskeyAlreadyRegistered("passkey_already_registered"),

    PasskeyNotCreated("passkey_not_created"),

    PasskeysNotSupported("passkeys_not_supported"),

    ScreensNotReady("screens_not_ready"),

    SessionNotEstablished("session_not_established"),

    Timeout("timeout"),

    Unauthorized("unauthorized"),

    Unknown("unknown"),

    UserBlocked("user_blocked"),

    UserChanged("user_changed"),

    UserNotFound("user_not_found"),

    VerificationCodeWrong("verification_code_wrong"),

    WidgetAlreadyExists("widget_already_exists");

    /**
     * Returns the localized message for this error code.
     *
     * Use this after the app has decided that an OwnID error should be shown and that the SDK default copy fits the
     * current screen. This function only translates the lookup key to text. It does not decide whether to show an error,
     * whether a more generic message is safer, or what recovery action the UI should offer. Use typed operation, flow, or
     * API failure classes for semantic handling.
     *
     * The message is resolved from the current error strings for [instanceName]. Those strings follow the SDK localization
     * fallback chain. If the instance is missing, has no resolver, or has not emitted strings yet, [fallbackErrorStrings]
     * provides the message. [fallbackErrorStrings] is not a localization override. For custom app copy, use the code as a
     * lookup key and provide your own message.
     *
     * @param instanceName OwnID instance whose error strings should be used.
     * @param fallbackErrorStrings Last-resort strings used when the instance cannot provide current error strings.
     */
    @OptIn(InternalOwnIdAPI::class)
    public fun toLocalizedMessage(
        instanceName: InstanceName = InstanceName.Default,
        fallbackErrorStrings: ErrorStrings = ErrorStrings.DEFAULT
    ): String =
        OwnId.getInstanceContainer(instanceName)
            ?.getOrNull<ErrorStringsResolver>()
            ?.toLocalizedMessage(this, fallbackErrorStrings)
            ?: resolveLocalizedMessage(fallbackErrorStrings)

    internal fun resolveLocalizedMessage(errorStrings: ErrorStrings): String = errorStrings.getString(this)

    override fun toString(): String = value
}
