package com.ownid.sdk.internal

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal enum class AuthMethod(internal val aliases: Array<String>) {
    Passkey(arrayOf("biometrics", "desktop-biometrics", "passkey")),
    Otp(arrayOf("email-fallback", "sms-fallback", "otp")),
    Password(arrayOf("password"));

    internal companion object {
        internal fun fromString(value: String): AuthMethod? = when (value.lowercase()) {
            in Passkey.aliases -> Passkey
            in Otp.aliases -> Otp
            in Password.aliases -> Password
            else -> null
        }
    }
}