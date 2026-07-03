package com.ownid.sdk.api.impl.oidc

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcCancelApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.CancelOidcChallengeRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.Reason
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse

internal class OidcChallengeCancelApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<Unit, OidcCancelApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, OidcCancelApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): OidcCancelApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = OidcCancelApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = OidcCancelApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = OidcCancelApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = OidcCancelApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcCancelApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            OidcCancelApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            OidcCancelApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcCancelApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(OidcCancelApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): OidcCancelApiFailure =
        failResponse.toUnexpectedFailure(OidcCancelApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challengeId: ChallengeId,
            reason: Reason?,
            accessToken: AccessToken?,
            traceParent: String?
        ): OidcChallengeCancelApiCall {

            val cancelReason = when (reason) {
                is Reason.Timeout -> com.ownid.sdk.api.impl.models.CancelReason.TIMEOUT
                is Reason.UserClose -> com.ownid.sdk.api.impl.models.CancelReason.USER_CLOSE
                is Reason.MoveToOtherChallenge -> com.ownid.sdk.api.impl.models.CancelReason.MOVE_TO_OTHER_CHALLENGE
                is Reason.SystemError -> com.ownid.sdk.api.impl.models.CancelReason.SYSTEM_ERROR
                is Reason.Unknown -> com.ownid.sdk.api.impl.models.CancelReason.UNKNOWN
                is Reason.AlreadyExists -> com.ownid.sdk.api.impl.models.CancelReason.ALREADY_EXISTS
                null -> null
            }

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("oidc/idp/cancel").build()
            ).apply {
                body = serialization.encodeToString(
                    CancelOidcChallengeRequest.serializer(), CancelOidcChallengeRequest(ApiChallengeId(challengeId.value), cancelReason)
                )
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return OidcChallengeCancelApiCall(serialization, request)
        }
    }
}
