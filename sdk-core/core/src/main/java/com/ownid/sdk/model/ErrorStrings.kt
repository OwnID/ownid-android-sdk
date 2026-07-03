package com.ownid.sdk.model

import com.ownid.sdk.model.ErrorStrings.Companion.DEFAULT
import com.ownid.sdk.ui.capability.StringsProvider

/**
 * User-facing error messages keyed by [ErrorCode].
 *
 * A complete instance contains one display string for every public error code. SDK string providers create these values
 * by resolving server-backed strings for the active language and filling any missing entries with embedded defaults.
 * [DEFAULT] is the built-in English set used when localized strings are unavailable.
 *
 * Use [getString] after the app has chosen an [ErrorCode] for display. Do not infer failure semantics from the returned
 * text; typed API, operation, and flow failures remain the source of truth for handling decisions.
 */
public class ErrorStrings(
    public val aborted: String,
    public val cancelNotSupported: String,
    public val deviceNotSupported: String,
    public val domElementNotFound: String,
    public val emptyLoginId: String,
    public val forbidden: String,
    public val integrationError: String,
    public val invalidArgument: String,
    public val invalidChallenge: String,
    public val loginIdTypeNotSupported: String,
    public val loginIdValidationFailed: String,
    public val loginWithPasswordFailed: String,
    public val maximumAttemptsReached: String,
    public val maximumChallengesReached: String,
    public val maximumResendAttemptsReached: String,
    public val missingCapabilityProvider: String,
    public val missingChannel: String,
    public val network: String,
    public val noApplicablePasskeys: String,
    public val notificationBlocked: String,
    public val oidcFailed: String,
    public val passkeyAlreadyRegistered: String,
    public val passkeyNotCreated: String,
    public val passkeysNotSupported: String,
    public val screensNotReady: String,
    public val sessionNotEstablished: String,
    public val timeout: String,
    public val unauthorized: String,
    public val unknown: String,
    public val userBlocked: String,
    public val userChanged: String,
    public val userNotFound: String,
    public val verificationCodeWrong: String,
    public val widgetAlreadyExists: String
) : StringsProvider.Data {

    public companion object {
        public val DEFAULT: ErrorStrings = ErrorStrings(
            aborted = "Aborted",
            cancelNotSupported = "Cancel not supported",
            deviceNotSupported = "Device not supported",
            domElementNotFound = "DOM Element not found",
            emptyLoginId = "Login ID validation failed",
            forbidden = "You don't have permission to perform this action",
            integrationError = "Integration error",
            invalidArgument = "Invalid argument",
            invalidChallenge = "Challenge does not exist or is expired",
            loginIdTypeNotSupported = "Login ID type not supported",
            loginIdValidationFailed = "Login ID validation failed",
            loginWithPasswordFailed = "Login with password failed",
            maximumAttemptsReached = "Maximum attempts reached",
            maximumChallengesReached = "Maximum challenges reached",
            maximumResendAttemptsReached = "Code resend limit reached",
            missingCapabilityProvider = "No provider for the required capability was found",
            missingChannel = "Missing communication channel",
            network = "Network error occurred. Please check your connection and try again",
            noApplicablePasskeys = "No applicable passkeys",
            notificationBlocked = "Notification was recently dismissed by the user",
            oidcFailed = "Social login did not complete successfully",
            passkeyAlreadyRegistered = "Passkey already registered",
            passkeyNotCreated = "Passkey not created",
            passkeysNotSupported = "Passkeys not supported",
            screensNotReady = "Screens not ready",
            sessionNotEstablished = "Session not established",
            timeout = "Timeout",
            unauthorized = "You are not authorized to perform this action",
            unknown = "Something went wrong. Try again later.",
            userBlocked = "Account is blocked",
            userChanged = "User changed",
            userNotFound = "You don't have an account",
            verificationCodeWrong = "Wrong code. Please try again.",
            widgetAlreadyExists = "Widget with the same ID already exists, please destroy it first"
        )
    }

    /**
     * Returns the display message for [errorCode] from this already selected string set.
     *
     * This lookup does not apply the SDK instance localization fallback chain. Use [ErrorCode.toLocalizedMessage] when a
     * lookup should first try the current strings for an OwnID instance and then fall back to a supplied [ErrorStrings].
     */
    public fun getString(errorCode: ErrorCode): String = when (errorCode) {
        ErrorCode.Aborted -> aborted
        ErrorCode.CancelNotSupported -> cancelNotSupported
        ErrorCode.DeviceNotSupported -> deviceNotSupported
        ErrorCode.DomElementNotFound -> domElementNotFound
        ErrorCode.EmptyLoginId -> emptyLoginId
        ErrorCode.Forbidden -> forbidden
        ErrorCode.IntegrationError -> integrationError
        ErrorCode.InvalidArgument -> invalidArgument
        ErrorCode.InvalidChallenge -> invalidChallenge
        ErrorCode.LoginIdTypeNotSupported -> loginIdTypeNotSupported
        ErrorCode.LoginIdValidationFailed -> loginIdValidationFailed
        ErrorCode.LoginWithPasswordFailed -> loginWithPasswordFailed
        ErrorCode.MaximumAttemptsReached -> maximumAttemptsReached
        ErrorCode.MaximumChallengesReached -> maximumChallengesReached
        ErrorCode.MaximumResendAttemptsReached -> maximumResendAttemptsReached
        ErrorCode.MissingCapabilityProvider -> missingCapabilityProvider
        ErrorCode.MissingChannel -> missingChannel
        ErrorCode.Network -> network
        ErrorCode.NoApplicablePasskeys -> noApplicablePasskeys
        ErrorCode.NotificationBlocked -> notificationBlocked
        ErrorCode.OidcFailed -> oidcFailed
        ErrorCode.PasskeyAlreadyRegistered -> passkeyAlreadyRegistered
        ErrorCode.PasskeyNotCreated -> passkeyNotCreated
        ErrorCode.PasskeysNotSupported -> passkeysNotSupported
        ErrorCode.ScreensNotReady -> screensNotReady
        ErrorCode.SessionNotEstablished -> sessionNotEstablished
        ErrorCode.Timeout -> timeout
        ErrorCode.Unauthorized -> unauthorized
        ErrorCode.Unknown -> unknown
        ErrorCode.UserBlocked -> userBlocked
        ErrorCode.UserChanged -> userChanged
        ErrorCode.UserNotFound -> userNotFound
        ErrorCode.VerificationCodeWrong -> verificationCodeWrong
        ErrorCode.WidgetAlreadyExists -> widgetAlreadyExists
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ErrorStrings) return false

        if (aborted != other.aborted) return false
        if (cancelNotSupported != other.cancelNotSupported) return false
        if (deviceNotSupported != other.deviceNotSupported) return false
        if (domElementNotFound != other.domElementNotFound) return false
        if (emptyLoginId != other.emptyLoginId) return false
        if (forbidden != other.forbidden) return false
        if (integrationError != other.integrationError) return false
        if (invalidArgument != other.invalidArgument) return false
        if (invalidChallenge != other.invalidChallenge) return false
        if (loginIdTypeNotSupported != other.loginIdTypeNotSupported) return false
        if (loginIdValidationFailed != other.loginIdValidationFailed) return false
        if (loginWithPasswordFailed != other.loginWithPasswordFailed) return false
        if (maximumAttemptsReached != other.maximumAttemptsReached) return false
        if (maximumChallengesReached != other.maximumChallengesReached) return false
        if (maximumResendAttemptsReached != other.maximumResendAttemptsReached) return false
        if (missingCapabilityProvider != other.missingCapabilityProvider) return false
        if (missingChannel != other.missingChannel) return false
        if (network != other.network) return false
        if (noApplicablePasskeys != other.noApplicablePasskeys) return false
        if (notificationBlocked != other.notificationBlocked) return false
        if (oidcFailed != other.oidcFailed) return false
        if (passkeyAlreadyRegistered != other.passkeyAlreadyRegistered) return false
        if (passkeyNotCreated != other.passkeyNotCreated) return false
        if (passkeysNotSupported != other.passkeysNotSupported) return false
        if (screensNotReady != other.screensNotReady) return false
        if (sessionNotEstablished != other.sessionNotEstablished) return false
        if (timeout != other.timeout) return false
        if (unauthorized != other.unauthorized) return false
        if (unknown != other.unknown) return false
        if (userBlocked != other.userBlocked) return false
        if (userChanged != other.userChanged) return false
        if (userNotFound != other.userNotFound) return false
        if (verificationCodeWrong != other.verificationCodeWrong) return false
        if (widgetAlreadyExists != other.widgetAlreadyExists) return false
        return true
    }

    override fun hashCode(): Int {
        var result = aborted.hashCode()
        result = 31 * result + cancelNotSupported.hashCode()
        result = 31 * result + deviceNotSupported.hashCode()
        result = 31 * result + domElementNotFound.hashCode()
        result = 31 * result + emptyLoginId.hashCode()
        result = 31 * result + forbidden.hashCode()
        result = 31 * result + integrationError.hashCode()
        result = 31 * result + invalidArgument.hashCode()
        result = 31 * result + invalidChallenge.hashCode()
        result = 31 * result + loginIdTypeNotSupported.hashCode()
        result = 31 * result + loginIdValidationFailed.hashCode()
        result = 31 * result + loginWithPasswordFailed.hashCode()
        result = 31 * result + maximumAttemptsReached.hashCode()
        result = 31 * result + maximumChallengesReached.hashCode()
        result = 31 * result + maximumResendAttemptsReached.hashCode()
        result = 31 * result + missingCapabilityProvider.hashCode()
        result = 31 * result + missingChannel.hashCode()
        result = 31 * result + network.hashCode()
        result = 31 * result + noApplicablePasskeys.hashCode()
        result = 31 * result + notificationBlocked.hashCode()
        result = 31 * result + oidcFailed.hashCode()
        result = 31 * result + passkeyAlreadyRegistered.hashCode()
        result = 31 * result + passkeyNotCreated.hashCode()
        result = 31 * result + passkeysNotSupported.hashCode()
        result = 31 * result + screensNotReady.hashCode()
        result = 31 * result + sessionNotEstablished.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + unauthorized.hashCode()
        result = 31 * result + unknown.hashCode()
        result = 31 * result + userBlocked.hashCode()
        result = 31 * result + userChanged.hashCode()
        result = 31 * result + userNotFound.hashCode()
        result = 31 * result + verificationCodeWrong.hashCode()
        result = 31 * result + widgetAlreadyExists.hashCode()
        return result
    }
}
