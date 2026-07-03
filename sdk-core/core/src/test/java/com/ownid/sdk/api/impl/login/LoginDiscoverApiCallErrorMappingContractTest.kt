package com.ownid.sdk.api.impl.login

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: WIRE-030, WIRE-040, WIRE-050
internal class LoginDiscoverApiCallErrorMappingContractTest {

    private val serialization = SerializationImpl()
    private val url = "https://example.com/api/login".toHttpUrl()
    private val request = Request.Builder().url(url).build()
    private val loginCall = LoginApiCall(serialization, request)
    private val discoverCall = DiscoverApiCall(serialization, request)

    @Test
    fun mapHttpErrorMapsBadLoginIdRequestVariantsForLoginAndDiscover() {
        val invalidArgumentBody = """
            {
              "errorCode": "invalid_argument",
              "message": "login id is required",
              "loginId": { "id": "ignored@example.com", "type": "Email" },
              "regex": "ignored"
            }
        """.trimIndent()

        val loginInvalidArgument = loginCall.mapHttpError(httpError(statusCode = 400, body = invalidArgumentBody))
        assertThat(loginInvalidArgument).isInstanceOf(LoginApiFailure.BadRequest.InvalidArgument::class.java)
        loginInvalidArgument as LoginApiFailure.BadRequest.InvalidArgument
        assertThat(loginInvalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val discoverInvalidArgument = discoverCall.mapHttpError(httpError(statusCode = 400, body = invalidArgumentBody))
        assertThat(discoverInvalidArgument).isInstanceOf(DiscoverApiFailure.BadRequest.InvalidArgument::class.java)
        discoverInvalidArgument as DiscoverApiFailure.BadRequest.InvalidArgument
        assertThat(discoverInvalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val validationBody = """
            {
              "errorCode": "login_id_validation_failed",
              "message": "login id does not match configured regex",
              "loginId": { "id": "bad@example", "type": "Email" },
              "regex": ".+@example\\.com"
            }
        """.trimIndent()

        val loginValidation = loginCall.mapHttpError(httpError(statusCode = 400, body = validationBody))
        assertThat(loginValidation).isInstanceOf(LoginApiFailure.BadRequest.InvalidLoginId::class.java)
        loginValidation as LoginApiFailure.BadRequest.InvalidLoginId
        assertThat(loginValidation.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(loginValidation.loginId).isEqualTo(LoginId("bad@example", LoginId.Type.Email))
        assertThat(loginValidation.regex).isEqualTo(".+@example\\.com")

        val discoverValidation = discoverCall.mapHttpError(httpError(statusCode = 400, body = validationBody))
        assertThat(discoverValidation).isInstanceOf(DiscoverApiFailure.BadRequest.InvalidLoginId::class.java)
        discoverValidation as DiscoverApiFailure.BadRequest.InvalidLoginId
        assertThat(discoverValidation.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(discoverValidation.loginId).isEqualTo(LoginId("bad@example", LoginId.Type.Email))
        assertThat(discoverValidation.regex).isEqualTo(".+@example\\.com")

        val unsupportedTypeBody = """
            {
              "errorCode": "login_id_type_not_supported",
              "message": "login id type is not supported"
            }
        """.trimIndent()

        val loginUnsupportedType = loginCall.mapHttpError(httpError(statusCode = 400, body = unsupportedTypeBody))
        assertThat(loginUnsupportedType).isInstanceOf(LoginApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        loginUnsupportedType as LoginApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(loginUnsupportedType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)

        val discoverUnsupportedType = discoverCall.mapHttpError(httpError(statusCode = 400, body = unsupportedTypeBody))
        assertThat(discoverUnsupportedType).isInstanceOf(DiscoverApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        discoverUnsupportedType as DiscoverApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(discoverUnsupportedType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)

        val unknownBody = """
            {
              "errorCode": "unknown",
              "message": "server could not classify login error"
            }
        """.trimIndent()

        val loginUnknown = loginCall.mapHttpError(httpError(statusCode = 400, body = unknownBody))
        assertThat(loginUnknown).isInstanceOf(LoginApiFailure.BadRequest.Unknown::class.java)
        loginUnknown as LoginApiFailure.BadRequest.Unknown
        assertThat(loginUnknown.errorCode).isEqualTo(ErrorCode.Unknown)

        val discoverUnknown = discoverCall.mapHttpError(httpError(statusCode = 400, body = unknownBody))
        assertThat(discoverUnknown).isInstanceOf(DiscoverApiFailure.BadRequest.Unknown::class.java)
        discoverUnknown as DiscoverApiFailure.BadRequest.Unknown
        assertThat(discoverUnknown.errorCode).isEqualTo(ErrorCode.Unknown)
    }

    @Test
    fun mapHttpErrorMapsFailedDependencyVariantsForLoginAndDiscover() {
        val providerBody = """
            {
              "errorCode": "integration_error",
              "message": "provider failed",
              "scope": "session"
            }
        """.trimIndent()

        val loginProvider = loginCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(loginProvider).isInstanceOf(LoginApiFailure.FailedDependency.ProviderFailed::class.java)
        loginProvider as LoginApiFailure.FailedDependency.ProviderFailed
        assertThat(loginProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(loginProvider.scope).isEqualTo(ApiFailureScope.SESSION)

        val discoverProvider = discoverCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(discoverProvider).isInstanceOf(DiscoverApiFailure.FailedDependency.ProviderFailed::class.java)
        discoverProvider as DiscoverApiFailure.FailedDependency.ProviderFailed
        assertThat(discoverProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(discoverProvider.scope).isEqualTo(ApiFailureScope.SESSION)

        val missingProviderBody = """
            {
              "errorCode": "missing_capability_provider",
              "message": "provider is not configured",
              "capability": "session",
              "scope": "data"
            }
        """.trimIndent()

        val loginMissingProvider = loginCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(loginMissingProvider).isInstanceOf(LoginApiFailure.FailedDependency.MissingProvider::class.java)
        loginMissingProvider as LoginApiFailure.FailedDependency.MissingProvider
        assertThat(loginMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(loginMissingProvider.capability).isEqualTo("session")
        assertThat(loginMissingProvider.scope).isEqualTo(ApiFailureScope.DATA)

        val discoverMissingProvider = discoverCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(discoverMissingProvider).isInstanceOf(DiscoverApiFailure.FailedDependency.MissingProvider::class.java)
        discoverMissingProvider as DiscoverApiFailure.FailedDependency.MissingProvider
        assertThat(discoverMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(discoverMissingProvider.capability).isEqualTo("session")
        assertThat(discoverMissingProvider.scope).isEqualTo(ApiFailureScope.DATA)
    }

    @Test
    fun mapHttpErrorMapsForbiddenJsonAndBlankFallbackForLoginAndDiscover() {
        val body = """
            {
              "errorCode": "forbidden",
              "message": "login is not allowed"
            }
        """.trimIndent()

        val loginForbidden = loginCall.mapHttpError(httpError(statusCode = 403, body = body))
        assertThat(loginForbidden).isInstanceOf(LoginApiFailure.Forbidden::class.java)
        loginForbidden as LoginApiFailure.Forbidden
        assertThat(loginForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val discoverForbidden = discoverCall.mapHttpError(httpError(statusCode = 403, body = body))
        assertThat(discoverForbidden).isInstanceOf(DiscoverApiFailure.Forbidden::class.java)
        discoverForbidden as DiscoverApiFailure.Forbidden
        assertThat(discoverForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val loginBlankForbidden = loginCall.mapHttpError(httpError(statusCode = 403, body = ""))
        assertThat(loginBlankForbidden).isInstanceOf(LoginApiFailure.Forbidden::class.java)
        loginBlankForbidden as LoginApiFailure.Forbidden
        assertThat(loginBlankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val discoverBlankForbidden = discoverCall.mapHttpError(httpError(statusCode = 403, body = "   "))
        assertThat(discoverBlankForbidden).isInstanceOf(DiscoverApiFailure.Forbidden::class.java)
        discoverBlankForbidden as DiscoverApiFailure.Forbidden
        assertThat(discoverBlankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
    }

    @Test
    fun mapHttpErrorMapsContractMismatchesAndUnhandledStatusesToUnexpectedForLoginAndDiscover() {
        val futureBadRequestBody = """
            {
              "errorCode": "future_login_error",
              "message": "future server contract"
            }
        """.trimIndent()

        val loginFutureBadRequest = loginCall.mapHttpError(httpError(statusCode = 400, body = futureBadRequestBody))
        assertThat(loginFutureBadRequest).isInstanceOf(LoginApiFailure.Unexpected::class.java)
        assertThat(loginFutureBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertUnexpectedApiContractFailure(loginFutureBadRequest as ApiUnexpectedFailure, statusCode = 400)

        val discoverFutureBadRequest = discoverCall.mapHttpError(httpError(statusCode = 400, body = futureBadRequestBody))
        assertThat(discoverFutureBadRequest).isInstanceOf(DiscoverApiFailure.Unexpected::class.java)
        assertThat(discoverFutureBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertUnexpectedApiContractFailure(discoverFutureBadRequest as ApiUnexpectedFailure, statusCode = 400)

        val loginUnhandledStatus = loginCall.mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(loginUnhandledStatus).isInstanceOf(LoginApiFailure.Unexpected::class.java)
        assertThat(loginUnhandledStatus.errorCode).isEqualTo(ErrorCode.Unknown)
        assertUnexpectedApiContractFailure(loginUnhandledStatus as ApiUnexpectedFailure, statusCode = 500)

        val discoverUnhandledStatus = discoverCall.mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(discoverUnhandledStatus).isInstanceOf(DiscoverApiFailure.Unexpected::class.java)
        assertThat(discoverUnhandledStatus.errorCode).isEqualTo(ErrorCode.Unknown)
        assertUnexpectedApiContractFailure(discoverUnhandledStatus as ApiUnexpectedFailure, statusCode = 500)
    }

    private fun httpError(statusCode: Int, body: String): Network.Response.Fail.HttpError =
        apiHttpError(url, statusCode, body)
}
