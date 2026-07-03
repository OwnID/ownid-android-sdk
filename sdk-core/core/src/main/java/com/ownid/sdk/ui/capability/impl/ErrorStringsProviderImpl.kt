package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.ErrorStrings.Companion.DEFAULT
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.ErrorStringsProvider
import com.ownid.sdk.ui.capability.StringsProvider
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps server error-code keys into typed error strings and fills missing keys from embedded defaults.
 */
internal class ErrorStringsEmbeddedRepositoryImpl : ErrorStringsProvider.EmbeddedRepository {

    @OptIn(InternalOwnIdAPI::class)
    override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): ErrorStrings = ErrorStrings(
        aborted = map[ErrorCode.Aborted.value] ?: DEFAULT.aborted,
        cancelNotSupported = map[ErrorCode.CancelNotSupported.value] ?: DEFAULT.cancelNotSupported,
        deviceNotSupported = map[ErrorCode.DeviceNotSupported.value] ?: DEFAULT.deviceNotSupported,
        domElementNotFound = map[ErrorCode.DomElementNotFound.value] ?: DEFAULT.domElementNotFound,
        emptyLoginId = map[ErrorCode.EmptyLoginId.value] ?: DEFAULT.emptyLoginId,
        forbidden = map[ErrorCode.Forbidden.value] ?: DEFAULT.forbidden,
        integrationError = map[ErrorCode.IntegrationError.value] ?: DEFAULT.integrationError,
        invalidArgument = map[ErrorCode.InvalidArgument.value] ?: DEFAULT.invalidArgument,
        invalidChallenge = map[ErrorCode.InvalidChallenge.value] ?: DEFAULT.invalidChallenge,
        loginIdTypeNotSupported = map[ErrorCode.LoginIdTypeNotSupported.value] ?: DEFAULT.loginIdTypeNotSupported,
        loginIdValidationFailed = map[ErrorCode.LoginIdValidationFailed.value] ?: DEFAULT.loginIdValidationFailed,
        loginWithPasswordFailed = map[ErrorCode.LoginWithPasswordFailed.value] ?: DEFAULT.loginWithPasswordFailed,
        maximumAttemptsReached = map[ErrorCode.MaximumAttemptsReached.value] ?: DEFAULT.maximumAttemptsReached,
        maximumChallengesReached = map[ErrorCode.MaximumChallengesReached.value] ?: DEFAULT.maximumChallengesReached,
        maximumResendAttemptsReached = map[ErrorCode.MaximumResendAttemptsReached.value] ?: DEFAULT.maximumResendAttemptsReached,
        missingCapabilityProvider = map[ErrorCode.MissingCapabilityProvider.value] ?: DEFAULT.missingCapabilityProvider,
        missingChannel = map[ErrorCode.MissingChannel.value] ?: DEFAULT.missingChannel,
        network = map[ErrorCode.Network.value] ?: DEFAULT.network,
        noApplicablePasskeys = map[ErrorCode.NoApplicablePasskeys.value] ?: DEFAULT.noApplicablePasskeys,
        notificationBlocked = map[ErrorCode.NotificationBlocked.value] ?: DEFAULT.notificationBlocked,
        oidcFailed = map[ErrorCode.OidcFailed.value] ?: DEFAULT.oidcFailed,
        passkeyAlreadyRegistered = map[ErrorCode.PasskeyAlreadyRegistered.value] ?: DEFAULT.passkeyAlreadyRegistered,
        passkeyNotCreated = map[ErrorCode.PasskeyNotCreated.value] ?: DEFAULT.passkeyNotCreated,
        passkeysNotSupported = map[ErrorCode.PasskeysNotSupported.value] ?: DEFAULT.passkeysNotSupported,
        screensNotReady = map[ErrorCode.ScreensNotReady.value] ?: DEFAULT.screensNotReady,
        sessionNotEstablished = map[ErrorCode.SessionNotEstablished.value] ?: DEFAULT.sessionNotEstablished,
        timeout = map[ErrorCode.Timeout.value] ?: DEFAULT.timeout,
        unauthorized = map[ErrorCode.Unauthorized.value] ?: DEFAULT.unauthorized,
        unknown = map[ErrorCode.Unknown.value] ?: DEFAULT.unknown,
        userBlocked = map[ErrorCode.UserBlocked.value] ?: DEFAULT.userBlocked,
        userChanged = map[ErrorCode.UserChanged.value] ?: DEFAULT.userChanged,
        userNotFound = map[ErrorCode.UserNotFound.value] ?: DEFAULT.userNotFound,
        verificationCodeWrong = map[ErrorCode.VerificationCodeWrong.value] ?: DEFAULT.verificationCodeWrong,
        widgetAlreadyExists = map[ErrorCode.WidgetAlreadyExists.value] ?: DEFAULT.widgetAlreadyExists
    )
}

/**
 * Reads error strings from the server locale data source.
 *
 * Missing locale data emits `null`; missing individual error-code keys are omitted so the embedded repository can fill
 * them. Unavailable or unreadable persisted locale payloads are visible here as missing server data; suppressed refresh
 * failures may continue serving previously available data when a payload exists.
 */
internal class ErrorStringsServerRepositoryImpl(
    private val serverLocaleProvider: ServerLocaleDataSource.Provider
) : ErrorStringsProvider.ServerRepository {

    private val prefix = arrayOf("errors")

    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {
        return serverLocaleProvider.getDataSource(languageTag).map { dataSource ->
            if (dataSource == null) return@map null

            mapOf(
                ErrorCode.Aborted.value to dataSource.getString(*prefix, ErrorCode.Aborted.value),
                ErrorCode.CancelNotSupported.value to dataSource.getString(*prefix, ErrorCode.CancelNotSupported.value),
                ErrorCode.DeviceNotSupported.value to dataSource.getString(*prefix, ErrorCode.DeviceNotSupported.value),
                ErrorCode.DomElementNotFound.value to dataSource.getString(*prefix, ErrorCode.DomElementNotFound.value),
                ErrorCode.EmptyLoginId.value to dataSource.getString(*prefix, ErrorCode.EmptyLoginId.value),
                ErrorCode.Forbidden.value to dataSource.getString(*prefix, ErrorCode.Forbidden.value),
                ErrorCode.IntegrationError.value to dataSource.getString(*prefix, ErrorCode.IntegrationError.value),
                ErrorCode.InvalidArgument.value to dataSource.getString(*prefix, ErrorCode.InvalidArgument.value),
                ErrorCode.InvalidChallenge.value to dataSource.getString(*prefix, ErrorCode.InvalidChallenge.value),
                ErrorCode.LoginIdTypeNotSupported.value to dataSource.getString(*prefix, ErrorCode.LoginIdTypeNotSupported.value),
                ErrorCode.LoginIdValidationFailed.value to dataSource.getString(*prefix, ErrorCode.LoginIdValidationFailed.value),
                ErrorCode.LoginWithPasswordFailed.value to dataSource.getString(*prefix, ErrorCode.LoginWithPasswordFailed.value),
                ErrorCode.MaximumAttemptsReached.value to dataSource.getString(*prefix, ErrorCode.MaximumAttemptsReached.value),
                ErrorCode.MaximumChallengesReached.value to dataSource.getString(*prefix, ErrorCode.MaximumChallengesReached.value),
                ErrorCode.MaximumResendAttemptsReached.value to dataSource.getString(*prefix, ErrorCode.MaximumResendAttemptsReached.value),
                ErrorCode.MissingCapabilityProvider.value to dataSource.getString(*prefix, ErrorCode.MissingCapabilityProvider.value),
                ErrorCode.MissingChannel.value to dataSource.getString(*prefix, ErrorCode.MissingChannel.value),
                ErrorCode.Network.value to dataSource.getString(*prefix, ErrorCode.Network.value),
                ErrorCode.NoApplicablePasskeys.value to dataSource.getString(*prefix, ErrorCode.NoApplicablePasskeys.value),
                ErrorCode.NotificationBlocked.value to dataSource.getString(*prefix, ErrorCode.NotificationBlocked.value),
                ErrorCode.OidcFailed.value to dataSource.getString(*prefix, ErrorCode.OidcFailed.value),
                ErrorCode.PasskeyAlreadyRegistered.value to dataSource.getString(*prefix, ErrorCode.PasskeyAlreadyRegistered.value),
                ErrorCode.PasskeyNotCreated.value to dataSource.getString(*prefix, ErrorCode.PasskeyNotCreated.value),
                ErrorCode.PasskeysNotSupported.value to dataSource.getString(*prefix, ErrorCode.PasskeysNotSupported.value),
                ErrorCode.ScreensNotReady.value to dataSource.getString(*prefix, ErrorCode.ScreensNotReady.value),
                ErrorCode.SessionNotEstablished.value to dataSource.getString(*prefix, ErrorCode.SessionNotEstablished.value),
                ErrorCode.Timeout.value to dataSource.getString(*prefix, ErrorCode.Timeout.value),
                ErrorCode.Unauthorized.value to dataSource.getString(*prefix, ErrorCode.Unauthorized.value),
                ErrorCode.Unknown.value to dataSource.getString(*prefix, ErrorCode.Unknown.value),
                ErrorCode.UserBlocked.value to dataSource.getString(*prefix, ErrorCode.UserBlocked.value),
                ErrorCode.UserChanged.value to dataSource.getString(*prefix, ErrorCode.UserChanged.value),
                ErrorCode.UserNotFound.value to dataSource.getString(*prefix, ErrorCode.UserNotFound.value),
                ErrorCode.VerificationCodeWrong.value to dataSource.getString(*prefix, ErrorCode.VerificationCodeWrong.value),
                ErrorCode.WidgetAlreadyExists.value to dataSource.getString(*prefix, ErrorCode.WidgetAlreadyExists.value),
            ).filterValues { it != null }.mapValues { it.value!! }
        }
    }
}

/**
 * Server repository used when locale data source setup is unavailable.
 *
 * Emits an empty map so string resolution completes with embedded defaults.
 */
internal object ErrorStringsServerRepositoryEmptyFallback : ErrorStringsProvider.ServerRepository {
    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> =
        flowOf(emptyMap())
}
