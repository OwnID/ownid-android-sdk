@file:JvmName("OwnIdApiResults")

package com.ownid.sdk.api

import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.UiError

/**
 * Marker interface for OwnID API capabilities.
 *
 * API capabilities expose direct OwnID operations that complete with typed [ApiResult] values. Callers own the
 * returned result: handle [ApiResult.Success] for endpoint payloads and [ApiResult.Failure] for the endpoint-specific
 * failure model.
 *
 * Coroutine cancellation is not encoded as an [ApiResult]. If the calling coroutine is canceled, cancellation is
 * propagated through normal coroutine cancellation.
 */
public interface ApiCapability : Capability

/**
 * Result of an API call.
 *
 * [Success] carries the endpoint payload. [Failure] carries an endpoint-specific [ApiFailure] value. Each endpoint
 * defines the failures that app code can branch on, including its `Unexpected` case for failures that are not an
 * endpoint-defined business response.
 *
 * Coroutine cancellation is propagated as cancellation and is not represented as an [ApiResult].
 */
public sealed interface ApiResult<out Success, out Failure> {
    public class Success<Success>(public val success: Success) : ApiResult<Success, Nothing> {
        override fun toString(): String = "Success"
    }

    public class Failure<Failure>(public val failure: Failure) : ApiResult<Nothing, Failure> {
        override fun toString(): String = "Failure(failure=$failure)"
    }
}

/**
 * Failure payload returned by direct API calls.
 *
 * Branch on the endpoint-specific failure type when deciding the app's next step, such as keeping the current screen
 * open, showing an error, offering another method, retrying later, or ending the flow. Expected endpoint failures and
 * endpoint `Unexpected` failures both expose this shared error shape.
 *
 * [message] is diagnostic text from the backend or SDK. It is not localized end-user copy. When the app shows an OwnID
 * error to the user, [errorCode] is the localization key to map to app copy or pass to [ErrorCode.toLocalizedMessage]
 * when the SDK default text is appropriate for that screen.
 */
public interface ApiFailure {
    /** Localization key for resolving failure text. */
    public val errorCode: ErrorCode

    /** Diagnostic message associated with the failure. */
    public val message: String
}

/**
 * Returns a UI-ready error for this API failure.
 *
 * The returned [UiError] uses this failure's [ApiFailure.errorCode]. The message is resolved from the OwnID strings
 * available for [instanceName], or from [fallbackErrorStrings] when the instance has no message for this code. The
 * diagnostic [ApiFailure.message] is not used as end-user copy.
 */
public fun ApiFailure.toUiError(
    instanceName: InstanceName = InstanceName.Default,
    fallbackErrorStrings: ErrorStrings = ErrorStrings.DEFAULT
): UiError = UiError(errorCode, errorCode.toLocalizedMessage(instanceName, fallbackErrorStrings))

/**
 * Base type for SDK-level failures that do not correspond to an expected business response.
 *
 * Endpoint-specific `Unexpected` failures extend this type. Treat them as failures where the SDK could not provide a
 * more specific endpoint-defined failure. They remain part of the endpoint failure model, so callers can retry, show a
 * generic error, or route them through the same handling used for other [ApiFailure] values.
 */
public abstract class ApiUnexpectedFailure internal constructor(
    final override val errorCode: ErrorCode,
    final override val message: String,
    @get:JvmSynthetic
    internal val cause: ApiUnexpectedCause
) : ApiFailure {
    override fun toString(): String = "Unexpected(errorCode=$errorCode, message=$message)"
}

/** Backend dependency scope reported by provider or capability dependency failures. */
public enum class ApiFailureScope(public val value: String) {
    DATA("data"),

    CHANNEL("channel"),

    SESSION("session")
}

public inline fun <Success, Failure> ApiResult<Success, Failure>.onSuccess(action: (Success) -> Unit): ApiResult<Success, Failure> = when (this) {
    is ApiResult.Success -> {
        action(success)
        this
    }

    is ApiResult.Failure -> this
}

public inline fun <Success, Failure> ApiResult<Success, Failure>.onError(action: (Failure) -> Unit): ApiResult<Success, Failure> = when (this) {
    is ApiResult.Success -> this
    is ApiResult.Failure -> {
        action(failure)
        this
    }
}

public inline fun <Success, Failure, T> ApiResult<Success, Failure>.fold(onSuccess: (Success) -> T, onError: (Failure) -> T): T = when (this) {
    is ApiResult.Success -> onSuccess(success)
    is ApiResult.Failure -> onError(failure)
}

public inline fun <Success, Failure, T> ApiResult<Success, Failure>.map(transform: (Success) -> T): ApiResult<T, Failure> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(success))
    is ApiResult.Failure -> ApiResult.Failure(failure)
}

public inline fun <Success, Failure, NewFailure> ApiResult<Success, Failure>.mapError(transform: (Failure) -> NewFailure): ApiResult<Success, NewFailure> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(success)
        is ApiResult.Failure -> ApiResult.Failure(transform(failure))
    }

public fun <Success, Failure> ApiResult<Success, Failure>.getOrNull(): Success? = when (this) {
    is ApiResult.Success -> success
    is ApiResult.Failure -> null
}

public fun <Success, Failure> ApiResult<Success, Failure>.errorOrNull(): Failure? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Failure -> failure
}
