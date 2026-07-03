package com.ownid.sdk.api.impl.models.oneof

import com.ownid.sdk.api.impl.models.InvalidArgumentErrorResponse
import com.ownid.sdk.api.impl.models.UnknownErrorResponse
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode

/**
 * Maps the OpenAPI `BadRequestErrorResponse` to an API-layer failure.
 *
 * Bad request.
 *
 * The server selects the concrete body schema through the `errorCode` value.
 */
internal fun <T> Serialization.mapBadRequestErrorResponse(
    body: String,
    invalidArgument: (ErrorCode, String) -> T,
    unknown: (ErrorCode, String) -> T
): T = mapErrorResponse(
    body = body,
    expected = "BadRequestErrorResponse",
    case(ApiErrorCode.INVALID_ARGUMENT, InvalidArgumentErrorResponse.serializer()) { response ->
        invalidArgument(response.errorCode.toDomainModel(), response.message)
    },
    case(ApiErrorCode.UNKNOWN, UnknownErrorResponse.serializer()) { response ->
        unknown(response.errorCode.toDomainModel(), response.message)
    }
)
