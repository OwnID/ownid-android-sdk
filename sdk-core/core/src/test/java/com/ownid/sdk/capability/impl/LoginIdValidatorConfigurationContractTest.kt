package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdConfiguration
import org.junit.Assert.assertThrows
import org.junit.Test

// Covers: MODEL-020, MODEL-030
internal class LoginIdValidatorConfigurationContractTest {

    @Test
    fun validatorReportsTypedFailuresAndPreservesRejectedLoginIdOnlyForValidationFailure() {
        val provider = LoginIdConfigurationProviderImpl().apply {
            setConfiguration(
                LoginIdConfiguration(
                    supportedTypes = listOf(LoginId.Type.Email),
                    validationRegexes = mapOf(LoginId.Type.Email to "^[^@]+@ownid\\.test$".toRegex())
                )
            )
        }
        val validator = LoginIdValidatorImpl(provider)
        val rejectedLoginId = LoginId("person@example.test", LoginId.Type.Email)

        val validationFailed = assertThrows(LoginIdValidationException.ValidationFailed::class.java) {
            validator.validate(rejectedLoginId)
        }
        val typeNotSupported = assertThrows(LoginIdValidationException.TypeNotSupported::class.java) {
            validator.validate(LoginId("+15551234567", LoginId.Type.PhoneNumber))
        }
        val rawTypeNotSupported = assertThrows(LoginIdValidationException.TypeNotSupported::class.java) {
            validator.determineLoginIdType("not-an-email")
        }

        assertThat(validationFailed.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(validationFailed.loginId).isSameInstanceAs(rejectedLoginId)
        assertThat(validationFailed.regex).isEqualTo("^[^@]+@ownid\\.test$")
        assertThat(typeNotSupported.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(rawTypeNotSupported.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
    }

    @Test
    fun providerNormalizesSupportedTypesAndValidatorUsesConfiguredPriorityAndRegexes() {
        val provider = LoginIdConfigurationProviderImpl()
        provider.setServerConfiguration(
            LoginIdConfiguration(
                supportedTypes = listOf(LoginId.Type.UserName, LoginId.Type.Email, LoginId.Type.UserName),
                validationRegexes = mapOf(
                    LoginId.Type.UserName to "^user-[0-9]+$".toRegex(),
                    LoginId.Type.Email to null,
                    LoginId.Type.PhoneNumber to "^\\+1[0-9]+$".toRegex()
                )
            )
        )
        val validator = LoginIdValidatorImpl(provider)

        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.UserName, LoginId.Type.Email).inOrder()
        assertThat(provider.configuration.validationRegexes.keys).containsExactly(LoginId.Type.UserName, LoginId.Type.Email)
        assertThat(validator.determineLoginIdType("person@example.test")).isEqualTo(LoginId.Type.UserName)
        assertThat(validator.validate(LoginId("user-123", LoginId.Type.UserName))).isEqualTo(LoginId("user-123", LoginId.Type.UserName))
        assertThat(validator.validate(LoginId("person@example.test", LoginId.Type.Email))).isEqualTo(LoginId("person@example.test", LoginId.Type.Email))

        assertThrows(LoginIdValidationException.ValidationFailed::class.java) {
            validator.validate(LoginId("person@example.test", LoginId.Type.UserName))
        }
    }

    @Test
    fun appConfigurationOverrideTakesPriorityUntilCleared() {
        val provider = LoginIdConfigurationProviderImpl()
        provider.setServerConfiguration(
            LoginIdConfiguration(
                supportedTypes = listOf(LoginId.Type.Email),
                validationRegexes = mapOf(LoginId.Type.Email to null)
            )
        )
        provider.setConfiguration(
            LoginIdConfiguration(
                supportedTypes = listOf(LoginId.Type.PhoneNumber),
                validationRegexes = mapOf(LoginId.Type.PhoneNumber to "^\\+1555[0-9]+$".toRegex())
            )
        )
        val validator = LoginIdValidatorImpl(provider)

        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.PhoneNumber)
        assertThat(validator.determineLoginIdType("+15551234567")).isEqualTo(LoginId.Type.PhoneNumber)
        assertThrows(LoginIdValidationException.TypeNotSupported::class.java) {
            validator.determineLoginIdType("person@example.test")
        }

        provider.clearConfiguration()

        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.Email)
        assertThat(validator.determineLoginIdType("person@example.test")).isEqualTo(LoginId.Type.Email)
        assertThrows(LoginIdValidationException.TypeNotSupported::class.java) {
            validator.determineLoginIdType("+15551234567")
        }
    }
}
