package com.ownid.sdk.api.impl.verification

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.EmailVerificationCancelApiFailure
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.EmailVerificationResendApiFailure
import com.ownid.sdk.api.EmailVerificationStartApiFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.VerificationMethod
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: WIRE-110, WIRE-120, WIRE-130
internal class EmailVerificationApiCallErrorMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://example.com/api/".toHttpUrl()
    private val url = "https://example.com/api/verifications/email".toHttpUrl()
    private val request = Request.Builder().url(url).build()
    private val startCall = EmailVerificationStartApiCall(serialization, request)
    private val completeCall = EmailVerificationCompleteApiCall(serialization, request)
    private val resendCall = EmailVerificationResendApiCall(serialization, request)
    private val cancelCall = EmailVerificationCancelApiCall(serialization, request)

    @Test
    fun createBuildsStartRequestAndMapsCreatedChallenge() {
        val call = EmailVerificationStartApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            loginIdHintId = "email-hint-id",
            accessToken = AccessToken("start-access-token"),
            verificationMethods = setOf(VerificationMethod.OTP, VerificationMethod.MAGIC_LINK),
            magicLinkRedirectUrl = "ownid://email/redirect",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/email/start",
            authorization = "Bearer start-access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId", "loginIdHintId", "verificationMethods", "magicLinkRedirectUrl")
        assertThat(body.getValue("loginId").jsonObject.jsonString("id")).isEqualTo("user@example.com")
        assertThat(body.getValue("loginId").jsonObject.jsonString("type")).isEqualTo("Email")
        assertThat(body.jsonString("loginIdHintId")).isEqualTo("email-hint-id")
        assertThat(body.jsonString("magicLinkRedirectUrl")).isEqualTo("ownid://email/redirect")
        assertThat(body.getValue("verificationMethods").jsonArray.map { it.jsonPrimitive.contentOrNull }).containsExactly("Otp", "MagicLink")

        val challenge = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "email-challenge",
                      "resendPolicy": {
                        "allow": true,
                        "attempts": 3,
                        "debounce": 0
                      },
                      "timeout": 45000,
                      "attempts": 5,
                      "channel": {
                        "channel": "u***r@example.com",
                        "id": "email-channel-id"
                      },
                      "methods": {
                        "otp": { "length": 2 },
                        "magicLink": {}
                      }
                    }
                """.trimIndent()
            )
        ).successValue()

        assertThat(challenge).isEqualTo(
            VerificationChallenge(
                challengeId = ChallengeId("email-challenge"),
                resendPolicy = VerificationChallenge.ResendPolicy(allow = true, attempts = 3, debounce = 1),
                timeout = Timeout(45000),
                attempts = 5,
                methods = VerificationChallenge.Methods(
                    otp = VerificationChallenge.Methods.Otp(length = 4),
                    magicLink = VerificationChallenge.Methods.MagicLink()
                ),
                channel = OperationChannel(channel = "u***r@example.com", id = "email-channel-id")
            )
        )
    }

    @Test
    fun mapHttpSuccessMapsInvalidStartChallengeBodyToUnexpected() {
        val missingChannel = startCall.mapHttpSuccess(
            apiSuccess(
                url = request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "email-challenge",
                      "resendPolicy": {"allow": true, "attempts": 3, "debounce": 1},
                      "timeout": 45000,
                      "attempts": 5,
                      "methods": {"otp": {"length": 6}}
                    }
                """.trimIndent()
            )
        ).failureValue()

        assertThat(missingChannel).isInstanceOf(EmailVerificationStartApiFailure.Unexpected::class.java)
        assertThat(missingChannel.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((missingChannel as EmailVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val emptyMethods = startCall.mapHttpSuccess(
            apiSuccess(
                url = request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "email-challenge",
                      "resendPolicy": {"allow": true, "attempts": 3, "debounce": 1},
                      "timeout": 45000,
                      "attempts": 5,
                      "channel": {"channel": "u***r@example.com", "id": "email-channel-id"},
                      "methods": {}
                    }
                """.trimIndent()
            )
        ).failureValue()

        assertThat(emptyMethods).isInstanceOf(EmailVerificationStartApiFailure.Unexpected::class.java)
        assertThat(emptyMethods.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((emptyMethods as EmailVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    }

    @Test
    fun createBuildsCompleteRequestAndMapsAccessAndProofTokenSuccess() {
        val call = EmailVerificationCompleteApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("email-challenge"),
            code = "123456",
            accessToken = AccessToken("complete-access-token"),
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/email/complete",
            authorization = "Bearer complete-access-token",
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("challengeId", "code")
        assertThat(body.jsonString("challengeId")).isEqualTo("email-challenge")
        assertThat(body.jsonString("code")).isEqualTo("123456")

        assertThat(call.mapHttpSuccess(apiSuccess(call.request.url, 200, """{"accessToken":"email-access"}""")).successValue())
            .isEqualTo(AccessToken("email-access"))
        assertThat(call.mapHttpSuccess(apiSuccess(call.request.url, 200, """{"proofToken":"email-proof"}""")).successValue())
            .isEqualTo(ProofToken("email-proof"))
    }

    @Test
    fun createBuildsResendRequestAndMapsNoContent() {
        val call = EmailVerificationResendApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("email-challenge"),
            accessToken = null,
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/email/resend",
            authorization = null,
            traceParent = null
        )
        assertThat(call.request.jsonBody(serialization).jsonString("challengeId")).isEqualTo("email-challenge")

        call.mapHttpSuccess(apiSuccess(call.request.url, 204, "")).assertUnitSuccess()
    }

    @Test
    fun createBuildsCancelRequestAndMapsNoContent() {
        val call = EmailVerificationCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("email-challenge"),
            accessToken = AccessToken("cancel-access-token"),
            reason = Reason.UserClose("dismissed"),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/email/cancel",
            authorization = "Bearer cancel-access-token",
            traceParent = null
        )
        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("challengeId", "reason")
        assertThat(body.jsonString("challengeId")).isEqualTo("email-challenge")
        assertThat(body.jsonString("reason")).isEqualTo("userClose")

        call.mapHttpSuccess(apiSuccess(call.request.url, 204, "")).assertUnitSuccess()
    }

    @Test
    fun mapHttpErrorMapsStartBadVerificationRequestVariants() {
        val invalidArgument = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_argument",
                      "message": "login id is required"
                    }
                """.trimIndent()
            )
        )

        assertThat(invalidArgument).isInstanceOf(EmailVerificationStartApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as EmailVerificationStartApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("login id is required")

        val invalidLoginId = startCall.mapHttpError(
            httpError(
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

        assertThat(invalidLoginId).isInstanceOf(EmailVerificationStartApiFailure.BadRequest.InvalidLoginId::class.java)
        invalidLoginId as EmailVerificationStartApiFailure.BadRequest.InvalidLoginId
        assertThat(invalidLoginId.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(invalidLoginId.message).isEqualTo("login id does not match configured regex")
        assertThat(invalidLoginId.loginId).isEqualTo(LoginId("bad@example", LoginId.Type.Email))
        assertThat(invalidLoginId.regex).isEqualTo(".+@example\\.com")

        val unsupportedLoginIdType = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "login_id_type_not_supported",
                      "message": "login id type is not supported"
                    }
                """.trimIndent()
            )
        )

        assertThat(unsupportedLoginIdType).isInstanceOf(EmailVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        unsupportedLoginIdType as EmailVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(unsupportedLoginIdType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupportedLoginIdType.message).isEqualTo("login id type is not supported")

        val missingChannel = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "missing_channel",
                      "message": "email channel is not available",
                      "loginId": { "id": "user@example.com", "type": "Email" }
                    }
                """.trimIndent()
            )
        )

        assertThat(missingChannel).isInstanceOf(EmailVerificationStartApiFailure.BadRequest.MissingChannel::class.java)
        missingChannel as EmailVerificationStartApiFailure.BadRequest.MissingChannel
        assertThat(missingChannel.errorCode).isEqualTo(ErrorCode.MissingChannel)
        assertThat(missingChannel.message).isEqualTo("email channel is not available")
        assertThat(missingChannel.loginId).isEqualTo(LoginId("user@example.com", LoginId.Type.Email))

        val unknown = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "unknown",
                      "message": "server could not classify verification start error"
                    }
                """.trimIndent()
            )
        )

        assertThat(unknown).isInstanceOf(EmailVerificationStartApiFailure.BadRequest.Unknown::class.java)
        unknown as EmailVerificationStartApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("server could not classify verification start error")
    }

    @Test
    fun mapHttpErrorMapsStartForbiddenUserNotFoundAndMaximumChallengesReached() {
        val forbidden = startCall.mapHttpError(
            httpError(
                statusCode = 403,
                body = """
                    {
                      "errorCode": "forbidden",
                      "message": "email verification start is not allowed"
                    }
                """.trimIndent()
            )
        )
        assertThat(forbidden).isInstanceOf(EmailVerificationStartApiFailure.Forbidden::class.java)
        forbidden as EmailVerificationStartApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("email verification start is not allowed")

        val userNotFound = startCall.mapHttpError(
            httpError(
                statusCode = 404,
                body = """
                    {
                      "errorCode": "user_not_found",
                      "message": "email account was not found"
                    }
                """.trimIndent()
            )
        )
        assertThat(userNotFound).isInstanceOf(EmailVerificationStartApiFailure.UserNotFound::class.java)
        userNotFound as EmailVerificationStartApiFailure.UserNotFound
        assertThat(userNotFound.errorCode).isEqualTo(ErrorCode.UserNotFound)
        assertThat(userNotFound.message).isEqualTo("email account was not found")

        val maximumChallengesReached = startCall.mapHttpError(
            httpError(
                statusCode = 429,
                body = """
                    {
                      "errorCode": "maximum_challenges_reached",
                      "message": "email challenge limit reached"
                    }
                """.trimIndent()
            )
        )
        assertThat(maximumChallengesReached).isInstanceOf(EmailVerificationStartApiFailure.MaximumChallengesReached::class.java)
        maximumChallengesReached as EmailVerificationStartApiFailure.MaximumChallengesReached
        assertThat(maximumChallengesReached.errorCode).isEqualTo(ErrorCode.MaximumChallengesReached)
        assertThat(maximumChallengesReached.message).isEqualTo("email challenge limit reached")
    }

    @Test
    fun mapHttpErrorMapsCompleteBadRequestVariantsAndForbidden() {
        val invalidArgument = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_argument",
                      "message": "verification code is required"
                    }
                """.trimIndent()
            )
        )
        assertThat(invalidArgument).isInstanceOf(EmailVerificationCompleteApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as EmailVerificationCompleteApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("verification code is required")

        val invalidChallenge = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_challenge",
                      "message": "challenge expired",
                      "challengeId": "email-expired"
                    }
                """.trimIndent()
            )
        )
        assertThat(invalidChallenge).isInstanceOf(EmailVerificationCompleteApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as EmailVerificationCompleteApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("email-expired"))

        val maximumAttemptsReached = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "maximum_attempts_reached",
                      "message": "attempt limit reached",
                      "challengeId": "email-limited"
                    }
                """.trimIndent()
            )
        )
        assertThat(maximumAttemptsReached).isInstanceOf(EmailVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttemptsReached as EmailVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttemptsReached.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttemptsReached.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttemptsReached.challengeId).isEqualTo(ChallengeId("email-limited"))

        val wrongCode = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "verification_code_wrong",
                      "message": "verification code is wrong",
                      "challengeId": "email-challenge"
                    }
                """.trimIndent()
            )
        )
        assertThat(wrongCode).isInstanceOf(EmailVerificationCompleteApiFailure.BadRequest.WrongCode::class.java)
        wrongCode as EmailVerificationCompleteApiFailure.BadRequest.WrongCode
        assertThat(wrongCode.errorCode).isEqualTo(ErrorCode.VerificationCodeWrong)
        assertThat(wrongCode.message).isEqualTo("verification code is wrong")
        assertThat(wrongCode.challengeId).isEqualTo(ChallengeId("email-challenge"))

        val unknown = completeCall.mapHttpError(httpError(statusCode = 400, body = """{"errorCode":"unknown","message":"unknown complete error"}"""))
        assertThat(unknown).isInstanceOf(EmailVerificationCompleteApiFailure.BadRequest.Unknown::class.java)
        unknown as EmailVerificationCompleteApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("unknown complete error")

        val forbidden = completeCall.mapHttpError(
            httpError(statusCode = 403, body = """{"errorCode":"forbidden","message":"email completion is not allowed"}""")
        )
        assertThat(forbidden).isInstanceOf(EmailVerificationCompleteApiFailure.Forbidden::class.java)
        forbidden as EmailVerificationCompleteApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("email completion is not allowed")
    }

    @Test
    fun mapHttpErrorMapsBlankForbiddenFallbacksForStartAndComplete() {
        val startForbidden = startCall.mapHttpError(httpError(statusCode = 403, body = ""))
        assertThat(startForbidden).isInstanceOf(EmailVerificationStartApiFailure.Forbidden::class.java)
        assertThat(startForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val completeForbidden = completeCall.mapHttpError(httpError(statusCode = 403, body = "  "))
        assertThat(completeForbidden).isInstanceOf(EmailVerificationCompleteApiFailure.Forbidden::class.java)
        assertThat(completeForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
    }

    @Test
    fun mapHttpErrorMapsResendBadRequestVariants() {
        val invalidArgument = resendCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_argument",
                      "message": "challenge id is required"
                    }
                """.trimIndent()
            )
        )

        assertThat(invalidArgument).isInstanceOf(EmailVerificationResendApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as EmailVerificationResendApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("challenge id is required")

        val invalidChallenge = resendCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_challenge",
                      "message": "challenge expired",
                      "challengeId": "challenge-expired"
                    }
                """.trimIndent()
            )
        )

        assertThat(invalidChallenge).isInstanceOf(EmailVerificationResendApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as EmailVerificationResendApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("challenge-expired"))

        val maximumResendAttemptsReached = resendCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "maximum_resend_attempts_reached",
                      "message": "resend limit reached",
                      "challengeId": "challenge-limited"
                    }
                """.trimIndent()
            )
        )

        assertThat(maximumResendAttemptsReached).isInstanceOf(EmailVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached::class.java)
        maximumResendAttemptsReached as EmailVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached
        assertThat(maximumResendAttemptsReached.errorCode).isEqualTo(ErrorCode.MaximumResendAttemptsReached)
        assertThat(maximumResendAttemptsReached.message).isEqualTo("resend limit reached")
        assertThat(maximumResendAttemptsReached.challengeId).isEqualTo(ChallengeId("challenge-limited"))

        val unknown = resendCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "unknown",
                      "message": "server could not classify resend error"
                    }
                """.trimIndent()
            )
        )

        assertThat(unknown).isInstanceOf(EmailVerificationResendApiFailure.BadRequest.Unknown::class.java)
        unknown as EmailVerificationResendApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("server could not classify resend error")
    }

    @Test
    fun mapHttpErrorMapsCancelBadRequestVariants() {
        val invalidArgument = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"invalid_argument","message":"challenge id is required"}""")
        )
        assertThat(invalidArgument).isInstanceOf(EmailVerificationCancelApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as EmailVerificationCancelApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("challenge id is required")

        val invalidChallenge = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"email-expired"}""")
        )
        assertThat(invalidChallenge).isInstanceOf(EmailVerificationCancelApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as EmailVerificationCancelApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("email-expired"))

        val maximumAttemptsReached = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"email-limited"}""")
        )
        assertThat(maximumAttemptsReached).isInstanceOf(EmailVerificationCancelApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttemptsReached as EmailVerificationCancelApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttemptsReached.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttemptsReached.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttemptsReached.challengeId).isEqualTo(ChallengeId("email-limited"))

        val unknown = cancelCall.mapHttpError(httpError(statusCode = 400, body = """{"errorCode":"unknown","message":"unknown cancel error"}"""))
        assertThat(unknown).isInstanceOf(EmailVerificationCancelApiFailure.BadRequest.Unknown::class.java)
        unknown as EmailVerificationCancelApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("unknown cancel error")
    }

    @Test
    fun mapHttpErrorMapsFailedDependencyVariantsForStartAndResend() {
        val providerBody = """
            {
              "errorCode": "integration_error",
              "message": "email provider failed",
              "scope": "channel"
            }
        """.trimIndent()

        val startProvider = startCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(startProvider).isInstanceOf(EmailVerificationStartApiFailure.FailedDependency.ProviderFailed::class.java)
        startProvider as EmailVerificationStartApiFailure.FailedDependency.ProviderFailed
        assertThat(startProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(startProvider.message).isEqualTo("email provider failed")
        assertThat(startProvider.scope).isEqualTo(ApiFailureScope.CHANNEL)

        val resendProvider = resendCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(resendProvider).isInstanceOf(EmailVerificationResendApiFailure.FailedDependency.ProviderFailed::class.java)
        resendProvider as EmailVerificationResendApiFailure.FailedDependency.ProviderFailed
        assertThat(resendProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(resendProvider.message).isEqualTo("email provider failed")
        assertThat(resendProvider.scope).isEqualTo(ApiFailureScope.CHANNEL)

        val missingProviderBody = """
            {
              "errorCode": "missing_capability_provider",
              "message": "email provider is not configured",
              "capability": "email-verification",
              "scope": "data"
            }
        """.trimIndent()

        val startMissingProvider = startCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(startMissingProvider).isInstanceOf(EmailVerificationStartApiFailure.FailedDependency.MissingProvider::class.java)
        startMissingProvider as EmailVerificationStartApiFailure.FailedDependency.MissingProvider
        assertThat(startMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(startMissingProvider.message).isEqualTo("email provider is not configured")
        assertThat(startMissingProvider.capability).isEqualTo("email-verification")
        assertThat(startMissingProvider.scope).isEqualTo(ApiFailureScope.DATA)

        val resendMissingProvider = resendCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(resendMissingProvider).isInstanceOf(EmailVerificationResendApiFailure.FailedDependency.MissingProvider::class.java)
        resendMissingProvider as EmailVerificationResendApiFailure.FailedDependency.MissingProvider
        assertThat(resendMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(resendMissingProvider.message).isEqualTo("email provider is not configured")
        assertThat(resendMissingProvider.capability).isEqualTo("email-verification")
        assertThat(resendMissingProvider.scope).isEqualTo(ApiFailureScope.DATA)
    }

    @Test
    fun mapHttpErrorMapsContractMismatchesAndUnhandledStatusesToUnexpected() {
        val futureStartBadRequest = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "future_verification_error",
                      "message": "future server contract"
                    }
                """.trimIndent()
            )
        )

        assertThat(futureStartBadRequest).isInstanceOf(EmailVerificationStartApiFailure.Unexpected::class.java)
        assertThat(futureStartBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureStartBadRequest as EmailVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val futureCompleteBadRequest = completeCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"future_complete_error","message":"future server contract"}""")
        )
        assertThat(futureCompleteBadRequest).isInstanceOf(EmailVerificationCompleteApiFailure.Unexpected::class.java)
        assertThat(futureCompleteBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureCompleteBadRequest as EmailVerificationCompleteApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val futureResendBadRequest = resendCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "future_resend_error",
                      "message": "future server contract"
                    }
                """.trimIndent()
            )
        )

        assertThat(futureResendBadRequest).isInstanceOf(EmailVerificationResendApiFailure.Unexpected::class.java)
        assertThat(futureResendBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureResendBadRequest as EmailVerificationResendApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val malformedFailedDependency = startCall.mapHttpError(httpError(statusCode = 424, body = """{"message":"missing discriminator"}"""))
        assertThat(malformedFailedDependency).isInstanceOf(EmailVerificationStartApiFailure.Unexpected::class.java)
        assertThat(malformedFailedDependency.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((malformedFailedDependency as EmailVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val unhandledStatus = cancelCall.mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(unhandledStatus).isInstanceOf(EmailVerificationCancelApiFailure.Unexpected::class.java)
        assertThat(unhandledStatus.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((unhandledStatus as EmailVerificationCancelApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    }

    private fun httpError(statusCode: Int, body: String): Network.Response.Fail.HttpError = apiHttpError(url, statusCode, body)
}
