package com.ownid.sdk

/**
 * Represents different authentication methods supported by OwnID.
 */
public enum class AuthMethod(internal val aliases: Array<String>) {
    Passkey(arrayOf("passkey", "biometrics", "desktop-biometrics")),
    Otp(arrayOf("otp", "email-fallback", "sms-fallback")),
    Password(arrayOf("password")),
    SocialGoogle(arrayOf("social-google")),
    SocialApple(arrayOf("social-apple"));

    override fun toString(): String = aliases.first()

    @InternalOwnIdAPI
    internal companion object {
        internal fun fromString(value: String): AuthMethod? = when (value.lowercase()) {
            in Passkey.aliases -> Passkey
            in Otp.aliases -> Otp
            in Password.aliases -> Password
            in SocialGoogle.aliases -> SocialGoogle
            in SocialApple.aliases -> SocialApple
            else -> null
        }
    }
}