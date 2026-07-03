package com.ownid.sdk.api.impl

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.capability.Network
import okhttp3.Request

/**
 * Internal adapter between one Auth Server endpoint and the SDK's typed [ApiResult] contract.
 *
 * Implementations own the endpoint boundary: they translate network outcomes into the endpoint's public success and
 * failure types. [execute] performs one network dispatch for the supplied [request]; it does not retry, persist state,
 * or emit telemetry on its own. Cancellation remains coroutine cancellation and is not converted into an API failure.
 */
internal interface ApiCall<ApiSuccess, ApiFailure> {

    val request: Request

    suspend fun execute(network: Network, request: Request = this.request): ApiResult<ApiSuccess, ApiFailure> =
        when (val response = network.execute(request)) {
            is Network.Response.Success -> mapHttpSuccess(response)
            is Network.Response.Fail.HttpError -> ApiResult.Failure(mapHttpError(response))
            is Network.Response.Fail.NetworkError -> ApiResult.Failure(mapUnhandled(response))
            is Network.Response.Fail.ResponseError -> ApiResult.Failure(mapUnhandled(response))
        }

    fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<ApiSuccess, ApiFailure>

    fun mapHttpError(failResponse: Network.Response.Fail.HttpError): ApiFailure

    fun mapUnhandled(failResponse: Network.Response.Fail): ApiFailure
}
