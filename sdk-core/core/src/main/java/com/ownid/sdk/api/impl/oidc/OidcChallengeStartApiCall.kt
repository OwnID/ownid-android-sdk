package com.ownid.sdk.api.impl.oidc

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.OidcChallengeResponse
import com.ownid.sdk.api.impl.models.StartOidcChallengeRequest
import com.ownid.sdk.api.impl.models.StartOidcChallengeRequestOauthResponseType
import com.ownid.sdk.api.impl.models.oneof.mapBadRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapFailedDependencyErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.SocialChallenge
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.Timeout
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.MaximumChallengesReachedErrorResponse as ApiMaximumChallengesReachedErrorResponse

internal class OidcChallengeStartApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<SocialChallenge, OidcStartApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<SocialChallenge, OidcStartApiFailure> = when (successResponse.code) {
        201 -> runApiMappingCatching {
            val response = serialization.decodeFromString(OidcChallengeResponse.serializer(), successResponse.body)
            ApiResult.Success(
                SocialChallenge(ChallengeId(response.challengeId.value), Timeout(response.timeout.value), response.clientId, response.challengeUrl)
            )
        }.getOrElse { error ->
            ApiResult.Failure(
                mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
            )
        }

        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): OidcStartApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = OidcStartApiFailure.BadRequest::InvalidArgument,
                unknown = OidcStartApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            OidcStartApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            OidcStartApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = OidcStartApiFailure.FailedDependency::ProviderFailed,
                missingProvider = OidcStartApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected, error)
        }

        429 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiMaximumChallengesReachedErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "maximum_challenges_reached") { "Unexpected errorCode=${response.errorCode.value} for maximum_challenges_reached response" }
            OidcStartApiFailure.MaximumChallengesReached(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): OidcStartApiFailure =
        failResponse.toUnexpectedFailure(OidcStartApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            provider: SocialProvider,
            oauthResponseType: OauthResponseType,
            accessToken: AccessToken?,
            loginIdHint: String?,
            redirectUri: String?,
            traceParent: String?
        ): OidcChallengeStartApiCall {

            val requestBody = StartOidcChallengeRequest(
                oauthResponseType = when (oauthResponseType) {
                    OauthResponseType.CODE -> StartOidcChallengeRequestOauthResponseType.CODE
                    OauthResponseType.ID_TOKEN -> StartOidcChallengeRequestOauthResponseType.ID_TOKEN
                },
                loginIdHint = loginIdHint,
                redirectUri = redirectUri
            )

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder()
                    .addEncodedPathSegments("oidc/idp/start")
                    .addEncodedPathSegment(provider.wireValue.lowercase())
                    .build()
            ).apply {
                body = serialization.encodeToString(StartOidcChallengeRequest.serializer(), requestBody)
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return OidcChallengeStartApiCall(serialization, request)
        }
    }
}
