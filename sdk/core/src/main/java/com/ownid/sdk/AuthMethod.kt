package com.ownid.sdk

/**
 * Represents different authentication methods supported by OwnID.
 */
public enum class AuthMethod(internal val aliases: Array<String>) {
    Passkey(arrayOf("biometrics", "desktop-biometrics", "passkey")),
    Otp(arrayOf("email-fallback", "sms-fallback", "otp")),
    Password(arrayOf("password"));

    @InternalOwnIdAPI
    internal companion object {
        internal fun fromString(value: String): AuthMethod? = when (value.lowercase()) {
            in Passkey.aliases -> Passkey
            in Otp.aliases -> Otp
            in Password.aliases -> Password
            else -> null
        }
    }
}