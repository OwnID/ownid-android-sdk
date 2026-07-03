package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.RawJsonObjectFieldExtractor
import com.ownid.sdk.api.impl.models.AttestationResultRequest
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.TransportType
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.AttestationAuthenticatorResponse as ApiAttestationAuthenticatorResponse
import com.ownid.sdk.api.impl.models.AttestationResultResponse as ApiAttestationResultResponse
import com.ownid.sdk.api.impl.models.AuthenticatorAttachment as ApiAuthenticatorAttachment
import com.ownid.sdk.api.impl.models.CredentialType as ApiCredentialType
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.TransportType as ApiTransportType
import com.ownid.sdk.api.impl.models.UnauthorizedErrorResponse as ApiUnauthorizedErrorResponse
import com.ownid.sdk.api.impl.models.UserNotFoundErrorResponse as ApiUserNotFoundErrorResponse

internal class AttestationResultApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<AttestationResponse, PasskeyAttestationVerifyApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure> =
        when (successResponse.code) {
            200 -> runApiMappingCatching {
                val parsed = serialization.decodeFromString(ApiAttestationResultResponse.serializer(), successResponse.body)
                val response = ApiAttestationResultResponse(
                    ownIdData = RawJsonObjectFieldExtractor.extractRequiredTopLevelRawValue(json = successResponse.body, fieldName = "ownIdData"),
                    proofToken = parsed.proofToken
                )

                ApiResult.Success(
                    AttestationResponse(
                        proofToken = ProofToken(response.proofToken),
                        ownIdData = response.ownIdData
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

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyAttestationVerifyApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyAttestationVerifyApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = PasskeyAttestationVerifyApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = PasskeyAttestationVerifyApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = PasskeyAttestationVerifyApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected, error)
        }

        401 -> if (failResponse.body.isBlank()) {
            PasskeyAttestationVerifyApiFailure.Unauthorized(ErrorCode.Unauthorized, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUnauthorizedErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "unauthorized") { "Unexpected errorCode=${response.errorCode.value} for unauthorized response" }
            PasskeyAttestationVerifyApiFailure.Unauthorized(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PasskeyAttestationVerifyApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PasskeyAttestationVerifyApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected, error)
        }

        404 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUserNotFoundErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "user_not_found") { "Unexpected errorCode=${response.errorCode.value} for user_not_found response" }
            PasskeyAttestationVerifyApiFailure.UserNotFound(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyAttestationVerifyApiFailure =
        failResponse.toUnexpectedFailure(PasskeyAttestationVerifyApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            attestationResult: AttestationResult,
            accessToken: AccessToken?,
            traceParent: String?
        ): AttestationResultApiCall {
            val requestBody = AttestationResultRequest(
                id = attestationResult.id,
                type = when (attestationResult.type) {
                    CredentialType.PUBLIC_KEY -> ApiCredentialType.PUBLIC_KEY
                },
                response = ApiAttestationAuthenticatorResponse(
                    clientDataJSON = attestationResult.response.clientDataJSON,
                    attestationObject = attestationResult.response.attestationObject,
                    transports = attestationResult.response.transports.map {
                        when (it) {
                            TransportType.USB -> ApiTransportType.USB
                            TransportType.NFC -> ApiTransportType.NFC
                            TransportType.BLE -> ApiTransportType.BLE
                            TransportType.SMART_CARD -> ApiTransportType.SMART_CARD
                            TransportType.HYBRID -> ApiTransportType.HYBRID
                            TransportType.INTERNAL -> ApiTransportType.INTERNAL
                            TransportType.CABLE -> ApiTransportType.CABLE
                        }
                    }
                ),
                authenticatorAttachment = attestationResult.authenticatorAttachment?.let {
                    when (it) {
                        AuthenticatorAttachment.PLATFORM -> ApiAuthenticatorAttachment.PLATFORM
                        AuthenticatorAttachment.CROSS_PLATFORM -> ApiAuthenticatorAttachment.CROSS_PLATFORM
                    }
                }
            )

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/attestation/result").build()
            ).apply {
                body = serialization.encodeToString(AttestationResultRequest.serializer(), requestBody)
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return AttestationResultApiCall(serialization, request)
        }
    }
}
