package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.model.LoginId

/**
 * Configuration-backed [LoginIdValidator].
 *
 * This implementation reads the current login-ID configuration on each call, so callers observe server configuration
 * changes after they are applied by the app-config provider. Raw values are matched in configured type order, and typed
 * values must be supported before their validation rule is evaluated.
 */
internal class LoginIdValidatorImpl(
    private val loginIdConfigurationProvider: LoginIdConfigurationProvider
) : LoginIdValidator {

    @Throws(LoginIdValidationException::class)
    override fun determineLoginIdType(loginId: String): LoginId.Type {
        val config = loginIdConfigurationProvider.configuration

        for (type in config.supportedTypes) {
            if (type.classificationRegex.matches(loginId)) return type
        }

        throw LoginIdValidationException.TypeNotSupported("LoginIdValidator.determineLoginIdType: Login Id type not supported")
    }

    @Throws(LoginIdValidationException::class)
    override fun validate(loginId: LoginId): LoginId {
        val config = loginIdConfigurationProvider.configuration

        if (!config.supportedTypes.contains(loginId.type)) {
            throw LoginIdValidationException.TypeNotSupported("LoginIdValidator.validate: Login Id type not supported")
        }

        val validationRegex = config.validationRegexes[loginId.type] ?: loginId.type.classificationRegex

        if (validationRegex.matches(loginId.id)) {
            return loginId
        }

        throw LoginIdValidationException.ValidationFailed(
            message = "LoginIdValidator.validate: Login Id validation failed: $loginId",
            loginId = loginId,
            regex = validationRegex.pattern
        )
    }
}
