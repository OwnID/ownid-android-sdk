package com.ownid.sdk.api.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.capability.Network
import okhttp3.Request

/**
 * Internal API call interceptor used to decorate requests and observe mapped API results.
 *
 * Interceptors are SDK-internal boundaries. They must preserve each endpoint's public success/failure contract unless
 * they intentionally replace the result with another value of the same [ApiResult] type. Interceptors may add
 * cross-cutting request metadata or telemetry, but they must not make endpoint-specific business decisions.
 */
internal interface ApiCallInterceptor {
    /** Called before request is sent to network layer. Return the request to dispatch. */
    suspend fun interceptRequest(request: Request): Request = request

    /** Called after API call maps network output into [ApiResult]. Return the result visible to the API caller. */
    suspend fun <ApiSuccess, ApiFailure> onResponse(
        request: Request,
        response: ApiResult<ApiSuccess, ApiFailure>
    ): ApiResult<ApiSuccess, ApiFailure> = response
}

/** Applies request interceptors in registration order and response interceptors in reverse order. */
internal class ApiCallPipelineInterceptor(
    interceptors: List<ApiCallInterceptor>
) : ApiCallInterceptor {

    private val interceptors: List<ApiCallInterceptor> = interceptors.toList()

    override suspend fun interceptRequest(request: Request): Request {
        var interceptedRequest = request
        for (interceptor in interceptors) {
            interceptedRequest = interceptor.interceptRequest(interceptedRequest)
        }
        return interceptedRequest
    }

    override suspend fun <ApiSuccess, ApiFailure> onResponse(
        request: Request,
        response: ApiResult<ApiSuccess, ApiFailure>
    ): ApiResult<ApiSuccess, ApiFailure> {
        var interceptedResponse = response
        for (interceptor in interceptors.asReversed()) {
            interceptedResponse = interceptor.onResponse(request, interceptedResponse)
        }
        return interceptedResponse
    }
}

/** Executes an [ApiCall] through optional [ApiCallInterceptor] while preserving the endpoint's typed result contract. */
internal suspend fun <ApiSuccess, ApiFailure> ApiCall<ApiSuccess, ApiFailure>.executeWithInterceptor(
    network: Network,
    interceptor: ApiCallInterceptor?
): ApiResult<ApiSuccess, ApiFailure> {
    val interceptedRequest = interceptor?.interceptRequest(request) ?: request
    val response = execute(network = network, request = interceptedRequest)
    return interceptor?.onResponse(interceptedRequest, response) ?: response
}
