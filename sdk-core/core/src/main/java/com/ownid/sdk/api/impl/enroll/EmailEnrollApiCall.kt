package com.ownid.sdk.api.impl.enroll

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailEnrollApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.EnrollRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadEnrollRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapFailedDependencyErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.UserNotFoundErrorResponse as ApiUserNotFoundErrorResponse

internal class EmailEnrollApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<Unit, EmailEnrollApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, EmailEnrollApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): EmailEnrollApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadEnrollRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = EmailEnrollApiFailure.BadRequest::InvalidArgument,
                unknown = EmailEnrollApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            EmailEnrollApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            EmailEnrollApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected, error)
        }

        404 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUserNotFoundErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "user_not_found") { "Unexpected errorCode=${response.errorCode.value} for user_not_found response" }
            EmailEnrollApiFailure.UserNotFound(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = EmailEnrollApiFailure.FailedDependency::ProviderFailed,
                missingProvider = EmailEnrollApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): EmailEnrollApiFailure =
        failResponse.toUnexpectedFailure(EmailEnrollApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            proofToken: ProofToken,
            accessToken: AccessToken?,
            traceParent: String?
        ): EmailEnrollApiCall {

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("verifications/email/enroll").build()
            ).apply {
                body = serialization.encodeToString(
                    EnrollRequest.serializer(),
                    EnrollRequest(proofToken.token)
                )
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return EmailEnrollApiCall(serialization, request)
        }
    }
}
