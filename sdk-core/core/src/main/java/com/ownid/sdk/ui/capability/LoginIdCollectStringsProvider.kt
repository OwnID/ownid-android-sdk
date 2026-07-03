package com.ownid.sdk.ui.capability

import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId

/**
 * Provides resolved strings for the login ID collection UI.
 *
 * Default SDK providers select the server locale keys from [LoginIdCollectStringsParams.loginIdTypes] and platform
 * passkey availability, then use [LoginIdCollectStrings.default] for any missing key.
 */
public interface LoginIdCollectStringsProvider : StringsProvider<LoginIdCollectStrings, LoginIdCollectStringsParams> {

    /** Repository that supplies complete login ID collection strings by applying embedded defaults. */
    public interface EmbeddedRepository : StringsProvider.EmbeddedRepository<LoginIdCollectStrings>

    /** Repository that reads raw login ID collection string keys from server locale data. */
    public interface ServerRepository : StringsProvider.ServerRepository
}

/**
 * Parameters for login ID collection.
 *
 * @param loginIdTypes Login ID types the form can collect. Email, phone number, and username select the matching
 * server keys and embedded defaults; duplicate values are ignored, and other values do not affect string selection.
 */
public class LoginIdCollectStringsParams(public val loginIdTypes: List<LoginId.Type>) : StringsProvider.Params

/** Complete UI strings for login ID collection. */
public class LoginIdCollectStrings(
    /** Title text for the login ID collection view. */
    public val title: String,
    /** Instructional message for the login ID collection view. */
    public val message: String,
    /** Placeholder text for the login ID input field. */
    public val placeholder: String,
    /** Label for the cancel action. */
    public val cancel: String,
    /** Label for the primary action button. */
    public val cta: String,
    /** Error message shown for [ErrorCode.LoginIdValidationFailed]. */
    public val error: String
) : StringsProvider.Data {

    public companion object {
        /**
         * Returns embedded English fallback strings for a login ID collection form.
         *
         * Email, phone number, and username values select the corresponding single or combined prompt. Unsupported
         * values are ignored; if no supported type remains, all returned strings are empty. When the platform passkey
         * authenticator is available, the title and message use biometric sign-in wording.
         */
        public fun default(loginIdTypes: List<LoginId.Type>, isSystemFidoCapable: Boolean): LoginIdCollectStrings {
            return when (loginIdTypes.distinct().filter { it == LoginId.Type.Email || it == LoginId.Type.PhoneNumber || it == LoginId.Type.UserName }.toSet()) {
                // email
                setOf(LoginId.Type.Email) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your email",
                    message = if (isSystemFidoCapable) "Enter your email" else "to receive a one-time code",
                    placeholder = "Email",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid email",
                )

                // phoneNumber
                setOf(LoginId.Type.PhoneNumber) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your phone number",
                    message = if (isSystemFidoCapable) "Enter your phone number" else "to receive a one-time code",
                    placeholder = "Phone number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid phone number",
                )

                // userName
                setOf(LoginId.Type.UserName) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your ID number",
                    message = if (isSystemFidoCapable) "Enter your ID number" else "to receive a one-time code",
                    placeholder = "ID number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid ID number",
                )

                // emailOrPhoneNumber
                setOf(LoginId.Type.Email, LoginId.Type.PhoneNumber) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your email or phone number",
                    message = if (isSystemFidoCapable) "Enter your email or phone number" else "to receive a one-time code",
                    placeholder = "Email or phone number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid email or phone number",
                )

                // emailOrUserName
                setOf(LoginId.Type.Email, LoginId.Type.UserName) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your email or ID number",
                    message = if (isSystemFidoCapable) "Enter your email or ID number" else "to receive a one-time code",
                    placeholder = "Email or ID number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid email or ID number",
                )

                // phoneNumberOrUserName
                setOf(LoginId.Type.PhoneNumber, LoginId.Type.UserName) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your phone or ID number",
                    message = if (isSystemFidoCapable) "Enter your phone or ID number" else "to receive a one-time code",
                    placeholder = "Phone or ID number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid phone or ID number",
                )

                // emailOrPhoneNumberOrUserName
                setOf(LoginId.Type.Email, LoginId.Type.PhoneNumber, LoginId.Type.UserName) -> LoginIdCollectStrings(
                    title = if (isSystemFidoCapable) "Sign In with Fingerprint" else "Enter your email, phone or ID number",
                    message = if (isSystemFidoCapable) "Enter your email, phone or ID number" else "to receive a one-time code",
                    placeholder = "Email, phone or ID number",
                    cancel = "Cancel",
                    cta = "Continue",
                    error = "Enter a valid email, phone or ID number",
                )

                else -> LoginIdCollectStrings(
                    title = "",
                    message = "",
                    placeholder = "",
                    cancel = "",
                    cta = "",
                    error = "",
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoginIdCollectStrings) return false

        if (title != other.title) return false
        if (message != other.message) return false
        if (placeholder != other.placeholder) return false
        if (cancel != other.cancel) return false
        if (cta != other.cta) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + placeholder.hashCode()
        result = 31 * result + cancel.hashCode()
        result = 31 * result + cta.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }
}
