package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.CancelReason
import com.ownid.sdk.api.impl.models.PasskeyCancelRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Reason
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId

internal class AssertionCancelApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<Unit, PasskeyAssertionCancelApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, PasskeyAssertionCancelApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyAssertionCancelApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyAssertionCancelApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = PasskeyAssertionCancelApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = PasskeyAssertionCancelApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = PasskeyAssertionCancelApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionCancelApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyAssertionCancelApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyAssertionCancelApiFailure =
        failResponse.toUnexpectedFailure(PasskeyAssertionCancelApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challenge: ChallengeId,
            reason: Reason?,
            traceParent: String?
        ): AssertionCancelApiCall {

            val cancelReason = reason.toApiCancelReason()

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/assertion/cancel").build()
            ).apply {
                body = serialization.encodeToString(PasskeyCancelRequest.serializer(), PasskeyCancelRequest(ApiChallengeId(challenge.value), cancelReason))
                queueTraceParent(traceParent)
            }.build()

            return AssertionCancelApiCall(serialization, request)
        }

        private fun Reason?.toApiCancelReason(): CancelReason = when (this) {
            is Reason.Timeout -> CancelReason.TIMEOUT
            is Reason.UserClose -> CancelReason.USER_CLOSE
            is Reason.MoveToOtherChallenge -> CancelReason.MOVE_TO_OTHER_CHALLENGE
            is Reason.SystemError -> CancelReason.SYSTEM_ERROR
            is Reason.AlreadyExists -> CancelReason.ALREADY_EXISTS
            is Reason.Unknown, null -> CancelReason.UNKNOWN
        }
    }
}
