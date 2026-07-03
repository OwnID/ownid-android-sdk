package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PhoneVerificationCancelApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.CancelVerificationRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Reason
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId

internal class PhoneVerificationCancelApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<Unit, PhoneVerificationCancelApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, PhoneVerificationCancelApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PhoneVerificationCancelApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PhoneVerificationCancelApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = PhoneVerificationCancelApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = PhoneVerificationCancelApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = PhoneVerificationCancelApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationCancelApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PhoneVerificationCancelApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PhoneVerificationCancelApiFailure =
        failResponse.toUnexpectedFailure(PhoneVerificationCancelApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challengeId: ChallengeId,
            accessToken: AccessToken?,
            reason: Reason?,
            traceParent: String?
        ): PhoneVerificationCancelApiCall {

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
                url = baseApiUrl.newBuilder().addEncodedPathSegments("verifications/phone/cancel").build()
            ).apply {
                body = serialization.encodeToString(
                    CancelVerificationRequest.serializer(),
                    CancelVerificationRequest(ApiChallengeId(challengeId.value), cancelReason)
                )
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return PhoneVerificationCancelApiCall(serialization, request)
        }
    }
}
