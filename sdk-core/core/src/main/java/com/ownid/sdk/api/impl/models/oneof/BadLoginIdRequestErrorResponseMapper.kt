package com.ownid.sdk.api.impl.models.oneof

import com.ownid.sdk.api.impl.models.InvalidArgumentErrorResponse
import com.ownid.sdk.api.impl.models.LoginIdTypeNotSupportedErrorResponse
import com.ownid.sdk.api.impl.models.LoginIdValidationErrorResponse
import com.ownid.sdk.api.impl.models.UnknownErrorResponse
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode

/**
 * Maps the OpenAPI `BadLoginIdRequestErrorResponse` to an API-layer failure.
 *
 * Bad request.
 *
 * The server selects the concrete body schema through the `errorCode` value.
 */
internal fun <T> Serialization.mapBadLoginIdRequestErrorResponse(
    body: String,
    invalidArgument: (ErrorCode, String) -> T,
    loginIdValidation: (ErrorCode, String, LoginId, String) -> T,
    loginIdTypeNotSupported: (ErrorCode, String) -> T,
    unknown: (ErrorCode, String) -> T
): T = mapErrorResponse(
    body = body,
    expected = "BadLoginIdRequestErrorResponse",
    case(ApiErrorCode.INVALID_ARGUMENT, InvalidArgumentErrorResponse.serializer()) { response ->
        invalidArgument(response.errorCode.toDomainModel(), response.message)
    },
    case(ApiErrorCode.LOGIN_ID_VALIDATION_FAILED, LoginIdValidationErrorResponse.serializer()) { response ->
        loginIdValidation(response.errorCode.toDomainModel(), response.message, response.loginId.toDomainModel(), response.regex)
    },
    case(ApiErrorCode.LOGIN_ID_TYPE_NOT_SUPPORTED, LoginIdTypeNotSupportedErrorResponse.serializer()) { response ->
        loginIdTypeNotSupported(response.errorCode.toDomainModel(), response.message)
    },
    case(ApiErrorCode.UNKNOWN, UnknownErrorResponse.serializer()) { response ->
        unknown(response.errorCode.toDomainModel(), response.message)
    }
)
