package com.ownid.sdk.api.impl.oidc

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AccessTokenWithUserInfoResponse
import com.ownid.sdk.api.impl.models.CompleteOidcChallengeRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapFailedDependencyErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ChallengeId as ApiChallengeId
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse

internal class OidcChallengeCompleteWithIdTokenApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<AccessTokenWithUserInfo, OidcCompleteApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure> =
        when (successResponse.code) {
            200 -> runApiMappingCatching {
                val response = serialization.decodeFromString(AccessTokenWithUserInfoResponse.serializer(), successResponse.body)
                ApiResult.Success(
                    AccessTokenWithUserInfo(
                        accessToken = AccessToken(response.accessToken),
                        loginId = response.loginId.toDomainModel(),
                        userInfo = response.userInfo,
                        provider = response.provider.let {
                            when (it) {
                                com.ownid.sdk.api.impl.models.OidcProvider.GOOGLE -> com.ownid.sdk.model.SocialProvider.GOOGLE
                                com.ownid.sdk.api.impl.models.OidcProvider.APPLE -> com.ownid.sdk.model.SocialProvider.APPLE
                            }
                        }
                    )
                )
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(
                        successResponse.toSuccessMappingFail(
                            message = "Failed to parse success response from ${successResponse.url}",
                            cause = error
                        )
                    )
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): OidcCompleteApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = OidcCompleteApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = OidcCompleteApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = OidcCompleteApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = OidcCompleteApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcCompleteApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            OidcCompleteApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            OidcCompleteApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcCompleteApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = OidcCompleteApiFailure.FailedDependency::ProviderFailed,
                missingProvider = OidcCompleteApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcCompleteApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(OidcCompleteApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): OidcCompleteApiFailure =
        failResponse.toUnexpectedFailure(OidcCompleteApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            challengeId: ChallengeId,
            idToken: String,
            accessToken: AccessToken?,
            traceParent: String?
        ): OidcChallengeCompleteWithIdTokenApiCall {

            val requestBody = CompleteOidcChallengeRequest(challengeId = ApiChallengeId(challengeId.value), idToken = idToken)

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("oidc/idp/complete").build()
            ).apply {
                body = serialization.encodeToString(CompleteOidcChallengeRequest.serializer(), requestBody)
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return OidcChallengeCompleteWithIdTokenApiCall(serialization, request)
        }
    }
}
