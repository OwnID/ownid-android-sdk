package com.ownid.sdk.api.impl.passkey

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.assertUnexpectedApiContractResponseFailure
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.TransportType
import com.ownid.sdk.model.UserVerification
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

// Covers: WIRE-060, WIRE-070
internal class AssertionApiCallMappingContractTest {

    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun createBuildsAssertionOptionsRequestWithLoginIdAccessTokenAndTraceParent() {
        val call = AssertionOptionsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            accessToken = AccessToken("access-token"),
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/assertion/options",
            authorization = "Bearer access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        val loginId = call.request.jsonBody().getValue("loginId").jsonObject
        assertThat(loginId.jsonString("id")).isEqualTo("user@example.com")
        assertThat(loginId.jsonString("type")).isEqualTo("Email")
    }

    @Test
    fun mapHttpSuccessMapsCreatedWebAuthnAssertionOptions() {
        val call = AssertionOptionsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            loginId = null,
            accessToken = null,
            traceParent = null
        )

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 201,
                body = """
                    {
                      "challenge": "Y2hhbGxlbmdl",
                      "rpId": "login.example.test",
                      "allowCredentials": [
                        {
                          "type": "public-key",
                          "id": "Y3JlZC0x",
                          "transports": ["internal", "hybrid"]
                        }
                      ],
                      "userVerification": "required",
                      "timeout": 45000
                    }
                """.trimIndent()
            )
        )

        val options: AssertionOptions = result.successValue()
        assertThat(options.challenge).isEqualTo(ChallengeId("Y2hhbGxlbmdl"))
        assertThat(options.rpId).isEqualTo("login.example.test")
        assertThatObject(options.userVerification).isEqualTo(UserVerification.REQUIRED)
        assertThat(options.timeout).isEqualTo(Timeout(45000))
        val credential = requireNotNull(options.allowCredentials).single()
        assertThat(credential.type).isEqualTo(CredentialType.PUBLIC_KEY)
        assertThat(credential.id).isEqualTo("Y3JlZC0x")
        assertThat(credential.transports).containsExactly(TransportType.INTERNAL, TransportType.HYBRID).inOrder()
    }

    @Test
    fun mapHttpSuccessAcceptsPaddedBase64UrlAssertionOptions() {
        val call = assertionOptionsCall()
        val challenge = paddedBase64UrlFixture("a")
        val credentialId = paddedBase64UrlFixture("credential")

        val result = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 201,
                body = """
                    {
                      "challenge": "$challenge",
                      "rpId": "login.example.test",
                      "allowCredentials": [
                        {
                          "type": "public-key",
                          "id": "$credentialId"
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

        val options = result.successValue()
        assertThat(options.challenge).isEqualTo(ChallengeId(challenge))
        assertThat(requireNotNull(options.allowCredentials).single().id).isEqualTo(credentialId)
    }

    @Test
    fun mapHttpSuccessMapsInvalidBase64UrlAssertionOptionsToUnexpected() {
        malformedAssertionOptionsBase64UrlCases().forEach { testCase ->
            val call = assertionOptionsCall()

            val failure = call.mapHttpSuccess(
                apiSuccess(
                    url = call.request.url,
                    code = 201,
                    body = testCase.body
                )
            ).failureValue()

            assertWithMessage("Malformed Base64url ${testCase.field} should map to unexpected")
                .that(failure)
                .isInstanceOf(PasskeyAssertionStartApiFailure.Unexpected::class.java)
            assertUnexpectedApiContractResponseFailure(failure as ApiUnexpectedFailure, statusCode = 201)
        }
    }

    @Test
    fun mapHttpErrorMapsForbiddenFailure() {
        val failure = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 403,
                body = """
                    {
                      "errorCode": "forbidden",
                      "message": "assertion is not allowed"
                    }
                """.trimIndent()
            )
        )

        assertThat(failure).isInstanceOf(PasskeyAssertionStartApiFailure.Forbidden::class.java)
        failure as PasskeyAssertionStartApiFailure.Forbidden
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(failure.message).isEqualTo("assertion is not allowed")

        val blankFailure = assertionOptionsCall().mapHttpError(apiHttpError(url = baseUrl, statusCode = 403, body = ""))
        assertThat(blankFailure).isInstanceOf(PasskeyAssertionStartApiFailure.Forbidden::class.java)
        assertThat(blankFailure.errorCode).isEqualTo(ErrorCode.Forbidden)
    }

    @Test
    fun mapHttpErrorMapsUserNotFoundFailure() {
        val failure = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 404,
                body = """
                    {
                      "errorCode": "user_not_found",
                      "message": "account does not exist"
                    }
                """.trimIndent()
            )
        )

        assertThat(failure).isInstanceOf(PasskeyAssertionStartApiFailure.UserNotFound::class.java)
        failure as PasskeyAssertionStartApiFailure.UserNotFound
        assertThat(failure.errorCode).isEqualTo(ErrorCode.UserNotFound)
        assertThat(failure.message).isEqualTo("account does not exist")
    }

    @Test
    fun mapHttpErrorMapsBadLoginIdRequestVariants() {
        val invalidArgument = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"login id is required"}"""
            )
        )
        assertThat(invalidArgument).isInstanceOf(PasskeyAssertionStartApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PasskeyAssertionStartApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("login id is required")

        val invalidLoginId = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """
                    {
                      "errorCode": "login_id_validation_failed",
                      "message": "login id does not match configured regex",
                      "loginId": { "id": "bad@example", "type": "Email" },
                      "regex": ".+@example\\.com"
                    }
                """.trimIndent()
            )
        )
        assertThat(invalidLoginId).isInstanceOf(PasskeyAssertionStartApiFailure.BadRequest.InvalidLoginId::class.java)
        invalidLoginId as PasskeyAssertionStartApiFailure.BadRequest.InvalidLoginId
        assertThat(invalidLoginId.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(invalidLoginId.message).isEqualTo("login id does not match configured regex")
        assertThat(invalidLoginId.loginId).isEqualTo(LoginId("bad@example", LoginId.Type.Email))
        assertThat(invalidLoginId.regex).isEqualTo(".+@example\\.com")

        val unsupportedType = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"login_id_type_not_supported","message":"login id type is not supported"}"""
            )
        )
        assertThat(unsupportedType).isInstanceOf(PasskeyAssertionStartApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        unsupportedType as PasskeyAssertionStartApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(unsupportedType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupportedType.message).isEqualTo("login id type is not supported")

        val unknown = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 400,
                body = """{"errorCode":"unknown","message":"server could not classify assertion error"}"""
            )
        )
        assertThat(unknown).isInstanceOf(PasskeyAssertionStartApiFailure.BadRequest.Unknown::class.java)
        unknown as PasskeyAssertionStartApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("server could not classify assertion error")
    }

    @Test
    fun mapHttpErrorMapsProviderFailedFailure() {
        val failure = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 424,
                body = """
                    {
                      "errorCode": "integration_error",
                      "message": "account provider failed",
                      "scope": "session"
                    }
                """.trimIndent()
            )
        )

        assertThat(failure).isInstanceOf(PasskeyAssertionStartApiFailure.FailedDependency.ProviderFailed::class.java)
        failure as PasskeyAssertionStartApiFailure.FailedDependency.ProviderFailed
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).isEqualTo("account provider failed")
        assertThat(failure.scope).isEqualTo(ApiFailureScope.SESSION)
    }

    @Test
    fun mapHttpErrorMapsMissingProviderFailure() {
        val failure = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 424,
                body = """
                    {
                      "errorCode": "missing_capability_provider",
                      "message": "session provider is not configured",
                      "capability": "session",
                      "scope": "data"
                    }
                """.trimIndent()
            )
        )

        assertThat(failure).isInstanceOf(PasskeyAssertionStartApiFailure.FailedDependency.MissingProvider::class.java)
        failure as PasskeyAssertionStartApiFailure.FailedDependency.MissingProvider
        assertThat(failure.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(failure.message).isEqualTo("session provider is not configured")
        assertThat(failure.capability).isEqualTo("session")
        assertThat(failure.scope).isEqualTo(ApiFailureScope.DATA)
    }

    @Test
    fun mapHttpErrorMapsMaximumChallengesReachedFailure() {
        val failure = assertionOptionsCall().mapHttpError(
            apiHttpError(
                url = baseUrl,
                statusCode = 429,
                body = """
                    {
                      "errorCode": "maximum_challenges_reached",
                      "message": "challenge limit reached"
                    }
                """.trimIndent()
            )
        )

        assertThat(failure).isInstanceOf(PasskeyAssertionStartApiFailure.MaximumChallengesReached::class.java)
        failure as PasskeyAssertionStartApiFailure.MaximumChallengesReached
        assertThat(failure.errorCode).isEqualTo(ErrorCode.MaximumChallengesReached)
        assertThat(failure.message).isEqualTo("challenge limit reached")
    }

    @Test
    fun assertionResultCreateBuildsVerifyRequestAndMapsAccessToken() {
        val call = AssertionResultApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            assertionResult = assertionResult(authenticatorAttachment = null),
            accessToken = AccessToken("access-token"),
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/assertion/result",
            authorization = "Bearer access-token",
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        val body = call.request.jsonBody()
        assertThat(body.jsonString("id")).isEqualTo("credential-id")
        assertThat(body.jsonString("type")).isEqualTo("public-key")
        assertThat(body.jsonString("authenticatorAttachment")).isEqualTo("platform")
        val response = body.getValue("response").jsonObject
        assertThat(response.jsonString("clientDataJSON")).isEqualTo("client-data-json")
        assertThat(response.jsonString("authenticatorData")).isEqualTo("authenticator-data")
        assertThat(response.jsonString("userHandle")).isEqualTo("user-handle")
        assertThat(response.jsonString("signature")).isEqualTo("signature")

        assertThat(
            call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 200, body = """{"accessToken":"ownid-access-token"}""")).successValue()
        )
            .isEqualTo(AccessToken("ownid-access-token"))
    }

    @Test
    fun assertionResultMapsBadChallengeAndForbiddenErrors() {
        val call = assertionResultCall()

        val invalidChallenge = call.mapHttpError(
            apiHttpError(
                url = call.request.url,
                statusCode = 400,
                body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"challenge-123"}"""
            )
        )
        assertThat(invalidChallenge).isInstanceOf(PasskeyAssertionVerifyApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PasskeyAssertionVerifyApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("challenge-123"))

        val forbidden = call.mapHttpError(
            apiHttpError(
                url = call.request.url,
                statusCode = 403,
                body = """{"errorCode":"forbidden","message":"assertion verification is not allowed"}"""
            )
        )
        assertThat(forbidden).isInstanceOf(PasskeyAssertionVerifyApiFailure.Forbidden::class.java)
        forbidden as PasskeyAssertionVerifyApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("assertion verification is not allowed")

        val blankForbidden = call.mapHttpError(apiHttpError(url = call.request.url, statusCode = 403, body = "  "))
        assertThat(blankForbidden).isInstanceOf(PasskeyAssertionVerifyApiFailure.Forbidden::class.java)
        assertThat(blankForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val malformed = call.mapHttpError(apiHttpError(url = call.request.url, statusCode = 400, body = """{"message":"missing discriminator"}"""))
        assertThat(malformed).isInstanceOf(PasskeyAssertionVerifyApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(malformed as ApiUnexpectedFailure, statusCode = 400)
    }

    @Test
    fun assertionCancelCreateBuildsCancelRequestAndMapsNoContent() {
        val call = AssertionCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            challenge = ChallengeId("assertion-challenge-1"),
            reason = Reason.MoveToOtherChallenge(),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/passkeys/assertion/cancel",
            authorization = null,
            traceParent = null
        )
        call.request.jsonBody().let { body ->
            assertThat(body.jsonString("challenge")).isEqualTo("assertion-challenge-1")
            assertThat(body.jsonString("reason")).isEqualTo("moveToOtherChallenge")
        }

        call.mapHttpSuccess(apiSuccess(url = call.request.url, code = 204, body = "")).assertUnitSuccess()
    }

    @Test
    fun assertionCancelMapsBadChallengeAndUnexpectedErrors() {
        val call = assertionCancelCall()

        val maximumAttempts = call.mapHttpError(
            apiHttpError(
                url = call.request.url,
                statusCode = 400,
                body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"challenge-123"}"""
            )
        )
        assertThat(maximumAttempts).isInstanceOf(PasskeyAssertionCancelApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttempts as PasskeyAssertionCancelApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttempts.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttempts.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttempts.challengeId).isEqualTo(ChallengeId("challenge-123"))

        val unhandledStatus = call.mapHttpError(apiHttpError(url = call.request.url, statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(unhandledStatus).isInstanceOf(PasskeyAssertionCancelApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(unhandledStatus as ApiUnexpectedFailure, statusCode = 500)
    }

    private fun assertionOptionsCall(): AssertionOptionsApiCall =
        AssertionOptionsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            loginId = null,
            accessToken = null,
            traceParent = null
        )

    private fun assertionResultCall(): AssertionResultApiCall =
        AssertionResultApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            assertionResult = assertionResult(authenticatorAttachment = AuthenticatorAttachment.CROSS_PLATFORM),
            accessToken = null,
            traceParent = null
        )

    private fun assertionCancelCall(): AssertionCancelApiCall =
        AssertionCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = passkeyTestSerialization,
            challenge = ChallengeId("assertion-challenge-1"),
            reason = Reason.Unknown(),
            traceParent = null
        )

    private fun malformedAssertionOptionsBase64UrlCases(): List<MalformedAssertionOptionsBase64UrlCase> =
        listOf(
            MalformedAssertionOptionsBase64UrlCase(
                field = "challenge",
                body = """
                    {
                      "challenge": "YQ=",
                      "rpId": "login.example.test",
                      "allowCredentials": [
                        {
                          "type": "public-key",
                          "id": "${paddedBase64UrlFixture("credential")}"
                        }
                      ]
                    }
                """.trimIndent()
            ),
            MalformedAssertionOptionsBase64UrlCase(
                field = "allowCredentials.id",
                body = """
                    {
                      "challenge": "${paddedBase64UrlFixture("a")}",
                      "rpId": "login.example.test",
                      "allowCredentials": [
                        {
                          "type": "public-key",
                          "id": "YQ="
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

    private fun assertionResult(authenticatorAttachment: AuthenticatorAttachment?): AssertionResult =
        AssertionResult(
            id = "credential-id",
            type = CredentialType.PUBLIC_KEY,
            response = AssertionResult.AuthenticatorResponse(
                clientDataJSON = "client-data-json",
                authenticatorData = "authenticator-data",
                userHandle = "user-handle",
                signature = "signature"
            ),
            authenticatorAttachment = authenticatorAttachment
        )

    private fun assertThatObject(actual: Any?) = assertThat(actual)

}

private data class MalformedAssertionOptionsBase64UrlCase(val field: String, val body: String)
