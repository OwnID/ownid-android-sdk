package com.ownid.sdk.api.impl

import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Network
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode
import com.ownid.sdk.api.impl.models.LoginId as ApiLoginId
import com.ownid.sdk.api.impl.models.LoginIdType as ApiLoginIdType
import com.ownid.sdk.api.impl.models.OperationType as ApiOperationType
import com.ownid.sdk.api.impl.models.ScopeType as ApiScopeType

/**
 * Runs an API implementation block on this dispatcher and converts local SDK exceptions into an unexpected failure.
 *
 * Coroutine cancellation is rethrown so Android direct APIs keep cancellation outside [ApiResult].
 */
internal suspend inline fun <ApiSuccess, ApiFailure> CoroutineDispatcher.runApiCatching(
    crossinline unexpectedFailure: (ErrorCode, String, ApiUnexpectedCause) -> ApiFailure,
    crossinline block: suspend () -> ApiResult<ApiSuccess, ApiFailure>
): ApiResult<ApiSuccess, ApiFailure> = withContext(this) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ApiResult.Failure(failure = unexpectedFailure(ErrorCode.Unknown, error.message ?: error::class.java.simpleName, ApiUnexpectedCause.Runtime(error)))
    }
}

internal inline fun <T> runApiMappingCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Exception) {
    Result.failure(error)
}

internal fun Network.Response.Success.toUnexpectedStatusFail(): Network.Response.Fail.ResponseError =
    Network.Response.Fail.ResponseError(url, code, IllegalStateException("Unexpected status code: $code"), headers, body)

internal fun Network.Response.Success.toSuccessMappingFail(message: String, cause: Throwable? = null): Network.Response.Fail.ResponseError =
    Network.Response.Fail.ResponseError(url, code, IllegalStateException(message, cause), headers, body)

/**
 * Maps a network-layer failure into the endpoint's unexpected failure type.
 *
 * Transport failures surface as [ErrorCode.Network]. Unmapped endpoint or response-shape failures surface as
 * [ErrorCode.Unknown] because they cannot be represented by a more specific typed failure.
 */
internal inline fun <ApiFailure> Network.Response.Fail.toUnexpectedFailure(
    create: (ErrorCode, String, ApiUnexpectedCause) -> ApiFailure,
    error: Throwable? = null
): ApiFailure = when (this) {
    is Network.Response.Fail.NetworkError ->
        create(ErrorCode.Network, toString(), ApiUnexpectedCause.Network(this))

    is Network.Response.Fail.HttpError ->
        create(ErrorCode.Unknown, error?.message ?: toString(), ApiUnexpectedCause.ApiContract(this, error))

    is Network.Response.Fail.ResponseError ->
        create(ErrorCode.Unknown, error?.message ?: this.error.message ?: toString(), ApiUnexpectedCause.ApiContract(this, error))
}

internal fun ApiErrorCode.toDomainModel(): ErrorCode = when (this) {
    ApiErrorCode.ABORTED -> ErrorCode.Aborted
    ApiErrorCode.CANCEL_NOT_SUPPORTED -> ErrorCode.CancelNotSupported
    ApiErrorCode.DEVICE_NOT_SUPPORTED -> ErrorCode.DeviceNotSupported
    ApiErrorCode.DOM_ELEMENT_NOT_FOUND -> ErrorCode.DomElementNotFound
    ApiErrorCode.EMPTY_LOGIN_ID -> ErrorCode.EmptyLoginId
    ApiErrorCode.FORBIDDEN -> ErrorCode.Forbidden
    ApiErrorCode.INTEGRATION_ERROR -> ErrorCode.IntegrationError
    ApiErrorCode.INVALID_ARGUMENT -> ErrorCode.InvalidArgument
    ApiErrorCode.INVALID_CHALLENGE -> ErrorCode.InvalidChallenge
    ApiErrorCode.LOGIN_ID_TYPE_NOT_SUPPORTED -> ErrorCode.LoginIdTypeNotSupported
    ApiErrorCode.LOGIN_ID_VALIDATION_FAILED -> ErrorCode.LoginIdValidationFailed
    ApiErrorCode.LOGIN_WITH_PASSWORD_FAILED -> ErrorCode.LoginWithPasswordFailed
    ApiErrorCode.MAXIMUM_ATTEMPTS_REACHED -> ErrorCode.MaximumAttemptsReached
    ApiErrorCode.MAXIMUM_CHALLENGES_REACHED -> ErrorCode.MaximumChallengesReached
    ApiErrorCode.MAXIMUM_RESEND_ATTEMPTS_REACHED -> ErrorCode.MaximumResendAttemptsReached
    ApiErrorCode.MISSING_CAPABILITY_PROVIDER -> ErrorCode.MissingCapabilityProvider
    ApiErrorCode.MISSING_CHANNEL -> ErrorCode.MissingChannel
    ApiErrorCode.NETWORK -> ErrorCode.Network
    ApiErrorCode.NO_APPLICABLE_PASSKEYS -> ErrorCode.NoApplicablePasskeys
    ApiErrorCode.NOTIFICATION_BLOCKED -> ErrorCode.NotificationBlocked
    ApiErrorCode.OIDC_FAILED -> ErrorCode.OidcFailed
    ApiErrorCode.PASSKEY_ALREADY_REGISTERED -> ErrorCode.PasskeyAlreadyRegistered
    ApiErrorCode.PASSKEY_NOT_CREATED -> ErrorCode.PasskeyNotCreated
    ApiErrorCode.PASSKEYS_NOT_SUPPORTED -> ErrorCode.PasskeysNotSupported
    ApiErrorCode.SCREENS_NOT_READY -> ErrorCode.ScreensNotReady
    ApiErrorCode.SESSION_NOT_ESTABLISHED -> ErrorCode.SessionNotEstablished
    ApiErrorCode.TIMEOUT -> ErrorCode.Timeout
    ApiErrorCode.UNAUTHORIZED -> ErrorCode.Unauthorized
    ApiErrorCode.UNKNOWN -> ErrorCode.Unknown
    ApiErrorCode.USER_BLOCKED -> ErrorCode.UserBlocked
    ApiErrorCode.USER_CHANGED -> ErrorCode.UserChanged
    ApiErrorCode.USER_NOT_FOUND -> ErrorCode.UserNotFound
    ApiErrorCode.VERIFICATION_CODE_WRONG -> ErrorCode.VerificationCodeWrong
    ApiErrorCode.WIDGET_ALREADY_EXISTS -> ErrorCode.WidgetAlreadyExists
}

internal fun ApiLoginId.toDomainModel(): LoginId = LoginId(id = id, type = type.toDomainModel())

internal fun ApiLoginIdType.toDomainModel(): LoginId.Type = when (this) {
    ApiLoginIdType.ANONYMOUS -> LoginId.Type.Anonymous
    ApiLoginIdType.USER_NAME -> LoginId.Type.UserName
    ApiLoginIdType.EMAIL -> LoginId.Type.Email
    ApiLoginIdType.PHONE_NUMBER -> LoginId.Type.PhoneNumber
    ApiLoginIdType.CREDENTIAL_ID -> LoginId.Type.CredentialId
    ApiLoginIdType.FACE_KEY_PERSON_ID -> LoginId.Type.FaceKeyPersonId
}

internal fun LoginId.Type.toApiModel(): ApiLoginIdType = when (this) {
    LoginId.Type.UserName -> ApiLoginIdType.USER_NAME
    LoginId.Type.Email -> ApiLoginIdType.EMAIL
    LoginId.Type.PhoneNumber -> ApiLoginIdType.PHONE_NUMBER
    LoginId.Type.CredentialId -> ApiLoginIdType.CREDENTIAL_ID
    LoginId.Type.Anonymous -> ApiLoginIdType.ANONYMOUS
    LoginId.Type.FaceKeyPersonId -> ApiLoginIdType.FACE_KEY_PERSON_ID
}

internal fun ApiOperationType.toDomainModel(): OperationType = when (this) {
    ApiOperationType.LOGIN_ID_COLLECT -> OperationType.LOGIN_ID_COLLECT
    ApiOperationType.EMAIL_VERIFICATION -> OperationType.EMAIL_VERIFICATION
    ApiOperationType.EMAIL_ENROLLMENT -> OperationType.EMAIL_ENROLLMENT
    ApiOperationType.PHONE_NUMBER_VERIFICATION -> OperationType.PHONE_NUMBER_VERIFICATION
    ApiOperationType.PHONE_NUMBER_ENROLLMENT -> OperationType.PHONE_NUMBER_ENROLLMENT
    ApiOperationType.PASSKEY_CREATION -> OperationType.PASSKEY_CREATION
    ApiOperationType.PASSKEY_AUTH -> OperationType.PASSKEY_AUTH
    ApiOperationType.PASSKEY_ENROLLMENT -> OperationType.PASSKEY_ENROLLMENT
    ApiOperationType.SESSION_CREATION -> OperationType.SESSION_CREATION
    ApiOperationType.DEFERRED_AUTHENTICATION -> OperationType.DEFERRED_AUTHENTICATION
    ApiOperationType.EXTERNAL_AUTHENTICATION -> OperationType.EXTERNAL_AUTHENTICATION
    ApiOperationType.PROFILE_COLLECTION -> OperationType.PROFILE_COLLECTION
    ApiOperationType.PASSWORD_AUTHENTICATION -> OperationType.PASSWORD_AUTHENTICATION
    ApiOperationType.OIDC_AUTHENTICATION_APPLE -> OperationType.OIDC_AUTHENTICATION_APPLE
    ApiOperationType.OIDC_AUTHENTICATION_GOOGLE -> OperationType.OIDC_AUTHENTICATION_GOOGLE
    ApiOperationType.REGISTRATION -> OperationType.REGISTRATION
    ApiOperationType.PROFILE_UPDATE -> OperationType.PROFILE_UPDATE
    ApiOperationType.SESSION_MANAGEMENT -> OperationType.SESSION_MANAGEMENT
    ApiOperationType.WEB_BRIDGE -> OperationType.WEB_BRIDGE
    ApiOperationType.FACE_KEY_VERIFICATION -> OperationType.FACE_KEY_VERIFICATION
    ApiOperationType.FACE_KEY_CREATION -> OperationType.FACE_KEY_CREATION
    ApiOperationType.FACE_KEY_ENROLLMENT -> OperationType.FACE_KEY_ENROLLMENT
}

internal fun OperationType.toApiModel(): ApiOperationType = when (this) {
    OperationType.LOGIN_ID_COLLECT -> ApiOperationType.LOGIN_ID_COLLECT
    OperationType.EMAIL_VERIFICATION -> ApiOperationType.EMAIL_VERIFICATION
    OperationType.EMAIL_ENROLLMENT -> ApiOperationType.EMAIL_ENROLLMENT
    OperationType.PHONE_NUMBER_VERIFICATION -> ApiOperationType.PHONE_NUMBER_VERIFICATION
    OperationType.PHONE_NUMBER_ENROLLMENT -> ApiOperationType.PHONE_NUMBER_ENROLLMENT
    OperationType.PASSKEY_CREATION -> ApiOperationType.PASSKEY_CREATION
    OperationType.PASSKEY_AUTH -> ApiOperationType.PASSKEY_AUTH
    OperationType.PASSKEY_ENROLLMENT -> ApiOperationType.PASSKEY_ENROLLMENT
    OperationType.SESSION_CREATION -> ApiOperationType.SESSION_CREATION
    OperationType.DEFERRED_AUTHENTICATION -> ApiOperationType.DEFERRED_AUTHENTICATION
    OperationType.EXTERNAL_AUTHENTICATION -> ApiOperationType.EXTERNAL_AUTHENTICATION
    OperationType.PROFILE_COLLECTION -> ApiOperationType.PROFILE_COLLECTION
    OperationType.PASSWORD_AUTHENTICATION -> ApiOperationType.PASSWORD_AUTHENTICATION
    OperationType.OIDC_AUTHENTICATION_APPLE -> ApiOperationType.OIDC_AUTHENTICATION_APPLE
    OperationType.OIDC_AUTHENTICATION_GOOGLE -> ApiOperationType.OIDC_AUTHENTICATION_GOOGLE
    OperationType.REGISTRATION -> ApiOperationType.REGISTRATION
    OperationType.PROFILE_UPDATE -> ApiOperationType.PROFILE_UPDATE
    OperationType.SESSION_MANAGEMENT -> ApiOperationType.SESSION_MANAGEMENT
    OperationType.WEB_BRIDGE -> ApiOperationType.WEB_BRIDGE
    OperationType.FACE_KEY_VERIFICATION -> ApiOperationType.FACE_KEY_VERIFICATION
    OperationType.FACE_KEY_CREATION -> ApiOperationType.FACE_KEY_CREATION
    OperationType.FACE_KEY_ENROLLMENT -> ApiOperationType.FACE_KEY_ENROLLMENT
}

internal fun ApiScopeType.toDomainModel(): ApiFailureScope = when (this) {
    ApiScopeType.DATA -> ApiFailureScope.DATA
    ApiScopeType.CHANNEL -> ApiFailureScope.CHANNEL
    ApiScopeType.SESSION -> ApiFailureScope.SESSION
}
