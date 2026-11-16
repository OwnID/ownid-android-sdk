package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdConfigurationProvider
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
                supportedTypes = listOf(LoginId.Type.Email),
                validationRegexes = mapOf(LoginId.Type.Email to "^[^@]+@ownid\\.test$".toRegex())
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
    fun defaultPhoneRuleClassifiesAndValidatesOnlyAsciiSevenToFifteenDigitValues() {
        val provider = LoginIdConfigurationProviderImpl().apply {
            setConfiguration(
                supportedTypes = listOf(LoginId.Type.PhoneNumber)
            )
        }
        val validator = LoginIdValidatorImpl(provider)

        listOf(
            "1234567",
            "+1234567",
            "1000000",
            "123456789012345",
            "+123456789012345"
        ).forEach { rawLoginId ->
            val loginId = LoginId(rawLoginId, LoginId.Type.PhoneNumber)

            assertThat(validator.determineLoginIdType(rawLoginId)).isEqualTo(LoginId.Type.PhoneNumber)
            assertThat(validator.validate(loginId)).isSameInstanceAs(loginId)
        }

        listOf(
            "123456",
            "+123456",
            "1234567890123456",
            "+1234567890123456",
            "0123456",
            "+0123456",
            "١٢٣٤٥٦٧",
            "1٢34567",
            "１２３４５６７",
            "＋1234567",
            " 1234567",
            "1234567 ",
            "123-4567",
            "1234567\n"
        ).forEach { rawLoginId ->
            val loginId = LoginId(rawLoginId, LoginId.Type.PhoneNumber)

            assertThrows(LoginIdValidationException.TypeNotSupported::class.java) {
                validator.determineLoginIdType(rawLoginId)
            }
            val validationFailure = assertThrows(LoginIdValidationException.ValidationFailed::class.java) {
                validator.validate(loginId)
            }
            assertThat(validationFailure.loginId).isSameInstanceAs(loginId)
            assertThat(validationFailure.regex).isEqualTo("^\\+?[1-9][0-9]{6,14}$")
        }
    }

    @Test
    fun providerNormalizesSupportedTypesAndIgnoresEmptyUpdates() {
        val provider: LoginIdConfigurationProvider = LoginIdConfigurationProviderImpl()
        provider.setServerConfiguration(
            supportedTypes = listOf(LoginId.Type.UserName, LoginId.Type.Email, LoginId.Type.UserName),
            validationRegexes = mapOf(
                LoginId.Type.UserName to "user-[0-9]+".toRegex(),
                LoginId.Type.Email to null,
                LoginId.Type.PhoneNumber to "ext-[0-9]+".toRegex()
            )
        )
        provider.setServerConfiguration(emptyList())
        provider.setConfiguration(emptyList())

        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.UserName, LoginId.Type.Email).inOrder()
        assertThat(provider.configuration.validationRegexes.keys).containsExactly(LoginId.Type.UserName, LoginId.Type.Email)
        assertThat(provider.configuration.validationRegexes[LoginId.Type.UserName]?.pattern).isEqualTo("user-[0-9]+")
    }

    @Test
    fun manualConfigurationStaysActiveAcrossServerUpdatesAndClearUsesLatestServer() {
        val provider = LoginIdConfigurationProviderImpl()
        val validator = LoginIdValidatorImpl(provider)
        val manual = LoginIdConfiguration(
            supportedTypes = listOf(LoginId.Type.PhoneNumber),
            validationRegexes = mapOf(LoginId.Type.PhoneNumber to "ext-[0-9]+".toRegex())
        )
        val manualLoginId = LoginId("ext-123", LoginId.Type.PhoneNumber)
        assertThat(validator.determineLoginIdType("person@example.test")).isEqualTo(LoginId.Type.Email)

        provider.setConfiguration(manual.supportedTypes, manual.validationRegexes)
        provider.setServerConfiguration(listOf(LoginId.Type.Email))
        assertThat(validator.determineLoginIdType("1234567")).isEqualTo(LoginId.Type.PhoneNumber)
        assertThat(validator.validate(manualLoginId)).isSameInstanceAs(manualLoginId)

        provider.setServerConfiguration(listOf(LoginId.Type.UserName), mapOf(LoginId.Type.UserName to "[0-9]{10}".toRegex()))
        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.PhoneNumber)
        assertThat(validator.determineLoginIdType("1234567")).isEqualTo(LoginId.Type.PhoneNumber)
        assertThat(validator.validate(manualLoginId)).isSameInstanceAs(manualLoginId)
        assertThrows(LoginIdValidationException.TypeNotSupported::class.java) { validator.determineLoginIdType("0123456789") }

        provider.clearConfiguration()
        assertThat(provider.configuration.supportedTypes).containsExactly(LoginId.Type.UserName)
        assertThat(validator.determineLoginIdType("0123456789")).isEqualTo(LoginId.Type.UserName)
        assertThat(validator.determineLoginIdType("ext-123")).isEqualTo(LoginId.Type.UserName)
        assertThrows(LoginIdValidationException.ValidationFailed::class.java) { validator.validate(LoginId("ext-123", LoginId.Type.UserName)) }

        provider.setConfiguration(manual.supportedTypes, manual.validationRegexes)
        assertThat(validator.determineLoginIdType("1234567")).isEqualTo(LoginId.Type.PhoneNumber)
        assertThat(validator.validate(manualLoginId)).isSameInstanceAs(manualLoginId)
        provider.clearConfiguration()
        assertThat(validator.determineLoginIdType("0123456789")).isEqualTo(LoginId.Type.UserName)
    }

    @Test
    fun configuredRulesApplyToTypedValidationWithoutChangingInference() {
        val provider = LoginIdConfigurationProviderImpl().apply {
            setConfiguration(
                supportedTypes = listOf(LoginId.Type.Email, LoginId.Type.UserName),
                validationRegexes = mapOf(
                    LoginId.Type.Email to "email:[A-Z]+".toRegex(),
                    LoginId.Type.UserName to "[0-9]{10}".toRegex()
                )
            )
        }
        val validator = LoginIdValidatorImpl(provider)
        listOf(
            LoginId("email:ADA", LoginId.Type.Email),
            LoginId("0123456789", LoginId.Type.UserName)
        ).forEach { loginId ->
            assertThat(validator.validate(loginId)).isSameInstanceAs(loginId)
        }
        listOf(
            LoginId("person@example.test", LoginId.Type.Email),
            LoginId("member", LoginId.Type.UserName),
            LoginId("123456789", LoginId.Type.UserName),
            LoginId("12345678901", LoginId.Type.UserName),
            LoginId("x0123456789", LoginId.Type.UserName),
            LoginId("0123456789\n", LoginId.Type.UserName),
            LoginId("", LoginId.Type.UserName),
            LoginId(" ", LoginId.Type.UserName)
        ).forEach { loginId ->
            assertThrows(LoginIdValidationException.ValidationFailed::class.java) { validator.validate(loginId) }
        }
        assertThat(validator.determineLoginIdType("person@example.test")).isEqualTo(LoginId.Type.Email)
        assertThat(validator.determineLoginIdType("email:ADA")).isEqualTo(LoginId.Type.UserName)
        assertThat(validator.determineLoginIdType("member")).isEqualTo(LoginId.Type.UserName)
        assertThrows(LoginIdValidationException.TypeNotSupported::class.java) { validator.determineLoginIdType(" ") }
    }

    @Test
    fun embeddedClassifiersUseSupportedOrderIndependentlyOfConfiguredRules() {
        val provider = LoginIdConfigurationProviderImpl()
        val validator = LoginIdValidatorImpl(provider)
        val regexes = mapOf(
            LoginId.Type.Email to "member-[0-9]+".toRegex(),
            LoginId.Type.UserName to "member-[0-9]+".toRegex()
        )
        listOf(
            listOf(LoginId.Type.Email, LoginId.Type.UserName),
            listOf(LoginId.Type.UserName, LoginId.Type.Email)
        ).forEach { types ->
            provider.setConfiguration(types, regexes)
            assertThat(validator.determineLoginIdType("person@example.test")).isEqualTo(types.first())
            assertThat(validator.determineLoginIdType("member-123")).isEqualTo(LoginId.Type.UserName)
        }
    }

    @Test
    fun omittedAndNullManualRegexesUseEmbeddedRulesInsteadOfServerRules() {
        val provider = LoginIdConfigurationProviderImpl().apply {
            setServerConfiguration(listOf(LoginId.Type.PhoneNumber), mapOf(LoginId.Type.PhoneNumber to "ext-[0-9]+".toRegex()))
        }
        val validator = LoginIdValidatorImpl(provider)
        listOf(
            LoginIdConfiguration(supportedTypes = listOf(LoginId.Type.PhoneNumber)),
            LoginIdConfiguration(listOf(LoginId.Type.PhoneNumber), mapOf(LoginId.Type.PhoneNumber to null))
        ).forEach { configuration ->
            provider.setConfiguration(configuration.supportedTypes, configuration.validationRegexes)
            val embeddedPhone = LoginId("1234567", LoginId.Type.PhoneNumber)
            assertThat(validator.determineLoginIdType(embeddedPhone.id)).isEqualTo(embeddedPhone.type)
            assertThat(validator.validate(embeddedPhone)).isSameInstanceAs(embeddedPhone)
            assertThrows(LoginIdValidationException.TypeNotSupported::class.java) { validator.determineLoginIdType("ext-123") }
        }
    }
}
