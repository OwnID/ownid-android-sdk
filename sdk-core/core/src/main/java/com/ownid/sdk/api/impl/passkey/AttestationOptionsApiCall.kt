package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AttestationOptionsRequest
import com.ownid.sdk.api.impl.models.AttestationOptionsResponse
import com.ownid.sdk.api.impl.models.LoginId
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
import com.ownid.sdk.model.AttestationConveyancePreference
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.KeyAlgorithmType
import com.ownid.sdk.model.PublicKeyCredentialDescriptor
import com.ownid.sdk.model.ResidentKey
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.TransportType
import com.ownid.sdk.model.UserVerification
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.MaximumChallengesReachedErrorResponse as ApiMaximumChallengesReachedErrorResponse

internal class AttestationOptionsApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<AttestationOptions, PasskeyAttestationStartApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AttestationOptions, PasskeyAttestationStartApiFailure> =
        when (successResponse.code) {
            200 -> runApiMappingCatching {
                val response = serialization.decodeFromString(AttestationOptionsResponse.serializer(), successResponse.body)
                WebAuthnOptionsValidation.validateAttestationOptionsResponse(response)
                ApiResult.Success(
                    AttestationOptions(
                        rp = response.rp.toDomainModel(),
                        user = response.user.toDomainModel(),
                        challenge = ChallengeId(response.challenge.value),
                        pubKeyCredParams = response.pubKeyCredParams.map { it.toDomainModel() }.ifEmpty {
                            listOf(
                                AttestationOptions.PubKeyCredParams(type = CredentialType.PUBLIC_KEY, alg = KeyAlgorithmType.ES256),
                                AttestationOptions.PubKeyCredParams(type = CredentialType.PUBLIC_KEY, alg = KeyAlgorithmType.RS256)
                            )
                        },
                        attestation = response.attestation?.toDomainModel(),
                        authenticatorSelection = response.authenticatorSelection?.toDomainModel(),
                        timeout = response.timeout?.let { Timeout(it.value) },
                        excludeCredentials = response.excludeCredentials?.map { it.toDomainModel() }
                    )
                )
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyAttestationStartApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadLoginIdRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyAttestationStartApiFailure.BadRequest::InvalidArgument,
                loginIdValidation = PasskeyAttestationStartApiFailure.BadRequest::InvalidLoginId,
                loginIdTypeNotSupported = PasskeyAttestationStartApiFailure.BadRequest::UnsupportedLoginIdType,
                unknown = PasskeyAttestationStartApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PasskeyAttestationStartApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PasskeyAttestationStartApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = PasskeyAttestationStartApiFailure.FailedDependency::ProviderFailed,
                missingProvider = PasskeyAttestationStartApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected, error)
        }

        429 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiMaximumChallengesReachedErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "maximum_challenges_reached") { "Unexpected errorCode=${response.errorCode.value} for maximum_challenges_reached response" }
            PasskeyAttestationStartApiFailure.MaximumChallengesReached(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyAttestationStartApiFailure =
        failResponse.toUnexpectedFailure(PasskeyAttestationStartApiFailure::Unexpected)

    private fun com.ownid.sdk.api.impl.models.RelayingParty.toDomainModel(): AttestationOptions.RelayingParty = AttestationOptions.RelayingParty(
        id = id,
        name = name
    )

    private fun com.ownid.sdk.api.impl.models.Fido2User.toDomainModel(): AttestationOptions.User = AttestationOptions.User(
        id = id,
        name = name,
        displayName = displayName
    )

    private fun com.ownid.sdk.api.impl.models.AttestationOptionsResponsePubKeyCredParamsItem.toDomainModel(): AttestationOptions.PubKeyCredParams =
        AttestationOptions.PubKeyCredParams(
            type = when (type) {
                com.ownid.sdk.api.impl.models.CredentialType.PUBLIC_KEY -> CredentialType.PUBLIC_KEY
            },
            alg = when (alg) {
                com.ownid.sdk.api.impl.models.KeyAlgorithmType.NUMBER_7 -> KeyAlgorithmType.ES256
                com.ownid.sdk.api.impl.models.KeyAlgorithmType.NUMBER_257 -> KeyAlgorithmType.RS256
            }
        )

    private fun com.ownid.sdk.api.impl.models.AttestationConveyancePreference.toDomainModel(): AttestationConveyancePreference = when (this) {
        com.ownid.sdk.api.impl.models.AttestationConveyancePreference.NONE -> AttestationConveyancePreference.NONE
        com.ownid.sdk.api.impl.models.AttestationConveyancePreference.DIRECT -> AttestationConveyancePreference.DIRECT
        com.ownid.sdk.api.impl.models.AttestationConveyancePreference.INDIRECT -> AttestationConveyancePreference.INDIRECT
        com.ownid.sdk.api.impl.models.AttestationConveyancePreference.ENTERPRISE -> AttestationConveyancePreference.ENTERPRISE
    }

    private fun com.ownid.sdk.api.impl.models.AttestationOptionsResponseAuthenticatorSelection.toDomainModel():
            AttestationOptions.AuthenticatorSelection = AttestationOptions.AuthenticatorSelection(
        authenticatorAttachment = authenticatorAttachment?.let {
            when (it) {
                com.ownid.sdk.api.impl.models.AuthenticatorAttachment.PLATFORM -> AuthenticatorAttachment.PLATFORM
                com.ownid.sdk.api.impl.models.AuthenticatorAttachment.CROSS_PLATFORM -> AuthenticatorAttachment.CROSS_PLATFORM
            }
        },
        userVerification = userVerification?.let {
            when (it) {
                com.ownid.sdk.api.impl.models.UserVerification.PREFERRED -> UserVerification.PREFERRED
                com.ownid.sdk.api.impl.models.UserVerification.REQUIRED -> UserVerification.REQUIRED
                com.ownid.sdk.api.impl.models.UserVerification.DISCOURAGED -> UserVerification.DISCOURAGED
            }
        },
        residentKey = residentKey?.let {
            when (it) {
                com.ownid.sdk.api.impl.models.ResidentKey.REQUIRED -> ResidentKey.REQUIRED
                com.ownid.sdk.api.impl.models.ResidentKey.PREFERRED -> ResidentKey.PREFERRED
                com.ownid.sdk.api.impl.models.ResidentKey.DISCOURAGED -> ResidentKey.DISCOURAGED
            }
        }
    )

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

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            loginId: com.ownid.sdk.model.LoginId?,
            accountDisplayName: String?,
            accessToken: AccessToken?,
            traceParent: String?
        ): AttestationOptionsApiCall {

            val requestBody = if (loginId != null || accountDisplayName != null) {
                val apiLoginId = loginId?.let { LoginId(id = it.id, type = it.type.toApiModel()) }
                AttestationOptionsRequest(apiLoginId, accountDisplayName)
            } else {
                null
            }

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/attestation/options").build()
            ).apply {
                requestBody?.let { body = serialization.encodeToString(AttestationOptionsRequest.serializer(), it) }
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return AttestationOptionsApiCall(serialization, request)
        }
    }
}
