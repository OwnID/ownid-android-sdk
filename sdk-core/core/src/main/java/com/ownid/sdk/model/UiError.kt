package com.ownid.sdk.model

/**
 * Error text prepared for an SDK operation screen.
 *
 * SDK operation UI state uses this object when an error is already meant to be visible in that operation screen. Treat
 * [localizedMessage] as a display value resolved from [errorCode] for the current string set. If custom copy is
 * needed, use [errorCode] as a stable localization key and replace the message in the app layer.
 *
 * This type describes presentation intent only. Use the typed operation or flow failure that produced the UI state for
 * semantic handling, retry decisions, and analytics.
 *
 * @property errorCode Message lookup key for custom text.
 * @property localizedMessage Default text that can be shown to the user.
 */
public class UiError(
    public val errorCode: ErrorCode,
    public val localizedMessage: String
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is UiError && errorCode == other.errorCode && localizedMessage == other.localizedMessage)

    override fun hashCode(): Int = 31 * errorCode.hashCode() + localizedMessage.hashCode()

    override fun toString(): String = "UiError(errorCode=$errorCode, localizedMessage=$localizedMessage)"
}

internal fun ErrorCode.toUiError(errorStrings: ErrorStrings): UiError =
    UiError(errorCode = this, localizedMessage = resolveLocalizedMessage(errorStrings))
