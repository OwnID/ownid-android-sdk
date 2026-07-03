package com.ownid.sdk.capability

import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId

/**
 * Resolves and validates login IDs according to the current OwnID application configuration.
 *
 * Use [determineLoginIdType] when your app has a raw login ID string and needs the SDK to choose the first supported
 * [LoginId.Type] whose classification rule matches that value.
 *
 * Use [validate] when your app already has a typed [LoginId] and needs to check that the type is supported and the
 * value is valid for that type.
 *
 * Login ID values are user identifiers. Validation failures may carry the rejected value for diagnostics, so avoid
 * forwarding failure descriptions to logs or analytics unless your app already treats that data as sensitive.
 */
public interface LoginIdValidator : Capability {
    /**
     * Determines the [LoginId.Type] for a raw login ID string.
     *
     * The resolved type is limited to the login-ID types supported by the current OwnID application configuration. The
     * configured type order defines the match priority.
     *
     * @return The supported type resolved for [loginId].
     * @throws LoginIdValidationException.TypeNotSupported when the SDK cannot resolve [loginId] to any supported type.
     */
    @Throws(LoginIdValidationException::class)
    public fun determineLoginIdType(loginId: String): LoginId.Type

    /**
     * Validates a typed [LoginId].
     *
     * Use this when your app already knows the login-ID type. Validation uses the configured regex for the declared
     * type when present, or that type's default classification rule otherwise.
     *
     * @return The validated [LoginId].
     * @throws LoginIdValidationException.TypeNotSupported when [loginId] has a type that is not supported by the current OwnID application configuration.
     * @throws LoginIdValidationException.ValidationFailed when [loginId] is supported but its value is invalid for the declared type.
     */
    @Throws(LoginIdValidationException::class)
    public fun validate(loginId: LoginId): LoginId
}

/**
 * Login ID classification or validation failure.
 *
 * [ValidationFailed] includes the rejected [LoginId] and regex. Treat this payload as sensitive diagnostic data.
 */
public sealed class LoginIdValidationException(public val errorCode: ErrorCode, override val message: String) : IllegalArgumentException(message) {
    /**
     * The login ID type is not supported by the current OwnID application configuration.
     *
     * This exception carries only [LoginIdValidationException.errorCode] and the diagnostic message.
     */
    public class TypeNotSupported(message: String) :
        LoginIdValidationException(ErrorCode.LoginIdTypeNotSupported, message) {
        override fun toString(): String = "LoginIdValidationException.TypeNotSupported(errorCode=$errorCode, message=$message)"
    }

    /** The login ID value does not match validation rules for its type. */
    public class ValidationFailed(message: String, public val loginId: LoginId, public val regex: String) :
        LoginIdValidationException(ErrorCode.LoginIdValidationFailed, message) {
        override fun toString(): String = "LoginIdValidationException.ValidationFailed(errorCode=$errorCode, message=$message, loginId=$loginId, regex=$regex)"
    }
}

/**
 * Resolves a raw [loginId] into a typed [LoginId] by inferring its [LoginId.Type].
 *
 * This helper delegates to [LoginIdValidator.determineLoginIdType] and, on success, returns a new [LoginId]
 * that preserves the original raw value and appends the inferred type.
 *
 * [LoginIdValidationException] from [LoginIdValidator.determineLoginIdType] is propagated unchanged.
 */
@Throws(LoginIdValidationException::class)
internal fun LoginIdValidator.appendWithType(loginId: String): LoginId = LoginId(loginId, determineLoginIdType(loginId))
