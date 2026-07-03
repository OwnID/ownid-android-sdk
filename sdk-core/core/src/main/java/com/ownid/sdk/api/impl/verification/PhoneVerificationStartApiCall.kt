package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.StartVerificationRequest
import com.ownid.sdk.api.impl.models.StartVerificationResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadVerificationRequestErrorResponse
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
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.VerificationMethod
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse
import com.ownid.sdk.api.impl.models.LoginId as ApiLoginId
import com.ownid.sdk.api.impl.models.MaximumChallengesReachedErrorResponse as ApiMaximumChallengesReachedErrorResponse
import com.ownid.sdk.api.impl.models.UserNotFoundErrorResponse as ApiUserNotFoundErrorResponse

internal class PhoneVerificationStartApiCall(
    private val serialization: Serialization,
    override val request: Request,
) : ApiCall<VerificationChallenge, PhoneVerificationStartApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<VerificationChallenge, PhoneVerificationStartApiFailure> =
        when (successResponse.code) {
            201 -> runApiMappingCatching {
                val response = serialization.decodeFromString(StartVerificationResponse.serializer(), successResponse.body)
                ApiResult.Success(
                    VerificationChallenge(
                        challengeId = ChallengeId(response.challengeId.value),
                        resendPolicy = VerificationChallenge.ResendPolicy(
                            allow = response.resendPolicy.allow,
                            attempts = response.resendPolicy.attempts,
                            debounce = response.resendPolicy.debounce.coerceAtLeast(1)
                        ),
                        timeout = Timeout(response.timeout.value),
                        attempts = response.attempts,
                        methods = VerificationChallenge.Methods(
                            otp = response.methods.otp?.let { VerificationChallenge.Methods.Otp(length = (it.length ?: 4).coerceAtLeast(4)) },
                            magicLink = response.methods.magicLink?.let { VerificationChallenge.Methods.MagicLink() }
                        ),
                        channel = OperationChannel(channel = response.channel.channel, id = response.channel.id)
                    )
                )
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PhoneVerificationStartApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadVerificationRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PhoneVerificationStartApiFailure.BadRequest::InvalidArgument,
                loginIdValidation = PhoneVerificationStartApiFailure.BadRequest::InvalidLoginId,
                loginIdTypeNotSupported = PhoneVerificationStartApiFailure.BadRequest::UnsupportedLoginIdType,
                missingChannel = PhoneVerificationStartApiFailure.BadRequest::MissingChannel,
                unknown = PhoneVerificationStartApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PhoneVerificationStartApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PhoneVerificationStartApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected, error)
        }

        404 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiUserNotFoundErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "user_not_found") { "Unexpected errorCode=${response.errorCode.value} for user_not_found response" }
            PhoneVerificationStartApiFailure.UserNotFound(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected, error)
        }

        424 -> runApiMappingCatching {
            serialization.mapFailedDependencyErrorResponse(
                body = failResponse.body,
                provider = PhoneVerificationStartApiFailure.FailedDependency::ProviderFailed,
                missingProvider = PhoneVerificationStartApiFailure.FailedDependency::MissingProvider
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected, error)
        }

        429 -> runApiMappingCatching {
            val response = serialization.decodeFromString(ApiMaximumChallengesReachedErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "maximum_challenges_reached") { "Unexpected errorCode=${response.errorCode.value} for maximum_challenges_reached response" }
            PhoneVerificationStartApiFailure.MaximumChallengesReached(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PhoneVerificationStartApiFailure =
        failResponse.toUnexpectedFailure(PhoneVerificationStartApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            loginId: com.ownid.sdk.model.LoginId?,
            loginIdHintId: String?,
            accessToken: AccessToken?,
            verificationMethods: Set<VerificationMethod>?,
            magicLinkRedirectUrl: String?,
            traceParent: String?
        ): PhoneVerificationStartApiCall {

            val methods = verificationMethods?.map {
                when (it) {
                    VerificationMethod.OTP -> com.ownid.sdk.api.impl.models.VerificationMethod.OTP
                    VerificationMethod.MAGIC_LINK -> com.ownid.sdk.api.impl.models.VerificationMethod.MAGIC_LINK
                }
            }

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("verifications/phone/start").build()
            ).apply {
                body = serialization.encodeToString(
                    StartVerificationRequest.serializer(),
                    StartVerificationRequest(
                        loginId = loginId?.let { ApiLoginId(id = it.id, type = it.type.toApiModel()) },
                        loginIdHintId = loginIdHintId,
                        verificationMethods = methods,
                        magicLinkRedirectUrl = magicLinkRedirectUrl
                    )
                )
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return PhoneVerificationStartApiCall(serialization, request)
        }
    }
}
