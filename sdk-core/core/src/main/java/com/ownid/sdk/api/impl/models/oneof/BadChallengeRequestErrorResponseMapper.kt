package com.ownid.sdk.api.impl.models.oneof

import com.ownid.sdk.api.impl.models.InvalidArgumentErrorResponse
import com.ownid.sdk.api.impl.models.InvalidChallengeErrorResponse
import com.ownid.sdk.api.impl.models.MaximumAttemptsReachedErrorResponse
import com.ownid.sdk.api.impl.models.UnknownErrorResponse
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode

/**
 * Maps the OpenAPI `BadChallengeRequestErrorResponse` to an API-layer failure.
 *
 * Bad request against an existing challenge.
 *
 * The server selects the concrete body schema through the `errorCode` value.
 */
internal fun <T> Serialization.mapBadChallengeRequestErrorResponse(
    body: String,
    invalidArgument: (ErrorCode, String) -> T,
    invalidChallenge: (ErrorCode, String, ChallengeId) -> T,
    maximumAttemptsReached: (ErrorCode, String, ChallengeId) -> T,
    unknown: (ErrorCode, String) -> T
): T = mapErrorResponse(
    body = body,
    expected = "BadChallengeRequestErrorResponse",
    case(ApiErrorCode.INVALID_ARGUMENT, InvalidArgumentErrorResponse.serializer()) { response ->
        invalidArgument(response.errorCode.toDomainModel(), response.message)
    },
    case(ApiErrorCode.INVALID_CHALLENGE, InvalidChallengeErrorResponse.serializer()) { response ->
        invalidChallenge(response.errorCode.toDomainModel(), response.message, ChallengeId(response.challengeId.value))
    },
    case(ApiErrorCode.MAXIMUM_ATTEMPTS_REACHED, MaximumAttemptsReachedErrorResponse.serializer()) { response ->
        maximumAttemptsReached(response.errorCode.toDomainModel(), response.message, ChallengeId(response.challengeId.value))
    },
    case(ApiErrorCode.UNKNOWN, UnknownErrorResponse.serializer()) { response ->
        unknown(response.errorCode.toDomainModel(), response.message)
    }
)
