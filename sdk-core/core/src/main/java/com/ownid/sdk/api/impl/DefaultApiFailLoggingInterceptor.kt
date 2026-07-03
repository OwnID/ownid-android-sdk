package com.ownid.sdk.api.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Network
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import okhttp3.Request

/**
 * Default API interceptor that reports unexpected API failures to [ServerLogger].
 *
 * Only SDK-detected API contract mismatches are reported. Business failures, transport failures, and local runtime
 * failures pass through unchanged without server logging. Logging is best-effort and never changes the API result
 * returned to the caller.
 */
@InternalOwnIdAPI
internal class DefaultApiFailLoggingInterceptor(
    private val serverLoggerProvider: () -> ServerLogger?,
) : ApiCallInterceptor {

    override suspend fun <ApiSuccess, ApiFailure> onResponse(
        request: Request,
        response: ApiResult<ApiSuccess, ApiFailure>
    ): ApiResult<ApiSuccess, ApiFailure> {
        if (response !is ApiResult.Failure) return response

        val failure = response.failure as? ApiUnexpectedFailure ?: return response
        val cause = failure.cause
        if (cause !is ApiUnexpectedCause.ApiContract) return response

        val serverLogger = serverLoggerProvider() ?: return response

        val message = buildMessage(request = request, failure = failure, cause = cause)
        try {
            serverLogger.log(
                level = OwnIdLogger.Level.Error,
                className = "DefaultApiFailLoggingInterceptor",
                message = message,
                cause = cause.error ?: (cause.failure as? Network.Response.Fail.ResponseError)?.error
            )
        } catch (_: Exception) {
        }

        return response
    }

    private fun buildMessage(request: Request, failure: ApiUnexpectedFailure, cause: ApiUnexpectedCause.ApiContract): String =
        buildString {
            val httpCode = when (val unexpectedCause = cause.failure) {
                is Network.Response.Fail.HttpError -> unexpectedCause.statusCode
                is Network.Response.Fail.ResponseError -> unexpectedCause.statusCode
                is Network.Response.Fail.NetworkError -> null
            }
            append("Unexpected ApiResult.Failure, method=${request.method}, url=${request.url}, errorCode=${failure.errorCode.value}")
            if (httpCode != null) append(", httpCode=$httpCode")
            if (failure.message.isNotBlank()) append(", message=${failure.message}")
        }
}
