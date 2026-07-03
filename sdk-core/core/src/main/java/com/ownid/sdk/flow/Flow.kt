@file:JvmName("OwnIdFlowResults")

package com.ownid.sdk.flow

import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError

/**
 * Marker interface for SDK capabilities that orchestrate authentication flows.
 *
 * Flows sit above direct APIs and operations: they choose the required operations, forward SDK context and provider
 * callbacks, and settle once with a [FlowResult].
 */
public interface FlowCapability : Capability

/**
 * Outcome of an authentication flow.
 *
 * A settled flow has exactly one terminal result: [Success], [Canceled], or [Failure]. Flow controllers return the
 * cached terminal result after settlement, so repeated `whenSettled` calls observe the same value.
 *
 * [Canceled] represents an intentional stop with a [Reason], such as user close, timeout, or system-level cancellation.
 * [Failure] represents a handled flow error with a typed [FlowFailure] payload. Use the specific result case instead
 * of inferring cancellation or failure from messages or error codes.
 *
 * @param R The type of the success payload.
 * @param F The type of the flow-specific failure payload.
 */
public sealed interface FlowResult<out R, out F : FlowFailure> {
    public class Success<R>(public val success: R) : FlowResult<R, Nothing> {
        override fun toString(): String = "Success"
    }

    public class Canceled(public val reason: Reason) : FlowResult<Nothing, Nothing> {
        override fun toString(): String = "Canceled(reason=$reason)"
    }

    public class Failure<F : FlowFailure>(public val failure: F) : FlowResult<Nothing, F> {
        override fun toString(): String = "Failure(failure=$failure)"
    }
}

/**
 * Failure payload returned by completed flows.
 *
 * Concrete flow failure types are declared by each flow. Branch on the flow-specific failure type when deciding the
 * app's next step. Failure payloads are distinct from [FlowResult.Canceled], which carries a [Reason] and does not
 * contain a [FlowFailure].
 *
 * [message] is diagnostic text from the backend, SDK, or provider path. It is not localized end-user copy. When the app
 * shows an OwnID error to the user, map [errorCode] to app copy or call [ErrorCode.toLocalizedMessage] when the SDK
 * default text is appropriate for that screen. [errorCode] is a localization key, not the semantic failure contract.
 */
public interface FlowFailure {
    /** Localization key for resolving failure text. */
    public val errorCode: ErrorCode

    /** Diagnostic message associated with the failure. */
    public val message: String
}

/**
 * Returns a UI-ready error for this flow failure.
 *
 * The returned [UiError] uses this failure's [FlowFailure.errorCode]. The message is resolved from the OwnID strings
 * available for [instanceName], or from [fallbackErrorStrings] when the instance has no message for this code. The
 * diagnostic [FlowFailure.message] is not copied into the UI error.
 */
public fun FlowFailure.toUiError(
    instanceName: InstanceName = InstanceName.Default,
    fallbackErrorStrings: ErrorStrings = ErrorStrings.DEFAULT
): UiError = UiError(errorCode, errorCode.toLocalizedMessage(instanceName, fallbackErrorStrings))

public inline fun <R, F : FlowFailure> FlowResult<R, F>.onSuccess(action: (R) -> Unit): FlowResult<R, F> {
    if (this is FlowResult.Success) action(success)
    return this
}

public inline fun <R, F : FlowFailure> FlowResult<R, F>.onError(action: (F) -> Unit): FlowResult<R, F> {
    if (this is FlowResult.Failure) action(failure)
    return this
}

public inline fun <R, F : FlowFailure> FlowResult<R, F>.onCanceled(action: (Reason) -> Unit): FlowResult<R, F> {
    if (this is FlowResult.Canceled) action(reason)
    return this
}

public inline fun <R, F : FlowFailure, T> FlowResult<R, F>.fold(
    onSuccess: (R) -> T,
    onCanceled: (Reason) -> T,
    onError: (F) -> T
): T = when (this) {
    is FlowResult.Success -> onSuccess(success)
    is FlowResult.Canceled -> onCanceled(reason)
    is FlowResult.Failure -> onError(failure)
}

public inline fun <R, F : FlowFailure, T> FlowResult<R, F>.map(transform: (R) -> T): FlowResult<T, F> = when (this) {
    is FlowResult.Success -> FlowResult.Success(transform(success))
    is FlowResult.Canceled -> this
    is FlowResult.Failure -> this
}

public fun <R, F : FlowFailure> FlowResult<R, F>.getOrNull(): R? = when (this) {
    is FlowResult.Success -> success
    is FlowResult.Canceled, is FlowResult.Failure -> null
}

public fun <R, F : FlowFailure> FlowResult<R, F>.errorOrNull(): F? = when (this) {
    is FlowResult.Failure -> failure
    is FlowResult.Success, is FlowResult.Canceled -> null
}

public fun <R, F : FlowFailure> FlowResult<R, F>.reasonOrNull(): Reason? = when (this) {
    is FlowResult.Canceled -> reason
    is FlowResult.Success, is FlowResult.Failure -> null
}
