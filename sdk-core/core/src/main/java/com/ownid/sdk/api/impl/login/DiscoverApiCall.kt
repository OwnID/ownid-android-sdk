package com.ownid.sdk.api.impl.login

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.RawJsonObjectFieldExtractor
import com.ownid.sdk.api.impl.models.AuthRequiredResponse
import com.ownid.sdk.api.impl.models.LoginRequest
import com.ownid.sdk.api.impl.models.LoginRequestExtendedClientCapabilities
import com.ownid.sdk.api.impl.models.LoginRequestExtendedClientCapabilitiesPasskeys
import com.ownid.sdk.api.impl.models.oneof.mapBadLoginIdRequestErrorResponse
import com.ownid.sdk.api.impl.models.oneof.mapFailedDependencyErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toApiModel
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthRequirements
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.OperationRequirement
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.LoginId as ApiLoginId
import com.ownid.sdk.api.impl.models.LoginResponse as ApiLoginResponse

internal class DiscoverApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<LoginResponse, DiscoverApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<LoginResponse, DiscoverApiFailure> = runApiMappingCatching {
        when (successResponse.code) {
            201 -> {
                val parsed = serialization.decodeFromString(ApiLoginResponse.serializer(), successResponse.body)
                val response = ApiLoginResponse(
                    accessToken = parsed.accessToken,
                    sessionPayload = RawJsonObjectFieldExtractor.extractRequiredTopLevelRawValue(json = successResponse.body, fieldName = "sessionPayload")
                )
                val result = LoginResponse.Success(AccessToken(response.accessToken), response.sessionPayload)
                ApiResult.Success(result)
            }

            206 -> {
                val response = serialization.decodeFromString(AuthRequiredResponse.serializer(), successResponse.body)

                when {
                    response.authRequirements != null -> ApiResult.Success(
                        LoginResponse.AuthRequired(
                            authRequirements = AuthRequirements(
                                targetScore = response.authRequirements.targetScore,
                                operations = response.authRequirements.operations.map { operation ->
                                    OperationRequirement(
                                        type = operation.type.toDomainModel(),
                                        score = operation.score,
                                        channels = operation.channels?.map { OperationChannel(channel = it.channel, id = it.id) }
                                    )
                                }
                            ),
                            reason = response.reason
                        )
                    )

                    response.accountBlocked != null -> ApiResult.Success(LoginResponse.AccountBlocked(reason = response.reason))

                    response.accountNotFound != null -> ApiResult.Success(LoginResponse.AccountNotFound(reason = response.reason))

                    else -> ApiResult.Failure(mapUnhandled(successResponse.toSuccessMappingFail(message = "Malformed 206 login response")))
                }
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }
    }.getOrElse { error ->
        ApiResult.Failure(
            mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
        )
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): DiscoverApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadLoginIdRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = DiscoverApiFailure.BadRequest::InvalidArgument,
                loginIdValidation = DiscoverApiFailure.BadRequest::InvalidLoginId,
                loginIdTypeNotSupported = DiscoverApiFailure.BadRequest::UnsupportedLoginIdType,
                unknown = DiscoverApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(DiscoverApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            DiscoverApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            DiscoverApiFailure.Forbidden(errorCode = response.errorCode.toDomainModel(), message = response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(DiscoverApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = DiscoverApiFailure.FailedDependency::ProviderFailed,
                missingProvider = DiscoverApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(DiscoverApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(DiscoverApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): DiscoverApiFailure =
        failResponse.toUnexpectedFailure(DiscoverApiFailure::Unexpected)

    internal companion object {

        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            loginId: com.ownid.sdk.model.LoginId,
            traceParent: String?
        ): DiscoverApiCall {

            val requestBody = LoginRequest(
                loginId = ApiLoginId(id = loginId.id, type = loginId.type.toApiModel()),
                extendedClientCapabilities = LoginRequestExtendedClientCapabilities(
                    passkeys = LoginRequestExtendedClientCapabilitiesPasskeys(peek = true)
                )
            )

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("login").build()
            ).apply {
                body = serialization.encodeToString(LoginRequest.serializer(), requestBody)
                queueTraceParent(traceParent)
            }.build()

            return DiscoverApiCall(serialization, request)
        }

    }
}
