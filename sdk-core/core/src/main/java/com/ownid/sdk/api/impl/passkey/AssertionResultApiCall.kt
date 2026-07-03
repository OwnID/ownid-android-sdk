package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AssertionResultRequest
import com.ownid.sdk.api.impl.models.AssertionResultResponse
import com.ownid.sdk.api.impl.models.oneof.mapBadChallengeRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.ErrorCode
import okhttp3.HttpUrl
import okhttp3.Request
import com.ownid.sdk.api.impl.models.AssertionAuthenticatorResponse as ApiAssertionAuthenticatorResponse
import com.ownid.sdk.api.impl.models.AuthenticatorAttachment as ApiAuthenticatorAttachment
import com.ownid.sdk.api.impl.models.CredentialType as ApiCredentialType
import com.ownid.sdk.api.impl.models.ForbiddenErrorResponse as ApiForbiddenErrorResponse

internal class AssertionResultApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<AccessToken, PasskeyAssertionVerifyApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> =
        when (successResponse.code) {
            200 -> runApiMappingCatching {
                val response = serialization.decodeFromString(AssertionResultResponse.serializer(), successResponse.body)
                ApiResult.Success(AccessToken(response.accessToken))
            }.getOrElse { error ->
                ApiResult.Failure(
                    mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
                )
            }

            else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
        }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): PasskeyAssertionVerifyApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadChallengeRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = PasskeyAssertionVerifyApiFailure.BadRequest::InvalidArgument,
                invalidChallenge = PasskeyAssertionVerifyApiFailure.BadRequest::InvalidChallenge,
                maximumAttemptsReached = PasskeyAssertionVerifyApiFailure.BadRequest::MaximumAttemptsReached,
                unknown = PasskeyAssertionVerifyApiFailure.BadRequest::Unknown
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionVerifyApiFailure::Unexpected, error)
        }

        403 -> if (failResponse.body.isBlank()) {
            PasskeyAssertionVerifyApiFailure.Forbidden(ErrorCode.Forbidden, failResponse.toString())
        } else runApiMappingCatching {
            val response = serialization.decodeFromString(ApiForbiddenErrorResponse.serializer(), failResponse.body)
            check(response.errorCode.value == "forbidden") { "Unexpected errorCode=${response.errorCode.value} for forbidden response" }
            PasskeyAssertionVerifyApiFailure.Forbidden(response.errorCode.toDomainModel(), response.message)
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(PasskeyAssertionVerifyApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(PasskeyAssertionVerifyApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): PasskeyAssertionVerifyApiFailure =
        failResponse.toUnexpectedFailure(PasskeyAssertionVerifyApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            assertionResult: AssertionResult,
            accessToken: AccessToken?,
            traceParent: String?
        ): AssertionResultApiCall {

            val assertionResultWithDefaults = AssertionResult(
                id = assertionResult.id,
                type = assertionResult.type,
                response = assertionResult.response,
                authenticatorAttachment = assertionResult.authenticatorAttachment ?: AuthenticatorAttachment.PLATFORM
            )

            val requestBody = AssertionResultRequest(
                id = assertionResultWithDefaults.id,
                type = when (assertionResultWithDefaults.type) {
                    CredentialType.PUBLIC_KEY -> ApiCredentialType.PUBLIC_KEY
                },
                response = ApiAssertionAuthenticatorResponse(
                    clientDataJSON = assertionResultWithDefaults.response.clientDataJSON,
                    authenticatorData = assertionResultWithDefaults.response.authenticatorData,
                    userHandle = assertionResultWithDefaults.response.userHandle,
                    signature = assertionResultWithDefaults.response.signature
                ),
                authenticatorAttachment = when (assertionResultWithDefaults.authenticatorAttachment) {
                    AuthenticatorAttachment.PLATFORM -> ApiAuthenticatorAttachment.PLATFORM
                    AuthenticatorAttachment.CROSS_PLATFORM -> ApiAuthenticatorAttachment.CROSS_PLATFORM
                    null -> ApiAuthenticatorAttachment.PLATFORM
                }
            )

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("passkeys/assertion/result").build()
            ).apply {
                body = serialization.encodeToString(AssertionResultRequest.serializer(), requestBody)
                queueAccessToken(accessToken)
                queueTraceParent(traceParent)
            }.build()

            return AssertionResultApiCall(serialization, request)
        }
    }
}
