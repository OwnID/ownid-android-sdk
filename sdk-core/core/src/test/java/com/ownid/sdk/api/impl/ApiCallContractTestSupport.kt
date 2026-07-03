@file:Suppress("unused")

package com.ownid.sdk.api.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.ErrorCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

internal fun assertJsonPostRequest(
    request: Request,
    url: String,
    authorization: String?,
    traceParent: String?
) {
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.url.toString()).isEqualTo(url)
    assertThat(request.header("Accept")).isEqualTo("application/json")
    assertThat(request.header("Cache-Control")).isEqualTo("no-store")
    assertThat(request.header("Authorization")).isEqualTo(authorization)
    assertThat(request.header("traceparent")).isEqualTo(traceParent)
}

internal fun Request.jsonBody(serialization: SerializationImpl = SerializationImpl()): JsonObject {
    val buffer = Buffer()
    requireNotNull(body).writeTo(buffer)
    return serialization.parseToJsonElement(buffer.readUtf8()).jsonObject
}

internal fun JsonObject.jsonString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

internal fun JsonObject.jsonStringArray(key: String): List<String> =
    getValue(key).jsonArray.jsonStrings()

internal fun JsonArray.jsonStrings(): List<String> = map { element ->
    element.jsonPrimitive.contentOrNull ?: error("Expected JSON string element: $element")
}

internal fun JsonObject.assertJsonStringsExactly(vararg pairs: Pair<String, String?>) {
    assertThat(keys).containsExactlyElementsIn(pairs.map { it.first })
    pairs.forEach { (key, value) ->
        assertThat(jsonString(key)).isEqualTo(value)
    }
}

internal fun apiSuccess(url: HttpUrl, code: Int, body: String): Network.Response.Success =
    Network.Response.Success(
        url = url,
        code = code,
        headers = emptyMap(),
        body = body
    )

internal fun apiHttpError(url: HttpUrl, statusCode: Int, body: String): Network.Response.Fail.HttpError =
    Network.Response.Fail.HttpError(
        url = url,
        statusCode = statusCode,
        headers = emptyMap(),
        body = body
    )

internal fun <Success, Failure> ApiResult<Success, Failure>.successValue(): Success {
    assertThat(this).isInstanceOf(ApiResult.Success::class.java)
    @Suppress("UNCHECKED_CAST")
    return (this as ApiResult.Success<Success>).success
}

internal fun <Success, Failure> ApiResult<Success, Failure>.failureValue(): Failure {
    assertThat(this).isInstanceOf(ApiResult.Failure::class.java)
    @Suppress("UNCHECKED_CAST")
    return (this as ApiResult.Failure<Failure>).failure
}

internal fun <Failure> ApiResult<Unit, Failure>.assertUnitSuccess() {
    assertThat(this).isInstanceOf(ApiResult.Success::class.java)
    assertThat((this as ApiResult.Success).success).isEqualTo(Unit)
}

internal fun assertUnexpectedApiContractFailure(failure: ApiUnexpectedFailure, statusCode: Int) {
    assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
    assertThat(failure.cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    val cause = failure.cause as ApiUnexpectedCause.ApiContract
    assertThat(cause.failure).isInstanceOf(Network.Response.Fail.HttpError::class.java)
    assertThat((cause.failure as Network.Response.Fail.HttpError).statusCode).isEqualTo(statusCode)
}

internal fun assertUnexpectedApiContractResponseFailure(failure: ApiUnexpectedFailure, statusCode: Int) {
    assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
    assertThat(failure.cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    val cause = failure.cause as ApiUnexpectedCause.ApiContract
    assertThat(cause.failure).isInstanceOf(Network.Response.Fail.ResponseError::class.java)
    assertThat((cause.failure as Network.Response.Fail.ResponseError).statusCode).isEqualTo(statusCode)
}

internal class FixedApiBaseUrl(private val url: HttpUrl) : ApiBaseUrl {
    override fun getBaseUrlOrThrow(): HttpUrl = url
}

internal class RecordingNetwork(private val responder: (suspend (Request) -> Network.Response)? = null) : Network {
    private val responders = mutableListOf<suspend (Request) -> Network.Response>()
    private val recordedRequests = mutableListOf<Request>()

    val requests: List<Request>
        get() = recordedRequests.toList()

    override val okHttpClient: OkHttpClient = OkHttpClient()
    override val retryConfig: Network.Request.RetryConfig? = null

    fun enqueue(responder: suspend (Request) -> Network.Response) {
        responders += responder
    }

    fun requestPaths(): List<String> = requests.map { it.url.encodedPath }

    override suspend fun execute(okHttpRequest: Request): Network.Response {
        recordedRequests += okHttpRequest
        val nextResponder = responder ?: if (responders.isNotEmpty()) responders.removeAt(0) else error("No response enqueued for ${okHttpRequest.url}")
        return nextResponder(okHttpRequest)
    }
}

internal class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override fun defaultDispatcher(): CoroutineDispatcher = dispatcher

    override fun ioDispatcher(): CoroutineDispatcher = dispatcher
}
