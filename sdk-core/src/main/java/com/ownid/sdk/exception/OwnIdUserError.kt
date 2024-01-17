package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.locale.OwnIdLocaleService

/**
 * Errors that are intended to be reported to end user.
 *
 * @param code          String constant for error.
 * @param userMessage   User-friendly localized text message describing the error.
 * @param message       Text message describing reason for exception.
 * @param cause         Original exception that is wrapped in [OwnIdUserError]
 */
@OptIn(InternalOwnIdAPI::class)
public open class OwnIdUserError @JvmOverloads @InternalOwnIdAPI constructor(
    public val code: String,
    public val userMessage: String,
    message: String,
    cause: Throwable? = null
) : OwnIdException(message, cause) {

    public object Code {
        public const val INVALID_LOGIN_ID: String = "INVALID_LOGIN_ID"
        public const val ACCOUNT_NOT_FOUND: String = "ACCOUNT_NOT_FOUND"
        public const val ACCOUNT_IS_BLOCKED: String = "ACCOUNT_IS_BLOCKED"
        public const val WRONG_CODE: String = "WRONG_CODE"
        public const val WRONG_CODE_LIMIT_REACHED: String = "WRONG_CODE_LIMIT_REACHED"
        public const val SEND_CODE_LIMIT_REACHED: String = "SEND_CODE_LIMIT_REACHED"
        public const val USER_NOT_FOUND: String = "USER_NOT_FOUND"
        public const val REQUIRES_BIOMETRIC_INPUT: String = "REQUIRES_BIOMETRIC_INPUT"

        public const val UNSPECIFIED: String = "ERROR_UNSPECIFIED"
    }

    @InternalOwnIdAPI
    public companion object {
        @InternalOwnIdAPI
        public fun map(localeService: OwnIdLocaleService, message: String, cause: Throwable): OwnIdException =
            when (cause) {
                is OwnIdFlowCanceled -> cause
                is OwnIdIntegrationError -> cause
                is OwnIdUserError -> cause
                else -> OwnIdUserError(Code.UNSPECIFIED, localeService.unspecifiedErrorUserMessage, message, cause)
            }
    }

    public fun isUnspecified(): Boolean = this.code == Code.UNSPECIFIED

    override fun toString(): String = "OwnIdUserError(code='$code', userMessage='$userMessage', message='$message', cause='$cause')"
}