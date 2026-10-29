package com.ownid.sdk.api

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadCompleteVerificationRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadEnrollRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadLoginIdRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadResendRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadVerificationRequestErrorResponse
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test
import java.io.IOException

// Covers: MODEL-110, WIRE-260, WIRE-270
internal class ApiResultContractTest {

    @Test
    fun successBranchHelpersUseSuccessPayloadOnlyAndReturnOriginalResult() {
        val result: ApiResult<String, TestApiFailure> = ApiResult.Success("payload")
        val calls = mutableListOf<String>()

        val returned = result
            .onSuccess { calls += "success:$it" }
            .onError { calls += "failure:$it" }

        assertThat(returned).isSameInstanceAs(result)
        assertThat(calls).containsExactly("success:payload")
        assertThat(result.fold(onSuccess = { "fold:$it" }, onError = { "unexpected:$it" })).isEqualTo("fold:payload")
        assertThat(result.getOrNull()).isEqualTo("payload")
        assertThat(result.errorOrNull()).isNull()
        assertThat(result.toString()).isEqualTo("Success")
    }

    @Test
    fun failureBranchHelpersUseFailurePayloadOnlyAndReturnOriginalResult() {
        val failure = TestApiFailure(ErrorCode.InvalidArgument, "bad request")
        val result: ApiResult<String, TestApiFailure> = ApiResult.Failure(failure)
        val calls = mutableListOf<String>()

        val returned = result
            .onSuccess { calls += "success:$it" }
            .onError { calls += "failure:${it.message}" }

        assertThat(returned).isSameInstanceAs(result)
        assertThat(calls).containsExactly("failure:bad request")
        assertThat(result.fold(onSuccess = { "unexpected:$it" }, onError = { "fold:${it.errorCode}" })).isEqualTo("fold:invalid_argument")
        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()).isSameInstanceAs(failure)
        assertThat(result.toString()).isEqualTo("Failure(failure=TestApiFailure(errorCode=invalid_argument, message=bad request))")
    }

    @Test
    fun mapTransformsSuccessAndLeavesFailureUntouched() {
        val success: ApiResult<Int, TestApiFailure> = ApiResult.Success("abc").map { it.length }
        val failurePayload = TestApiFailure(ErrorCode.Forbidden, "denied")
        val failure: ApiResult<Int, TestApiFailure> = ApiResult.Failure(failurePayload).map { value: String -> value.length }

        assertThat(success.getOrNull()).isEqualTo(3)
        assertThat(failure.errorOrNull()).isSameInstanceAs(failurePayload)
    }

    @Test
    fun mapErrorTransformsFailureAndLeavesSuccessValueUntouched() {
        val success: ApiResult<String, String> = ApiResult.Success("abc").mapError { failure: TestApiFailure -> failure.message }
        val failure: ApiResult<String, ErrorCode> = ApiResult.Failure(TestApiFailure(ErrorCode.Network, "offline")).mapError { it.errorCode }

        assertThat(success.getOrNull()).isEqualTo("abc")
        assertThatObject(failure.errorOrNull()).isEqualTo(ErrorCode.Network)
    }

    @Test
    fun runApiCatchingConvertsRuntimeExceptionButRethrowsCancellation(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        val runtimeResult: ApiResult<Unit, TestApiFailure> = dispatcher.runApiCatching(
            unexpectedFailure = { errorCode, message, _ -> TestApiFailure(errorCode, message) },
            block = { throw IllegalStateException("boom") }
        )

        assertThat(runtimeResult.errorOrNull()).isEqualTo(TestApiFailure(ErrorCode.Unknown, "boom"))

        var cancellation: CancellationException? = null
        try {
            dispatcher.runApiCatching<Unit, TestApiFailure>(
                unexpectedFailure = { errorCode, message, _ -> TestApiFailure(errorCode, message) },
                block = { throw CancellationException("cancelled") }
            )
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertThat(cancellation).hasMessageThat().isEqualTo("cancelled")
    }

    @Test
    fun apiCallExecuteCollapsesTransportFailureToPublicUnexpectedNetworkFailure(): Unit = runTest {
        val url = "https://api.ownid.test/generic".toHttpUrl()
        val result = TestApiCall(Request.Builder().url(url).build())
            .execute(RecordingNetwork { Network.Response.Fail.NetworkError(url, IOException("offline")) })
        val failure = result.failureValue()

        assertThat(failure.errorCode).isEqualTo(ErrorCode.Network)
        assertThat(failure.message).isNotEmpty()
    }

    @Test
    fun apiCallExecuteCollapsesUnexpectedStatusDecodeAndBodyShapeFailuresToPublicUnexpectedUnknownFailures(): Unit = runTest {
        val url = "https://api.ownid.test/generic".toHttpUrl()
        val call = TestApiCall(Request.Builder().url(url).build())

        val unexpectedStatus = call.execute(RecordingNetwork { apiSuccess(url, code = 201, body = """{"value":"ok"}""") }).failureValue()
        val decodeFailure = call.execute(RecordingNetwork { apiSuccess(url, code = 200, body = "{") }).failureValue()
        val bodyShapeFailure = call.execute(RecordingNetwork { apiSuccess(url, code = 200, body = "{}") }).failureValue()
        val httpFailure = call.execute(RecordingNetwork { apiHttpError(url, statusCode = 500, body = """{"error":"server"}""") }).failureValue()
        val responseFailure = call.execute(
            RecordingNetwork {
                Network.Response.Fail.ResponseError(url, statusCode = 200, error = IllegalStateException("bad body"), headers = emptyMap(), body = "{}")
            }
        ).failureValue()

        listOf(unexpectedStatus, decodeFailure, bodyShapeFailure, httpFailure, responseFailure).forEach { failure ->
            assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
            assertThat(failure.message).isNotEmpty()
        }
    }

    @Test
    fun badRequestUnionsRejectUnsupportedCodesAndMapLiteralUnknown() {
        val serialization = SerializationImpl()
        val mappers: Map<String, (String) -> TestApiFailure?> = mapOf(
            "basic bad request" to { body ->
                serialization.mapBadRequestErrorResponse(body, invalidArgument = { _, _ -> null }, unknown = ::TestApiFailure)
            },
            "enrollment" to { body ->
                serialization.mapBadEnrollRequestErrorResponse(body, invalidArgument = { _, _ -> null }, unknown = ::TestApiFailure)
            },
            "login ID" to { body ->
                serialization.mapBadLoginIdRequestErrorResponse(
                    body,
                    invalidArgument = { _, _ -> null },
                    loginIdValidation = { _, _, _, _ -> null },
                    loginIdTypeNotSupported = { _, _ -> null },
                    unknown = ::TestApiFailure
                )
            },
            "challenge" to { body ->
                serialization.mapBadChallengeRequestErrorResponse(
                    body,
                    invalidArgument = { _, _ -> null },
                    invalidChallenge = { _, _, _ -> null },
                    maximumAttemptsReached = { _, _, _ -> null },
                    unknown = ::TestApiFailure
                )
            },
            "verification start" to { body ->
                serialization.mapBadVerificationRequestErrorResponse(
                    body,
                    invalidArgument = { _, _ -> null },
                    loginIdValidation = { _, _, _, _ -> null },
                    loginIdTypeNotSupported = { _, _ -> null },
                    missingChannel = { _, _, _ -> null },
                    unknown = ::TestApiFailure
                )
            },
            "verification completion" to { body ->
                serialization.mapBadCompleteVerificationRequestErrorResponse(
                    body,
                    invalidArgument = { _, _ -> null },
                    invalidChallenge = { _, _, _ -> null },
                    maximumAttemptsReached = { _, _, _ -> null },
                    verificationCodeWrong = { _, _, _ -> null },
                    unknown = ::TestApiFailure
                )
            },
            "verification resend" to { body ->
                serialization.mapBadResendRequestErrorResponse(
                    body,
                    invalidArgument = { _, _ -> null },
                    invalidChallenge = { _, _, _ -> null },
                    maximumResendAttemptsReached = { _, _, _ -> null },
                    unknown = ::TestApiFailure
                )
            }
        )
        val unsupportedBody = """{"errorCode":"unsupported_backend_code","message":"diagnostic"}"""
        val unknownBody = """{"errorCode":"unknown","message":"diagnostic"}"""

        for ((family, map) in mappers) {
            val rejection = runCatching { map(unsupportedBody) }.exceptionOrNull()
            assertWithMessage("$family must reject unsupported codes").that(rejection).isInstanceOf(IllegalStateException::class.java)
            assertWithMessage("$family must select the unknown branch").that(map(unknownBody))
                .isEqualTo(TestApiFailure(ErrorCode.Unknown, "diagnostic"))
        }
    }

    private data class TestApiFailure(override val errorCode: ErrorCode, override val message: String) : ApiFailure

    private class TestApiCall(override val request: Request) : ApiCall<String, TestUnexpectedFailure> {
        private val serialization = SerializationImpl()

        override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<String, TestUnexpectedFailure> = when (successResponse.code) {
            200 -> runApiMappingCatching {
                val body = serialization.parseToJsonElement(successResponse.body).jsonObject
                body["value"]?.jsonPrimitive?.contentOrNull ?: error("Malformed success response")
            }.fold(
                onSuccess = { ApiResult.Success(it) },
                onFailure = { error ->
                    val message = if (error.message == "Malformed success response") error.message.orEmpty() else "Failed to parse success response"
                    ApiResult.Failure(mapUnhandled(successResponse.toSuccessMappingFail(message, error)))
                }
            )

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

        override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): TestUnexpectedFailure =
            failResponse.toUnexpectedFailure(::TestUnexpectedFailure)

        override fun mapUnhandled(failResponse: Network.Response.Fail): TestUnexpectedFailure =
            failResponse.toUnexpectedFailure(::TestUnexpectedFailure)
    }

    private class TestUnexpectedFailure(
        errorCode: ErrorCode,
        message: String,
        cause: ApiUnexpectedCause
    ) : ApiUnexpectedFailure(errorCode, message, cause)

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}
