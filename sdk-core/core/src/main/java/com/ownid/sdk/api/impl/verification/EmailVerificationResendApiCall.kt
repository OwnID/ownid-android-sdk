package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailVerificationResendApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.VerificationRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadResendRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapFailedDependencyErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId

internal class EmailVerificationResendApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<Unit, EmailVerificationResendApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, EmailVerificationResendApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): EmailVerificationResendApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadResendRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = EmailVerificationResendApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = EmailVerificationResendApiFailure.BadRequest::InvalidChallenge,
                maximumResendAttemptsReached = EmailVerificationResendApiFailure.BadRequest::MaximumResendAttemptsReached,
                unknown = EmailVerificationResendApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailVerificationResendApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = EmailVerificationResendApiFailure.FailedDependency::ProviderFailed,
                missingProvider = EmailVerificationResendApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailVerificationResendApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(EmailVerificationResendApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): EmailVerificationResendApiFailure =
        failResponse.toUnexpectedFailure(EmailVerificationResendApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challengeId: ChallengeId,
            accessToken: AccessToken?,
            traceParent: String?
        ): EmailVerificationResendApiCall {

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("verifications/email/resend").build()
            ).apply {
                body = serialization.encodeToString(VerificationRequest.serializer(), VerificationRequest(ApiChallengeId(challengeId.value)))
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return EmailVerificationResendApiCall(serialization, request)
        }
    }
}
