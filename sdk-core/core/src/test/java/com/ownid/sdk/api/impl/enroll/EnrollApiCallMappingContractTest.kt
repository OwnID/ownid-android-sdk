package com.ownid.sdk.api.impl.enroll

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.EmailEnrollApiFailure
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.api.PhoneEnrollApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertJsonStringsExactly
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: WIRE-170, WIRE-180
internal class EnrollApiCallMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun emailCreateBuildsEnrollRequestAndMapsNoContent() {
        val call = EmailEnrollApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            proofToken = ProofToken("email-proof-token"),
            accessToken = AccessToken("access-token"),
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/verifications/email/enroll",
            authorization = "Bearer access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly("proofToken" to "email-proof-token")

        call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 204, body = "")).assertUnitSuccess()
    }

    @Test
    fun phoneCreateBuildsEnrollRequestAndMapsNoContent() {
        val call = PhoneEnrollApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            proofToken = ProofToken("phone-proof-token"),
            accessToken = null,
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/verifications/phone/enroll",
            authorization = null,
            traceParent = null
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly("proofToken" to "phone-proof-token")

        call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 204, body = "")).assertUnitSuccess()
    }

    @Test
    fun passkeyCreateBuildsAttestationEnrollRequestAndMapsNoContent() {
        val call = PasskeyEnrollApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            proofToken = ProofToken("passkey-proof-token"),
            accessToken = AccessToken("access-token"),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/attestation/enroll",
            authorization = "Bearer access-token",
            traceParent = null
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly("proofToken" to "passkey-proof-token")

        call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 204, body = "")).assertUnitSuccess()
    }

    @Test
    fun mapHttpErrorMapsInvalidArgumentForEnrollApis() {
        val body = """
            {
              "errorCode": "invalid_argument",
              "message": "proof token is required"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        email as EmailEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(email.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        phone as PhoneEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(phone.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        passkey as PasskeyEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(passkey.message).isEqualTo("proof token is required")
    }

    @Test
    fun mapHttpErrorMapsForbiddenForEnrollApis() {
        val body = """
            {
              "errorCode": "forbidden",
              "message": "enrollment is not allowed"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 403, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.Forbidden::class.java)
        email as EmailEnrollApiFailure.Forbidden
        assertThat(email.errorCode).isEqualTo(ErrorCode.Forbidden)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 403, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.Forbidden::class.java)
        phone as PhoneEnrollApiFailure.Forbidden
        assertThat(phone.errorCode).isEqualTo(ErrorCode.Forbidden)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 403, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.Forbidden::class.java)
        passkey as PasskeyEnrollApiFailure.Forbidden
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(passkey.message).isEqualTo("enrollment is not allowed")
    }

    @Test
    fun mapHttpErrorMapsUserNotFoundForEnrollApis() {
        val body = """
            {
              "errorCode": "user_not_found",
              "message": "account does not exist"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 404, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.UserNotFound::class.java)
        email as EmailEnrollApiFailure.UserNotFound
        assertThat(email.errorCode).isEqualTo(ErrorCode.UserNotFound)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 404, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.UserNotFound::class.java)
        phone as PhoneEnrollApiFailure.UserNotFound
        assertThat(phone.errorCode).isEqualTo(ErrorCode.UserNotFound)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 404, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.UserNotFound::class.java)
        passkey as PasskeyEnrollApiFailure.UserNotFound
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.UserNotFound)
        assertThat(passkey.message).isEqualTo("account does not exist")
    }

    @Test
    fun mapHttpErrorMapsFailedDependencyProviderFailureForEnrollApis() {
        val body = """
            {
              "errorCode": "integration_error",
              "message": "provider failed",
              "scope": "data"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.FailedDependency.ProviderFailed::class.java)
        email as EmailEnrollApiFailure.FailedDependency.ProviderFailed
        assertThat(email.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(email.scope).isEqualTo(ApiFailureScope.DATA)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.FailedDependency.ProviderFailed::class.java)
        phone as PhoneEnrollApiFailure.FailedDependency.ProviderFailed
        assertThat(phone.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(phone.scope).isEqualTo(ApiFailureScope.DATA)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.FailedDependency.ProviderFailed::class.java)
        passkey as PasskeyEnrollApiFailure.FailedDependency.ProviderFailed
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(passkey.message).isEqualTo("provider failed")
        assertThat(passkey.scope).isEqualTo(ApiFailureScope.DATA)
    }

    @Test
    fun mapHttpErrorMapsFailedDependencyMissingProviderForEnrollApis() {
        val body = """
            {
              "errorCode": "missing_capability_provider",
              "message": "provider is not configured",
              "capability": "account",
              "scope": "session"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.FailedDependency.MissingProvider::class.java)
        email as EmailEnrollApiFailure.FailedDependency.MissingProvider
        assertThat(email.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(email.capability).isEqualTo("account")
        assertThat(email.scope).isEqualTo(ApiFailureScope.SESSION)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.FailedDependency.MissingProvider::class.java)
        phone as PhoneEnrollApiFailure.FailedDependency.MissingProvider
        assertThat(phone.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(phone.capability).isEqualTo("account")
        assertThat(phone.scope).isEqualTo(ApiFailureScope.SESSION)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 424, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.FailedDependency.MissingProvider::class.java)
        passkey as PasskeyEnrollApiFailure.FailedDependency.MissingProvider
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(passkey.message).isEqualTo("provider is not configured")
        assertThat(passkey.capability).isEqualTo("account")
        assertThat(passkey.scope).isEqualTo(ApiFailureScope.SESSION)
    }

    @Test
    fun mapHttpErrorMapsUnknownBadRequestForEnrollApis() {
        val body = """
            {
              "errorCode": "unknown",
              "message": "server could not classify enroll error"
            }
        """.trimIndent()

        val email = emailCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.BadRequest.Unknown::class.java)
        assertThat(email.errorCode).isEqualTo(ErrorCode.Unknown)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.BadRequest.Unknown::class.java)
        assertThat(phone.errorCode).isEqualTo(ErrorCode.Unknown)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 400, body = body))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.BadRequest.Unknown::class.java)
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(passkey.message).isEqualTo("server could not classify enroll error")
    }

    @Test
    fun mapHttpErrorMapsBlankForbiddenFallbackForEnrollApis() {
        val email = emailCall().mapHttpError(httpError(statusCode = 403, body = ""))
        assertThat(email).isInstanceOf(EmailEnrollApiFailure.Forbidden::class.java)
        assertThat(email.errorCode).isEqualTo(ErrorCode.Forbidden)

        val phone = phoneCall().mapHttpError(httpError(statusCode = 403, body = "   "))
        assertThat(phone).isInstanceOf(PhoneEnrollApiFailure.Forbidden::class.java)
        assertThat(phone.errorCode).isEqualTo(ErrorCode.Forbidden)

        val passkey = passkeyCall().mapHttpError(httpError(statusCode = 403, body = "\n"))
        assertThat(passkey).isInstanceOf(PasskeyEnrollApiFailure.Forbidden::class.java)
        assertThat(passkey.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(passkey.message).isEqualTo("HttpError(statusCode=403)")
    }

    @Test
    fun mapHttpErrorMapsMalformedAndUnhandledEnrollResponsesToUnexpected() {
        val malformedBody = """{"message":"missing discriminator"}"""

        emailCall().mapHttpError(httpError(statusCode = 400, body = malformedBody)).assertUnexpected(statusCode = 400)
        phoneCall().mapHttpError(httpError(statusCode = 400, body = malformedBody)).assertUnexpected(statusCode = 400)
        passkeyCall().mapHttpError(httpError(statusCode = 400, body = malformedBody)).assertUnexpected(statusCode = 400)

        emailCall().mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}""")).assertUnexpected(statusCode = 500)
        phoneCall().mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}""")).assertUnexpected(statusCode = 500)
        passkeyCall().mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}""")).assertUnexpected(statusCode = 500)
    }

    private fun emailCall(): EmailEnrollApiCall =
        EmailEnrollApiCall(serialization, Request.Builder().url("https://auth.example.test/api/verifications/email/enroll").build())

    private fun phoneCall(): PhoneEnrollApiCall =
        PhoneEnrollApiCall(serialization, Request.Builder().url("https://auth.example.test/api/verifications/phone/enroll").build())

    private fun passkeyCall(): PasskeyEnrollApiCall =
        PasskeyEnrollApiCall(serialization, Request.Builder().url("https://auth.example.test/api/passkeys/attestation/enroll").build())

    private fun httpError(statusCode: Int, body: String): Network.Response.Fail.HttpError =
        apiHttpError(baseUrl, statusCode, body)

    private fun ApiFailure.assertUnexpected(statusCode: Int) {
        assertThat(this).isInstanceOf(ApiUnexpectedFailure::class.java)
        assertUnexpectedApiContractFailure(this as ApiUnexpectedFailure, statusCode)
    }
}
