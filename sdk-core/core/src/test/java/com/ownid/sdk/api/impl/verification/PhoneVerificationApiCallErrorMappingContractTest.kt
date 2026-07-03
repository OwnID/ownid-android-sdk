package com.ownid.sdk.api.impl.verification

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.PhoneVerificationCancelApiFailure
import com.ownid.sdk.api.PhoneVerificationCompleteApiFailure
import com.ownid.sdk.api.PhoneVerificationResendApiFailure
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
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

// Covers: WIRE-140, WIRE-150, WIRE-160
internal class PhoneVerificationApiCallErrorMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://example.com/api/".toHttpUrl()
    private val url = "https://example.com/api/verifications/phone".toHttpUrl()
    private val request = Request.Builder().url(url).build()
    private val startCall = PhoneVerificationStartApiCall(serialization, request)
    private val completeCall = PhoneVerificationCompleteApiCall(serialization, request)
    private val resendCall = PhoneVerificationResendApiCall(serialization, request)
    private val cancelCall = PhoneVerificationCancelApiCall(serialization, request)

    @Test
    fun createBuildsStartRequestAndMapsCreatedChallenge() {
        val call = PhoneVerificationStartApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            loginId = LoginId("+15551234567", LoginId.Type.PhoneNumber),
            loginIdHintId = "phone-hint-id",
            accessToken = AccessToken("start-access-token"),
            verificationMethods = setOf(VerificationMethod.OTP, VerificationMethod.MAGIC_LINK),
            magicLinkRedirectUrl = "ownid://phone/redirect",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/phone/start",
            authorization = "Bearer start-access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId", "loginIdHintId", "verificationMethods", "magicLinkRedirectUrl")
        assertThat(body.getValue("loginId").jsonObject.jsonString("id")).isEqualTo("+15551234567")
        assertThat(body.getValue("loginId").jsonObject.jsonString("type")).isEqualTo("PhoneNumber")
        assertThat(body.jsonString("loginIdHintId")).isEqualTo("phone-hint-id")
        assertThat(body.jsonString("magicLinkRedirectUrl")).isEqualTo("ownid://phone/redirect")
        assertThat(body.getValue("verificationMethods").jsonArray.map { it.jsonPrimitive.contentOrNull }).containsExactly("Otp", "MagicLink")

        val challenge = call.mapHttpSuccess(
            apiSuccess(
                url = call.request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "phone-challenge",
                      "resendPolicy": {
                        "allow": true,
                        "attempts": 3,
                        "debounce": 0
                      },
                      "timeout": 45000,
                      "attempts": 5,
                      "channel": {
                        "channel": "+1******4567",
                        "id": "phone-channel-id"
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
                challengeId = ChallengeId("phone-challenge"),
                resendPolicy = VerificationChallenge.ResendPolicy(allow = true, attempts = 3, debounce = 1),
                timeout = Timeout(45000),
                attempts = 5,
                methods = VerificationChallenge.Methods(
                    otp = VerificationChallenge.Methods.Otp(length = 4),
                    magicLink = VerificationChallenge.Methods.MagicLink()
                ),
                channel = OperationChannel(channel = "+1******4567", id = "phone-channel-id")
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
                      "challengeId": "phone-challenge",
                      "resendPolicy": {"allow": true, "attempts": 3, "debounce": 1},
                      "timeout": 45000,
                      "attempts": 5,
                      "methods": {"otp": {"length": 6}}
                    }
                """.trimIndent()
            )
        ).failureValue()

        assertThat(missingChannel).isInstanceOf(PhoneVerificationStartApiFailure.Unexpected::class.java)
        assertThat(missingChannel.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((missingChannel as PhoneVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val emptyMethods = startCall.mapHttpSuccess(
            apiSuccess(
                url = request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "phone-challenge",
                      "resendPolicy": {"allow": true, "attempts": 3, "debounce": 1},
                      "timeout": 45000,
                      "attempts": 5,
                      "channel": {"channel": "+1******4567", "id": "phone-channel-id"},
                      "methods": {}
                    }
                """.trimIndent()
            )
        ).failureValue()

        assertThat(emptyMethods).isInstanceOf(PhoneVerificationStartApiFailure.Unexpected::class.java)
        assertThat(emptyMethods.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((emptyMethods as PhoneVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    }

    @Test
    fun createBuildsCompleteRequestAndMapsAccessAndProofTokenSuccess() {
        val call = PhoneVerificationCompleteApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("phone-challenge"),
            code = "123456",
            accessToken = AccessToken("complete-access-token"),
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/phone/complete",
            authorization = "Bearer complete-access-token",
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("challengeId", "code")
        assertThat(body.jsonString("challengeId")).isEqualTo("phone-challenge")
        assertThat(body.jsonString("code")).isEqualTo("123456")

        assertThat(call.mapHttpSuccess(apiSuccess(call.request.url, 200, """{"accessToken":"phone-access"}""")).successValue())
            .isEqualTo(AccessToken("phone-access"))
        assertThat(call.mapHttpSuccess(apiSuccess(call.request.url, 200, """{"proofToken":"phone-proof"}""")).successValue())
            .isEqualTo(ProofToken("phone-proof"))
    }

    @Test
    fun createBuildsResendRequestAndMapsNoContent() {
        val call = PhoneVerificationResendApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("phone-challenge"),
            accessToken = null,
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/phone/resend",
            authorization = null,
            traceParent = null
        )
        assertThat(call.request.jsonBody(serialization).jsonString("challengeId")).isEqualTo("phone-challenge")

        call.mapHttpSuccess(apiSuccess(call.request.url, 204, "")).assertUnitSuccess()
    }

    @Test
    fun createBuildsCancelRequestAndMapsNoContent() {
        val call = PhoneVerificationCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("phone-challenge"),
            accessToken = AccessToken("cancel-access-token"),
            reason = Reason.UserClose("dismissed"),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/verifications/phone/cancel",
            authorization = "Bearer cancel-access-token",
            traceParent = null
        )
        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("challengeId", "reason")
        assertThat(body.jsonString("challengeId")).isEqualTo("phone-challenge")
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

        assertThat(invalidArgument).isInstanceOf(PhoneVerificationStartApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PhoneVerificationStartApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("login id is required")

        val invalidLoginId = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "login_id_validation_failed",
                      "message": "login id does not match configured regex",
                      "loginId": { "id": "+1555", "type": "PhoneNumber" },
                      "regex": "\\+1\\d+"
                    }
                """.trimIndent()
            )
        )

        assertThat(invalidLoginId).isInstanceOf(PhoneVerificationStartApiFailure.BadRequest.InvalidLoginId::class.java)
        invalidLoginId as PhoneVerificationStartApiFailure.BadRequest.InvalidLoginId
        assertThat(invalidLoginId.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(invalidLoginId.message).isEqualTo("login id does not match configured regex")
        assertThat(invalidLoginId.loginId).isEqualTo(LoginId("+1555", LoginId.Type.PhoneNumber))
        assertThat(invalidLoginId.regex).isEqualTo("\\+1\\d+")

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

        assertThat(unsupportedLoginIdType).isInstanceOf(PhoneVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType::class.java)
        unsupportedLoginIdType as PhoneVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType
        assertThat(unsupportedLoginIdType.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupportedLoginIdType.message).isEqualTo("login id type is not supported")

        val missingChannel = startCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "missing_channel",
                      "message": "phone channel is not available",
                      "loginId": { "id": "+15551234567", "type": "PhoneNumber" }
                    }
                """.trimIndent()
            )
        )

        assertThat(missingChannel).isInstanceOf(PhoneVerificationStartApiFailure.BadRequest.MissingChannel::class.java)
        missingChannel as PhoneVerificationStartApiFailure.BadRequest.MissingChannel
        assertThat(missingChannel.errorCode).isEqualTo(ErrorCode.MissingChannel)
        assertThat(missingChannel.message).isEqualTo("phone channel is not available")
        assertThat(missingChannel.loginId).isEqualTo(LoginId("+15551234567", LoginId.Type.PhoneNumber))

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

        assertThat(unknown).isInstanceOf(PhoneVerificationStartApiFailure.BadRequest.Unknown::class.java)
        unknown as PhoneVerificationStartApiFailure.BadRequest.Unknown
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
                      "message": "phone verification start is not allowed"
                    }
                """.trimIndent()
            )
        )
        assertThat(forbidden).isInstanceOf(PhoneVerificationStartApiFailure.Forbidden::class.java)
        forbidden as PhoneVerificationStartApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("phone verification start is not allowed")

        val userNotFound = startCall.mapHttpError(
            httpError(
                statusCode = 404,
                body = """
                    {
                      "errorCode": "user_not_found",
                      "message": "phone account was not found"
                    }
                """.trimIndent()
            )
        )
        assertThat(userNotFound).isInstanceOf(PhoneVerificationStartApiFailure.UserNotFound::class.java)
        userNotFound as PhoneVerificationStartApiFailure.UserNotFound
        assertThat(userNotFound.errorCode).isEqualTo(ErrorCode.UserNotFound)
        assertThat(userNotFound.message).isEqualTo("phone account was not found")

        val maximumChallengesReached = startCall.mapHttpError(
            httpError(
                statusCode = 429,
                body = """
                    {
                      "errorCode": "maximum_challenges_reached",
                      "message": "phone challenge limit reached"
                    }
                """.trimIndent()
            )
        )
        assertThat(maximumChallengesReached).isInstanceOf(PhoneVerificationStartApiFailure.MaximumChallengesReached::class.java)
        maximumChallengesReached as PhoneVerificationStartApiFailure.MaximumChallengesReached
        assertThat(maximumChallengesReached.errorCode).isEqualTo(ErrorCode.MaximumChallengesReached)
        assertThat(maximumChallengesReached.message).isEqualTo("phone challenge limit reached")
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
        assertThat(invalidArgument).isInstanceOf(PhoneVerificationCompleteApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PhoneVerificationCompleteApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("verification code is required")

        val invalidChallenge = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_challenge",
                      "message": "challenge expired",
                      "challengeId": "phone-expired"
                    }
                """.trimIndent()
            )
        )
        assertThat(invalidChallenge).isInstanceOf(PhoneVerificationCompleteApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PhoneVerificationCompleteApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("phone-expired"))

        val maximumAttemptsReached = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "maximum_attempts_reached",
                      "message": "attempt limit reached",
                      "challengeId": "phone-limited"
                    }
                """.trimIndent()
            )
        )
        assertThat(maximumAttemptsReached).isInstanceOf(PhoneVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttemptsReached as PhoneVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttemptsReached.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttemptsReached.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttemptsReached.challengeId).isEqualTo(ChallengeId("phone-limited"))

        val wrongCode = completeCall.mapHttpError(
            httpError(
                statusCode = 400,
                body = """
                    {
                      "errorCode": "verification_code_wrong",
                      "message": "verification code is wrong",
                      "challengeId": "phone-challenge"
                    }
                """.trimIndent()
            )
        )
        assertThat(wrongCode).isInstanceOf(PhoneVerificationCompleteApiFailure.BadRequest.WrongCode::class.java)
        wrongCode as PhoneVerificationCompleteApiFailure.BadRequest.WrongCode
        assertThat(wrongCode.errorCode).isEqualTo(ErrorCode.VerificationCodeWrong)
        assertThat(wrongCode.message).isEqualTo("verification code is wrong")
        assertThat(wrongCode.challengeId).isEqualTo(ChallengeId("phone-challenge"))

        val unknown = completeCall.mapHttpError(httpError(statusCode = 400, body = """{"errorCode":"unknown","message":"unknown complete error"}"""))
        assertThat(unknown).isInstanceOf(PhoneVerificationCompleteApiFailure.BadRequest.Unknown::class.java)
        unknown as PhoneVerificationCompleteApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("unknown complete error")

        val forbidden = completeCall.mapHttpError(
            httpError(statusCode = 403, body = """{"errorCode":"forbidden","message":"phone completion is not allowed"}""")
        )
        assertThat(forbidden).isInstanceOf(PhoneVerificationCompleteApiFailure.Forbidden::class.java)
        forbidden as PhoneVerificationCompleteApiFailure.Forbidden
        assertThat(forbidden.errorCode).isEqualTo(ErrorCode.Forbidden)
        assertThat(forbidden.message).isEqualTo("phone completion is not allowed")
    }

    @Test
    fun mapHttpErrorMapsBlankForbiddenFallbacksForStartAndComplete() {
        val startForbidden = startCall.mapHttpError(httpError(statusCode = 403, body = ""))
        assertThat(startForbidden).isInstanceOf(PhoneVerificationStartApiFailure.Forbidden::class.java)
        assertThat(startForbidden.errorCode).isEqualTo(ErrorCode.Forbidden)

        val completeForbidden = completeCall.mapHttpError(httpError(statusCode = 403, body = "  "))
        assertThat(completeForbidden).isInstanceOf(PhoneVerificationCompleteApiFailure.Forbidden::class.java)
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

        assertThat(invalidArgument).isInstanceOf(PhoneVerificationResendApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PhoneVerificationResendApiFailure.BadRequest.InvalidArgument
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

        assertThat(invalidChallenge).isInstanceOf(PhoneVerificationResendApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PhoneVerificationResendApiFailure.BadRequest.InvalidChallenge
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

        assertThat(maximumResendAttemptsReached).isInstanceOf(PhoneVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached::class.java)
        maximumResendAttemptsReached as PhoneVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached
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

        assertThat(unknown).isInstanceOf(PhoneVerificationResendApiFailure.BadRequest.Unknown::class.java)
        unknown as PhoneVerificationResendApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("server could not classify resend error")
    }

    @Test
    fun mapHttpErrorMapsCancelBadRequestVariants() {
        val invalidArgument = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"invalid_argument","message":"challenge id is required"}""")
        )
        assertThat(invalidArgument).isInstanceOf(PhoneVerificationCancelApiFailure.BadRequest.InvalidArgument::class.java)
        invalidArgument as PhoneVerificationCancelApiFailure.BadRequest.InvalidArgument
        assertThat(invalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(invalidArgument.message).isEqualTo("challenge id is required")

        val invalidChallenge = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"invalid_challenge","message":"challenge expired","challengeId":"phone-expired"}""")
        )
        assertThat(invalidChallenge).isInstanceOf(PhoneVerificationCancelApiFailure.BadRequest.InvalidChallenge::class.java)
        invalidChallenge as PhoneVerificationCancelApiFailure.BadRequest.InvalidChallenge
        assertThat(invalidChallenge.errorCode).isEqualTo(ErrorCode.InvalidChallenge)
        assertThat(invalidChallenge.message).isEqualTo("challenge expired")
        assertThat(invalidChallenge.challengeId).isEqualTo(ChallengeId("phone-expired"))

        val maximumAttemptsReached = cancelCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"maximum_attempts_reached","message":"attempt limit reached","challengeId":"phone-limited"}""")
        )
        assertThat(maximumAttemptsReached).isInstanceOf(PhoneVerificationCancelApiFailure.BadRequest.MaximumAttemptsReached::class.java)
        maximumAttemptsReached as PhoneVerificationCancelApiFailure.BadRequest.MaximumAttemptsReached
        assertThat(maximumAttemptsReached.errorCode).isEqualTo(ErrorCode.MaximumAttemptsReached)
        assertThat(maximumAttemptsReached.message).isEqualTo("attempt limit reached")
        assertThat(maximumAttemptsReached.challengeId).isEqualTo(ChallengeId("phone-limited"))

        val unknown = cancelCall.mapHttpError(httpError(statusCode = 400, body = """{"errorCode":"unknown","message":"unknown cancel error"}"""))
        assertThat(unknown).isInstanceOf(PhoneVerificationCancelApiFailure.BadRequest.Unknown::class.java)
        unknown as PhoneVerificationCancelApiFailure.BadRequest.Unknown
        assertThat(unknown.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(unknown.message).isEqualTo("unknown cancel error")
    }

    @Test
    fun mapHttpErrorMapsFailedDependencyVariantsForStartAndResend() {
        val providerBody = """
            {
              "errorCode": "integration_error",
              "message": "phone provider failed",
              "scope": "channel"
            }
        """.trimIndent()

        val startProvider = startCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(startProvider).isInstanceOf(PhoneVerificationStartApiFailure.FailedDependency.ProviderFailed::class.java)
        startProvider as PhoneVerificationStartApiFailure.FailedDependency.ProviderFailed
        assertThat(startProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(startProvider.message).isEqualTo("phone provider failed")
        assertThat(startProvider.scope).isEqualTo(ApiFailureScope.CHANNEL)

        val resendProvider = resendCall.mapHttpError(httpError(statusCode = 424, body = providerBody))
        assertThat(resendProvider).isInstanceOf(PhoneVerificationResendApiFailure.FailedDependency.ProviderFailed::class.java)
        resendProvider as PhoneVerificationResendApiFailure.FailedDependency.ProviderFailed
        assertThat(resendProvider.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(resendProvider.message).isEqualTo("phone provider failed")
        assertThat(resendProvider.scope).isEqualTo(ApiFailureScope.CHANNEL)

        val missingProviderBody = """
            {
              "errorCode": "missing_capability_provider",
              "message": "phone provider is not configured",
              "capability": "phone-verification",
              "scope": "data"
            }
        """.trimIndent()

        val startMissingProvider = startCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(startMissingProvider).isInstanceOf(PhoneVerificationStartApiFailure.FailedDependency.MissingProvider::class.java)
        startMissingProvider as PhoneVerificationStartApiFailure.FailedDependency.MissingProvider
        assertThat(startMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(startMissingProvider.message).isEqualTo("phone provider is not configured")
        assertThat(startMissingProvider.capability).isEqualTo("phone-verification")
        assertThat(startMissingProvider.scope).isEqualTo(ApiFailureScope.DATA)

        val resendMissingProvider = resendCall.mapHttpError(httpError(statusCode = 424, body = missingProviderBody))
        assertThat(resendMissingProvider).isInstanceOf(PhoneVerificationResendApiFailure.FailedDependency.MissingProvider::class.java)
        resendMissingProvider as PhoneVerificationResendApiFailure.FailedDependency.MissingProvider
        assertThat(resendMissingProvider.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(resendMissingProvider.message).isEqualTo("phone provider is not configured")
        assertThat(resendMissingProvider.capability).isEqualTo("phone-verification")
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

        assertThat(futureStartBadRequest).isInstanceOf(PhoneVerificationStartApiFailure.Unexpected::class.java)
        assertThat(futureStartBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureStartBadRequest as PhoneVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val futureCompleteBadRequest = completeCall.mapHttpError(
            httpError(statusCode = 400, body = """{"errorCode":"future_complete_error","message":"future server contract"}""")
        )
        assertThat(futureCompleteBadRequest).isInstanceOf(PhoneVerificationCompleteApiFailure.Unexpected::class.java)
        assertThat(futureCompleteBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureCompleteBadRequest as PhoneVerificationCompleteApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

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

        assertThat(futureResendBadRequest).isInstanceOf(PhoneVerificationResendApiFailure.Unexpected::class.java)
        assertThat(futureResendBadRequest.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((futureResendBadRequest as PhoneVerificationResendApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val malformedFailedDependency = startCall.mapHttpError(httpError(statusCode = 424, body = """{"message":"missing discriminator"}"""))
        assertThat(malformedFailedDependency).isInstanceOf(PhoneVerificationStartApiFailure.Unexpected::class.java)
        assertThat(malformedFailedDependency.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((malformedFailedDependency as PhoneVerificationStartApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)

        val unhandledStatus = cancelCall.mapHttpError(httpError(statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(unhandledStatus).isInstanceOf(PhoneVerificationCancelApiFailure.Unexpected::class.java)
        assertThat(unhandledStatus.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((unhandledStatus as PhoneVerificationCancelApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    }

    private fun httpError(statusCode: Int, body: String): Network.Response.Fail.HttpError = apiHttpError(url, statusCode, body)
}
