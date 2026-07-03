package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AccessOrProofTokensResponse
import com.ownid.sdk.api.impl.models.CompleteVerificationRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadCompleteVerificationRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse

internal class EmailVerificationCompleteApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<AccessOrProofToken, EmailVerificationCompleteApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure> =
        when (successResponse.code) {
            200 -> runApiMappingCatching {
                val response = serialization.decodeFromString(AccessOrProofTokensResponse.serializer(), successResponse.body)

                when {
                    response.accessToken != null -> ApiResult.Success(AccessToken(response.accessToken))
                    response.proofToken != null -> ApiResult.Success(ProofToken(response.proofToken))
                    else -> ApiResult.Failure(
                        mapUnhandled(successResponse.toSuccessMappingFail(message = "Malformed verification complete response"))
                    )
                }
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): EmailVerificationCompleteApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadCompleteVerificationRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = EmailVerificationCompleteApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = EmailVerificationCompleteApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = EmailVerificationCompleteApiFailure.BadRequest::MaximumAttemptsReached,
                verificationCodeWrong = EmailVerificationCompleteApiFailure.BadRequest::WrongCode,
                unknown = EmailVerificationCompleteApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailVerificationCompleteApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            EmailVerificationCompleteApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            EmailVerificationCompleteApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailVerificationCompleteApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(EmailVerificationCompleteApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): EmailVerificationCompleteApiFailure =
        failResponse.toUnexpectedFailure(EmailVerificationCompleteApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challengeId: ChallengeId,
            code: String,
            accessToken: AccessToken?,
            traceParent: String?
        ): EmailVerificationCompleteApiCall {

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("verifications/email/complete").build()
            ).apply {
                body =
                    serialization.encodeToString(CompleteVerificationRequest.serializer(), CompleteVerificationRequest(ApiChallengeId(challengeId.value), code))
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return EmailVerificationCompleteApiCall(serialization, request)
        }
    }
}
