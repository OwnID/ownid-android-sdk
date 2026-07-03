package com.ownid.sdk.api.impl.enroll

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AttestationEnrollRequest
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

internal class PasskeyEnrollApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<Unit, PasskeyEnrollApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, PasskeyEnrollApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyEnrollApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadEnrollRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyEnrollApiFailure.BadRequest::InvalidArgument,
                unknown = PasskeyEnrollApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PasskeyEnrollApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PasskeyEnrollApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected, error)
        }

        404 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUserNotFoundErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "user_not_found") { "Unexpected errorCode=${response.errorCode.value} for user_not_found response" }
            PasskeyEnrollApiFailure.UserNotFound(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = PasskeyEnrollApiFailure.FailedDependency::ProviderFailed,
                missingProvider = PasskeyEnrollApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyEnrollApiFailure =
        failResponse.toUnexpectedFailure(PasskeyEnrollApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            proofToken: ProofToken,
            accessToken: AccessToken?,
            traceParent: String?
        ): PasskeyEnrollApiCall {

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/attestation/enroll").build()
            ).apply {
                body = serialization.encodeToString(
                    AttestationEnrollRequest.serializer(),
                    AttestationEnrollRequest(proofToken.token)
                )
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return PasskeyEnrollApiCall(serialization, request)
        }
    }
}
