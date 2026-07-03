package com.ownid.sdk.api.impl.models.oneof

import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.impl.models.MissingProviderErrorResponse
import com.ownid.sdk.api.impl.models.ProviderErrorResponse
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode

/**
 * Maps the OpenAPI `FailedDependencyErrorResponse` to an API-layer failure.
 *
 * Provider or capability dependency failed.
 *
 * The server selects the concrete body schema through the `errorCode` value.
 * Error mapping follows the OpenAPI `oneOf` schema cases instead of trying schemas in fallback order.
 */
internal fun <T> Serialization.mapFailedDependencyErrorResponse(
    body: String,
    provider: (ErrorCode, String, ApiFailureScope) -> T,
    missingProvider: (ErrorCode, String, String, ApiFailureScope) -> T
): T = mapErrorResponse(
    body = body,
    expected = "FailedDependencyErrorResponse",
    case(ApiErrorCode.INTEGRATION_ERROR, ProviderErrorResponse.serializer()) { response ->
        provider(response.errorCode.toDomainModel(), response.message, response.scope.toDomainModel())
    },
    case(ApiErrorCode.MISSING_CAPABILITY_PROVIDER, MissingProviderErrorResponse.serializer()) { response ->
        missingProvider(response.errorCode.toDomainModel(), response.message, response.capability, response.scope.toDomainModel())
    }
)
