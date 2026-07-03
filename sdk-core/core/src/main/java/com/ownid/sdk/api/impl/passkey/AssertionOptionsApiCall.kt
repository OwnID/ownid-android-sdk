package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AssertionOptionsRequest
import com.ownid.sdk.api.impl.models.AssertionOptionsResponse
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
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.PublicKeyCredentialDescriptor
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.TransportType
import com.ownid.sdk.model.UserVerification
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.LoginId as ApiLoginId
import com.ownid.sdk.api.impl.models.MaximumChallengesReachedErrorResponse as ApiMaximumChallengesReachedErrorResponse
import com.ownid.sdk.api.impl.models.UserNotFoundErrorResponse as ApiUserNotFoundErrorResponse

internal class AssertionOptionsApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<AssertionOptions, PasskeyAssertionStartApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AssertionOptions, PasskeyAssertionStartApiFailure> =
        when (successResponse.code) {
            201 -> runApiMappingCatching {
                val response = serialization.decodeFromString(AssertionOptionsResponse.serializer(), successResponse.body)
                WebAuthnOptionsValidation.validateAssertionOptionsResponse(response)
                ApiResult.Success(
                    AssertionOptions(
                        challenge = ChallengeId(response.challenge.value),
                        rpId = response.rpId,
                        allowCredentials = response.allowCredentials?.map { it.toDomainModel() },
                        userVerification = response.userVerification?.toDomainModel(),
                        timeout = response.timeout?.let { Timeout(it.value) }
                    )
                )
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(
                        successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error)
                    )
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyAssertionStartApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadLoginIdRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyAssertionStartApiFailure.BadRequest::InvalidArgument,
                loginIdValidation = PasskeyAssertionStartApiFailure.BadRequest::InvalidLoginId,
                loginIdTypeNotSupported = PasskeyAssertionStartApiFailure.BadRequest::UnsupportedLoginIdType,
                unknown = PasskeyAssertionStartApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PasskeyAssertionStartApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PasskeyAssertionStartApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected, error)
        }

        404 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUserNotFoundErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "user_not_found") { "Unexpected errorCode=${response.errorCode.value} for user_not_found response" }
            PasskeyAssertionStartApiFailure.UserNotFound(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = PasskeyAssertionStartApiFailure.FailedDependency::ProviderFailed,
                missingProvider = PasskeyAssertionStartApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected, error)
        }

        429 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiMaximumChallengesReachedErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "maximum_challenges_reached") { "Unexpected errorCode=${response.errorCode.value} for maximum_challenges_reached response" }
            PasskeyAssertionStartApiFailure.MaximumChallengesReached(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyAssertionStartApiFailure =
        failResponse.toUnexpectedFailure(PasskeyAssertionStartApiFailure::Unexpected)

    private fun com.ownid.sdk.api.impl.models.PublicKeyCredentialDescriptor.toDomainModel(): PublicKeyCredentialDescriptor = PublicKeyCredentialDescriptor(
        type = when (type) {
            com.ownid.sdk.api.impl.models.CredentialType.PUBLIC_KEY -> CredentialType.PUBLIC_KEY
        },
        id = id,
        transports = transports?.map {
            when (it) {
                com.ownid.sdk.api.impl.models.TransportType.USB -> TransportType.USB
                com.ownid.sdk.api.impl.models.TransportType.NFC -> TransportType.NFC
                com.ownid.sdk.api.impl.models.TransportType.BLE -> TransportType.BLE
                com.ownid.sdk.api.impl.models.TransportType.SMART_CARD -> TransportType.SMART_CARD
                com.ownid.sdk.api.impl.models.TransportType.HYBRID -> TransportType.HYBRID
                com.ownid.sdk.api.impl.models.TransportType.INTERNAL -> TransportType.INTERNAL
                com.ownid.sdk.api.impl.models.TransportType.CABLE -> TransportType.CABLE
            }
        }
    )

    private fun com.ownid.sdk.api.impl.models.UserVerification.toDomainModel(): UserVerification = when (this) {
        com.ownid.sdk.api.impl.models.UserVerification.PREFERRED -> UserVerification.PREFERRED
        com.ownid.sdk.api.impl.models.UserVerification.REQUIRED -> UserVerification.REQUIRED
        com.ownid.sdk.api.impl.models.UserVerification.DISCOURAGED -> UserVerification.DISCOURAGED
    }

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            loginId: com.ownid.sdk.model.LoginId?,
            accessToken: AccessToken?,
            traceParent: String?
        ): AssertionOptionsApiCall {

            val requestBody = loginId?.let { AssertionOptionsRequest(loginId = ApiLoginId(id = it.id, type = it.type.toApiModel())) }

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/assertion/options").build()
            ).apply {
                requestBody?.let { body = serialization.encodeToString(AssertionOptionsRequest.serializer(), it) }
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return AssertionOptionsApiCall(serialization, request)
        }
    }
}
