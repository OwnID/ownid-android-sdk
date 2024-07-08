package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError

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

    @InternalOwnIdAPI
    public companion object {
        @InternalOwnIdAPI
        public fun map(localeService: OwnIdLocaleService, message: String, cause: Throwable): OwnIdException =
            when (cause) {
                is OwnIdFlowCanceled -> cause
                is OwnIdIntegrationError -> cause
                is OwnIdUserError -> cause
                else -> OwnIdUserError(OwnIdNativeFlowError.CodeLocal.UNSPECIFIED, localeService.unspecifiedErrorUserMessage, message, cause)
            }
    }

    public fun isUnspecified(): Boolean = this.code == OwnIdNativeFlowError.CodeLocal.UNSPECIFIED

    @InternalOwnIdAPI
    override fun toMap(): Map<String, Any?> = super.toMap().plus("userMessage" to userMessage).plus("code" to code)

    override fun toString(): String = "OwnIdUserError(code='$code', userMessage='$userMessage', message='$message', cause='$cause')"
}